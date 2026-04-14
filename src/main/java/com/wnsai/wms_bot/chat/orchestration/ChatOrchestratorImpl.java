package com.wnsai.wms_bot.chat.orchestration;

import com.wnsai.wms_bot.ai.adapter.OllamaLLMAdapter;
import com.wnsai.wms_bot.ai.model.LiveWarehouseContext;
import com.wnsai.wms_bot.ai.service.PromptContextService;
import com.wnsai.wms_bot.ai.service.WMSPromptBuilder;
import com.wnsai.wms_bot.ai.util.ResponseCleanerUtil;
import com.wnsai.wms_bot.chat.ChatRequest;
import com.wnsai.wms_bot.chat.ChatResponse;
import com.wnsai.wms_bot.context.ContextBuilder;
import com.wnsai.wms_bot.embedding.EmbeddingService;
import com.wnsai.wms_bot.intent.IntentClassifier;
import com.wnsai.wms_bot.intent.IntentResult;
import com.wnsai.wms_bot.navigation.NavigationCommand;
import com.wnsai.wms_bot.navigation.NavigationResolver;
import com.wnsai.wms_bot.quick.QuickResponder;
import com.wnsai.wms_bot.repository.ChatSessionRepository;
import com.wnsai.wms_bot.security.RoleAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * Central orchestration pipeline.
 *
 * Changes from base version:
 *   1. PromptContextService fetches live warehouse stats before every LLM call.
 *   2. ResponseCleanerUtil strips <think>...</think> blocks from the token stream.
 *   3. WMSPromptBuilder receives LiveWarehouseContext for real-data injection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorImpl implements ChatOrchestrator {

    private final IntentClassifier    intentClassifier;
    private final QuickResponder      quickResponder;
    private final NavigationResolver  navigationResolver;
    private final ContextBuilder      contextBuilder;
    private final EmbeddingService    embeddingService;
    private final OllamaLLMAdapter    ollamaAdapter;
    private final WMSPromptBuilder    promptBuilder;
    private final PromptContextService promptContextService;  // live DB stats
    private final ResponseCleanerUtil  responseCleaner;       // strips <think> tags
    private final ChatSessionRepository sessionRepo;           // for language fallback
    private final RoleAccessPolicy      roleAccessPolicy;       // server-side RBAC gate

    @Override
    public Flux<ChatResponse> handle(ChatRequest req) {
        return resolveEffectiveLanguage(req)
            .flatMapMany(effectiveLang -> dispatch(withLanguage(req, effectiveLang)));
    }

    /**
     * Language resolution precedence:
     *   1. request.language (if non-blank) — user just picked a language
     *   2. ChatSession.language           — user's previous choice for this session
     *   3. "en" default
     */
    private Mono<String> resolveEffectiveLanguage(ChatRequest req) {
        if (req.language() != null && !req.language().isBlank()) {
            return Mono.just(req.language().toLowerCase());
        }
        if (req.sessionId() == null || req.sessionId().isBlank()) {
            return Mono.just("en");
        }
        return Mono.fromCallable(() ->
                sessionRepo.findBySessionId(req.sessionId())
                    .map(s -> s.getLanguage())
                    .filter(l -> l != null && !l.isBlank())
                    .map(String::toLowerCase)
                    .orElse("en"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private ChatRequest withLanguage(ChatRequest req, String language) {
        return new ChatRequest(
            req.message(), language, req.role(),
            req.warehouseId(), req.sessionId(), req.warehouseName(),
            req.context(), req.userId()
        );
    }

    private Flux<ChatResponse> dispatch(ChatRequest req) {
        long start = System.currentTimeMillis();

        IntentResult intent = intentClassifier.classify(req.message());
        log.info("SessionId={} intent={} confidence={} warehouseId={} lang={}",
            req.sessionId(), intent.type(), intent.confidence(),
            req.warehouseId(), req.language());

        return switch (intent.type()) {

            // Greeting: pure cache lookup, < 50ms
            case GREETING -> {
                String text = quickResponder.greet(req.language());
                log.info("GREETING response in {}ms", System.currentTimeMillis() - start);
                yield Flux.just(ChatResponse.instant(text), ChatResponse.done());
            }

            // Navigation: route resolution, < 100ms
            case NAVIGATION -> {
                Optional<NavigationCommand> cmd = navigationResolver.resolve(req.message());
                if (cmd.isPresent()) {
                    String route = cmd.get().route();
                    Optional<ChatResponse> denied = roleAccessPolicy.check(req.role(), route);
                    if (denied.isPresent()) {
                        log.info("NAVIGATION denied role={} route={} in {}ms",
                            req.role(), route, System.currentTimeMillis() - start);
                        yield Flux.just(denied.get(), ChatResponse.done());
                    }
                    log.info("NAVIGATION -> {} in {}ms",
                        route, System.currentTimeMillis() - start);
                    yield Flux.just(
                        ChatResponse.navigation(route, cmd.get().label()),
                        ChatResponse.done()
                    );
                }
                yield streamWithLiveContext(req, "", start);
            }

            // Quick Query: single DB fetch, < 300ms
            case QUICK_QUERY -> {
                String entity = intent.extractedEntity();
                yield quickResponder.handleQuickQuery(entity, req.warehouseId())
                    .map(ChatResponse::instant)
                    .flux()
                    .concatWith(Flux.just(ChatResponse.done()))
                    .doOnComplete(() -> log.info("QUICK_QUERY done in {}ms",
                        System.currentTimeMillis() - start));
            }

            // AI Query: build RAG context + live stats + stream LLM
            case AI_QUERY, UNKNOWN -> streamWithContext(req, start);
        };
    }

    // Streaming with RAG context + live warehouse data

    private Flux<ChatResponse> streamWithContext(ChatRequest req, long start) {
        Mono<String> dbContext  = contextBuilder.buildContext(
            req.message(), req.warehouseId(), req.role());
        Mono<String> ragContext = embeddingService.findRelevantDocs(req.message());

        return Mono.zip(dbContext, ragContext)
            .flatMapMany(tuple -> {
                String fullContext = tuple.getT1() + "\n" + tuple.getT2();
                log.info("AI_QUERY context built in {}ms, streaming...",
                    System.currentTimeMillis() - start);
                return streamWithLiveContext(req, fullContext, start);
            })
            .onErrorResume(e -> {
                log.error("Context build failed, streaming without context: {}", e.getMessage());
                return streamWithLiveContext(req, "", start);
            });
    }

    // Fetch live stats -> build prompt -> stream LLM -> clean <think> tags

    private Flux<ChatResponse> streamWithLiveContext(ChatRequest req, String ragContext, long start) {
        return promptContextService.fetchContext(req.warehouseId())
            .flatMapMany(live -> streamLLM(req, ragContext, live, start));
    }

    private Flux<ChatResponse> streamLLM(ChatRequest req, String ragContext,
                                          LiveWarehouseContext live, long start) {
        String warehouseName = req.warehouseName() != null ? req.warehouseName() : req.warehouseId();
        String systemPrompt  = promptBuilder.build(
            req.language(),
            req.role(),
            warehouseName,
            null,
            ragContext,
            live
        );

        return ollamaAdapter.streamChat(systemPrompt, req.message())
            .transform(responseCleaner::cleanStream)  // strip <think>...</think>
            .filter(token -> !token.isBlank())
            .map(ChatResponse::token)
            .concatWith(Flux.just(ChatResponse.done()))
            .doOnSubscribe(s -> log.info("LLM stream started (context+live) in {}ms",
                System.currentTimeMillis() - start))
            .doOnComplete(() -> log.info("LLM stream complete in {}ms",
                System.currentTimeMillis() - start))
            .onErrorResume(e -> {
                log.error("LLM stream error: {}", e.getMessage());
                return Flux.just(
                    ChatResponse.error("AI_OFFLINE"),
                    ChatResponse.done()
                );
            });
    }
}
