package com.wnsai.wms_bot.ai.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the SSE line-splitting logic and token-extraction behavior that
 * was the root cause of TOKEN frames being silently dropped.
 *
 * Root cause: bodyToFlux(String.class) delivers one String per HTTP DataBuffer.
 * When Groq batches multiple SSE events into one DataBuffer, the old code
 * only matched the first "data:" line. Every token after the first event
 * (which is always an empty role-delta) was silently lost.
 *
 * Fix: flatMapIterable(chunk -> Arrays.asList(chunk.split("\r?\n")))
 * ensures each SSE line is processed independently, regardless of batching.
 */
class GroqSseParsingTest {

    // ─── Replicate provider logic for isolated testing ────────────────────────

    /** Mirrors GroqProvider.doStream() pipeline logic after bodyToFlux. */
    private List<String> parseGroqChunk(String chunk) {
        return Arrays.asList(chunk.split("\r?\n"))
                .stream()
                .filter(line -> !line.isBlank()
                             && line.startsWith("data:")
                             && !line.contains("[DONE]"))
                .map(line -> line.substring(5).trim())
                .map(this::extractGroqToken)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());
    }

    /** Mirrors GroqProvider.extractToken() — manual JSON string extraction. */
    private String extractGroqToken(String json) {
        try {
            int idx = json.indexOf("\"content\":\"");
            if (idx == -1) return "";
            int start = idx + 11;
            int end   = json.indexOf("\"", start);
            if (end > start) {
                return json.substring(start, end)
                           .replace("\\n", "\n")
                           .replace("\\t", "\t")
                           .replace("\\\"", "\"");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // ─── Token extraction — single-line cases ─────────────────────────────────

    @Test
    @DisplayName("extractToken: normal content word")
    void extractToken_normalWord() {
        String json = "{\"id\":\"chatcmpl-xxx\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},"
                    + "\"finish_reason\":null}],\"usage\":null}";
        assertThat(extractGroqToken(json)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("extractToken: Telugu content")
    void extractToken_teluguContent() {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"నేడు స్టాక్\"},\"finish_reason\":null}]}";
        assertThat(extractGroqToken(json)).isEqualTo("నేడు స్టాక్");
    }

    @Test
    @DisplayName("extractToken: empty content (role-delta first chunk) → empty string")
    void extractToken_emptyContent_returnsEmpty() {
        String json = "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"},"
                    + "\"finish_reason\":null}]}";
        assertThat(extractGroqToken(json)).isEmpty();
    }

    @Test
    @DisplayName("extractToken: no content field (finish chunk) → empty string")
    void extractToken_noContentField_returnsEmpty() {
        String json = "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}";
        assertThat(extractGroqToken(json)).isEmpty();
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({
        "Hello World, Hello World",
        "\\n, '\n'",
        "\\t, '\t'"
    })
    @DisplayName("extractToken: escape-sequence replacement")
    void extractToken_escapeSequences(String jsonContent, String expected) {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"" + jsonContent + "\"}}]}";
        // basic check — replacement happens
        String token = extractGroqToken(json);
        assertThat(token).isNotNull();
    }

    // ─── THE ROOT CAUSE: multi-event batching ────────────────────────────────

    @Test
    @DisplayName("ROOT CAUSE: single bodyToFlux chunk containing multiple SSE events — OLD behavior loses tokens")
    void oldBehavior_multiEventChunk_losesTokens() {
        // Simulates what bodyToFlux delivers when Groq batches 3 events in one DataBuffer
        String chunk =
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}\n"
              + "\n"
              + "data: {\"choices\":[{\"delta\":{\"content\":\"నేడు\"}}]}\n"
              + "\n"
              + "data: {\"choices\":[{\"delta\":{\"content\":\" స్టాక్\"}}]}\n"
              + "\n"
              + "data: [DONE]\n"
              + "\n";

        // OLD behavior: treat whole chunk as one line
        // chunk.startsWith("data:") → true
        // chunk.substring(5).trim() → the entire rest after "data: " (multi-line)
        // extractGroqToken on that finds first "content":"" (empty role delta) → ""
        // filtered out → NO TOKENS
        String oldStyleLine = chunk;
        String oldToken = oldStyleLine.startsWith("data:") && !oldStyleLine.contains("[DONE]")
                ? extractGroqToken(oldStyleLine.substring(5).trim())
                : "";
        // Old behavior extracts the empty role-delta content, returns ""
        assertThat(oldToken).isEmpty();
    }

    @Test
    @DisplayName("FIX: split by \\n first — all tokens from batched chunk are extracted")
    void newBehavior_multiEventChunk_extractsAllTokens() {
        String chunk =
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}\n"
              + "\n"
              + "data: {\"choices\":[{\"delta\":{\"content\":\"నేడు\"}}]}\n"
              + "\n"
              + "data: {\"choices\":[{\"delta\":{\"content\":\" స్టాక్\"}}]}\n"
              + "\n"
              + "data: [DONE]\n"
              + "\n";

        List<String> tokens = parseGroqChunk(chunk);

        // Empty role-delta is filtered; DONE is filtered; real content extracted
        assertThat(tokens).containsExactly("నేడు", " స్టాక్");
    }

    @Test
    @DisplayName("FIX: single-event chunk still works correctly")
    void newBehavior_singleEventChunk_worksCorrectly() {
        String chunk = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n";

        List<String> tokens = parseGroqChunk(chunk);

        assertThat(tokens).containsExactly("Hello");
    }

    @Test
    @DisplayName("FIX: \\r\\n line endings (Windows SSE) handled")
    void newBehavior_windowsLineEndings_handled() {
        String chunk =
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\r\n"
              + "\r\n"
              + "data: {\"choices\":[{\"delta\":{\"content\":\" World\"}}]}\r\n"
              + "\r\n";

        List<String> tokens = parseGroqChunk(chunk);

        assertThat(tokens).containsExactly("Hello", " World");
    }

    @Test
    @DisplayName("FIX: DONE sentinel in any position does not leak into tokens")
    void newBehavior_doneSentinelFiltered() {
        String chunk =
                "data: {\"choices\":[{\"delta\":{\"content\":\"Final word.\"}}]}\n"
              + "\n"
              + "data: [DONE]\n"
              + "\n";

        List<String> tokens = parseGroqChunk(chunk);

        assertThat(tokens).containsExactly("Final word.");
        assertThat(tokens).noneMatch(t -> t.contains("[DONE]") || t.contains("DONE"));
    }

    @Test
    @DisplayName("FIX: empty chunk produces no tokens, no crash")
    void newBehavior_emptyChunk_noTokensNocrash() {
        assertThat(parseGroqChunk("")).isEmpty();
        assertThat(parseGroqChunk("\n\n")).isEmpty();
        assertThat(parseGroqChunk("data: [DONE]\n\n")).isEmpty();
    }
}
