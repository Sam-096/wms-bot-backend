package com.wnsai.wms_bot.context;

import reactor.core.publisher.Mono;

public interface ContextBuilder {

    /**
     * Build a structured context block to inject into the LLM prompt.
     * Only called for AI_QUERY intents. Max 500 tokens.
     */
    Mono<String> buildContext(String message, String warehouseId, String role);
}
