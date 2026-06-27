package com.wnsai.wms_bot.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResponseCleanerUtil#cleanStream(Flux)}.
 *
 * Tests four required behaviors:
 *   1. Long response (≥50 chars)  — streams normally, no duplicate content
 *   2. Short response (<50 chars)  — flushed as final token on completion
 *   3. Rule-based / word-token     — unchanged behavior
 *   4. Empty / whitespace          — no token emitted, stream completes
 */
class ResponseCleanerUtilTest {

    private final ResponseCleanerUtil cleaner = new ResponseCleanerUtil();

    /** Splits text into single-character tokens, simulating LLM char-by-char streaming. */
    private static Flux<String> chars(String text) {
        return Flux.fromStream(text.chars().mapToObj(c -> String.valueOf((char) c)));
    }

    // ─── Case 1: Long response — streams normally ─────────────────────────────

    @Test
    @DisplayName("Long response (≥50 chars) — all content emitted, no duplication")
    void longResponse_streamsNormally_noduplication() {
        String response =
            "స్టాక్ వివరాల కోసం Operations > Inventory వెళ్ళండి. " +
            "అక్కడ మీ స్టాక్ పూర్తి సమాచారం దొరుకుతుంది.";
        assertThat(response.length()).isGreaterThanOrEqualTo(50);

        List<String> tokens = cleaner.cleanStream(chars(response)).collectList().block();

        assertThat(tokens).isNotNull().isNotEmpty();
        String assembled = String.join("", tokens);
        assertThat(assembled.strip()).isEqualTo(response.strip());
        // No duplication: assembled length must not exceed original
        assertThat(assembled.length()).isLessThanOrEqualTo(response.length());
    }

    @Test
    @DisplayName("Exactly 50 chars — threshold fires naturally, concatWith emits nothing extra")
    void exactlyFiftyChars_noFlushNeeded() {
        String response = "12345678901234567890123456789012345678901234567890"; // exactly 50
        assertThat(response).hasSize(50);

        List<String> tokens = cleaner.cleanStream(chars(response)).collectList().block();

        assertThat(tokens).isNotNull().isNotEmpty();
        assertThat(String.join("", tokens)).isEqualTo(response);
    }

    // ─── Case 2: Short response — flushed on completion ──────────────────────

    @Test
    @DisplayName("Short Telugu response (<50 chars) — flushed as one final token on stream end")
    void shortTeluguResponse_flushedOnCompletion() {
        String response = "చూడండి.";       // "Look at it." — 8 Telugu chars
        assertThat(response.length()).isLessThan(50);

        StepVerifier.create(cleaner.cleanStream(chars(response)))
                .assertNext(token -> assertThat(token).isEqualTo(response))
                .verifyComplete();
    }

    @Test
    @DisplayName("49-char response — under threshold, flushed on completion, content preserved")
    void fortyNineChars_flushedOnCompletion() {
        String response = "1234567890123456789012345678901234567890123456789"; // exactly 49
        assertThat(response).hasSize(49);

        List<String> tokens = cleaner.cleanStream(chars(response)).collectList().block();

        assertThat(tokens).isNotNull().isNotEmpty();
        assertThat(String.join("", tokens).strip()).isEqualTo(response);
    }

    @Test
    @DisplayName("Short response — exactly one token emitted (the flushed buffer), not zero")
    void shortResponse_emitsExactlyOneToken() {
        String response = "Ok.";  // 3 chars

        StepVerifier.create(cleaner.cleanStream(chars(response)))
                .expectNextCount(1)   // exactly one flushed token
                .verifyComplete();
    }

    // ─── Case 3: Rule-based word tokens — unchanged behavior ─────────────────

    @Test
    @DisplayName("Rule-based word tokens (long) — behavior unchanged, content intact")
    void ruleBased_longWordTokens_unchanged() {
        // Mirrors RuleBasedProvider.stream() which emits words split on whitespace
        Flux<String> wordTokens = Flux.just(
                "క్షమించండి, ", "AI ", "సేవ ", "తాత్కాలికంగా ",
                "అందుబాటులో ", "లేదు. ", "దయచేసి ", "కొన్ని ",
                "నిమిషాల ", "తర్వాత ", "మళ్ళీ ", "ప్రయత్నించండి."
        );
        String expected =
                "క్షమించండి, AI సేవ తాత్కాలికంగా అందుబాటులో లేదు. " +
                "దయచేసి కొన్ని నిమిషాల తర్వాత మళ్ళీ ప్రయత్నించండి.";

        List<String> tokens = cleaner.cleanStream(wordTokens).collectList().block();

        assertThat(tokens).isNotNull().isNotEmpty();
        assertThat(String.join("", tokens).strip()).isEqualTo(expected.strip());
    }

    @Test
    @DisplayName("Rule-based English word tokens — behavior unchanged")
    void ruleBased_englishWordTokens_unchanged() {
        Flux<String> wordTokens = Flux.just(
                "AI ", "service ", "is ", "temporarily ", "unavailable. ",
                "Please ", "check ", "the ", "Stock ", "Inventory ", "page."
        );
        String expected =
                "AI service is temporarily unavailable. Please check the Stock Inventory page.";

        List<String> tokens = cleaner.cleanStream(wordTokens).collectList().block();

        assertThat(tokens).isNotNull().isNotEmpty();
        assertThat(String.join("", tokens).strip()).isEqualTo(expected.strip());
    }

    // ─── Case 4: Empty / whitespace — nothing emitted ────────────────────────

    @Test
    @DisplayName("Empty stream — no tokens emitted, stream completes cleanly")
    void emptyStream_nothingEmitted() {
        StepVerifier.create(cleaner.cleanStream(Flux.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Whitespace-only tokens — nothing emitted (isBlank() guard)")
    void whitespaceOnly_nothingEmitted() {
        StepVerifier.create(cleaner.cleanStream(Flux.just("   ", "\t", "  ")))
                .verifyComplete();
    }

    @Test
    @DisplayName("Single space token — nothing emitted")
    void singleSpace_nothingEmitted() {
        StepVerifier.create(cleaner.cleanStream(Flux.just(" ")))
                .verifyComplete();
    }

    // ─── Think-block stripping ────────────────────────────────────────────────

    @Test
    @DisplayName("<think> block — stripped, content after </think> reaches frontend")
    void thinkBlock_stripped_answerEmitted() {
        Flux<String> input = Flux.just(
                "<think>", "internal reasoning here</think>",
                "Go to Operations > Inward Receipts to create a new GRN. Click + New to start."
        );

        List<String> tokens = cleaner.cleanStream(input).collectList().block();

        assertThat(tokens).isNotNull().isNotEmpty();
        String assembled = String.join("", tokens).strip();
        assertThat(assembled).doesNotContain("<think>").doesNotContain("</think>");
        assertThat(assembled).contains("Operations");
    }

    // ─── DONE terminal contract ───────────────────────────────────────────────

    @Test
    @DisplayName("cleanStream never emits DONE — that is the orchestrator's responsibility")
    void cleanStream_neverEmitsDone() {
        // Both short (flush path) and normal path should produce only text, never DONE
        Flux<String> shortInput = Flux.just("Hi");
        Flux<String> longInput  = chars(
                "This is a much longer response that exceeds the fifty character threshold easily."
        );

        List<String> shortTokens = cleaner.cleanStream(shortInput).collectList().block();
        List<String> longTokens  = cleaner.cleanStream(longInput).collectList().block();

        assertThat(shortTokens).noneMatch(t -> t.contains("DONE") || t.contains("\"type\""));
        assertThat(longTokens).noneMatch(t -> t.contains("DONE")  || t.contains("\"type\""));
    }
}
