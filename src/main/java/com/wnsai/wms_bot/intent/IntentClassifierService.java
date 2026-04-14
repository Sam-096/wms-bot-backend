package com.wnsai.wms_bot.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Keyword-based intent classifier. Pure in-memory — never calls Ollama.
 * Target: < 5ms for any input.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifierService implements IntentClassifier {

    private final IntentKeywordConfig keywords;

    @Override
    public IntentResult classify(String message) {
        if (message == null || message.isBlank()) {
            return new IntentResult(IntentType.UNKNOWN, 0.0, null, null);
        }

        long start = System.currentTimeMillis();
        String lower = message.toLowerCase().trim();

        // ── 1. GREETING — highest priority, fastest exit ─────────────────────
        if (matchesAny(lower, keywords.getGreeting())) {
            log.info("Intent=GREETING confidence=0.95 msg='{}' in {}ms",
                truncate(message), elapsed(start));
            return new IntentResult(IntentType.GREETING, 0.95, null, null);
        }

        // ── 2. NAVIGATION ────────────────────────────────────────────────────
        String navRoute = resolveNavRoute(lower);
        if (navRoute != null) {
            log.info("Intent=NAVIGATION route={} in {}ms", navRoute, elapsed(start));
            return new IntentResult(IntentType.NAVIGATION, 0.90, null, navRoute);
        }

        // ── 3. QUICK_QUERY ───────────────────────────────────────────────────
        String entity = resolveQuickEntity(lower);
        if (entity != null) {
            log.info("Intent=QUICK_QUERY entity={} in {}ms", entity, elapsed(start));
            return new IntentResult(IntentType.QUICK_QUERY, 0.85, entity, null);
        }

        // ── 4. AI_QUERY — anything warehouse-related goes to Ollama ──────────
        log.info("Intent=AI_QUERY confidence=0.70 in {}ms", elapsed(start));
        return new IntentResult(IntentType.AI_QUERY, 0.70, null, null);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private boolean matchesAny(String lower, java.util.List<String> candidates) {
        return candidates.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    private String resolveNavRoute(String lower) {
        // New inward/outward must be checked before generic inward/outward
        if (contains(lower, "new inward"))     return "/inward/new";
        if (contains(lower, "new outward"))    return "/outward/new";
        if (contains(lower, "inward") && isNavIntent(lower))   return "/inward";
        if (contains(lower, "outward") && isNavIntent(lower))  return "/outward";
        if (contains(lower, "gate pass") && isNavIntent(lower)) return "/gate-pass";
        if (contains(lower, "gate entry") && isNavIntent(lower)) return "/gate-pass";
        if (contains(lower, "inventory") && isNavIntent(lower)) return "/inventory";
        if (contains(lower, "bonds") && isNavIntent(lower))    return "/bonds";
        if (contains(lower, "bond") && isNavIntent(lower))     return "/bonds";
        if (contains(lower, "reports") && isNavIntent(lower))  return "/reports";
        if (contains(lower, "report") && isNavIntent(lower))   return "/reports";
        if (contains(lower, "dashboard") && isNavIntent(lower)) return "/dashboard";
        return null;
    }

    /** Nav intent = message has navigation verbs/markers in any of the 10 supported languages */
    private boolean isNavIntent(String lower) {
        return matchesAny(lower, java.util.List.of(
            // English
            "go to", "open", "navigate", "take me", "show", "page",
            // Telugu
            "వెళ్ళు", "చూపించు", "తెరవు", "పేజీ",
            // Hindi / Marathi (Devanagari)
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

    private String resolveQuickEntity(String lower) {
        if (contains(lower, "low stock") || (contains(lower, "stock") && contains(lower, "ఎంత")))
            return "LOW_STOCK";
        if (contains(lower, "pending inward") || (contains(lower, "pending") && contains(lower, "inward")))
            return "PENDING_INWARD";
        if (contains(lower, "today outward") || (contains(lower, "today") && contains(lower, "outward")))
            return "TODAY_OUTWARD";
        if (contains(lower, "active gate pass") || (contains(lower, "active") && contains(lower, "gate")))
            return "ACTIVE_GATE_PASSES";
        if (contains(lower, "expiring bond") || (contains(lower, "expir") && contains(lower, "bond")))
            return "EXPIRING_BONDS";
        return null;
    }

    private boolean contains(String text, String keyword) {
        return text.contains(keyword.toLowerCase());
    }

    private String truncate(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
