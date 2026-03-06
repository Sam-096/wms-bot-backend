package com.wnsai.wms_bot.ai.adapter;

import com.wnsai.wms_bot.ai.LLMFallbackChain;
import com.wnsai.wms_bot.ai.port.ILLMEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * ILLMEngine adapter that delegates all calls to the 3-tier LLMFallbackChain.
 * Tier 1: Ollama (local) → Tier 2: Groq (cloud) → Tier 3: Rule-Based (always works).
 */
@Slf4j
@Component("ollamaLLM")
public class OllamaLLMAdapter implements ILLMEngine {

    private final LLMFallbackChain chain;

    public OllamaLLMAdapter(LLMFallbackChain chain) {
        this.chain = chain;
        log.info("OllamaLLMAdapter init — backed by 3-tier fallback chain");
    }

    @Override
    public Flux<String> streamChat(String systemPrompt, String userMessage) {
        String language = detectLanguage(userMessage);
        return chain.stream(systemPrompt, userMessage, language);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return streamChat(systemPrompt, userMessage)
                .reduce("", String::concat)
                .block();
    }

    /** Detect language from Unicode ranges — Telugu (0C00–0C7F), Hindi (0900–097F). */
    private String detectLanguage(String message) {
        if (message == null) return "en";
        for (char c : message.toCharArray()) {
            if (c >= '\u0C00' && c <= '\u0C7F') return "te";
            if (c >= '\u0900' && c <= '\u097F') return "hi";
        }
        return "en";
    }
}
