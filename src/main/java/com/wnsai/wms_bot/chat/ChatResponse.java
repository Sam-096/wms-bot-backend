package com.wnsai.wms_bot.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * SSE response envelope.
 *
 * type=INSTANT    → full reply (no streaming); responseType describes structure
 * type=NAVIGATION → route + content (label)
 * type=TOKEN      → one streaming chunk; all metadata fields null for efficiency
 * type=DONE       → end-of-stream; carries intent, aiProvider, processingTimeMs, suggestions
 * type=ERROR      → content carries error code
 * type=TABLE      → structured data; data field carries rows
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
    String       type,           // INSTANT | NAVIGATION | TOKEN | DONE | ERROR | TABLE
    String       content,        // text payload (null for DONE/TABLE)
    String       route,          // populated only for NAVIGATION
    String       responseType,   // TEXT | TABLE | CHART | LIST | REPORT | ACTION | ALERT
    String       intent,         // classified intent (DONE + INSTANT events)
    String       language,       // detected / requested language
    Object       data,           // structured payload for TABLE / CHART types
    List<String> suggestions,    // follow-up question suggestions
    String       sessionId,      // echoed session ID
    String       messageId,      // UUID of persisted ChatMessage (after DONE)
    String       aiProvider,     // OLLAMA | GROQ | RULE_BASED
    Long         processingTimeMs,
    String       timestamp
) {

    // ─── Streaming factories (keep lightweight — no metadata) ─────────────────

    public static ChatResponse token(String content) {
        return new ChatResponse("TOKEN", content, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static ChatResponse error(String code) {
        return new ChatResponse("ERROR", code, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    // ─── Terminal / rich factories ────────────────────────────────────────────

    public static ChatResponse instant(String content) {
        return new ChatResponse("INSTANT", content, null, "TEXT", null, null,
                null, null, null, null, null, null, now());
    }

    public static ChatResponse instant(String content, String intent, String language,
                                       String aiProvider, List<String> suggestions) {
        return new ChatResponse("INSTANT", content, null, "TEXT", intent, language,
                null, suggestions, null, null, aiProvider, null, now());
    }

    public static ChatResponse navigation(String route, String label) {
        return new ChatResponse("NAVIGATION", label, route, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static ChatResponse done() {
        return new ChatResponse("DONE", null, null, null, null, null,
                null, null, null, null, null, null, now());
    }

    public static ChatResponse done(String intent, String aiProvider,
                                    long processingTimeMs, List<String> suggestions) {
        return new ChatResponse("DONE", null, null, null, intent, null,
                null, suggestions, null, null, aiProvider, processingTimeMs, now());
    }

    /** Structured table response — replaces INSTANT for DB query results */
    public static ChatResponse table(Object data, String intent, List<String> suggestions) {
        return new ChatResponse("INSTANT", null, null, "TABLE", intent, null,
                data, suggestions, null, null, null, null, now());
    }

    /** Structured list response */
    public static ChatResponse list(Object data, String intent, List<String> suggestions) {
        return new ChatResponse("INSTANT", null, null, "LIST", intent, null,
                data, suggestions, null, null, null, null, now());
    }

    /**
     * Access denied — role lacks permission for the requested action.
     * Frontend renders this as a blocked-action card with navigation buttons.
     *
     * Example payload:
     * {
     *   "type": "ACCESS_DENIED",
     *   "content": "You don't have access to user management.",
     *   "responseType": "ACTION",
     *   "data": [
     *     {"label": "Go to Dashboard", "route": "/dashboard"}
     *   ]
     * }
     */
    public static ChatResponse accessDenied(String message, List<Map<String, String>> actions) {
        return new ChatResponse("ACCESS_DENIED", message, null, "ACTION",
                "ACCESS_CONTROL", null, actions, null, null, null,
                "RULE_BASED", null, now());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String now() {
        return OffsetDateTime.now().toString();
    }
}
