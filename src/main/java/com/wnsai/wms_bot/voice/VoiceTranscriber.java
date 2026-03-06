package com.wnsai.wms_bot.voice;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface VoiceTranscriber {

    /**
     * Transcribe audio to text using Sarvam STT API.
     * Timeout: 5s. Returns transcript, detected language, and confidence.
     */
    Mono<TranscriptResult> transcribe(FilePart audio);

    record TranscriptResult(String text, String language, double confidence) {}
}
