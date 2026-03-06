package com.wnsai.wms_bot.cache;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of pre-built greeting responses per language.
 * Loaded once at startup — zero latency lookups thereafter.
 */
@Slf4j
@Component
public class GreetingResponseCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        cache.put("te", "నమస్కారం! మీ warehouse లో ఏమి జరుగుతుందో చెప్పండి 🏭");
        cache.put("hi", "नमस्ते! आपके गोदाम में क्या हो रहा है? 🏭");
        cache.put("en", "Hello! What's happening in your warehouse today? 🏭");
        cache.put("ta", "வணக்கம்! உங்கள் கிடங்கில் என்ன நடக்கிறது? 🏭");
        cache.put("kn", "ನಮಸ್ಕಾರ! ನಿಮ್ಮ ಗೋದಾಮಿನಲ್ಲಿ ಏನು ನಡೆಯುತ್ತಿದೆ? 🏭");
        cache.put("mr", "नमस्कार! तुमच्या गोदामात काय चालू आहे? 🏭");
        cache.put("bn", "নমস্কার! আপনার গুদামে কী ঘটছে? 🏭");
        cache.put("gu", "નમસ્કાર! તમારા ગોડાઉનમાં શું થઈ રહ્યું છે? 🏭");
        cache.put("pa", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ! ਤੁਹਾਡੇ ਗੁਦਾਮ ਵਿੱਚ ਕੀ ਹੋ ਰਿਹਾ ਹੈ? 🏭");
        cache.put("or", "ନମସ୍କାର! ଆପଣଙ୍କ ଗୋଦାମ ରେ କ'ଣ ଚାଲୁ ଅଛି? 🏭");
        log.info("GreetingResponseCache loaded {} language entries", cache.size());
    }

    /** Returns greeting for the given language code, defaults to English. */
    public String get(String language) {
        return cache.getOrDefault(
            language != null ? language.toLowerCase() : "en",
            cache.get("en")
        );
    }
}
