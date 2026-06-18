package com.merchanthub.service;

import com.merchanthub.domain.Alert;
import com.merchanthub.dto.AlertDtos.AlertResponse;
import com.merchanthub.repo.AlertRepository;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRepository alerts;

    public AlertService(AlertRepository alerts) {
        this.alerts = alerts;
    }

    /** Persists an alert for the current tenant. The row change is what Supabase
     *  Realtime broadcasts to the subscribed dashboard. */
    @Transactional
    public Alert create(String type, Map<String, Object> payload) {
        Alert a = new Alert();
        a.setMerchantId(TenantContext.requireMerchantId());
        a.setType(type);
        a.setPayload(payload);
        return alerts.save(a);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(boolean unreadOnly, int limit) {
        UUID mid = TenantContext.requireMerchantId();
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        var rows = unreadOnly
                ? alerts.findByMerchantIdAndReadFalseOrderByCreatedAtDesc(mid, page)
                : alerts.findByMerchantIdOrderByCreatedAtDesc(mid, page);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return alerts.countByMerchantIdAndReadFalse(TenantContext.requireMerchantId());
    }

    @Transactional
    public void markRead(UUID id) {
        Alert a = alerts.findByIdAndMerchantId(id, TenantContext.requireMerchantId())
                .orElseThrow(() -> new ApiExceptions.NotFound("Alert not found"));
        a.setRead(true);
        alerts.save(a);
    }

    @Transactional
    public void markAllRead() {
        alerts.markAllRead(TenantContext.requireMerchantId());
    }

    public AlertResponse toResponse(Alert a) {
        return new AlertResponse(a.getId(), a.getType(), a.getPayload(), a.isRead(), a.getCreatedAt());
    }
}
