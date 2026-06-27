package com.wnsai.wms_bot.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Keyword-based intent classifier. Pure in-memory — never calls any LLM.
 * Target: < 5ms for any input.
 *
 * Classification order (first match wins):
 *   1. GREETING  — whole-word token match only (not substring)
 *   2. NAVIGATION
 *   3. QUICK_QUERY
 *   4. AI_QUERY  — default for all warehouse/business questions
 *
 * After GREETING fires, GreetingGuardrail evaluates whether the result is valid.
 * If domain signals are present, GREETING is overridden to AI_QUERY deterministically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifierService implements IntentClassifier {

    private final IntentKeywordConfig keywords;
    private final GreetingGuardrail   guardrail;

    @Override
    public IntentResult classify(String message) {
        if (message == null || message.isBlank()) {
            return new IntentResult(IntentType.UNKNOWN, 0.0, null, null);
        }

        long   start      = System.currentTimeMillis();
        String normalized = normalize(message);

        // ── 1. GREETING — token-level match (not substring) ──────────────────
        if (matchesGreeting(normalized)) {
            IntentResult raw = new IntentResult(IntentType.GREETING, 0.95, null, null);
            log.info("Intent classifier raw=GREETING confidence=0.95 message='{}' in {}ms",
                    truncate(message), elapsed(start));
            IntentResult guarded = guardrail.evaluate(message, raw);
            if (guarded.type() != IntentType.GREETING) {
                // guardrail already logged the override; nothing more to log here
                return guarded;
            }
            log.info("Final route=GREETING");
            return guarded;
        }

        // ── 2. NAVIGATION ────────────────────────────────────────────────────
        String navRoute = resolveNavRoute(normalized);
        if (navRoute != null) {
            log.info("Intent=NAVIGATION route={} in {}ms", navRoute, elapsed(start));
            return new IntentResult(IntentType.NAVIGATION, 0.90, null, navRoute);
        }

        // ── 3. QUICK_QUERY ───────────────────────────────────────────────────
        String entity = resolveQuickEntity(normalized);
        if (entity != null) {
            log.info("Intent=QUICK_QUERY entity={} in {}ms", entity, elapsed(start));
            return new IntentResult(IntentType.QUICK_QUERY, 0.85, entity, null);
        }

        // ── 4. AI_QUERY — default for all warehouse/business questions ────────
        log.info("Intent=AI_QUERY confidence=0.70 in {}ms", elapsed(start));
        return new IntentResult(IntentType.AI_QUERY, 0.70, null, null);
    }

    // ─── Greeting matching (fixed) ────────────────────────────────────────────

    /**
     * Matches a greeting by checking each configured phrase against individual
     * tokens in the message — not via String.contains().
     *
     * This prevents short keywords like "hi" from matching inside words such as
     * "vehicles" (ve-hi-cles), "this", "while", "ship", etc.
     */
    private boolean matchesGreeting(String normalized) {
        for (String phrase : keywords.getGreeting()) {
            if (isGreetingMatch(normalized, phrase.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches a greeting phrase using token or prefix semantics:
     * - Multi-word phrases (e.g. "good morning"): text must equal or start with the phrase.
     * - Single words: must appear as a standalone token (not inside another word).
     * Trailing punctuation on tokens is stripped before comparison.
     */
    private boolean isGreetingMatch(String text, String phrase) {
        if (phrase.contains(" ")) {
            // Multi-word: exact equality or phrase followed by more words
            return text.equals(phrase) || text.startsWith(phrase + " ");
        }
        // Single word: split on whitespace and compare each token
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            String clean = stripTrailingPunctuation(token);
            if (clean.equals(phrase)) return true;
        }
        return false;
    }

    // ─── Navigation resolution ────────────────────────────────────────────────

    private String resolveNavRoute(String normalized) {
        if (contains(normalized, "new inward"))                             return "/inward/new";
        if (contains(normalized, "new outward"))                            return "/outward/new";
        if (contains(normalized, "inward")    && isNavIntent(normalized))   return "/inward";
        if (contains(normalized, "outward")   && isNavIntent(normalized))   return "/outward";
        if (contains(normalized, "gate pass") && isNavIntent(normalized))   return "/gate-pass";
        if (contains(normalized, "gate entry") && isNavIntent(normalized))  return "/gate-pass";
        if (contains(normalized, "inventory") && isNavIntent(normalized))   return "/inventory";
        if (contains(normalized, "bonds")     && isNavIntent(normalized))   return "/bonds";
        if (contains(normalized, "bond")      && isNavIntent(normalized))   return "/bonds";
        if (contains(normalized, "reports")   && isNavIntent(normalized))   return "/reports";
        if (contains(normalized, "report")    && isNavIntent(normalized))   return "/reports";
        if (contains(normalized, "dashboard") && isNavIntent(normalized))   return "/dashboard";
        return null;
    }

    private boolean isNavIntent(String normalized) {
        return matchesAny(normalized, List.of(
            // English
            "go to", "open", "navigate", "take me", "show", "page",
            // Telugu
            "వెళ్ళు", "చూపించు", "తెరవు", "పేజీ",
            // Hindi / Marathi
            "जाओ", "खोलो", "दिखाओ", "पेज", "पृष्ठ",
            // Tamil
            "செல்", "திற", "காட்டு", "பக்கம்",
            // Kannada
            "ಹೋಗು", "ತೆರೆ", "ತೋರಿಸು", "ಪುಟ",
            // Bengali
            "যাও", "খোলো", "দেখাও", "পাতা",
            // Gujarati
            "જાઓ", "ખોલો", "બતાવો", "પેજ",
            // Punjabi
            "ਜਾਓ", "ਖੋਲ੍ਹੋ", "ਦਿਖਾਓ", "ਪੰਨਾ",
            // Odia
            "ଯାଅ", "ଖୋଲ", "ଦେଖାଅ", "ପୃଷ୍ଠା"
        ));
    }

    // ─── Quick-query resolution ───────────────────────────────────────────────

    private String resolveQuickEntity(String normalized) {
        if (contains(normalized, "low stock") || (contains(normalized, "stock") && contains(normalized, "ఎంత")))
            return "LOW_STOCK";
        if (contains(normalized, "pending inward") || (contains(normalized, "pending") && contains(normalized, "inward")))
            return "PENDING_INWARD";
        if (contains(normalized, "today outward") || (contains(normalized, "today") && contains(normalized, "outward")))
            return "TODAY_OUTWARD";
        if (contains(normalized, "active gate pass") || (contains(normalized, "active") && contains(normalized, "gate")))
            return "ACTIVE_GATE_PASSES";
        if (contains(normalized, "expiring bond") || (contains(normalized, "expir") && contains(normalized, "bond")))
            return "EXPIRING_BONDS";
        return null;
    }

    // ─── Text utilities ───────────────────────────────────────────────────────

    /** Lowercase, collapse whitespace, replace punctuation with spaces for uniform matching. */
    private String normalize(String message) {
        return message.toLowerCase()
                      .replaceAll("[?!.,;:।॥؟]+", " ")
                      .trim()
                      .replaceAll("\\s+", " ");
    }

    /** Substring check — safe for navigation/quick-query (long keyword phrases, not single chars). */
    private boolean contains(String text, String keyword) {
        return text.contains(keyword.toLowerCase());
    }

    /** Used for nav-intent multi-keyword lists where substring is intentional. */
    private boolean matchesAny(String text, List<String> candidates) {
        return candidates.stream().anyMatch(k -> text.contains(k.toLowerCase()));
    }

    private String stripTrailingPunctuation(String token) {
        return token.replaceAll("[?!.,;:।॥؟]+$", "");
    }

    private String truncate(String s) {
        return s != null && s.length() > 50 ? s.substring(0, 50) + "…" : s;
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
