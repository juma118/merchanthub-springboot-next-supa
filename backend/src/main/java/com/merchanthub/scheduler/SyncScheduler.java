package com.merchanthub.scheduler;

import com.merchanthub.service.SyncService;
import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pull-sync across all tenants — the reliability backstop for missed webhooks.
 *
 * <p>Runs on a background thread with no JWT, so it explicitly pins the tenant
 * context per merchant before delegating to {@link SyncService} (a separate bean,
 * so its {@code @Transactional} and the tenant aspect engage). Scheduling cadence
 * is wired in {@link SchedulingConfig} so an interval of 0 cleanly disables it.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final MerchantResolver merchantResolver;
    private final SyncService syncService;

    public SyncScheduler(MerchantResolver merchantResolver, SyncService syncService) {
        this.merchantResolver = merchantResolver;
        this.syncService = syncService;
    }

    public void runAllTenants() {
        log.info("Starting scheduled pull-sync across all tenants");
        for (MerchantResolver.SyncTarget target : merchantResolver.listForSync()) {
            try {
                TenantContext.setMerchantId(target.merchantId());
                syncService.doSync(target.merchantId(), target.shopApiKey());
            } catch (Exception e) {
                log.error("Scheduled sync failed for merchant {}: {}", target.merchantId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
