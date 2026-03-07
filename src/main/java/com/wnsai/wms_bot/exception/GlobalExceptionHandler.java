package com.wnsai.wms_bot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global WebFlux exception handler.
 * Maps every exception layer to a consistent JSON error envelope.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation errors (@Valid on ChatRequest) ─────────────────────────────
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(WebExchangeBindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return Mono.just(ResponseEntity.badRequest()
            .body(error(HttpStatus.BAD_REQUEST, detail)));
    }

    // ── Empty message ─────────────────────────────────────────────────────────
    @ExceptionHandler(EmptyMessageException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleEmpty(EmptyMessageException ex) {
        log.warn("Empty message received");
        return Mono.just(ResponseEntity.badRequest()
            .body(error(HttpStatus.BAD_REQUEST, ex.getMessage())));
    }

    // ── Ollama offline ────────────────────────────────────────────────────────
    @ExceptionHandler(OllamaOfflineException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleOllama(OllamaOfflineException ex) {
        log.error("Ollama offline: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(error(HttpStatus.SERVICE_UNAVAILABLE,
                "AI is offline. Basic queries still work.")));
    }

    // ── Sarvam timeout ────────────────────────────────────────────────────────
    @ExceptionHandler(SarvamTimeoutException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleSarvam(SarvamTimeoutException ex) {
        log.error("Sarvam timeout: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
            .body(error(HttpStatus.REQUEST_TIMEOUT, "Voice service timeout")));
    }

    // ── Unknown intent (silent fallback — should not reach here) ─────────────
    @ExceptionHandler(UnknownIntentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnknown(UnknownIntentException ex) {
        log.warn("Unknown intent: {}", ex.getMessage());
        return Mono.just(ResponseEntity.ok()
            .body(error(HttpStatus.OK, "Intent unclear — please rephrase your question.")));
    }

    // ── Entity not found ──────────────────────────────────────────────────────
    @ExceptionHandler(EntityNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error(HttpStatus.NOT_FOUND, ex.getMessage())));
    }

    // ── Insufficient stock ────────────────────────────────────────────────────
    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleStock(InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage())));
    }

    // ── Invalid credentials ───────────────────────────────────────────────────
    @ExceptionHandler(InvalidCredentialsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAuth(InvalidCredentialsException ex) {
        log.warn("Invalid credentials attempt");
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of(
                "error", "Invalid credentials",
                "code",  "AUTH_FAILED",
                "timestamp", Instant.now().toString()
            )));
    }

    // ── Duplicate email/username (register conflict) ──────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, ex.getMessage())));
    }

    // ── DB unique constraint violation ────────────────────────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DB constraint violation: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "A record with this email or username already exists")));
    }

    // ── Access denied ─────────────────────────────────────────────────────────
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccess(
            org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(HttpStatus.FORBIDDEN, "Access denied — insufficient permissions")));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Data unavailable. Try again.")));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Map<String, Object> error(HttpStatus status, String message) {
        return Map.of(
            "status",    status.value(),
            "error",     status.getReasonPhrase(),
            "message",   message,
            "timestamp", Instant.now().toString()
        );
    }
}
