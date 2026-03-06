package com.wnsai.wms_bot.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Sarvam AI Speech-To-Text adapter for Indian languages.
 * Timeout: 5 seconds. Returns transcript + detected language + confidence.
 */
@Slf4j
@Service
public class SarvamService implements VoiceTranscriber {

    private final WebClient client;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public SarvamService(
            @Value("${sarvam.api.base-url:https://api.sarvam.ai}") String baseUrl,
            @Value("${sarvam.api.key:}") String apiKey) {
        this.client = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("api-subscription-key", apiKey)
            .build();
    }

    @Override
    public Mono<TranscriptResult> transcribe(FilePart audio) {
        return DataBufferUtils.join(audio.content())
            .flatMap(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);

                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("file", bytes)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                        "form-data; name=\"file\"; filename=\"" + audio.filename() + "\"")
                    .contentType(MediaType.parseMediaType(
                        audio.headers().getContentType() != null
                            ? audio.headers().getContentType().toString()
                            : "audio/wav"));
                builder.part("model", "saarika:v2");
                builder.part("language_code", "unknown");

                return client.post()
                    .uri("/v1/speech-to-text-translate")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(TIMEOUT)
                    .map(this::parseResponse);
            })
            .onErrorResume(e -> {
                if (e instanceof java.util.concurrent.TimeoutException) {
                    log.error("Sarvam STT timeout after {}s", TIMEOUT.getSeconds());
                    return Mono.error(new SarvamTimeoutException("Voice service timeout"));
                }
                if (e instanceof WebClientResponseException wce) {
                    log.error("Sarvam STT error {}: {}", wce.getStatusCode(), wce.getResponseBodyAsString());
                }
                log.error("Sarvam STT failed: {}", e.getMessage());
                return Mono.error(e);
            });
    }

    private TranscriptResult parseResponse(Map<String, Object> body) {
        String transcript = body.get("transcript") instanceof String s ? s : "";
        String language   = body.get("language_code") instanceof String s ? s : "en";
        double confidence = 0.90; // Sarvam doesn't return confidence; assume high

        log.info("Sarvam STT: language={} transcript='{}'",
            language, transcript.length() > 50 ? transcript.substring(0, 50) + "…" : transcript);
        return new TranscriptResult(transcript, language, confidence);
    }

    // Re-declared here so the exception is in the right package context
    public static class SarvamTimeoutException extends RuntimeException {
        public SarvamTimeoutException(String msg) { super(msg); }
    }
}
