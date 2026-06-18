package com.merchanthub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record DevTokenRequest(@NotBlank @Email String email) {}

    public record MerchantBrief(UUID id, String name, String email) {}

    public record DevTokenResponse(String token, String tokenType, MerchantBrief merchant) {}
}
