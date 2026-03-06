package com.wnsai.wms_bot.intent;

public record IntentResult(
    IntentType type,
    double confidence,
    String extractedEntity,
    String navigationRoute
) {}
