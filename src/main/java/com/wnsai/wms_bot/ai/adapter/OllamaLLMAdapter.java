package com.wnsai.wms_bot.ai.adapter;

import com.wnsai.wms_bot.ai.port.ILLMEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

@Component("ollamaLLM")
public class OllamaLLMAdapter implements ILLMEngine {

    private static final Logger log = LoggerFactory.getLogger(OllamaLLMAdapter.class);

    private final WebClient client;
    private final String    model;

    public OllamaLLMAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.2}")                  String model) {
        this.model  = model;
        this.client = WebClient.builder().baseUrl(baseUrl).build();
        log.info("🦙 OllamaLLMAdapter init — baseUrl={}, model={}", baseUrl, model);
    }

    @Override
    public Flux<String> streamChat(String systemPrompt, String userMessage) {
        return client.post()
                .uri("/api/chat")
                .bodyValue(Map.of(
                        "model",    model,
                        "stream",   true,
                        "messages", List.of(
                                Map.of("role", "system",  "content", systemPrompt),
                                Map.of("role", "user",    "content", userMessage)
                        )
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.contains("\"content\""))
                .map(this::extractToken)
                .filter(t -> !t.isEmpty())
                .onErrorResume(e -> {
                    log.error("❌ Ollama unavailable: {}", e.getMessage());
                    return Flux.just("Ollama not available on this server.");
                });
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return streamChat(systemPrompt, userMessage)
                .reduce("", String::concat)
                .block();
    }

    private String extractToken(String json) {
        try {
            int start = json.indexOf("\"content\":\"") + 11;
            int end   = json.indexOf("\"", start);
            return (start > 10 && end > start) ? json.substring(start, end) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
