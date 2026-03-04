package com.wnsai.wms_bot.ai.port;

import reactor.core.publisher.Flux;

public interface ILLMEngine {
    Flux<String> streamChat(String systemPrompt, String userMessage);
    String chat(String systemPrompt, String userMessage);
}
