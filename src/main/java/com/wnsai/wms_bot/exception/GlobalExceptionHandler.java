package com.wnsai.wms_bot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global WebFlux exception handler.
 * Maps every exception layer to a consistent JSON error envelope:
 *
 * {
 *   "code":      "SESSION_NOT_FOUND",   ← machine-readable
 *   "message":   "Chat session not found or expired",
 *   "path":      "/api/v1/chat/sessions/xxx",
 *   "timestamp": "2026-03-09T10:00:00Z",
 *   "status":    404
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation errors (@Valid on request bodies) ──────────────────────────
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        log.warn("Validation failed at {}: {}", path(exchange), detail);
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, exchange);
    }

    // ── ResponseStatusException (WebFlux 405, 404 etc.) ──────────────────────
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleResponseStatus(
            ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return respond(status, "HTTP_ERROR",
            ex.getReason() != null ? ex.getReason() : ex.getMessage(), exchange);
    }

    // ── AI provider circuit open ───────────────────────────────────────────────
    @ExceptionHandler(WmsExceptions.AiProviderException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAiProvider(
            WmsExceptions.AiProviderException ex, ServerWebExchange exchange) {
        log.error("AI provider error at {}: code={} message={}", path(exchange), ex.getCode(), ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ex.getCode(), ex.getMessage(), exchange);
    }

    // ── Session not found ─────────────────────────────────────────────────────
    @ExceptionHandler(WmsExceptions.SessionNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleSession(
            WmsExceptions.SessionNotFoundException ex, ServerWebExchange exchange) {
        log.warn("Session not found at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", ex.getMessage(), exchange);
    }

    // ── Warehouse not found ───────────────────────────────────────────────────
    @ExceptionHandler(WmsExceptions.WarehouseNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleWarehouse(
            WmsExceptions.WarehouseNotFoundException ex, ServerWebExchange exchange) {
        log.warn("Warehouse not found at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", ex.getMessage(), exchange);
    }

    // ── Rate limit exceeded ───────────────────────────────────────────────────
    @ExceptionHandler(WmsExceptions.RateLimitExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRateLimit(
            WmsExceptions.RateLimitExceededException ex, ServerWebExchange exchange) {
        log.warn("Rate limit exceeded at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", ex.getMessage(), exchange);
    }

    // ── Empty message ─────────────────────────────────────────────────────────
    @ExceptionHandler(EmptyMessageException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleEmpty(
            EmptyMessageException ex, ServerWebExchange exchange) {
        return respond(HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE", ex.getMessage(), exchange);
    }

    // ── Ollama offline ────────────────────────────────────────────────────────
    @ExceptionHandler(OllamaOfflineException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleOllama(
            OllamaOfflineException ex, ServerWebExchange exchange) {
        log.error("Ollama offline at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
            "AI is offline. Basic queries still work.", exchange);
    }

    // ── Sarvam timeout ────────────────────────────────────────────────────────
    @ExceptionHandler(SarvamTimeoutException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleSarvam(
            SarvamTimeoutException ex, ServerWebExchange exchange) {
        log.error("Sarvam timeout at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
            "Voice AI service timed out.", exchange);
    }

    // ── Unknown intent ────────────────────────────────────────────────────────
    @ExceptionHandler(UnknownIntentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnknown(
            UnknownIntentException ex, ServerWebExchange exchange) {
        log.warn("Unknown intent at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.OK, "UNKNOWN_INTENT",
            "Intent unclear — please rephrase your question.", exchange);
    }

    // ── Entity not found ──────────────────────────────────────────────────────
    @ExceptionHandler(EntityNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNotFound(
            EntityNotFoundException ex, ServerWebExchange exchange) {
        log.warn("Entity not found at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), exchange);
    }

    // ── Insufficient stock ────────────────────────────────────────────────────
    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleStock(
            InsufficientStockException ex, ServerWebExchange exchange) {
        log.warn("Insufficient stock at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK",
            ex.getMessage(), exchange);
    }

    // ── Invalid credentials ───────────────────────────────────────────────────
    @ExceptionHandler(InvalidCredentialsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAuth(
            InvalidCredentialsException ex, ServerWebExchange exchange) {
        log.warn("Invalid credentials at {}", path(exchange));
        return respond(HttpStatus.UNAUTHORIZED, "AUTH_FAILED",
            "Invalid email or password.", exchange);
    }

    // ── Duplicate email/username ──────────────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArg(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        log.warn("Conflict at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.CONFLICT, "DUPLICATE_ENTRY", ex.getMessage(), exchange);
    }

    // ── DB unique constraint violation ────────────────────────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDataIntegrity(
            DataIntegrityViolationException ex, ServerWebExchange exchange) {
        log.warn("DB constraint at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.CONFLICT, "DUPLICATE_ENTRY",
            "A record with this email or username already exists.", exchange);
    }

    // ── Access denied ─────────────────────────────────────────────────────────
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccess(
            org.springframework.security.access.AccessDeniedException ex,
            ServerWebExchange exchange) {
        log.warn("Access denied at {}: {}", path(exchange), ex.getMessage());
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "You don't have permission to perform this action.", exchange);
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGeneric(
            Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled exception at {}: {}", path(exchange), ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Something went wrong. Please try again.", exchange);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Mono<ResponseEntity<Map<String, Object>>> respond(
            HttpStatus status, String code, String message, ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(status).body(Map.of(
            "code",      code,
            "message",   message != null ? message : status.getReasonPhrase(),
            "path",      path(exchange),
            "status",    status.value(),
            "timestamp", Instant.now().toString()
        )));
    }

    private String path(ServerWebExchange exchange) {
        return exchange != null ? exchange.getRequest().getPath().value() : "unknown";
    }
}
