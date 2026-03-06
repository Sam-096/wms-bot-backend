package com.wnsai.wms_bot.intent;

public interface IntentClassifier {

    /**
     * Classify a user message into an intent WITHOUT calling any LLM.
     * Must complete in < 10ms (pure in-memory keyword matching).
     */
    IntentResult classify(String message);
}
