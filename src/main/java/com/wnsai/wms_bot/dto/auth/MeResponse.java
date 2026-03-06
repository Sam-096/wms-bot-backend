package com.wnsai.wms_bot.dto.auth;

import java.time.OffsetDateTime;

public record MeResponse(
    String         userId,
    String         username,
    String         email,
    String         role,
    String         warehouseId,
    Boolean        isActive,
    OffsetDateTime createdAt,
    OffsetDateTime lastLogin
) {}
