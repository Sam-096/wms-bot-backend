package com.wnsai.wms_bot.chat.orchestration;

import com.wnsai.wms_bot.chat.ChatRequest;
import com.wnsai.wms_bot.chat.ChatResponse;
import reactor.core.publisher.Flux;

public interface ChatOrchestrator {

    /**
     * Full intent-first orchestration pipeline.
     *
     * Flow:
     *  1. Classify intent (never calls LLM)
     *  2. GREETING  → emit INSTANT, close SSE    (< 50ms)
     *  3. NAVIGATION → emit NAVIGATION, close SSE (< 100ms)
     *  4. QUICK_QUERY → emit INSTANT, close SSE   (< 300ms)
     *  5. AI_QUERY  → build context + stream tokens
     *  6. UNKNOWN   → stream with base prompt
     */
    Flux<ChatResponse> handle(ChatRequest request);
}
