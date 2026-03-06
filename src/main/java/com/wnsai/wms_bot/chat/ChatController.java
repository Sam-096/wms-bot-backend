package com.wnsai.wms_bot.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wnsai.wms_bot.chat.orchestration.ChatOrchestrator;
import com.wnsai.wms_bot.entity.ChatMessage;
import com.wnsai.wms_bot.entity.ChatSession;
import com.wnsai.wms_bot.intent.IntentClassifier;
import com.wnsai.wms_bot.repository.ChatMessageRepository;
import com.wnsai.wms_bot.repository.ChatSessionRepository;
import com.wnsai.wms_bot.service.WarehouseDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * POST /api/v1/chat            — SSE stream (text/event-stream)
 * GET  /api/v1/chat/sessions   — list sessions for a warehouse
 * PUT  /api/v1/chat/sessions/{id}/title  — rename session
 * DELETE /api/v1/chat/sessions/{id}      — soft-delete session
 * GET  /api/v1/chat/sessions/{id}/messages — messages in session
 * POST /api/v1/chat/messages/{msgId}/feedback — helpful feedback
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

    private final ChatOrchestrator       orchestrator;
    private final ObjectMapper           objectMapper;
    private final IntentClassifier       intentClassifier;
    private final WarehouseDataService   warehouseDataService;
    private final ChatSessionRepository  chatSessionRepo;
    private final ChatMessageRepository  chatMessageRepo;

    // ─── Primary SSE chat endpoint ────────────────────────────────────────────

    @PostMapping(
        value    = "/chat",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest req) {
        log.info("POST /api/v1/chat sessionId={} lang={} role={} warehouseId={}",
            req.sessionId(), req.language(), req.role(), req.warehouseId());

        long startMs = System.currentTimeMillis();

        String detectedIntent = intentClassifier.classify(req.message()).type().name();
        double confidence     = intentClassifier.classify(req.message()).confidence();

        AtomicReference<StringBuilder> responseBuffer = new AtomicReference<>(new StringBuilder());
        AtomicLong                     firstTokenMs   = new AtomicLong(0);

        return orchestrator.handle(req)
            .doOnNext(response -> {
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

    // ─── Session management ───────────────────────────────────────────────────

    /**
     * GET /api/v1/chat/sessions?warehouseId=WH001&page=0&size=20
     * Returns non-deleted sessions for the warehouse, newest first.
     */
    @GetMapping("/chat/sessions")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<Page<ChatSession>> listSessions(
            @RequestParam                      String warehouseId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Mono.fromCallable(() ->
            chatSessionRepo.findByWarehouseIdAndIsDeletedFalseOrderByLastActiveDesc(
                warehouseId, PageRequest.of(page, size))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * PUT /api/v1/chat/sessions/{sessionId}/title
     * Body: {"title": "Morning Dispatch"}
     */
    @PutMapping("/chat/sessions/{sessionId}/title")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<Void> renameSession(
            @PathVariable String sessionId,
            @RequestBody  Map<String, String> body) {
        String title = body.getOrDefault("title", "");
        return Mono.fromRunnable(() -> chatSessionRepo.updateTitle(sessionId, title))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    /**
     * DELETE /api/v1/chat/sessions/{sessionId}
     * Soft-deletes the session (sets is_deleted = true).
     */
    @DeleteMapping("/chat/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<Void> deleteSession(@PathVariable String sessionId) {
        return Mono.fromRunnable(() -> chatSessionRepo.softDelete(sessionId))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    /**
     * GET /api/v1/chat/sessions/{sessionId}/messages
     * Returns all messages in the session, ordered by creation time (JPA default).
     */
    @GetMapping("/chat/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> chatMessageRepo.findBySessionId(sessionId))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * POST /api/v1/chat/messages/{messageId}/feedback
     * Body: {"helpful": true}
     * Marks a message as helpful or not — used for training data collection.
     */
    @PostMapping("/chat/messages/{messageId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<Void> submitFeedback(
            @PathVariable UUID messageId,
            @RequestBody  Map<String, Boolean> body) {
        Boolean helpful = body.get("helpful");
        return Mono.fromRunnable(() -> chatMessageRepo.updateFeedback(messageId, helpful))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    // ─── Async DB persistence ─────────────────────────────────────────────────

    private void persistAsync(ChatRequest req, String intent, double confidence,
                              String botResponse, long responseTimeMs) {
        ChatMessage msg = ChatMessage.builder()
            .sessionId(req.sessionId())
            .warehouseId(req.warehouseId())
            .userMessage(req.message())
            .botResponse(botResponse)
            .intent(intent)
            .language(req.language())
            .confidence(confidence)
            .responseTimeMs(responseTimeMs)
            .wasHelpful(null)
            .build();

        Mono.fromCallable(() -> warehouseDataService.saveChatMessage(msg))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                saved -> {},
                err   -> log.error("persistAsync failed: {}", err.getMessage())
            );
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String toJson(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"ERROR\",\"content\":\"SERIALIZATION_ERROR\"}";
        }
    }
}
