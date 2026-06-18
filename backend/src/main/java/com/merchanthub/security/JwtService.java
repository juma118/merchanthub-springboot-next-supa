package com.merchanthub.security;

import com.merchanthub.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Validates Supabase-style HS256 JWTs and (in dev) mints them. Supabase signs
 * access tokens with the project's JWT secret using HS256; the {@code sub} claim
 * is the auth user id and {@code email} carries the user's email.
 */
@Service
public class JwtService {

    private final byte[] secret;

    public JwtService(AppProperties props) {
        if (props.getJwtSecret() == null || props.getJwtSecret().length() < 32) {
            throw new IllegalStateException("merchanthub.jwt-secret must be set and >= 32 chars");
        }
        this.secret = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }

    public record Claims(UUID sub, String email) {}

    /** Verifies signature + expiry and returns the relevant claims. */
    public Claims validate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new JwtException("Invalid token signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                throw new JwtException("Token expired");
            }
            String sub = claims.getSubject();
            if (sub == null) {
                throw new JwtException("Token missing 'sub' claim");
            }
            return new Claims(UUID.fromString(sub), (String) claims.getClaim("email"));
        } catch (ParseException | JOSEException e) {
            throw new JwtException("Malformed token: " + e.getMessage());
        }
    }

    /** Mints a short-lived HS256 token (dev only). */
    public String mint(UUID sub, String email, long ttlSeconds) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sub.toString())
                    .claim("email", email)
                    .claim("role", "authenticated")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(ttlSeconds)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to mint token", e);
        }
    }

    public static class JwtException extends RuntimeException {
        public JwtException(String message) { super(message); }
    }
}
