package com.merchanthub.service;

import com.merchanthub.domain.Inventory;
import com.merchanthub.domain.Product;
import com.merchanthub.dto.CommonDtos.PageResponse;
import com.merchanthub.dto.ProductDtos.*;
import com.merchanthub.repo.InventoryRepository;
import com.merchanthub.repo.ProductRepository;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductService {

    private final ProductRepository products;
    private final InventoryRepository inventory;

    public ProductService(ProductRepository products, InventoryRepository inventory) {
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String q, int page, int size) {
        UUID mid = TenantContext.requireMerchantId();
        Page<Product> result = products.search(mid,
                (q == null || q.isBlank()) ? "" : q.trim(),
                PageRequest.of(page, size, Sort.by("name").ascending()));
        Map<UUID, Inventory> invByProduct = new HashMap<>();
        for (Inventory inv : inventory.findByMerchantId(mid)) invByProduct.put(inv.getProductId(), inv);
        return PageResponse.of(result, p -> toResponse(p, invByProduct.get(p.getId())));
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        UUID mid = TenantContext.requireMerchantId();
        if (products.existsByMerchantIdAndSku(mid, req.sku())) {
            throw new ApiExceptions.Conflict("A product with SKU '" + req.sku() + "' already exists");
        }
        Product p = new Product();
        p.setMerchantId(mid);
        apply(p, req);
        products.save(p);
        Inventory inv = upsertInventory(mid, p.getId(),
                req.quantity() != null ? req.quantity() : 0,
                req.lowStockThreshold() != null ? req.lowStockThreshold() : 5);
        return toResponse(p, inv);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest req) {
        UUID mid = TenantContext.requireMerchantId();
        Product p = products.findByIdAndMerchantId(id, mid)
                .orElseThrow(() -> new ApiExceptions.NotFound("Product not found"));
        if (!p.getSku().equals(req.sku()) && products.existsByMerchantIdAndSku(mid, req.sku())) {
            throw new ApiExceptions.Conflict("A product with SKU '" + req.sku() + "' already exists");
        }
        apply(p, req);
        products.save(p);
        Inventory inv = inventory.findByMerchantIdAndProductId(mid, id).orElse(null);
        if (req.quantity() != null || req.lowStockThreshold() != null) {
            int qty = req.quantity() != null ? req.quantity() : (inv != null ? inv.getQuantity() : 0);
            int thr = req.lowStockThreshold() != null ? req.lowStockThreshold() : (inv != null ? inv.getLowStockThreshold() : 5);
            inv = upsertInventory(mid, id, qty, thr);
        }
        return toResponse(p, inv);
    }

    @Transactional
    public void delete(UUID id) {
        UUID mid = TenantContext.requireMerchantId();
        Product p = products.findByIdAndMerchantId(id, mid)
                .orElseThrow(() -> new ApiExceptions.NotFound("Product not found"));
        products.delete(p);  // inventory + order_items cascade at the DB level
    }

    // ── CSV ──────────────────────────────────────────────────────────────────
    private static final String CSV_HEADER = "sku,name,description,price,quantity,low_stock_threshold,image_url";

    @Transactional
    public ImportResult importCsv(String csv) {
        UUID mid = TenantContext.requireMerchantId();
        List<String> lines = csv.lines().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) return new ImportResult(0, 0, List.of("Empty file"));

        int start = lines.get(0).toLowerCase().contains("sku") ? 1 : 0;
        int imported = 0, updated = 0;
        List<String> errors = new ArrayList<>();

        for (int i = start; i < lines.size(); i++) {
            int rowNum = i + 1;
            try {
                List<String> cols = parseCsvLine(lines.get(i));
                String sku = col(cols, 0);
                String name = col(cols, 1);
                if (sku == null || sku.isBlank() || name == null || name.isBlank()) {
                    errors.add("Row " + rowNum + ": sku and name are required");
                    continue;
                }
                BigDecimal price = parseDecimal(col(cols, 3));
                Integer qty = parseInt(col(cols, 4));
                Integer thr = parseInt(col(cols, 5));

                Optional<Product> existing = products.findByMerchantIdAndSku(mid, sku);
                Product p = existing.orElseGet(Product::new);
                p.setMerchantId(mid);
                p.setSku(sku);
                p.setName(name);
                p.setDescription(col(cols, 2));
                p.setPrice(price);
                p.setImageUrl(col(cols, 6));
                products.save(p);
                upsertInventory(mid, p.getId(), qty != null ? qty : 0, thr != null ? thr : 5);

                if (existing.isPresent()) updated++; else imported++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
            }
        }
        return new ImportResult(imported, updated, errors);
    }

    @Transactional(readOnly = true)
    public String exportCsv() {
        UUID mid = TenantContext.requireMerchantId();
        Map<UUID, Inventory> inv = new HashMap<>();
        for (Inventory i : inventory.findByMerchantId(mid)) inv.put(i.getProductId(), i);
        StringBuilder sb = new StringBuilder(CSV_HEADER).append("\n");
        for (Product p : products.findByMerchantId(mid)) {
            Inventory i = inv.get(p.getId());
            sb.append(csv(p.getSku())).append(',')
                    .append(csv(p.getName())).append(',')
                    .append(csv(p.getDescription())).append(',')
                    .append(p.getPrice() == null ? "0" : p.getPrice().toPlainString()).append(',')
                    .append(i != null ? i.getQuantity() : 0).append(',')
                    .append(i != null ? i.getLowStockThreshold() : 5).append(',')
                    .append(csv(p.getImageUrl())).append('\n');
        }
        return sb.toString();
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private void apply(Product p, ProductRequest req) {
        p.setSku(req.sku());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPrice(req.price() != null ? req.price() : BigDecimal.ZERO);
        p.setImageUrl(req.imageUrl());
    }

    private Inventory upsertInventory(UUID merchantId, UUID productId, int qty, int thr) {
        Inventory inv = inventory.findByMerchantIdAndProductId(merchantId, productId)
                .orElseGet(Inventory::new);
        inv.setMerchantId(merchantId);
        inv.setProductId(productId);
        inv.setQuantity(qty);
        inv.setLowStockThreshold(thr);
        return inventory.save(inv);
    }

    private ProductResponse toResponse(Product p, Inventory inv) {
        InventoryView view = new InventoryView(
                inv != null ? inv.getQuantity() : 0,
                inv != null ? inv.getLowStockThreshold() : 5);
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getPrice(), p.getImageUrl(), p.getExternalId(), p.getCreatedAt(), p.getUpdatedAt(), view);
    }

    private static String col(List<String> cols, int idx) {
        return idx < cols.size() ? emptyToNull(cols.get(idx)) : null;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null) return BigDecimal.ZERO;
        return new BigDecimal(s.trim());
    }

    private static Integer parseInt(String s) {
        return s == null ? null : Integer.valueOf(s.trim());
    }

    /** Minimal RFC-4180-ish line parser (handles quoted fields with commas). */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuotes = false;
                } else cur.append(c);
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString()); cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
