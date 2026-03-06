package com.wnsai.wms_bot.ai.port;

import reactor.core.publisher.Flux;

/**
 * Low-level LLM provider abstraction.
 * Each tier implements this interface.
 * Implementations must throw/error the Flux on failure so the fallback chain can try the next tier.
 */
public interface LLMProvider {

    /**
     * Stream tokens from the provider.
     *
     * @param systemPrompt the system/context prompt
     * @param userMessage  the user's message
     * @param language     hint: "en", "hi", "te" etc. (used by rule-based tier)
     * @return Flux of tokens; errors on connection/auth failure so caller can fallback
     */
    Flux<String> stream(String systemPrompt, String userMessage, String language);

    /** Display name used in logs: OLLAMA, GROQ, RULE_BASED */
    String getName();
}
