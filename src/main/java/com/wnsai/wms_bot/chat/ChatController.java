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
import org.springframework.security.core.Authentication;
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
 * ════════════════════════════════════════════════════════════
 * POST /api/v1/chat  — SSE stream (text/event-stream)
 * ════════════════════════════════════════════════════════════
 *
 * REQUEST:
 * {
 *   "message":       "string",          required, max 500 chars
 *   "sessionId":     "string",          optional
 *   "warehouseId":   "string",          optional
 *   "warehouseName": "string",          optional
 *   "language":      "en|te|hi|...",    optional
 *   "context": {                        optional — injected by frontend
 *     "pendingInward":   0,
 *     "pendingOutward":  0,
 *     "lowStockCount":   0,
 *     "openGatePasses":  0
 *   }
 * }
 * NOTE: "role" and "userId" are extracted SERVER-SIDE from JWT — client values ignored.
 *
 * ════════════════════════════════════════════════════════════
 * SSE EVENT CONTRACTS (Frontend must handle all types):
 * ════════════════════════════════════════════════════════════
 *
 * TOKEN — streaming word chunk:
 * { "type": "TOKEN", "content": "Here is" }
 *
 * INSTANT — complete non-streamed reply:
 * { "type": "INSTANT", "responseType": "TEXT",  "content": "Here is your stock summary..." }
 * { "type": "INSTANT", "responseType": "TABLE", "data": [...] }
 * { "type": "INSTANT", "responseType": "LIST",  "data": [...] }
 *
 * NAVIGATION — redirect the user to a route:
 * { "type": "NAVIGATION", "content": "Going to Bonds", "route": "/bonds" }
 *
 * ACCESS_DENIED — role lacks permission:
 * {
 *   "type": "ACCESS_DENIED",
 *   "content": "You don't have access to user management.",
 *   "responseType": "ACTION",
 *   "data": [{ "label": "Go to Dashboard", "route": "/dashboard" }]
 * }
 *
 * DONE — end of stream:
 * { "type": "DONE", "intent": "AI_QUERY", "aiProvider": "OLLAMA", "processingTimeMs": 1234 }
 *
 * ERROR — pipeline failure:
 * { "type": "ERROR", "content": "AI_OFFLINE" }
 *
 * ════════════════════════════════════════════════════════════
 * FRONTEND INTEGRATION VALUES:
 *   A) Role field in login response:  "role"
 *   B) JWT claim name for role:       "role"
 *   C) Chat endpoint URL:             POST /api/v1/chat
 *      Accept: text/event-stream
 *      Authorization: Bearer <token>
 * ════════════════════════════════════════════════════════════
 *
 * Other endpoints:
 * GET    /api/v1/chat/sessions                      — list sessions
 * PUT    /api/v1/chat/sessions/{id}/title           — rename session
 * DELETE /api/v1/chat/sessions/{id}                 — soft-delete
 * GET    /api/v1/chat/sessions/{id}/messages        — message history
 * POST   /api/v1/chat/messages/{msgId}/feedback     — helpful feedback
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// NOTE: @CrossOrigin removed — CORS is handled globally by CorsWebFilter bean in SecurityConfig
public class ChatController {

    private final ChatOrchestrator       orchestrator;
    private final ObjectMapper           objectMapper;
    private final IntentClassifier       intentClassifier;
    private final WarehouseDataService   warehouseDataService;
    private final ChatSessionRepository  chatSessionRepo;
    private final ChatMessageRepository  chatMessageRepo;

    // ─── Primary SSE chat endpoint ────────────────────────────────────────────

    @PostMapping(
        value    = {"/chat", "/chat/stream"},
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> chat(
            @Valid @RequestBody ChatRequest req,
            Authentication authentication) {

        // ── Extract role + userId from JWT (server-side — cannot be spoofed) ──
        String jwtRole   = extractRole(authentication);
        String jwtUserId = extractUserId(authentication);

        // Build a secure request overriding any client-supplied role/userId
        ChatRequest secureReq = new ChatRequest(
            req.message(), req.language(), jwtRole,
            req.warehouseId(), req.sessionId(), req.warehouseName(),
            req.context(), jwtUserId
        );

        log.info("POST /api/v1/chat sessionId={} lang={} jwtRole={} warehouseId={} userId={}",
            secureReq.sessionId(), secureReq.language(), jwtRole,
            secureReq.warehouseId(), jwtUserId);

        long startMs = System.currentTimeMillis();

        // Classify once — orchestrator also calls classify internally, but we need
        // the result here for persistence. Two separate calls would be triple classification.
        var    intentResult    = intentClassifier.classify(secureReq.message());
        String detectedIntent  = intentResult.type().name();
        double confidence      = intentResult.confidence();

        AtomicReference<StringBuilder> responseBuffer = new AtomicReference<>(new StringBuilder());
        AtomicLong                     firstTokenMs   = new AtomicLong(0);

        return orchestrator.handle(secureReq)
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
            .doFinally(signal -> persistAsync(secureReq, detectedIntent, confidence,
                responseBuffer.get().toString(),
                System.currentTimeMillis() - startMs))
            .onErrorResume(e -> {
                log.error("Chat pipeline error: {}", e.getMessage());
                return Flux.just(
                    ServerSentEvent.<String>builder().event("message")
                        .data(toJson(ChatResponse.error("INTERNAL_ERROR"))).build(),
                    ServerSentEvent.<String>builder().event("message")
                        .data(toJson(ChatResponse.done())).build()
                );
            });
    }

    // ─── Session management ───────────────────────────────────────────────────

    @GetMapping("/chat/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR','VIEWER','GATE_STAFF')")
    public Mono<Page<ChatSession>> listSessions(
            @RequestParam                      String warehouseId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Mono.fromCallable(() ->
            chatSessionRepo.findByWarehouseIdAndIsDeletedFalseOrderByLastActiveDesc(
                warehouseId, PageRequest.of(page, size))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/chat/sessions/{sessionId}/title")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR')")
    public Mono<Void> renameSession(
            @PathVariable String sessionId,
            @RequestBody  Map<String, String> body) {
        String title = body.getOrDefault("title", "");
        return Mono.fromRunnable(() -> chatSessionRepo.updateTitle(sessionId, title))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR')")
    public Mono<Void> deleteSession(@PathVariable String sessionId) {
        return Mono.fromRunnable(() -> chatSessionRepo.softDelete(sessionId))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    /**
     * PATCH /api/v1/chat/sessions/{sessionId}
     * Upserts a chat session — creates if not found, updates if exists.
     * Called by the frontend after receiving an AI response to persist session state.
     *
     * Body (all fields optional):
     *   { "warehouseId": "WH-001", "title": "My Chat", "language": "en" }
     */
    @PatchMapping("/chat/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR','VIEWER','GATE_STAFF')")
    public Mono<ChatSession> upsertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        Map<String, String> payload = (body != null) ? body : Map.of();
        String jwtUserId = extractUserId(authentication);

        return Mono.fromCallable(() -> {
            var existing = chatSessionRepo.findBySessionId(sessionId);
            if (existing.isPresent()) {
                ChatSession s = existing.get();
                if (payload.containsKey("title"))    s.setTitle(payload.get("title"));
                if (payload.containsKey("language")) s.setLanguage(payload.get("language"));
                return chatSessionRepo.save(s);
            }
            UUID userUuid = null;
            try { if (jwtUserId != null) userUuid = UUID.fromString(jwtUserId); }
            catch (IllegalArgumentException ignored) {}

            ChatSession s = ChatSession.builder()
                .sessionId(sessionId)
                .warehouseId(payload.getOrDefault("warehouseId", "UNKNOWN"))
                .language(payload.getOrDefault("language", "en"))
                .title(payload.getOrDefault("title", "New Chat"))
                .userId(userUuid)
                .messageCount(0)
                .isDeleted(false)
                .build();
            log.info("Creating new ChatSession sessionId={} warehouseId={}",
                sessionId, s.getWarehouseId());
            return chatSessionRepo.save(s);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR','VIEWER','GATE_STAFF')")
    public Mono<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> chatMessageRepo.findBySessionId(sessionId))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/chat/messages/{messageId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR','VIEWER','GATE_STAFF')")
    public Mono<Void> submitFeedback(
            @PathVariable UUID messageId,
            @RequestBody  Map<String, Boolean> body) {
        Boolean helpful = body.get("helpful");
        return Mono.fromRunnable(() -> chatMessageRepo.updateFeedback(messageId, helpful))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    // ─── JWT extraction helpers ───────────────────────────────────────────────

    /**
     * Extracts role from the JWT-backed Authentication object.
     * JwtAuthFilter stores authorities as "ROLE_MANAGER" etc.
     * We strip the prefix to get plain role: "MANAGER".
     */
    private String extractRole(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return "VIEWER";
        }
        return auth.getAuthorities().stream()
                .findFirst()
                .map(ga -> ga.getAuthority().replace("ROLE_", ""))
                .orElse("VIEWER");
    }

    private String extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        return auth.getPrincipal().toString();
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

        // Keep session.language in sync — so future requests w/o a language fall back correctly
        if (req.sessionId() != null && !req.sessionId().isBlank()
            && req.language() != null && !req.language().isBlank()) {
            Mono.fromRunnable(() -> chatSessionRepo.updateLanguage(req.sessionId(), req.language()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    v   -> {},
                    err -> log.warn("updateLanguage failed for session {}: {}",
                        req.sessionId(), err.getMessage())
                );
        }
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
