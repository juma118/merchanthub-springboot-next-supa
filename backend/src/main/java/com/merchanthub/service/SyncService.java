package com.merchanthub.service;

import com.merchanthub.domain.Alert;
import com.merchanthub.domain.Inventory;
import com.merchanthub.domain.Product;
import com.merchanthub.domain.SyncLog;
import com.merchanthub.dto.SyncDtos.SyncLogResponse;
import com.merchanthub.dto.SyncDtos.SyncRunResponse;
import com.merchanthub.integration.ShopApiClient;
import com.merchanthub.integration.ShopDtos;
import com.merchanthub.repo.InventoryRepository;
import com.merchanthub.repo.ProductRepository;
import com.merchanthub.repo.SyncLogRepository;
import com.merchanthub.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled / on-demand PULL reconciliation against the shop API — the reliable
 * counterpart to webhooks. Upserts the catalog + inventory levels and ingests any
 * orders that webhooks may have missed. Every run is recorded in {@code sync_logs}.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final ShopApiClient shopApi;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final OrderIngestionService ingestion;
    private final SyncLogRepository syncLogs;
    private final AlertService alerts;

    public SyncService(ShopApiClient shopApi, ProductRepository products, InventoryRepository inventory,
                       OrderIngestionService ingestion, SyncLogRepository syncLogs, AlertService alerts) {
        this.shopApi = shopApi;
        this.products = products;
        this.inventory = inventory;
        this.ingestion = ingestion;
        this.syncLogs = syncLogs;
        this.alerts = alerts;
    }

    /**
     * Reconcile one merchant. The caller MUST have pinned the tenant
     * ({@link TenantContext}) to {@code merchantId} — the JWT filter does this for
     * API requests and the scheduler does it explicitly.
     */
    @Transactional
    public SyncRunResponse doSync(UUID merchantId, String apiKey) {
        SyncLog logRow = new SyncLog();
        logRow.setMerchantId(merchantId);
        logRow.setType(SyncLog.TYPE_PULL);
        int processed = 0;
        try {
            // 1) Catalog + inventory levels (the shop API is authoritative on stock).
            for (ShopDtos.ShopProduct sp : shopApi.fetchProducts(apiKey)) {
                upsertProduct(merchantId, sp);
                processed++;
            }
            // 2) Orders that may have been missed by webhooks (idempotent ingest).
            for (ShopDtos.ShopOrder so : shopApi.fetchOrders(apiKey, null)) {
                List<OrderIngestionService.ItemInput> items = (so.items() == null ? List.<ShopDtos.ShopItem>of() : so.items())
                        .stream()
                        .map(i -> new OrderIngestionService.ItemInput(i.sku(), i.quantity(), i.unitPrice()))
                        .toList();
                if (ingestion.ingest(so.externalId(), so.total(), so.currency(), so.status(),
                        so.customerEmail(), so.createdAt(), items)) {
                    processed++;
                }
            }
            logRow.setStatus(SyncLog.SUCCESS);
            logRow.setRecordsProcessed(processed);
            logRow.setDetail("Pull sync completed");
            logRow.setFinishedAt(Instant.now());
            syncLogs.save(logRow);

            Map<String, Object> payload = new HashMap<>();
            payload.put("records_processed", processed);
            alerts.create(Alert.SYNC_COMPLETE, payload);

            log.info("Pull sync for merchant {} processed {} records", merchantId, processed);
            return new SyncRunResponse(logRow.getId(), logRow.getStatus(), processed);
        } catch (Exception e) {
            logRow.setStatus(SyncLog.FAILED);
            logRow.setDetail(e.getMessage());
            logRow.setRecordsProcessed(processed);
            logRow.setFinishedAt(Instant.now());
            syncLogs.save(logRow);

            Map<String, Object> payload = new HashMap<>();
            payload.put("error", e.getMessage());
            alerts.create(Alert.SYNC_FAILED, payload);

            log.error("Pull sync for merchant {} failed: {}", merchantId, e.getMessage());
            return new SyncRunResponse(logRow.getId(), logRow.getStatus(), processed);
        }
    }

    @Transactional(readOnly = true)
    public List<SyncLogResponse> recentLogs() {
        return syncLogs.findByMerchantIdOrderByStartedAtDesc(
                        TenantContext.requireMerchantId(), PageRequest.of(0, 50))
                .stream()
                .map(s -> new SyncLogResponse(s.getId(), s.getType(), s.getStatus(), s.getDetail(),
                        s.getRecordsProcessed(), s.getStartedAt(), s.getFinishedAt()))
                .toList();
    }

    private void upsertProduct(UUID merchantId, ShopDtos.ShopProduct sp) {
        Product p = products.findByMerchantIdAndExternalId(merchantId, sp.externalId())
                .or(() -> products.findByMerchantIdAndSku(merchantId, sp.sku()))
                .orElseGet(Product::new);
        p.setMerchantId(merchantId);
        p.setExternalId(sp.externalId());
        p.setSku(sp.sku());
        p.setName(sp.name());
        p.setDescription(sp.description());
        p.setPrice(sp.price() != null ? sp.price() : BigDecimal.ZERO);
        p.setImageUrl(sp.imageUrl());
        products.save(p);

        if (sp.quantity() != null) {
            Inventory inv = inventory.findByMerchantIdAndProductId(merchantId, p.getId())
                    .orElseGet(Inventory::new);
            inv.setMerchantId(merchantId);
            inv.setProductId(p.getId());
            inv.setQuantity(sp.quantity());
            if (inv.getLowStockThreshold() == 0) inv.setLowStockThreshold(5);
            inventory.save(inv);
        }
    }
}
