package com.merchanthub.service;

import com.merchanthub.domain.Merchant;
import com.merchanthub.dto.CommonDtos.MeResponse;
import com.merchanthub.repo.MerchantRepository;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    private final MerchantRepository merchants;

    public MerchantService(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrent() {
        Merchant m = merchants.findById(TenantContext.requireMerchantId())
                .orElseThrow(() -> new ApiExceptions.NotFound("Merchant not found"));
        return new MeResponse(m.getId(), m.getName(), m.getEmail(), m.getShopApiKey());
    }
}
