package com.wnsai.wms_bot.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wnsai.wms_bot.chat.orchestration.ChatOrchestrator;
import com.wnsai.wms_bot.entity.ChatMessage;
import com.wnsai.wms_bot.intent.IntentClassifier;
import com.wnsai.wms_bot.service.WarehouseDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * POST /api/v1/chat → SSE stream (text/event-stream)
 *
 * SSE event data formats:
 *   {type:"INSTANT",    content:"…"}
 *   {type:"NAVIGATION", content:"Inward Receipts", route:"/inward"}
 *   {type:"TOKEN",      content:"word"}
 *   {type:"DONE"}
 *   {type:"ERROR",      content:"AI_OFFLINE"}
 *
 * After the SSE stream completes, the exchange is persisted
 * asynchronously to chat_messages (fire-and-forget, never blocks SSE).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = {
    "http://localhost:4200",
    "https://wmsai.netlify.app"
})
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestrator    orchestrator;
    private final ObjectMapper        objectMapper;
    private final IntentClassifier    intentClassifier;
    private final WarehouseDataService warehouseDataService;

    @PostMapping(
        value    = "/chat",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest req) {
        log.info("POST /api/v1/chat sessionId={} lang={} role={} warehouseId={}",
            req.sessionId(), req.language(), req.role(), req.warehouseId());

        long startMs = System.currentTimeMillis();

        // Classify intent once (< 5ms) — used for the DB record
        String detectedIntent = intentClassifier.classify(req.message()).type().name();
        double confidence     = intentClassifier.classify(req.message()).confidence();

        // Accumulators for the full bot response across all TOKEN/INSTANT events
        AtomicReference<StringBuilder> responseBuffer = new AtomicReference<>(new StringBuilder());
        AtomicLong                     firstTokenMs   = new AtomicLong(0);

        return orchestrator.handle(req)
            .doOnNext(response -> {
                // Accumulate content for DB persistence
                if (response.content() != null) {
                    if ("TOKEN".equals(response.type()) || "INSTANT".equals(response.type())) {
                        responseBuffer.get().append(response.content());
                        firstTokenMs.compareAndSet(0, System.currentTimeMillis() - startMs);
                    }
                }
            })
            .map(response -> ServerSentEvent.<String>builder()
                .event("message")
                .data(toJson(response))
                .build())
            .doFinally(signal -> persistAsync(req, detectedIntent, confidence,
                responseBuffer.get().toString(),
                System.currentTimeMillis() - startMs))
            .onErrorResume(e -> {
                log.error("Chat pipeline error: {}", e.getMessage());
                String errJson = toJson(ChatResponse.error("INTERNAL_ERROR"));
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("message")
                    .data(errJson)
                    .build());
            });
    }

    // ─── Async DB persistence ─────────────────────────────────────────────────

    /**
     * Fire-and-forget: saves the chat exchange to chat_messages.
     * Runs on boundedElastic to avoid blocking the event loop.
     * Never throws — any failure is logged by WarehouseDataService.
     */
    private void persistAsync(ChatRequest req,
                              String intent,
                              double confidence,
                              String botResponse,
                              long responseTimeMs) {
        ChatMessage msg = ChatMessage.builder()
            .sessionId(req.sessionId())
            .warehouseId(req.warehouseId())
            .userMessage(req.message())
            .botResponse(botResponse)
            .intent(intent)
            .language(req.language())
            .confidence(confidence)
            .responseTimeMs(responseTimeMs)
            .wasHelpful(null)   // set later via feedback endpoint
            .build();

        reactor.core.publisher.Mono
            .fromCallable(() -> warehouseDataService.saveChatMessage(msg))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                saved -> { /* success — logged inside service */ },
                err   -> log.error("persistAsync failed: {}", err.getMessage())
            );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String toJson(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"ERROR\",\"content\":\"SERIALIZATION_ERROR\"}";
        }
    }
}
