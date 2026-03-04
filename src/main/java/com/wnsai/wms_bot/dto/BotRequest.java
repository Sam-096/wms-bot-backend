package com.wnsai.wms_bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BotRequest(

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 500, message = "Message too long — max 500 characters")
    String message,

    @Pattern(regexp = "^(te|hi|en|ta|kn|mr)$", message = "Invalid language code")
    String language,

    @Pattern(regexp = "^(driver|gatekeeper|supervisor|qc_officer|manager|accountant|lender|auditor|customer|admin)$",
             message = "Invalid role")
    String role,

    @Size(max = 100)
    String sessionId,

    @Size(max = 100)
    String warehouseName,     // ← comma was MISSING here — root cause

    String currentScreen,
    String contextData

) {}
