package com.wnsai.wms_bot.ai.provider;

import com.wnsai.wms_bot.ai.port.LLMProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Tier 3 — Rule-Based fallback. Always succeeds. No external dependencies.
 * Returns language-appropriate responses from an in-memory keyword→response map.
 * Emits words individually to preserve the token-streaming feel on the client.
 */
@Slf4j
@Component
public class RuleBasedProvider implements LLMProvider {

    // keyword → { lang-code → response }
    private static final Map<String, Map<String, String>> TOPIC_RESPONSES = Map.of(
        "stock", Map.of(
            "te", "క్షమించండి, AI సేవ తాత్కాలికంగా అందుబాటులో లేదు. స్టాక్ స్థాయిలను తనిఖీ చేయడానికి దయచేసి స్టాక్ ఇన్వెంటరీ పేజీని సందర్శించండి.",
            "hi", "क्षमा करें, AI सेवा अस्थायी रूप से अनुपलब्ध है। स्टॉक स्तर जांचने के लिए कृपया स्टॉक इन्वेंटरी पेज देखें।",
            "en", "AI service is temporarily unavailable. Please visit the Stock Inventory page to check current stock levels."
        ),
        "inward", Map.of(
            "te", "AI అందుబాటులో లేదు. ఇన్వార్డ్ రసీదుల వివరాలకు దయచేసి ఇన్వార్డ్ రసీదులు పేజీని తనిఖీ చేయండి.",
            "hi", "AI उपलब्ध नहीं है। इनवार्ड विवरण के लिए कृपया इनवार्ड रसीदें पेज जांचें।",
            "en", "AI is unavailable. Please check the Inward Receipts page for GRN and inward transaction details."
        ),
        "outward", Map.of(
            "te", "AI అందుబాటులో లేదు. అవుట్‌వార్డ్ డిస్పాచ్ వివరాలకు దయచేసి అవుట్‌వార్డ్ డిస్పాచ్ పేజీని చూడండి.",
            "hi", "AI उपलब्ध नहीं है। आउटवार्ड विवरण के लिए कृपया आउटवार्ड डिस्पैच पेज देखें।",
            "en", "AI is unavailable. Please check the Outward Dispatch page for dispatch and delivery details."
        ),
        "gate", Map.of(
            "te", "AI అందుబాటులో లేదు. గేట్ పాస్ వివరాలకు గేట్ పాస్ మేనేజ్‌మెంట్ పేజీని తనిఖీ చేయండి.",
            "hi", "AI उपलब्ध नहीं है। गेट पास विवरण के लिए गेट पास मैनेजमेंट पेज देखें।",
            "en", "AI is unavailable. Please check the Gate Pass Management page for vehicle entry and exit details."
        ),
        "bond", Map.of(
            "te", "AI అందుబాటులో లేదు. బాండ్ వివరాలను చూడటానికి బాండ్ మేనేజ్‌మెంట్ పేజీని తనిఖీ చేయండి.",
            "hi", "AI उपलब्ध नहीं है। बॉन्ड विवरण के लिए बॉन्ड मैनेजमेंट पेज देखें।",
            "en", "AI is unavailable. Please check the Bond Management page for bonded goods and expiry information."
        )
    );

    private static final Map<String, String> DEFAULT_RESPONSE = Map.of(
        "te", "క్షమించండి, AI సేవ తాత్కాలికంగా అందుబాటులో లేదు. దయచేసి కొన్ని నిమిషాల తర్వాత మళ్ళీ ప్రయత్నించండి లేదా సహాయం కోసం మీ సూపర్‌వైజర్‌ని సంప్రదించండి.",
        "hi", "क्षमा करें, AI सेवा अस्थायी रूप से अनुपलब्ध है। कृपया कुछ मिनट बाद पुनः प्रयास करें या सहायता के लिए अपने सुपरवाइजर से संपर्क करें।",
        "en", "AI service is temporarily unavailable. Please try again in a few minutes or contact your supervisor for assistance."
    );

    @Override
    public String getName() { return "RULE_BASED"; }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, String language) {
        String lang     = resolveLanguage(userMessage, language);
        String response = findResponse(userMessage == null ? "" : userMessage.toLowerCase(), lang);
        log.info("RuleBasedProvider: serving rule-based response (lang={})", lang);
        // Emit word-by-word to preserve streaming UX
        String[] words = response.split("(?<=\\s)|(?=\\s)");
        return Flux.fromArray(words);
    }

    private String resolveLanguage(String message, String hint) {
        if (hint != null && !hint.isBlank() && !"null".equalsIgnoreCase(hint)) return hint;
        if (message == null) return "en";
        for (char c : message.toCharArray()) {
            if (c >= '\u0C00' && c <= '\u0C7F') return "te";   // Telugu
            if (c >= '\u0900' && c <= '\u097F') return "hi";   // Hindi/Devanagari
        }
        return "en";
    }

    private String findResponse(String lowerMsg, String lang) {
        for (Map.Entry<String, Map<String, String>> entry : TOPIC_RESPONSES.entrySet()) {
            if (lowerMsg.contains(entry.getKey())) {
                Map<String, String> byLang = entry.getValue();
                return byLang.getOrDefault(lang, byLang.get("en"));
            }
        }
        return DEFAULT_RESPONSE.getOrDefault(lang, DEFAULT_RESPONSE.get("en"));
    }
}
