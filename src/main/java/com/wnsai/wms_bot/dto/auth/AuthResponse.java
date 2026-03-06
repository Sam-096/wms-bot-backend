package com.wnsai.wms_bot.dto.auth;

public record AuthResponse(
    String userId,
    String username,
    String email,
    String role,
    String warehouseId,
    String warehouseName,
    String token,
    String refreshToken,
    long   expiresIn      // seconds
) {}
