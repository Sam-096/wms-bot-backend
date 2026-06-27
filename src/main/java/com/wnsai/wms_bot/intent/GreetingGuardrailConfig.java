package com.wnsai.wms_bot.intent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the GreetingGuardrail veto layer.
 * All values have production-safe defaults; override via application.yml under
 * app.intent.greeting.*
 *
 * Registered automatically by @ConfigurationPropertiesScan on WmsBotApplication.
 */
@ConfigurationProperties(prefix = "app.intent.greeting")
public class GreetingGuardrailConfig {

    /** Maximum character length a message may have to qualify as a pure greeting. */
    private int maxLength = 40;

    /**
     * Domain/business keywords whose presence in a message immediately vetoes GREETING.
     * Add new language variants or domain terms here; no code change required.
     */
    private List<String> blockedDomainKeywords = new ArrayList<>(List.of(
        // ── English ──────────────────────────────────────────────────────────
        "warehouse", "stock", "vehicle", "vehicles", "truck", "trucks",
        "bag", "bags", "gate pass", "gate entry", "summary", "report", "reports",
        "commodity", "commodities", "count", "inside", "loaded", "unloaded",
        "dispatch", "inward", "outward", "inventory", "grn", "lot",
        "how many", "how much", "what is", "what are", "show me", "tell me",
        // ── Telugu ───────────────────────────────────────────────────────────
        "వేర్ హౌస్", "గోదాం", "స్టాక్", "బ్యాగ్స్", "బ్యాగులు",
        "వాహనాలు", "ట్రక్", "గేట్ పాస్", "సారాంశం", "రిపోర్ట్",
        "లోపల", "బయట", "ఎంత", "ఎన్ని", "చూపించు", "ఉంది", "వచ్చాయి",
        // ── Transliterated Telugu-English ────────────────────────────────────
        "godaam", "bagulu", "vahanaalu", "truk", "geyt paas", "stock enni",
        "warehouse lo", "bags vachai"
    ));

    /**
     * Strict allow-list of recognised greeting phrases.
     * Used only for length-aware validation; main detection is in IntentKeywordConfig.
     */
    private List<String> allowedPhrases = new ArrayList<>(List.of(
        "hi", "hello", "hey", "hii", "heya", "sup",
        "good morning", "good evening", "good night", "good afternoon",
        "namaste", "namaskar",
        "హలో", "నమస్కారం", "నమస్తే", "శుభోదయం", "కేమ్ ఉన్నావ్",
        "नमस्ते", "नमस्कार", "हेलो", "हाय",
        "வணக்கம்", "ஹாய்", "ஹலோ",
        "ನಮಸ್ಕಾರ", "ಹಲೋ", "ಹಾಯ್",
        "নমস্কার", "হ্যালো", "হাই",
        "ਸਤ ਸ੍ਰੀ ਅਕਾਲ", "ਨਮਸਕਾਰ", "ਹੈਲੋ",
        "ନମସ୍କାର", "ହେଲୋ"
    ));

    // ─── Accessors ────────────────────────────────────────────────────────────

    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

    public List<String> getBlockedDomainKeywords() { return blockedDomainKeywords; }
    public void setBlockedDomainKeywords(List<String> blockedDomainKeywords) {
        this.blockedDomainKeywords = blockedDomainKeywords;
    }

    public List<String> getAllowedPhrases() { return allowedPhrases; }
    public void setAllowedPhrases(List<String> allowedPhrases) {
        this.allowedPhrases = allowedPhrases;
    }
}
