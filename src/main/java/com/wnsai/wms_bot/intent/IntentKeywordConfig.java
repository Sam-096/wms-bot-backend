package com.wnsai.wms_bot.intent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyword lists for intent classification.
 * Bean registered via @ConfigurationPropertiesScan in WmsBotApplication.
 * Override defaults in application.yml under intent.keywords.*
 */
@ConfigurationProperties(prefix = "intent.keywords")
public class IntentKeywordConfig {

    // All 10 supported languages + common English
    private List<String> greeting = new ArrayList<>(List.of(
        // English
        "hi", "hello", "hey", "hii", "heya", "sup", "good morning",
        "namaste", "namaskar",
        // Telugu
        "హలో", "నమస్కారం", "నమస్తే", "శుభోదయం", "కేమ్ ఉన్నావ్",
        // Hindi / Marathi (shared Devanagari)
        "नमस्ते", "नमस्कार", "हेलो", "हाय",
        // Tamil
        "வணக்கம்", "ஹாய்", "ஹலோ",
        // Kannada
        "ನಮಸ್ಕಾರ", "ಹಲೋ", "ಹಾಯ್",
        // Bengali
        "নমস্কার", "হ্যালো", "হাই",
        // Gujarati
        "નમસ્કાર", "નમસ્તે", "હેલો",
        // Punjabi
        "ਸਤ ਸ੍ਰੀ ਅਕਾਲ", "ਨਮਸਕਾਰ", "ਹੈਲੋ",
        // Odia
        "ନମସ୍କାର", "ହେଲୋ"
    ));

    // Navigation trigger phrases
    private List<String> navigation = new ArrayList<>(List.of(
        "inward కి వెళ్ళు", "go to dashboard", "open inventory",
        "inward page", "outward చూపించు", "reports కి వెళ్ళు",
        "gate pass open", "bonds చూపించు",
        "go to", "navigate to", "take me to", "open the",
        "కి వెళ్ళు", "పేజీ తెరవు", "చూపించు", "తెరవు"
    ));

    // Quick-query trigger phrases (answered from DB directly)
    private List<String> quickQuery = new ArrayList<>(List.of(
        "stock ఎంత", "pending inward", "today outward",
        "active gate passes", "expiring bonds", "low stock",
        "stock count", "how many items", "total stock",
        "ఎంత ఉంది", "ఎన్ని bags", "తక్కువ stock"
    ));

    // ─── Getters and Setters ────────────────────────────────────────────────────

    public List<String> getGreeting() { return greeting; }
    public void setGreeting(List<String> greeting) { this.greeting = greeting; }

    public List<String> getNavigation() { return navigation; }
    public void setNavigation(List<String> navigation) { this.navigation = navigation; }

    public List<String> getQuickQuery() { return quickQuery; }
    public void setQuickQuery(List<String> quickQuery) { this.quickQuery = quickQuery; }
}
