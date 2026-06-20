package com.merchanthub.service;

import com.merchanthub.domain.Alert;
import com.merchanthub.domain.Inventory;
import com.merchanthub.domain.Product;
import com.merchanthub.dto.ProductDtos.InventoryRow;
import com.merchanthub.dto.ProductDtos.InventoryUpdateRequest;
import com.merchanthub.repo.InventoryRepository;
import com.merchanthub.repo.ProductRepository;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventory;
    private final ProductRepository products;
    private final AlertService alerts;
    private final com.merchanthub.messaging.OutboxService outbox;

    public InventoryService(InventoryRepository inventory, ProductRepository products, AlertService alerts,
                            com.merchanthub.messaging.OutboxService outbox) {
        this.inventory = inventory;
        this.products = products;
        this.alerts = alerts;
        this.outbox = outbox;
    }

    public record ItemQty(String sku, int quantity) {}

    @Transactional(readOnly = true)
    public List<InventoryRow> list() {
        UUID mid = TenantContext.requireMerchantId();
        Map<UUID, Product> byId = new HashMap<>();
        for (Product p : products.findByMerchantId(mid)) byId.put(p.getId(), p);
        return inventory.findByMerchantId(mid).stream()
                .map(inv -> {
                    Product p = byId.get(inv.getProductId());
                    return new InventoryRow(
                            inv.getProductId(),
                            p != null ? p.getSku() : null,
                            p != null ? p.getName() : null,
                            inv.getQuantity(),
                            inv.getLowStockThreshold(),
                            inv.isLowStock());
                })
                .sorted((a, b) -> Boolean.compare(b.lowStock(), a.lowStock()))
                .toList();
    }

    @Transactional
    public InventoryRow update(UUID productId, InventoryUpdateRequest req) {
        UUID mid = TenantContext.requireMerchantId();
        Product p = products.findByIdAndMerchantId(productId, mid)
                .orElseThrow(() -> new ApiExceptions.NotFound("Product not found"));
        Inventory inv = inventory.findByMerchantIdAndProductId(mid, productId)
                .orElseGet(() -> newInventory(mid, productId));
        boolean wasOk = inv.getQuantity() > inv.getLowStockThreshold();
        inv.setQuantity(req.quantity());
        inv.setLowStockThreshold(req.lowStockThreshold());
        inventory.save(inv);
        maybeLowStockAlert(p, inv, wasOk);
        return new InventoryRow(productId, p.getSku(), p.getName(),
                inv.getQuantity(), inv.getLowStockThreshold(), inv.isLowStock());
    }

    @Transactional
    public void applyOrderItems(List<ItemQty> items) {
        UUID mid = TenantContext.requireMerchantId();
        for (ItemQty item : items) {
            if (item.sku() == null) continue;
            products.findByMerchantIdAndSku(mid, item.sku()).ifPresent(p -> {
                Inventory inv = inventory.findByMerchantIdAndProductId(mid, p.getId())
                        .orElseGet(() -> newInventory(mid, p.getId()));
                boolean wasOk = inv.getQuantity() > inv.getLowStockThreshold();
                inv.setQuantity(Math.max(0, inv.getQuantity() - item.quantity()));
                inventory.save(inv);
                maybeLowStockAlert(p, inv, wasOk);
            });
        }
    }

    private Inventory newInventory(UUID merchantId, UUID productId) {
        Inventory inv = new Inventory();
        inv.setMerchantId(merchantId);
        inv.setProductId(productId);
        inv.setQuantity(0);
        inv.setLowStockThreshold(5);
        return inv;
    }

    private void maybeLowStockAlert(Product p, Inventory inv, boolean wasOk) {
        if (wasOk && inv.isLowStock()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("product_id", p.getId().toString());
            payload.put("sku", p.getSku());
            payload.put("name", p.getName());
            payload.put("quantity", inv.getQuantity());
            payload.put("threshold", inv.getLowStockThreshold());
            alerts.create(Alert.LOW_STOCK, payload);

            // Mirror the alert as a domain event for downstream services (e.g. email/Slack).
            Map<String, Object> event = new HashMap<>(payload);
            event.put("event", "LowStockDetected");
            event.put("merchantId", p.getMerchantId().toString());
            event.put("occurredAt", java.time.Instant.now().toString());
            outbox.append(com.merchanthub.messaging.OutboxService.TOPIC_LOW_STOCK,
                    p.getMerchantId().toString(), "LowStockDetected", event);
        }
    }
}
