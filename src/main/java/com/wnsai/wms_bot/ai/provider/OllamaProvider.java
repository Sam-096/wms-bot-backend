package com.wnsai.wms_bot.ai.provider;

import com.wnsai.wms_bot.ai.port.LLMProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Tier 1 — Local Ollama LLM.
 * True token-by-token SSE streaming from /api/chat.
 * @PostConstruct checks if the configured model exists; logs WARN if not (does NOT fail startup).
 * Errors immediately on connection failure so LLMFallbackChain falls through to Tier 2.
 */
@Slf4j
@Component
public class OllamaProvider implements LLMProvider {

    private final WebClient client;
    private final String    model;

    public OllamaProvider(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.2:3b}")               String model) {
        this.model  = model;
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
        log.info("OllamaProvider init — baseUrl={}, model={}", baseUrl, model);
    }

    @PostConstruct
    void checkModelAvailability() {
        try {
            String tags = client.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            if (tags != null && tags.contains(model)) {
                log.info("OllamaProvider: model '{}' is available ✓", model);
            } else {
                log.warn("OllamaProvider: model '{}' not found in /api/tags. " +
                         "Run: ollama pull {}", model, model);
            }
        } catch (Exception e) {
            log.warn("OllamaProvider: Ollama not reachable at startup ({}). " +
                     "Fallback chain will handle requests.", e.getMessage());
        }
    }

    @Override
    public String getName() { return "OLLAMA"; }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, String language) {
        return client.post()
                .uri("/api/chat")
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
                .timeout(Duration.ofSeconds(10))
                .filter(line -> line.contains("\"content\""))
                .map(this::extractToken)
                .filter(t -> !t.isBlank());
    }

    private String extractToken(String json) {
        try {
            int start = json.indexOf("\"content\":\"") + 11;
            int end   = json.indexOf("\"", start);
            if (start > 10 && end > start) {
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
