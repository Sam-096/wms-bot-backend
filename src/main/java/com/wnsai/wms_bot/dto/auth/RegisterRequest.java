package com.wnsai.wms_bot.dto.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username: only letters, digits and underscore")
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100)
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(MANAGER|OPERATOR|GATE_STAFF|VIEWER)$",
             message = "Role must be MANAGER, OPERATOR, GATE_STAFF, or VIEWER")
    String role,

    @Size(max = 50)
    String warehouseId
) {}
