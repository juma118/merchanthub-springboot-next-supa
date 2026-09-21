package com.merchanthub.webhookingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public edge endpoint shops POST orders to. Verifies the HMAC signature and
 * resolves the merchant here — fast, native-image-startup work — then hands
 * the validated order to Kafka and returns immediately. The Spring Boot
 * backend (WebhookEventListener) does the actual persistence asynchronously.
 */
@Path("/webhooks/orders")
public class WebhookIngestResource {

    private static final Logger LOG = Logger.getLogger(WebhookIngestResource.class);

    @Inject WebhookSignatureVerifier signatureVerifier;
    @Inject MerchantLookupService merchantLookup;
    @Inject WebhookEventPublisher publisher;
    @Inject ObjectMapper objectMapper;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receiveOrder(String rawBody, @HeaderParam("X-Shop-Signature") String signature) {
        if (!signatureVerifier.verify(rawBody, signature)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid webhook signature"))
                    .build();
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Malformed webhook body: " + e.getMessage()))
                    .build();
        }
        if (payload.apiKey() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Webhook missing apiKey"))
                    .build();
        }

        Optional<UUID> merchantId = merchantLookup.findMerchantIdByApiKey(payload.apiKey());
        if (merchantId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Unknown shop API key"))
                    .build();
        }

        publisher.publish(merchantId.get(), rawBody);
        LOG.infof("Webhook accepted for merchant %s, published to Kafka", merchantId.get());

        return Response.accepted(Map.of("status", "accepted")).build();
    }
}
