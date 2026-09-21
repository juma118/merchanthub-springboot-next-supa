package com.merchanthub.webhookingest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WebhookSignatureVerifierTest {

    // Matches the default `webhook.secret` in application.properties.
    private static final String SECRET = "dev-webhook-hmac-secret-change-me";

    @jakarta.inject.Inject WebhookSignatureVerifier verifier;

    @Test
    void acceptsACorrectlySignedBody() throws Exception {
        String body = "{\"apiKey\":\"abc\",\"order\":{}}";
        assertTrue(verifier.verify(body, sign(body, SECRET)));
    }

    @Test
    void rejectsATamperedBody() throws Exception {
        String body = "{\"apiKey\":\"abc\",\"order\":{}}";
        String signature = sign(body, SECRET);
        assertFalse(verifier.verify(body + "tampered", signature));
    }

    @Test
    void rejectsAMissingSignature() {
        assertFalse(verifier.verify("{}", null));
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
