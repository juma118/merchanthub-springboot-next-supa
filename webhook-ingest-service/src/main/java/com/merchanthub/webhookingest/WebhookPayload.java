package com.merchanthub.webhookingest;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors the shape mock-shop-api posts; only the apiKey is read here — the
 * raw order body is forwarded to the backend untouched. */
public record WebhookPayload(@JsonProperty("apiKey") String apiKey) {}
