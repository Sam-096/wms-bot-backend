package com.wnsai.wms_bot.ai.controller;

import com.wnsai.wms_bot.ai.service.LLMEngineFactory;
import com.wnsai.wms_bot.ai.service.WMSPromptBuilder;
import com.wnsai.wms_bot.dto.BotRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/bot")
@CrossOrigin(origins = {
    "http://localhost:4200",
    "https://wmsai.netlify.app"        
})
public class WMSBotController {

    private final LLMEngineFactory llmFactory;
    private final WMSPromptBuilder promptBuilder;

    public WMSBotController(
            LLMEngineFactory llmFactory,
            WMSPromptBuilder promptBuilder) {
        this.llmFactory    = llmFactory;
        this.promptBuilder = promptBuilder;
    }

    @GetMapping("/")
    public String root() {
        return "✅ WMS Bot Backend is live on Render!";
    }

    @GetMapping("/health")
    public String health() {
        return "✅ Godown AI is running!";
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@Valid @RequestBody BotRequest req) {
        String prompt = promptBuilder.build(
                req.language(),
                req.role(),
                req.warehouseName(),
                req.currentScreen(),
                req.contextData()
        );
        return llmFactory.get().streamChat(prompt, req.message());
    }
}
