package com.wnsai.wms_bot.chat;

import com.wnsai.wms_bot.intent.IntentType;
import org.springframework.stereotype.Component;

/**
 * Maps a classified IntentType to a routing tier:
 *
 *   SIMPLE   → answer from in-memory cache or rule-based logic (< 50 ms)
 *   DATABASE → fetch structured data from DB, return TABLE/LIST response (< 300 ms)
 *   COMPLEX  → full LLM pipeline with context + embeddings (< 5 s, streaming)
 */
@Component
public class IntentRouter {

    public enum RouteType {
        SIMPLE,
        DATABASE,
        COMPLEX
    }

    public RouteType route(IntentType intentType) {
        return switch (intentType) {
            case GREETING, NAVIGATION -> RouteType.SIMPLE;
            case QUICK_QUERY          -> RouteType.DATABASE;
            case AI_QUERY, UNKNOWN    -> RouteType.COMPLEX;
        };
    }
}
