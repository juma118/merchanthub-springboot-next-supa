package com.merchanthub.service;

import com.merchanthub.dto.AuthDtos.AuthResponse;
import com.merchanthub.dto.AuthDtos.MerchantBrief;
import com.merchanthub.security.JwtService;
import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Self-contained email/password auth — no external identity provider. Tokens
 * are the same HS256 JWTs {@link com.merchanthub.security.JwtAuthFilter}
 * already validates; {@code auth_user_id} is just an internal id minted at
 * registration and used as the JWT {@code sub}.
 */
@Service
public class AuthService {

    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60;
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final JwtService jwtService;
    private final MerchantResolver merchantResolver;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JwtService jwtService, MerchantResolver merchantResolver, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.merchantResolver = merchantResolver;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(String name, String email, String password) {
        String normalized = email.trim().toLowerCase();
        if (merchantResolver.findByEmail(normalized).isPresent()) {
            throw new ApiExceptions.Conflict("An account with this email already exists");
        }

        UUID authUid = UUID.randomUUID();
        String hash = passwordEncoder.encode(password);
        UUID merchantId = merchantResolver.registerWithPassword(authUid, name.trim(), normalized, hash);

        return issueToken(authUid, merchantId, name.trim(), normalized);
    }

    public AuthResponse login(String email, String password) {
        String normalized = email.trim().toLowerCase();
        MerchantResolver.MerchantRow merchant = merchantResolver.findByEmail(normalized)
                .orElseThrow(() -> new ApiExceptions.Unauthorized(INVALID_CREDENTIALS));

        if (merchant.passwordHash() == null || !passwordEncoder.matches(password, merchant.passwordHash())) {
            throw new ApiExceptions.Unauthorized(INVALID_CREDENTIALS);
        }

        return issueToken(merchant.authUserId(), merchant.id(), merchant.name(), merchant.email());
    }

    private AuthResponse issueToken(UUID authUid, UUID merchantId, String name, String email) {
        String token = jwtService.mint(authUid, email, TOKEN_TTL_SECONDS);
        return new AuthResponse(token, "Bearer", new MerchantBrief(merchantId, name, email));
    }
}
