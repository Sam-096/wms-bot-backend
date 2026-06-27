package com.wnsai.wms_bot.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the complete intent classification + guardrail stack.
 *
 * No Spring context — classifier and guardrail are instantiated directly
 * with default config so tests stay fast and focused.
 */
class GreetingGuardrailTest {

    private IntentClassifierService classifier;
    private GreetingGuardrail       guardrail;

    @BeforeEach
    void setup() {
        GreetingGuardrailConfig guardrailConfig = new GreetingGuardrailConfig();
        IntentKeywordConfig     keywordConfig   = new IntentKeywordConfig();
        guardrail  = new GreetingGuardrail(guardrailConfig);
        classifier = new IntentClassifierService(keywordConfig, guardrail);
    }

    // ─── Valid greetings ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid greetings → GREETING")
    class ValidGreetings {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "hi", "Hi", "HI",
            "hello", "Hello", "HELLO",
            "hey",
            "హలో",
            "నమస్కారం",
            "good morning",
            "good evening",
            "namaste",
            "namaskar"
        })
        void pureGreeting_classifiedAsGreeting(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type())
                    .as("'%s' should be GREETING", message)
                    .isEqualTo(IntentType.GREETING);
        }

        @Test
        @DisplayName("Greeting with exclamation mark → GREETING")
        void greetingWithPunctuation() {
            assertThat(classifier.classify("hello!").type()).isEqualTo(IntentType.GREETING);
            assertThat(classifier.classify("hi!").type()).isEqualTo(IntentType.GREETING);
        }
    }

    // ─── Greeting + business request → AI_QUERY ───────────────────────────────

    @Nested
    @DisplayName("Greeting + business request → AI_QUERY (guardrail override)")
    class GreetingPlusBusiness {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "hello warehouse summary",
            "hi how many vehicles are inside",
            "hello show me stock report",
            "hey gate pass status",
            "hello how many bags",
            "hi truck count today",
            "హలో స్టాక్ ఎంత ఉంది",
            "నమస్కారం వాహనాలు ఎన్ని",
            "hello show inward report"
        })
        void greetingPlusBusiness_overriddenToAiQuery(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type())
                    .as("'%s' should be overridden to AI_QUERY", message)
                    .isEqualTo(IntentType.AI_QUERY);
        }

        @Test
        @DisplayName("GREETING overridden to AI_QUERY has confidence 0.80")
        void guardrailOverride_hasCorrectConfidence() {
            IntentResult result = classifier.classify("hello how many vehicles inside warehouse");
            assertThat(result.type()).isEqualTo(IntentType.AI_QUERY);
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.70);
        }
    }

    // ─── Pure business queries → AI_QUERY ────────────────────────────────────

    @Nested
    @DisplayName("Pure warehouse/business queries → AI_QUERY")
    class PureBusinessQueries {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "How many vehicles are inside the warehouse",
            "how many vehicles are inside the warehouse",
            "HOW MANY VEHICLES ARE INSIDE THE WAREHOUSE",
            "How many bags came inside",
            "stock check",
            "Gate pass status",
            "Warehouse summary",
            "vehicle count today",
            "truck status",
            "how many trucks are loaded",
            "inventory details for today",
            "commodity count",
            "dispatch summary for today",
            "inward outward report",
            "వేర్ హౌస్ లోపల ఎన్ని బ్యాగ్స్ వచ్చాయి",
            "స్టాక్ ఎంత ఉంది",
            "వాహనాలు ఎన్ని లోపల ఉన్నాయి"
        })
        void pureBusinessQuery_classifiedAsAiQuery(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type())
                    .as("'%s' should be AI_QUERY", message)
                    .isEqualTo(IntentType.AI_QUERY);
        }

        @Test
        @DisplayName("'vehicles' containing 'hi' as substring → AI_QUERY (the original bug)")
        void vehiclesContainsHi_notGreeting() {
            // This is the confirmed production bug: "hi" is a substring of "vehicles"
            IntentResult result = classifier.classify("How many vehicles are inside the warehouse");
            assertThat(result.type())
                    .as("'vehicles' contains 'hi' but must not trigger GREETING")
                    .isEqualTo(IntentType.AI_QUERY);
        }

        @Test
        @DisplayName("'this' and 'while' contain 'hi' — not misclassified as GREETING")
        void hiInsideOtherWords_notGreeting() {
            assertThat(classifier.classify("what is this report about").type())
                    .isEqualTo(IntentType.AI_QUERY);
            assertThat(classifier.classify("show the dispatch while trucks are waiting").type())
                    .isNotEqualTo(IntentType.GREETING);
        }
    }

    // ─── Transliterated queries ───────────────────────────────────────────────

    @Nested
    @DisplayName("Transliterated Telugu-English → AI_QUERY")
    class TransliteratedQueries {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "warehouse lo enni bags vachai",
            "stock enni undi",
            "inward report chupinchu",
            "bags count cheppu",
            "trucks inside count"
        })
        void transliteratedBusinessQuery_notGreeting(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type())
                    .as("Transliterated '%s' must not be GREETING", message)
                    .isNotEqualTo(IntentType.GREETING);
        }
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null → UNKNOWN")
        void nullMessage_unknown() {
            assertThat(classifier.classify(null).type()).isEqualTo(IntentType.UNKNOWN);
        }

        @Test
        @DisplayName("blank / whitespace → UNKNOWN")
        void blankMessage_unknown() {
            assertThat(classifier.classify("").type()).isEqualTo(IntentType.UNKNOWN);
            assertThat(classifier.classify("   ").type()).isEqualTo(IntentType.UNKNOWN);
        }

        @Test
        @DisplayName("punctuation only → AI_QUERY (not GREETING, not crash)")
        void punctuationOnly_nocrash() {
            IntentResult result = classifier.classify("???");
            assertThat(result.type()).isNotEqualTo(IntentType.GREETING);
        }

        @Test
        @DisplayName("Single character not in greeting list → AI_QUERY")
        void singleAmbiguousChar_notGreeting() {
            assertThat(classifier.classify("h").type()).isNotEqualTo(IntentType.GREETING);
        }

        @Test
        @DisplayName("Long message even if it starts with 'hi' → guardrail triggers length veto")
        void longMessageStartingWithHi_guardrailVetoes() {
            // 41+ chars, starts with "hi" — too long for a pure greeting
            String msg = "hi there I am asking about the warehouse report summary for today";
            IntentResult result = classifier.classify(msg);
            assertThat(result.type()).isEqualTo(IntentType.AI_QUERY);
        }

        @Test
        @DisplayName("'hey' as standalone word → GREETING; not inside another word")
        void heyStandalone_greeting() {
            assertThat(classifier.classify("hey").type()).isEqualTo(IntentType.GREETING);
            assertThat(classifier.classify("hey!").type()).isEqualTo(IntentType.GREETING);
        }
    }

    // ─── Guardrail unit tests (isolated from classifier) ─────────────────────

    @Nested
    @DisplayName("GreetingGuardrail.evaluate() — direct unit tests")
    class GuardrailDirectTests {

        @Test
        @DisplayName("Non-GREETING result passes through unchanged")
        void nonGreeting_passesThrough() {
            IntentResult aiQuery = new IntentResult(IntentType.AI_QUERY, 0.70, null, null);
            IntentResult result  = guardrail.evaluate("any message", aiQuery);
            assertThat(result).isSameAs(aiQuery);
        }

        @Test
        @DisplayName("GREETING with domain keyword → overridden to AI_QUERY")
        void greetingWithDomainKeyword_overridden() {
            IntentResult greeting = new IntentResult(IntentType.GREETING, 0.95, null, null);
            IntentResult result   = guardrail.evaluate("hello warehouse report", greeting);
            assertThat(result.type()).isEqualTo(IntentType.AI_QUERY);
            assertThat(result.confidence()).isEqualTo(0.80);
        }

        @Test
        @DisplayName("GREETING exceeding maxLength → overridden to AI_QUERY")
        void greetingTooLong_overridden() {
            String longGreeting = "hello my friend how are you doing today at the warehouse";
            IntentResult greeting = new IntentResult(IntentType.GREETING, 0.95, null, null);
            IntentResult result   = guardrail.evaluate(longGreeting, greeting);
            assertThat(result.type()).isEqualTo(IntentType.AI_QUERY);
        }

        @Test
        @DisplayName("Clean short greeting → GREETING preserved")
        void shortCleanGreeting_preserved() {
            IntentResult greeting = new IntentResult(IntentType.GREETING, 0.95, null, null);
            IntentResult result   = guardrail.evaluate("hello", greeting);
            assertThat(result.type()).isEqualTo(IntentType.GREETING);
            assertThat(result.confidence()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("Telugu clean greeting → GREETING preserved")
        void teluguGreeting_preserved() {
            IntentResult greeting = new IntentResult(IntentType.GREETING, 0.95, null, null);
            IntentResult result   = guardrail.evaluate("నమస్కారం", greeting);
            assertThat(result.type()).isEqualTo(IntentType.GREETING);
        }

        @Test
        @DisplayName("Telugu greeting + domain keyword → overridden")
        void teluguGreetingPlusDomainKeyword_overridden() {
            IntentResult greeting = new IntentResult(IntentType.GREETING, 0.95, null, null);
            IntentResult result   = guardrail.evaluate("హలో స్టాక్ ఎంత ఉంది", greeting);
            assertThat(result.type()).isEqualTo(IntentType.AI_QUERY);
        }
    }
}
