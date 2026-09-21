package com.merchanthub.ai;

import com.merchanthub.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Minimal client for the Anthropic Messages API — just enough to send a
 * single-turn prompt and get text back. No SDK dependency: one JSON POST,
 * using Spring's built-in RestClient. Returns empty when no API key is
 * configured so the feature degrades to "unavailable" rather than failing
 * startup or throwing on every request.
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AppProperties props;
    private final RestClient restClient;

    public AnthropicClient(AppProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().baseUrl(API_URL).build();
    }

    public boolean isConfigured() {
        return props.getAnthropicApiKey() != null && !props.getAnthropicApiKey().isBlank();
    }

    /** Sends a single user-turn prompt and returns the concatenated text of the reply. */
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new IllegalStateException("Anthropic API key not configured");
        }

        Map<String, Object> body = Map.of(
                "model", props.getAnthropicModel(),
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)));

        try {
            AnthropicResponse response = restClient.post()
                    .header("x-api-key", props.getAnthropicApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(AnthropicResponse.class);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                return "";
            }
            return response.content().stream()
                    .map(AnthropicResponse.ContentBlock::text)
                    .filter(t -> t != null && !t.isBlank())
                    .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
        } catch (Exception e) {
            log.warn("Anthropic API call failed: {}", e.getMessage());
            throw new RuntimeException("AI insight generation failed: " + e.getMessage(), e);
        }
    }

    public record AnthropicResponse(List<ContentBlock> content) {
        public record ContentBlock(String type, String text) {}
    }
}
