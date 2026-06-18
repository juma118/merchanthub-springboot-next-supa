package com.merchanthub;

import com.merchanthub.config.AppProperties;
import com.merchanthub.dto.CommonDtos.PageResponse;
import com.merchanthub.dto.OrderDtos.OrderSummary;
import com.merchanthub.dto.ProductDtos.ProductRequest;
import com.merchanthub.dto.ProductDtos.ProductResponse;
import com.merchanthub.service.OrderService;
import com.merchanthub.service.ProductService;
import com.merchanthub.service.WebhookService;
import com.merchanthub.tenant.MerchantResolver;
import com.merchanthub.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired OrderService orderService;
    @Autowired WebhookService webhookService;
    @Autowired MerchantResolver merchantResolver;
    @Autowired AppProperties props;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void merchantsCannotSeeEachOthersProducts() {
        UUID acme = merchantResolver.provision(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID() + "@x.com");
        UUID globex = merchantResolver.provision(UUID.randomUUID(), "Globex", "globex-" + UUID.randomUUID() + "@x.com");

        TenantContext.setMerchantId(acme);
        productService.create(new ProductRequest("ISO-1", "Acme Widget", null, new BigDecimal("9.99"), null, 10, 2));

        TenantContext.setMerchantId(globex);
        productService.create(new ProductRequest("ISO-1", "Globex Gizmo", null, new BigDecimal("4.99"), null, 7, 2));

        TenantContext.setMerchantId(acme);
        PageResponse<ProductResponse> acmeView = productService.list(null, 0, 50);
        assertThat(acmeView.content()).extracting(ProductResponse::name).contains("Acme Widget");
        assertThat(acmeView.content()).extracting(ProductResponse::name).doesNotContain("Globex Gizmo");

        TenantContext.setMerchantId(globex);
        PageResponse<ProductResponse> globexView = productService.list(null, 0, 50);
        assertThat(globexView.content()).extracting(ProductResponse::name).contains("Globex Gizmo");
        assertThat(globexView.content()).extracting(ProductResponse::name).doesNotContain("Acme Widget");
    }

    @Test
    void signedWebhookIngestsOrderForOwningMerchant() throws Exception {
        String email = "wh-" + UUID.randomUUID() + "@x.com";
        UUID merchantId = merchantResolver.provision(UUID.randomUUID(), "Webhook Co", email);
        String apiKey = merchantResolver.findByEmail(email).orElseThrow().shopApiKey();

        // Seed a product so the line item resolves and stock is decremented.
        TenantContext.setMerchantId(merchantId);
        productService.create(new ProductRequest("WID-1", "Hooked Widget", null, new BigDecimal("29.99"), null, 100, 5));
        TenantContext.clear();

        String body = """
                {"apiKey":"%s","order":{"external_id":"wh-order-1","total":59.98,"currency":"USD",\
                "status":"paid","customer_email":"buyer@x.com","created_at":"2026-06-01T10:00:00Z",\
                "items":[{"sku":"WID-1","quantity":2,"unit_price":29.99}]}}""".formatted(apiKey);

        webhookService.handleOrderWebhook(body, sign(body, props.getWebhookSecret()));

        TenantContext.setMerchantId(merchantId);
        PageResponse<OrderSummary> orders = orderService.list(null, null, null, 0, 50);
        assertThat(orders.content()).extracting(OrderSummary::externalId).contains("wh-order-1");
    }

    @Test
    void webhookWithBadSignatureIsRejected() {
        String body = "{\"apiKey\":\"whatever\",\"order\":{}}";
        try {
            webhookService.handleOrderWebhook(body, "deadbeef");
            assertThat(false).as("expected signature rejection").isTrue();
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).containsIgnoringCase("signature");
        }
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
