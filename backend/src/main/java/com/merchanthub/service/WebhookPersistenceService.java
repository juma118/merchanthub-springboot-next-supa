package com.merchanthub.service;

import com.merchanthub.domain.SyncLog;
import com.merchanthub.dto.WebhookDtos.WebhookItem;
import com.merchanthub.dto.WebhookDtos.WebhookOrder;
import com.merchanthub.repo.SyncLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional persistence for an inbound webhook order. Kept in its own bean so
 * the {@code @Transactional} proxy (and the tenant-isolation aspect) engage when
 * called from {@link WebhookService}. Records a {@code webhook} sync-log entry in
 * the same transaction as the order, so ingestion and its audit trail commit
 * atomically.
 */
@Service
public class WebhookPersistenceService {

    private final OrderIngestionService ingestion;
    private final SyncLogRepository syncLogs;

    public WebhookPersistenceService(OrderIngestionService ingestion, SyncLogRepository syncLogs) {
        this.ingestion = ingestion;
        this.syncLogs = syncLogs;
    }

    @Transactional
    public void persist(UUID merchantId, WebhookOrder order) {
        SyncLog logRow = new SyncLog();
        logRow.setMerchantId(merchantId);
        logRow.setType(SyncLog.TYPE_WEBHOOK);
        try {
            List<OrderIngestionService.ItemInput> items = (order.items() == null ? List.<WebhookItem>of() : order.items())
                    .stream()
                    .map(i -> new OrderIngestionService.ItemInput(i.sku(), i.quantity(), i.unitPrice()))
                    .toList();
            boolean created = ingestion.ingest(order.externalId(), order.total(), order.currency(),
                    order.status(), order.customerEmail(), order.createdAt(), items);
            logRow.setStatus(SyncLog.SUCCESS);
            logRow.setRecordsProcessed(created ? 1 : 0);
            logRow.setDetail(created ? "Order ingested via webhook" : "Duplicate order ignored");
        } finally {
            logRow.setFinishedAt(Instant.now());
            syncLogs.save(logRow);
        }
    }
}
