package com.wnsai.wms_bot.exception;

/**
 * Grouped custom exceptions for the WMS backend.
 *
 * Error code → HTTP status mapping (enforced in GlobalExceptionHandler):
 *   AI_UNAVAILABLE       → 503
 *   GROQ_CIRCUIT_OPEN    → 503
 *   SARVAM_CIRCUIT_OPEN  → 503
 *   SESSION_NOT_FOUND    → 404
 *   WAREHOUSE_NOT_FOUND  → 404
 *   TOO_MANY_REQUESTS    → 429
 */
public final class WmsExceptions {

    private WmsExceptions() {}

    // ── AI provider errors ─────────────────────────────────────────────────

    /** Thrown when an AI provider (Groq/Sarvam) fails and the circuit is open. */
    public static class AiProviderException extends RuntimeException {
        private final String code;

        public AiProviderException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }

    // ── Session / entity errors ────────────────────────────────────────────

    /** Thrown when a chat session cannot be found by sessionId. */
    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String sessionId) {
            super("Chat session not found or expired: " + sessionId);
        }
    }

    /** Thrown when a warehouse cannot be found by warehouseId. */
    public static class WarehouseNotFoundException extends RuntimeException {
        public WarehouseNotFoundException(String warehouseId) {
            super("Warehouse not found: " + warehouseId);
        }
    }

    // ── Rate limiting ──────────────────────────────────────────────────────

    /** Thrown when a rate limit is exceeded (login attempts, API calls). */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
