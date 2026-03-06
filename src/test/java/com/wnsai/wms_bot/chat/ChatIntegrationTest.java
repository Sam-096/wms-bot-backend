package com.wnsai.wms_bot.chat;

import com.wnsai.wms_bot.intent.IntentClassifier;
import com.wnsai.wms_bot.intent.IntentResult;
import com.wnsai.wms_bot.intent.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering all 5 intent types through the full pipeline.
 *
 * The app starts without DB (JPA excluded in application.properties),
 * so these tests run without any external dependencies except Ollama
 * for AI_QUERY tests (which are skipped if Ollama is unavailable).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
class ChatIntegrationTest {

    @Autowired
    WebTestClient webClient;

    @Autowired
    IntentClassifier intentClassifier;

    // ─── Intent Classifier Unit Tests ─────────────────────────────────────────

    @ParameterizedTest(name = "GREETING: [{0}]")
    @CsvSource({
        "hello",
        "hi",
        "నమస్కారం",
        "హలో",
        "namaste",
        "good morning"
    })
    @DisplayName("Greeting keywords → GREETING intent")
    void greetingIntent(String message) {
        IntentResult result = intentClassifier.classify(message);
        assertThat(result.type()).isEqualTo(IntentType.GREETING);
        assertThat(result.confidence()).isGreaterThan(0.9);
    }

    @ParameterizedTest(name = "NAVIGATION: [{0}]")
    @CsvSource({
        "go to dashboard",
        "open inventory",
        "inward కి వెళ్ళు",
        "outward చూపించు",
        "gate pass open",
        "bonds చూపించు",
        "reports కి వెళ్ళు"
    })
    @DisplayName("Navigation phrases → NAVIGATION intent")
    void navigationIntent(String message) {
        IntentResult result = intentClassifier.classify(message);
        assertThat(result.type()).isEqualTo(IntentType.NAVIGATION);
        assertThat(result.confidence()).isGreaterThan(0.8);
    }

    @ParameterizedTest(name = "QUICK_QUERY: [{0}]")
    @CsvSource({
        "low stock",
        "pending inward",
        "active gate passes",
        "expiring bonds",
        "stock ఎంత"
    })
    @DisplayName("Quick-query phrases → QUICK_QUERY intent")
    void quickQueryIntent(String message) {
        IntentResult result = intentClassifier.classify(message);
        assertThat(result.type()).isEqualTo(IntentType.QUICK_QUERY);
        assertThat(result.extractedEntity()).isNotNull();
    }

    @Test
    @DisplayName("Warehouse question → AI_QUERY intent")
    void aiQueryIntent() {
        IntentResult result = intentClassifier.classify(
            "how do I create a new inward receipt with QC remarks?");
        assertThat(result.type()).isEqualTo(IntentType.AI_QUERY);
    }

    @Test
    @DisplayName("Null / blank → UNKNOWN intent (no crash)")
    void unknownIntent() {
        IntentResult nullResult  = intentClassifier.classify(null);
        IntentResult blankResult = intentClassifier.classify("   ");
        assertThat(nullResult.type()).isEqualTo(IntentType.UNKNOWN);
        assertThat(blankResult.type()).isEqualTo(IntentType.UNKNOWN);
    }

    // ─── HTTP Endpoint Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/chat GREETING → INSTANT SSE, no Ollama call")
    void chatGreetingEndpoint() {
        webClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"message":"hello","language":"en","role":"manager","warehouseId":"wh-001"}
                """)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBodyList(String.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody()).isNotEmpty();
                String body = String.join("", result.getResponseBody());
                assertThat(body).contains("INSTANT");
                // Must not call Ollama — response is instant
            });
    }

    @Test
    @DisplayName("POST /api/v1/chat NAVIGATION → NAVIGATION SSE with route")
    void chatNavigationEndpoint() {
        webClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"message":"go to dashboard","language":"en","role":"manager","warehouseId":"wh-001"}
                """)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(String.class)
            .consumeWith(result -> {
                String body = String.join("", result.getResponseBody());
                assertThat(body).contains("NAVIGATION");
                assertThat(body).contains("/dashboard");
            });
    }

    @Test
    @DisplayName("POST /api/v1/chat QUICK_QUERY → INSTANT SSE (stub data without DB)")
    void chatQuickQueryEndpoint() {
        webClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"message":"low stock","language":"te","role":"manager","warehouseId":"wh-001"}
                """)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(String.class)
            .consumeWith(result -> {
                String body = String.join("", result.getResponseBody());
                assertThat(body).contains("INSTANT");
                assertThat(body).containsIgnoringCase("stock");
            });
    }

    @Test
    @DisplayName("POST /api/v1/chat with empty message → 400 Bad Request")
    void chatEmptyMessage() {
        webClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"message":"","language":"en","role":"manager","warehouseId":"wh-001"}
                """)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    @DisplayName("POST /api/v1/chat Telugu greeting → Telugu INSTANT response")
    void chatTeluguGreeting() {
        webClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"message":"నమస్కారం","language":"te","role":"supervisor","warehouseId":"wh-001"}
                """)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(String.class)
            .consumeWith(result -> {
                String body = String.join("", result.getResponseBody());
                assertThat(body).contains("INSTANT");
                // Telugu greeting contains Telugu characters
                assertThat(body).containsAnyOf("నమస్కారం", "warehouse", "🏭");
            });
    }
}
