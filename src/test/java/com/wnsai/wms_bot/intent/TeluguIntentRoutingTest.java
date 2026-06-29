package com.wnsai.wms_bot.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Telugu warehouse queries reach the correct intent bucket.
 *
 * Root cause confirmed: resolveQuickEntity() checked only English "stock",
 * not Telugu "స్టాక్". Queries like "స్టాక్ ఎంత ఉంది" fell through to
 * AI_QUERY, bypassing the direct DB lookup path.
 */
class TeluguIntentRoutingTest {

    private IntentClassifierService classifier;

    @BeforeEach
    void setup() {
        GreetingGuardrailConfig guardrailConfig = new GreetingGuardrailConfig();
        IntentKeywordConfig     keywordConfig   = new IntentKeywordConfig();
        GreetingGuardrail       guardrail       = new GreetingGuardrail(guardrailConfig);
        classifier = new IntentClassifierService(keywordConfig, guardrail);
    }

    // ─── QUICK_QUERY: stock / inventory ──────────────────────────────────────

    @Nested
    @DisplayName("Stock queries → QUICK_QUERY (not AI_QUERY)")
    class StockQueries {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            // THE ORIGINAL BUG CASE
            "స్టాక్ ఎంత ఉంది",
            // Variations
            "స్టాక్ ఎంత",
            "ఇప్పుడు స్టాక్ ఎంత ఉంది",
            "తక్కువ స్టాక్ ఏమిటి",
            // English + Telugu mix
            "stock ఎంత ఉంది",
            "low stock చూపించు",
            // Pure English (should still work)
            "low stock",
            "stock count"
        })
        void stockQuery_routesToQuickQuery(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type())
                    .as("'%s' should be QUICK_QUERY (direct DB lookup)", message)
                    .isEqualTo(IntentType.QUICK_QUERY);
            assertThat(result.extractedEntity()).isEqualTo("LOW_STOCK");
        }

        @ParameterizedTest(name = "[{0}] entity={1}")
        @CsvSource({
            "స్టాక్ ఎంత ఉంది, LOW_STOCK",
            "స్టాక్ ఎంత, LOW_STOCK",
            "తక్కువ స్టాక్, LOW_STOCK",
            "low stock, LOW_STOCK"
        })
        void stockQuery_hasCorrectEntity(String message, String expectedEntity) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type()).isEqualTo(IntentType.QUICK_QUERY);
            assertThat(result.extractedEntity()).isEqualTo(expectedEntity);
        }
    }

    // ─── QUICK_QUERY: pending inward ─────────────────────────────────────────

    @Nested
    @DisplayName("Pending inward queries → QUICK_QUERY")
    class PendingInwardQueries {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "పెండింగ్ ఇన్వార్డ్ ఎంత",
            "ఇన్వార్డ్ పెండింగ్ చూపించు",
            "pending inward count",
            "how many pending inward"
        })
        void pendingInward_routesToQuickQuery(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type()).isEqualTo(IntentType.QUICK_QUERY);
            assertThat(result.extractedEntity()).isEqualTo("PENDING_INWARD");
        }
    }

    // ─── QUICK_QUERY: gate passes / vehicles ─────────────────────────────────

    @Nested
    @DisplayName("Gate pass / vehicle queries → QUICK_QUERY")
    class GateVehicleQueries {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "లోపల వాహనాలు ఎంత",
            "గేట్ పాస్ ఓపెన్ ఎంత",
            "active gate passes",
            "how many vehicles active gate"
        })
        void gateQuery_routesToQuickQuery(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type()).isEqualTo(IntentType.QUICK_QUERY);
            assertThat(result.extractedEntity()).isEqualTo("ACTIVE_GATE_PASSES");
        }
    }

    // ─── AI_QUERY: complex warehouse questions ────────────────────────────────

    @Nested
    @DisplayName("Complex warehouse questions → AI_QUERY (not QUICK_QUERY)")
    class ComplexQueries {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "వేర్ హౌస్ లో ఏమి జరుగుతుందో చెప్పు",           // "Tell me what's happening in warehouse"
            "ఇన్వార్డ్ రసీదు ఎలా చేయాలి",                    // "How to create inward receipt"
            "How do I create a GRN entry with quality check",
            "explain the bond management process"
        })
        void complexQuery_routesToAiQuery(String message) {
            IntentResult result = classifier.classify(message);
            assertThat(result.type())
                    .as("Complex query '%s' should go to AI_QUERY", message)
                    .isEqualTo(IntentType.AI_QUERY);
        }
    }

    // ─── Regression: greeting path still clean ───────────────────────────────

    @Nested
    @DisplayName("Greetings still route correctly after Telugu additions")
    class GreetingRegression {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "హలో", "నమస్కారం", "hello", "hi", "good morning"
        })
        void greetings_stillRouteToGreeting(String message) {
            assertThat(classifier.classify(message).type())
                    .isEqualTo(IntentType.GREETING);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "హలో స్టాక్ ఎంత ఉంది",       // greeting + stock = AI_QUERY (guardrail)
            "hello show stock report"
        })
        void greetingPlusStock_overriddenToAiQuery(String message) {
            // Guardrail vetoes GREETING when domain keywords present
            assertThat(classifier.classify(message).type())
                    .isNotEqualTo(IntentType.GREETING);
        }
    }
}
