package com.wnsai.wms_bot.ai.provider;

import com.wnsai.wms_bot.ai.port.LLMProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Tier 2 — Groq Cloud API (OpenAI-compatible).
 * Uses llama3-8b-8192 with 8s timeout.
 * If GROQ_API_KEY is blank, immediately errors so the chain falls through to Tier 3.
 *
 * SSE format from Groq:
 *   data: {"choices":[{"delta":{"content":"token"},...}],...}
 *   data: [DONE]
 */
@Slf4j
@Component
public class GroqProvider implements LLMProvider {

    private final WebClient client;
    private final String    model;
    private final String    apiKey;
    private final Duration  timeout;

    public GroqProvider(
            @Value("${groq.api-key:}")                               String apiKey,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.model:llama3-8b-8192}")                   String model,
            @Value("${groq.timeout:8000}")                           int timeoutMs) {
        this.apiKey   = apiKey;
        this.model    = model;
        this.timeout  = Duration.ofMillis(timeoutMs);
        this.client   = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GroqProvider: GROQ_API_KEY not set — Tier 2 disabled. " +
                     "Set GROQ_API_KEY env var on Render to enable cloud AI.");
        } else {
            log.info("GroqProvider init — baseUrl={}, model={}", baseUrl, model);
        }
    }

    @Override
    public String getName() { return "GROQ"; }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, String language) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new IllegalStateException("GROQ_API_KEY not configured — skipping Tier 2"));
        }

        return client.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(Map.of(
                        "model",    model,
                        "stream",   true,
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user",   "content", userMessage)
                        )
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout)
                .filter(line -> line.startsWith("data:") && !line.contains("[DONE]"))
                .map(line -> line.substring(5).trim())   // strip "data:" prefix
                .map(this::extractToken)
                .filter(t -> !t.isBlank());
    }

    private String extractToken(String json) {
        try {
            // {"choices":[{"delta":{"content":"token"},...}]}
            int idx = json.indexOf("\"content\":\"");
            if (idx == -1) return "";
            int start = idx + 11;
            int end   = json.indexOf("\"", start);
            if (end > start) {
                return json.substring(start, end)
                           .replace("\\n", "\n")
                           .replace("\\t", "\t")
                           .replace("\\\"", "\"");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
