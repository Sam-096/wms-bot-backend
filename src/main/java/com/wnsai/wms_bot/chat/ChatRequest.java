package com.wnsai.wms_bot.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 500, message = "Message too long — max 500 characters")
    String message,

    @Pattern(regexp = "^(te|hi|en|ta|kn|mr|bn|gu|pa|or|ne)?$",
             message = "Invalid language code")
    String language,

    // Case-insensitive — server always overrides this with the JWT role anyway,
    // but we still validate shape so a wildly malformed value surfaces as 400.
    @Pattern(regexp = "(?i)^(admin|manager|operator|viewer|gate_staff|driver|gatekeeper|supervisor|qc_officer|accountant|lender|auditor|customer)?$",
             message = "Invalid role")
    String role,

    @Size(max = 100)
    String warehouseId,

    @Size(max = 100)
    String sessionId,

    /** Optional: human-readable warehouse name for richer AI prompts */
    @Size(max = 150)
    String warehouseName,

    /**
     * Optional snapshot of current warehouse state injected by the frontend.
     * Helps the AI give context-aware responses without an extra DB round-trip.
     */
    ChatContext context,

    /**
     * Injected server-side from JWT — never trusted from request body.
     * ChatController overwrites whatever the client sends here.
     */
    @Size(max = 36)
    String userId

) {
    /** Lightweight context snapshot — all fields optional/nullable */
    public record ChatContext(
        Long    pendingInward,
        Long    pendingOutward,
        Long    lowStockCount,
        Integer openGatePasses
    ) {}
}
