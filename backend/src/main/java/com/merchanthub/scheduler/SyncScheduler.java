package com.merchanthub.scheduler;

import com.merchanthub.service.SyncService;
import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
