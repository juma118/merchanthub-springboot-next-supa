package com.merchanthub.security;

import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates every request carrying a Bearer JWT: validates the token,
 * resolves (or auto-provisions) the owning merchant, and pins it into both the
 * Spring Security context and the {@link TenantContext} for the duration of the
 * request. Requests without a token pass through unauthenticated and are
 * rejected later by the security rules if the path is protected.
 *
 * <p>Intentionally NOT a Spring bean: that would make Spring Boot auto-register it
 * as a servlet filter in addition to the security chain, running it twice. It is
 * constructed directly in {@link SecurityConfig}.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MerchantResolver merchantResolver;

    public JwtAuthFilter(JwtService jwtService, MerchantResolver merchantResolver) {
        this.jwtService = jwtService;
        this.merchantResolver = merchantResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        JwtService.Claims claims;
        try {
            claims = jwtService.validate(header.substring(7).trim());
        } catch (JwtService.JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            return;
        }

        try {
            MerchantResolver.MerchantRow merchant = merchantResolver.findByAuthUid(claims.sub())
                    .orElseGet(() -> {
                        String email = claims.email();
                        String name = deriveName(email);
                        UUID id = merchantResolver.provision(claims.sub(), name, email);
                        return new MerchantResolver.MerchantRow(id, claims.sub(), name, email, null);
                    });

            MerchantPrincipal principal = new MerchantPrincipal(merchant.id(), claims.sub(), claims.email());
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.setMerchantId(merchant.id());

            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static String deriveName(String email) {
        if (email == null || email.isBlank()) return "New Merchant";
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
