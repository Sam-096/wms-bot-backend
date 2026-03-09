package com.wnsai.wms_bot.ai;

import com.wnsai.wms_bot.ai.port.LLMProvider;
import com.wnsai.wms_bot.ai.provider.GroqProvider;
import com.wnsai.wms_bot.ai.provider.OllamaProvider;
import com.wnsai.wms_bot.ai.provider.RuleBasedProvider;
import com.wnsai.wms_bot.ai.provider.SarvamProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 4-tier LLM fallback chain:
 *   Tier 1 → OllamaProvider    (local, disabled in production via ollama.enabled=false)
 *   Tier 2 → GroqProvider      (cloud, 8s timeout, skipped if GROQ_API_KEY not set)
 *   Tier 3 → SarvamProvider    (Indian AI, 5s timeout, skipped if SARVAM_API_KEY not set)
 *   Tier 4 → RuleBasedProvider (always works, no external deps)
 *
 * Logs INFO "Response from: OLLAMA/GROQ/SARVAM/RULE_BASED" on first token from the winning tier.
 */
@Slf4j
@Component
public class LLMFallbackChain {

    private final OllamaProvider    ollamaProvider;
    private final GroqProvider      groqProvider;
    private final SarvamProvider    sarvamProvider;
    private final RuleBasedProvider ruleBasedProvider;

    public LLMFallbackChain(OllamaProvider ollamaProvider,
                             GroqProvider groqProvider,
                             SarvamProvider sarvamProvider,
                             RuleBasedProvider ruleBasedProvider) {
        this.ollamaProvider    = ollamaProvider;
        this.groqProvider      = groqProvider;
        this.sarvamProvider    = sarvamProvider;
        this.ruleBasedProvider = ruleBasedProvider;
    }

    public Flux<String> stream(String systemPrompt, String userMessage, String language) {
        return withLogging(ollamaProvider, systemPrompt, userMessage, language)
                .onErrorResume(e -> {
                    log.warn("Tier 1 (OLLAMA) failed: {} — trying Tier 2 (GROQ)", e.getMessage());
                    return withLogging(groqProvider, systemPrompt, userMessage, language);
                })
                .onErrorResume(e -> {
                    log.warn("Tier 2 (GROQ) failed: {} — trying Tier 3 (SARVAM)", e.getMessage());
                    return withLogging(sarvamProvider, systemPrompt, userMessage, language);
                })
                .onErrorResume(e -> {
                    log.warn("Tier 3 (SARVAM) failed: {} — falling back to Tier 4 (RULE_BASED)", e.getMessage());
                    return withLogging(ruleBasedProvider, systemPrompt, userMessage, language);
                });
    }

    /** Wraps a provider's Flux to log which tier produced the first token. */
    private Flux<String> withLogging(LLMProvider provider,
                                     String systemPrompt,
                                     String userMessage,
                                     String language) {
        AtomicBoolean firstToken = new AtomicBoolean(false);
        return provider.stream(systemPrompt, userMessage, language)
                .doOnNext(token -> {
                    if (firstToken.compareAndSet(false, true)) {
                        log.info("Response from: {}", provider.getName());
                    }
                });
    }
}
