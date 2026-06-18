package com.merchanthub.web;

import com.merchanthub.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Signed new-order webhook from the shop API (push ingestion). The raw body is
     * read verbatim so the HMAC signature can be verified before parsing.
     */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> receiveOrder(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Shop-Signature", required = false) String signature) {
        webhookService.handleOrderWebhook(rawBody, signature);
        return Map.of("status", "accepted");
    }
}
