package com.wnsai.wms_bot.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic guardrail that vetoes GREETING when domain/business signals are present.
 *
 * Responsibility: post-classification safety net only. It does not reclassify from scratch;
 * it only overrides an already-detected GREETING when evidence says otherwise.
 *
 * Veto rules (applied in order, first match wins):
 *   1. Message contains a blocked domain keyword        → AI_QUERY  (most authoritative)
 *   2. Message length exceeds configured max            → AI_QUERY
 *   3. No veto triggered                                → keep GREETING
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreetingGuardrail {

    private final GreetingGuardrailConfig config;

    /**
     * Evaluates a GREETING result. Non-GREETING intents pass through unchanged.
     *
     * @param message          original user message (used for length check + logging)
     * @param classifierResult result produced by the raw classifier
     * @return original result, or AI_QUERY override with 0.80 confidence
     */
    public IntentResult evaluate(String message, IntentResult classifierResult) {
        if (classifierResult.type() != IntentType.GREETING) {
            return classifierResult;
        }

        String normalized = normalize(message);

        // ── Veto 1: domain keyword presence ──────────────────────────────────
        List<String> domainHits = matchedDomainKeywords(normalized);
        if (!domainHits.isEmpty()) {
            log.info("Greeting guardrail rejected — domain keywords={} message='{}'",
                    domainHits, truncate(message));
            log.info("Intent overridden from GREETING -> AI_QUERY");
            log.info("Final route=AI_QUERY");
            return new IntentResult(IntentType.AI_QUERY, 0.80, null, null);
        }

        // ── Veto 2: message too long to be a pure greeting ───────────────────
        int len = message.trim().length();
        if (len > config.getMaxLength()) {
            log.info("Greeting guardrail rejected — message length {} > maxLength={}  message='{}'",
                    len, config.getMaxLength(), truncate(message));
            log.info("Intent overridden from GREETING -> AI_QUERY");
            log.info("Final route=AI_QUERY");
            return new IntentResult(IntentType.AI_QUERY, 0.70, null, null);
        }

        log.debug("Greeting guardrail passed — Final route=GREETING message='{}'", truncate(message));
        return classifierResult;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<String> matchedDomainKeywords(String normalized) {
        return config.getBlockedDomainKeywords().stream()
                .filter(kw -> containsSignal(normalized, kw.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether a domain keyword is present as a whole word/phrase.
     *
     * Multi-word phrases (e.g. "gate pass", "how many") use plain contains().
     * Single words use space/edge boundary checks that work for both ASCII
     * and Telugu/Unicode tokens without regex compilation per call.
     *
     * Input text is already lowercased and punctuation-normalised by the caller.
     */
    private boolean containsSignal(String text, String keyword) {
        if (keyword.contains(" ")) {
            return text.contains(keyword);
        }
        // Single word: must appear bounded by spaces or string edges
        return text.equals(keyword)
                || text.startsWith(keyword + " ")
                || text.endsWith(" " + keyword)
                || text.contains(" " + keyword + " ");
    }

    /** Lowercase, collapse whitespace, replace common punctuation with spaces. */
    private String normalize(String message) {
        if (message == null) return "";
        return message.toLowerCase()
                      .replaceAll("[?!.,;:।॥؟]+", " ")
                      .trim()
                      .replaceAll("\\s+", " ");
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
