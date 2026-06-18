package com.merchanthub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchanthub.config.AppProperties;
import com.merchanthub.dto.WebhookDtos.WebhookPayload;
import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Receives signed new-order webhooks from the shop API (the push ingestion path).
 * Verifies the HMAC-SHA256 signature, maps the payload to its tenant by API key,
 * and persists the order. The resulting row change is what Supabase Realtime
 * broadcasts live to the dashboard.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final MerchantResolver merchantResolver;
    private final WebhookPersistenceService persistence;

    public WebhookService(AppProperties props, ObjectMapper objectMapper, MerchantResolver merchantResolver,
                          WebhookPersistenceService persistence) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.merchantResolver = merchantResolver;
        this.persistence = persistence;
    }

    public void handleOrderWebhook(String rawBody, String signature) {
        if (!verifySignature(rawBody, signature)) {
            throw new ApiExceptions.Unauthorized("Invalid webhook signature");
        }
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            throw new ApiExceptions.BadRequest("Malformed webhook body: " + e.getMessage());
        }
        if (payload.apiKey() == null || payload.order() == null) {
            throw new ApiExceptions.BadRequest("Webhook missing apiKey or order");
        }

        MerchantResolver.MerchantRow merchant = merchantResolver.findByApiKey(payload.apiKey())
                .orElseThrow(() -> new ApiExceptions.Unauthorized("Unknown shop API key"));

        // Pin the tenant, then run the persistence in a separate, proxied bean so
        // its @Transactional (and the tenant aspect) actually engage.
        TenantContext.setMerchantId(merchant.id());
        try {
            persistence.persist(merchant.id(), payload.order());
            log.info("Webhook order {} ingested for merchant {}", payload.order().externalId(), merchant.id());
        } finally {
            TenantContext.clear();
        }
    }

    private boolean verifySignature(String body, String signature) {
        if (signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
