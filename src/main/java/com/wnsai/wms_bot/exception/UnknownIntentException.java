package com.wnsai.wms_bot.exception;

/**
 * Thrown when intent cannot be classified — silently falls back to AI_QUERY.
 * Not surfaced to the user directly.
 */
public class UnknownIntentException extends RuntimeException {
    public UnknownIntentException(String message) {
        super(message);
    }
}
