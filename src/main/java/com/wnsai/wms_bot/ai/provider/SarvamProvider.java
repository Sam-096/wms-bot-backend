package com.wnsai.wms_bot.ai.provider;

import com.wnsai.wms_bot.ai.port.LLMProvider;
import com.wnsai.wms_bot.exception.WmsExceptions;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Tier 3 — Sarvam AI (Indian language LLM, OpenAI-compatible chat endpoint).
 * Used as fallback when Groq is unavailable or circuit is open.
 * Especially suited for Telugu / Hindi responses.
 *
 * Circuit breaker "sarvam" mirrors Groq config (60% fail in 5 calls → 30s open).
 * If SARVAM_API_KEY is blank, immediately errors so chain falls through to Tier 4.
 */
@Slf4j
@Component
public class SarvamProvider implements LLMProvider {

    private final WebClient      client;
    private final String         model;
    private final String         apiKey;
    private final Duration       timeout;
    private final CircuitBreaker circuitBreaker;

    public SarvamProvider(
            @Value("${sarvam.api.key:}")                               String apiKey,
            @Value("${sarvam.api.base-url:https://api.sarvam.ai}")     String baseUrl,
            @Value("${sarvam.model:sarvam-m}")                         String model,
            @Value("${sarvam.timeout:5000}")                           int timeoutMs,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.apiKey         = apiKey;
        this.model          = model;
        this.timeout        = Duration.ofMillis(timeoutMs);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("sarvam");
        this.client         = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-subscription-key", apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SarvamProvider: SARVAM_API_KEY not set — Tier 3 disabled. " +
                     "Set SARVAM_API_KEY env var on Render to enable.");
        } else {
            log.info("SarvamProvider init — baseUrl={}, model={}", baseUrl, model);
        }
    }

    @PostConstruct
    void logStartupStatus() {
        log.info("SarvamProvider startup: keyPresent={}, model={}, timeout={}ms, circuitBreaker={}",
                !(apiKey == null || apiKey.isBlank()), model,
                timeout.toMillis(), circuitBreaker.getState());
    }

    @Override
    public String getName() { return "SARVAM"; }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, String language) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new IllegalStateException(
                    "SARVAM_API_KEY not configured — skipping Tier 3"));
        }

        return doStream(systemPrompt, userMessage)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.error("SarvamProvider FAILED — type={} message={} circuitState={}",
                        e.getClass().getSimpleName(), e.getMessage(), circuitBreaker.getState()))
                .onErrorMap(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                        e -> new WmsExceptions.AiProviderException("SARVAM_CIRCUIT_OPEN",
                                "Sarvam circuit breaker open — too many recent failures"));
    }

    private Flux<String> doStream(String systemPrompt, String userMessage) {
        return client.post()
                .uri("/v1/chat/completions")
                .bodyValue(Map.of(
                        "model",       model,
                        "temperature", 0.3,
                        "max_tokens",  500,
                        "stream",      true,
                        "messages",    List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user",   "content", userMessage)
                        )
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout)
                .filter(line -> line.contains("\"content\"") && !line.contains("[DONE]"))
                .map(this::extractToken)
                .filter(t -> !t.isBlank());
    }

    private String extractToken(String line) {
        try {
            String json  = line.startsWith("data:") ? line.substring(5).trim() : line;
            int    start = json.indexOf("\"content\":\"") + 11;
            if (start < 11) return "";
            int end = json.indexOf("\"", start);
            return (end > start) ? json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"") : "";
        } catch (Exception e) {
            return "";
        }
    }
}
