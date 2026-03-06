package com.wnsai.wms_bot.chat.orchestration;

import com.wnsai.wms_bot.ai.adapter.OllamaLLMAdapter;
import com.wnsai.wms_bot.ai.service.WMSPromptBuilder;
import com.wnsai.wms_bot.chat.ChatRequest;
import com.wnsai.wms_bot.chat.ChatResponse;
import com.wnsai.wms_bot.context.ContextBuilder;
import com.wnsai.wms_bot.embedding.EmbeddingService;
import com.wnsai.wms_bot.intent.IntentClassifier;
import com.wnsai.wms_bot.intent.IntentResult;
import com.wnsai.wms_bot.navigation.NavigationCommand;
import com.wnsai.wms_bot.navigation.NavigationResolver;
import com.wnsai.wms_bot.quick.QuickResponder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Central orchestration pipeline.
 *
 * Depends ONLY on interfaces — never on concrete implementations.
 * SOLID: single responsibility (routing), open for extension (new intents).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorImpl implements ChatOrchestrator {

    private final IntentClassifier     intentClassifier;
    private final QuickResponder       quickResponder;
    private final NavigationResolver   navigationResolver;
    private final ContextBuilder       contextBuilder;
    private final EmbeddingService     embeddingService;
    private final OllamaLLMAdapter     ollamaAdapter;
    private final WMSPromptBuilder     promptBuilder;

    @Override
    public Flux<ChatResponse> handle(ChatRequest req) {
        long start = System.currentTimeMillis();

        // ── Step 1: Classify — never touches LLM ──────────────────────────────
        IntentResult intent = intentClassifier.classify(req.message());
        log.info("SessionId={} intent={} confidence={} warehouseId={}",
            req.sessionId(), intent.type(), intent.confidence(), req.warehouseId());

        return switch (intent.type()) {

            // ── Greeting: pure cache lookup, < 50ms ───────────────────────────
            case GREETING -> {
                String text = quickResponder.greet(req.language());
                log.info("GREETING response in {}ms", System.currentTimeMillis() - start);
                yield Flux.just(ChatResponse.instant(text));
            }

            // ── Navigation: route resolution, < 100ms ─────────────────────────
            case NAVIGATION -> {
                Optional<NavigationCommand> cmd = navigationResolver.resolve(req.message());
                if (cmd.isPresent()) {
                    log.info("NAVIGATION → {} in {}ms",
                        cmd.get().route(), System.currentTimeMillis() - start);
                    yield Flux.just(ChatResponse.navigation(
                        cmd.get().route(), cmd.get().label()));
                }
                // Fallback: classify as AI_QUERY if route resolution fails
                yield streamOllama(req, "", start);
            }

            // ── Quick Query: single DB fetch, < 300ms ─────────────────────────
            case QUICK_QUERY -> {
                String entity = intent.extractedEntity();
                yield quickResponder.handleQuickQuery(entity, req.warehouseId())
                    .map(ChatResponse::instant)
                    .flux()
                    .doOnComplete(() -> log.info("QUICK_QUERY done in {}ms",
                        System.currentTimeMillis() - start));
            }

            // ── AI Query: build context + RAG + stream Ollama ─────────────────
            case AI_QUERY, UNKNOWN -> streamWithContext(req, start);
        };
    }

    // ─── Streaming with context + RAG ─────────────────────────────────────────

    private Flux<ChatResponse> streamWithContext(ChatRequest req, long start) {
        Mono<String> dbContext  = contextBuilder.buildContext(
            req.message(), req.warehouseId(), req.role());
        Mono<String> ragContext = embeddingService.findRelevantDocs(req.message());

        return Mono.zip(dbContext, ragContext)
            .flatMapMany(tuple -> {
                String fullContext = tuple.getT1() + "\n" + tuple.getT2();
                log.info("AI_QUERY context built in {}ms, streaming…",
                    System.currentTimeMillis() - start);
                return streamOllama(req, fullContext, start);
            })
            .onErrorResume(e -> {
                log.error("Context build failed, streaming without context: {}", e.getMessage());
                return streamOllama(req, "", start);
            });
    }

    // ─── Ollama SSE streaming ─────────────────────────────────────────────────

    private Flux<ChatResponse> streamOllama(ChatRequest req, String context, long start) {
        String systemPrompt = promptBuilder.build(
            req.language(),
            req.role(),
            req.warehouseId(),
            null,          // currentScreen not in new ChatRequest
            context
        );

        return ollamaAdapter.streamChat(systemPrompt, req.message())
            .map(ChatResponse::token)
            .concatWith(Flux.just(ChatResponse.done()))
            .doOnSubscribe(s -> log.info("Ollama stream started in {}ms",
                System.currentTimeMillis() - start))
            .doOnComplete(() -> log.info("Ollama stream complete in {}ms",
                System.currentTimeMillis() - start))
            .onErrorResume(e -> {
                log.error("Ollama offline: {}", e.getMessage());
                return Flux.just(
                    ChatResponse.error("AI_OFFLINE"),
                    ChatResponse.done()
                );
            });
    }
}
