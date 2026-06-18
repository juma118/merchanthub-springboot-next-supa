package com.merchanthub.web;

import com.merchanthub.dto.CommonDtos.MeResponse;
import com.merchanthub.dto.SyncDtos.SyncLogResponse;
import com.merchanthub.dto.SyncDtos.SyncRunResponse;
import com.merchanthub.service.MerchantService;
import com.merchanthub.service.SyncService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final MerchantService merchantService;

    public SyncController(SyncService syncService, MerchantService merchantService) {
        this.syncService = syncService;
        this.merchantService = merchantService;
    }

    /** On-demand pull-sync for the authenticated merchant. */
    @PostMapping("/run")
    public SyncRunResponse run() {
        MeResponse me = merchantService.getCurrent();
        return syncService.doSync(me.id(), me.shopApiKey());
    }

    @GetMapping("/logs")
    public List<SyncLogResponse> logs() {
        return syncService.recentLogs();
    }
}
