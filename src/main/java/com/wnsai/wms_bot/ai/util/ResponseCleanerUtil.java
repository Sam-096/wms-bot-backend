package com.wnsai.wms_bot.ai.util;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.regex.Pattern;

/**
 * Strips &lt;think&gt;...&lt;/think&gt; reasoning blocks from LLM responses.
 *
 * Issue: Models like DeepSeek-R1 and Qwen-QwQ emit internal reasoning wrapped
 * in &lt;think&gt; tags before the actual answer. These must never reach the frontend.
 *
 * Two modes:
 *   1. {@link #stripThinkTags(String)}   — for complete strings (non-streaming)
 *   2. {@link #cleanStream(Flux)}         — for SSE token streams (stateful scan)
 *
 * Streaming strategy:
 *   Tokens are buffered until the system can determine whether the response
 *   starts with a &lt;think&gt; block. If a think block is detected, all content is
 *   suppressed until &lt;/think&gt; is found. Non-think responses pass through with
 *   a preamble buffer latency of ~50ms (≈10 first tokens).
 */
@Component
public class ResponseCleanerUtil {

    private static final Pattern THINK_PATTERN =
            Pattern.compile("(?s)<think>.*?</think>\\s*");

    // Strips ```action{...}``` blocks the LLM may have been prompted to emit
    private static final Pattern ACTION_BLOCK_PATTERN =
            Pattern.compile("(?s)```action\\{.*?}```\\s*");

    // Safety net: strips JSON-like envelopes the LLM may hallucinate, e.g.
    //   {"type":"ACCESS_DENIED", ...}   or   {\"type\":\"INFO\", ...}
    // Matches a brace-balanced object that starts with (optionally escaped) "type":
    // Non-greedy up to the first closing brace that follows a quoted value or ].
    private static final Pattern JSON_ENVELOPE_PATTERN =
            Pattern.compile("(?s)\\{\\s*[\\\\]?\"type[\\\\]?\"\\s*:.*?\\}(?:\\s*\\])?\\s*");

    // Strips raw ``` code fences entirely — frontend renders plain text
    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("(?s)```[a-zA-Z]*\\n?.*?```\\s*");

    // ─── String mode ──────────────────────────────────────────────────────────

    /**
     * Removes all &lt;think&gt;...&lt;/think&gt; blocks from a complete response string.
     * Also trims leading/trailing whitespace left after removal.
     */
    public String stripThinkTags(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String cleaned = THINK_PATTERN.matcher(raw).replaceAll("");
        cleaned = ACTION_BLOCK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = JSON_ENVELOPE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = CODE_FENCE_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    // ─── Streaming mode ───────────────────────────────────────────────────────

    /**
     * Filters &lt;think&gt; blocks from an SSE token Flux without breaking streaming.
     *
     * State machine transitions:
     *   UNDETERMINED → PASS_THROUGH  (if preamble fills without finding &lt;think&gt;)
     *   UNDETERMINED → IN_THINK      (if &lt;think&gt; found in preamble)
     *   IN_THINK     → PASS_THROUGH  (when &lt;/think&gt; found)
     *   PASS_THROUGH → PASS_THROUGH  (steady state, all tokens emitted)
     */
    public Flux<String> cleanStream(Flux<String> rawTokens) {
        return rawTokens
                .scan(CleanState.initial(), CleanState::process)
                .skip(1)                               // skip the initial empty state
                .filter(s -> !s.toEmit().isEmpty())
                .map(CleanState::toEmit);
    }

    // ─── Internal state machine ───────────────────────────────────────────────

    /**
     * Immutable state carried through Reactor's {@code scan} operator.
     * {@code scan} is always sequential — no concurrency concern.
     */
    record CleanState(
            Phase  phase,
            String pending,   // buffered chars not yet emitted
            String toEmit     // tokens to emit for this step (empty = nothing)
    ) {

        enum Phase { UNDETERMINED, IN_THINK, PASS_THROUGH }

        static CleanState initial() {
            return new CleanState(Phase.UNDETERMINED, "", "");
        }

        CleanState process(String token) {
            String curr = pending + token;

            return switch (phase) {

                case UNDETERMINED -> {
                    if (curr.contains("<think>")) {
                        // Think block detected: emit content before <think>, suppress the rest
                        int idx    = curr.indexOf("<think>");
                        String pre = curr.substring(0, idx);
                        String rem = curr.substring(idx + 7); // content after <think>
                        yield new CleanState(Phase.IN_THINK, rem, pre);
                    } else if (curr.length() >= 50 || curr.endsWith("\n")) {
                        // Enough preamble buffered without seeing <think> — safe to pass through
                        yield new CleanState(Phase.PASS_THROUGH, "", curr);
                    } else {
                        // Still buffering the preamble
                        yield new CleanState(Phase.UNDETERMINED, curr, "");
                    }
                }

                case IN_THINK -> {
                    if (curr.contains("</think>")) {
                        // End of think block — emit everything after </think>
                        int endIdx = curr.indexOf("</think>");
                        String after = curr.substring(endIdx + 8).stripLeading();
                        yield new CleanState(Phase.PASS_THROUGH, "", after);
                    } else {
                        // Still inside think block — discard, keep buffering partial tag
                        // Keep last 9 chars as pending to detect split "</think>" across tokens
                        String tail = curr.length() > 9 ? curr.substring(curr.length() - 9) : curr;
                        yield new CleanState(Phase.IN_THINK, tail, "");
                    }
                }

                case PASS_THROUGH -> {
                    // Strip action blocks, JSON envelopes, and code fences mid-stream.
                    // If the token starts a brace that could be an envelope, buffer it
                    // until we see a closing brace or hit a size guard.
                    int openIdx = curr.indexOf('{');
                    if (openIdx >= 0) {
                        String afterBrace = curr.substring(openIdx);
                        boolean looksLikeEnvelope =
                                afterBrace.startsWith("{\"type")
                             || afterBrace.startsWith("{ \"type")
                             || afterBrace.startsWith("{\\\"type")
                             || afterBrace.startsWith("{\n\"type");
                        if (looksLikeEnvelope && !afterBrace.contains("}")) {
                            // buffer — wait for closing brace
                            String pre = curr.substring(0, openIdx);
                            yield new CleanState(Phase.PASS_THROUGH, afterBrace, pre);
                        }
                    }
                    String out = ACTION_BLOCK_PATTERN.matcher(curr).replaceAll("");
                    out = JSON_ENVELOPE_PATTERN.matcher(out).replaceAll("");
                    out = CODE_FENCE_PATTERN.matcher(out).replaceAll("");
                    yield new CleanState(Phase.PASS_THROUGH, "", out);
                }
            };
        }
    }
}
