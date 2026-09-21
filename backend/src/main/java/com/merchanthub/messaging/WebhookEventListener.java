package com.merchanthub.messaging;

import com.merchanthub.dto.WebhookDtos.WebhookOrder;
import com.merchanthub.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes orders already HMAC-verified and merchant-resolved by the
 * standalone webhook-ingest-service (Quarkus) and persists them. Splitting
 * verification (edge, native, fast-start) from persistence (this consumer)
 * keeps the public webhook front door decoupled from the Spring Boot
 * deploy/restart cadence.
 */
@Component
public class WebhookEventListener {

    public static final String TOPIC_ORDER_WEBHOOK_RECEIVED = "order.webhook.received";

    private static final Logger log = LoggerFactory.getLogger(WebhookEventListener.class);

    private final WebhookService webhookService;

    public WebhookEventListener(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @KafkaListener(
            topics = TOPIC_ORDER_WEBHOOK_RECEIVED,
            groupId = "${spring.kafka.consumer.group-id:merchanthub-backend}")
    public void onOrderWebhookReceived(
            @Payload String rawBody,
            @Header(KafkaHeaders.RECEIVED_KEY) String merchantIdKey) {
        UUID merchantId = UUID.fromString(merchantIdKey);
        try {
            WebhookOrder order = webhookService.parseOrder(rawBody);
            webhookService.ingestForMerchant(merchantId, order);
        } catch (Exception e) {
            log.error("Failed to ingest webhook-received event for merchant {}: {}", merchantId, e.getMessage(), e);
        }
    }
}
