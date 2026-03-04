package com.wnsai.wms_bot.ai.service;

import com.wnsai.wms_bot.ai.port.ILLMEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LLMEngineFactory {

    private final ILLMEngine sarvam;
    private final ILLMEngine ollama;

    @Value("${ai.provider:sarvam}")
    private String provider;

    public LLMEngineFactory(
            @Qualifier("sarvamLLM") ILLMEngine sarvam,
            @Qualifier("ollamaLLM") ILLMEngine ollama) {
        this.sarvam = sarvam;
        this.ollama = ollama;
    }

    public ILLMEngine get() {
        return "ollama".equals(provider) ? ollama : sarvam;
    }
}
