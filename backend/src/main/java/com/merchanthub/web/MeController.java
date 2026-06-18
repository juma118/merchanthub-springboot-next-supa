package com.merchanthub.web;

import com.merchanthub.dto.CommonDtos.MeResponse;
import com.merchanthub.service.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MerchantService merchantService;

    public MeController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public MeResponse me() {
        return merchantService.getCurrent();
    }
}
