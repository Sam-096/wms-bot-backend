package com.wnsai.wms_bot.chat;

/**
 * Sealed response envelope sent as SSE data field (JSON).
 *
 * type=INSTANT   → content carries the full instant reply
 * type=NAVIGATION → route + content (label)
 * type=TOKEN     → content carries one streaming token
 * type=DONE      → signals end of stream
 * type=ERROR     → content carries error code
 */
public record ChatResponse(
    String type,
    String content,
    String route
) {
    public static ChatResponse instant(String content) {
        return new ChatResponse("INSTANT", content, null);
    }

    public static ChatResponse navigation(String route, String label) {
        return new ChatResponse("NAVIGATION", label, route);
    }

    public static ChatResponse token(String content) {
        return new ChatResponse("TOKEN", content, null);
    }

    public static ChatResponse done() {
        return new ChatResponse("DONE", null, null);
    }

    public static ChatResponse error(String code) {
        return new ChatResponse("ERROR", code, null);
    }
}
