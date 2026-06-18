package com.merchanthub.service;

import com.merchanthub.config.AppProperties;
import com.merchanthub.dto.AuthDtos.DevTokenResponse;
import com.merchanthub.dto.AuthDtos.MerchantBrief;
import com.merchanthub.security.JwtService;
import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Issues HS256 tokens for local development so the whole stack is runnable
 * without a real Supabase project. Disabled unless {@code merchanthub.dev-auth-enabled}.
 */
@Service
public class AuthService {

    private static final long DEV_TOKEN_TTL_SECONDS = 24 * 60 * 60;

    private final AppProperties props;
    private final JwtService jwtService;
    private final MerchantResolver merchantResolver;

    public AuthService(AppProperties props, JwtService jwtService, MerchantResolver merchantResolver) {
        this.props = props;
        this.jwtService = jwtService;
        this.merchantResolver = merchantResolver;
    }

    public DevTokenResponse devToken(String email) {
        if (!props.isDevAuthEnabled()) {
            throw new ApiExceptions.Forbidden("Dev auth is disabled");
        }
        String normalized = email.trim().toLowerCase();

        MerchantResolver.MerchantRow merchant = merchantResolver.findByEmail(normalized).orElse(null);
        UUID authUid;
        UUID merchantId;
        String name;
        if (merchant != null) {
            authUid = merchant.authUserId();
            merchantId = merchant.id();
            name = merchant.name();
        } else {
            // Stable synthetic auth uid for a brand-new dev user, then provision.
            authUid = UUID.nameUUIDFromBytes(("merchanthub:" + normalized).getBytes(StandardCharsets.UTF_8));
            name = deriveName(normalized);
            merchantId = merchantResolver.provision(authUid, name, normalized);
        }

        String token = jwtService.mint(authUid, normalized, DEV_TOKEN_TTL_SECONDS);
        return new DevTokenResponse(token, "Bearer", new MerchantBrief(merchantId, name, normalized));
    }

    private static String deriveName(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
