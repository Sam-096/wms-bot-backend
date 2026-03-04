package com.wnsai.wms_bot.ai.adapter;

import com.wnsai.wms_bot.ai.port.ILLMEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

@Component("sarvamLLM")
public class SarvamLLMAdapter implements ILLMEngine {

    private final WebClient client;
    private final String model;

    public SarvamLLMAdapter(
            @Value("${sarvam.api.base-url}") String baseUrl,
            @Value("${sarvam.api.key}") String apiKey,
            @Value("${sarvam.model}") String model) {
        this.model = model;
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-subscription-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public Flux<String> streamChat(String systemPrompt, String userMessage) {
        return client.post()
                .uri("/v1/chat/completions")
                .bodyValue(buildBody(systemPrompt, userMessage, true))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.contains("content")
                             && !line.contains("[DONE]"))
                .map(this::extractToken)
                .filter(token -> !token.isEmpty());
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        Map<?, ?> response = client.post()
                .uri("/v1/chat/completions")
                .bodyValue(buildBody(systemPrompt, userMessage, false))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        try {
            var choices = (List<?>) response.get("choices");
            var first   = (Map<?, ?>) choices.get(0);
            var message = (Map<?, ?>) first.get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return "క్షమించండి, మళ్ళీ అడగండి.";
        }
    }

    private Map<String, Object> buildBody(
            String system, String user, boolean stream) {
        return Map.of(
                "model",       model,
                "temperature", 0.3,
                "max_tokens",  250,
                "stream",      stream,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user",   "content", user)
                )
        );
    }

    private String extractToken(String line) {
        try {
            String json = line.startsWith("data:")
                    ? line.substring(5).trim() : line;
            int start = json.indexOf("\"content\":\"") + 11;
            if (start < 11) return "";
            int end = json.indexOf("\"", start);
            return (end > start) ? json.substring(start, end) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
