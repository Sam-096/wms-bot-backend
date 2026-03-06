package com.wnsai.wms_bot.voice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * POST /api/v1/voice/transcribe
 * Accepts audio file → returns transcript, detected language, confidence.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@CrossOrigin(origins = {
    "http://localhost:4200",
    "https://wmsai.netlify.app"
})
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceTranscriber transcriber;

    @PostMapping(
        value    = "/transcribe",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<Map<String, Object>> transcribe(
            @RequestPart("audio") FilePart audio) {

        log.info("POST /api/v1/voice/transcribe file={}", audio.filename());

        return transcriber.transcribe(audio)
            .map(result -> {
                Map<String, Object> resp = new java.util.LinkedHashMap<>();
                resp.put("text",       result.text());
                resp.put("language",   result.language());
                resp.put("confidence", result.confidence());
                return resp;
            })
            .onErrorMap(
                SarvamService.SarvamTimeoutException.class,
                e -> new SarvamTimeoutException("Voice service timeout")
            );
    }

    // Local exception alias for the controller layer
    static class SarvamTimeoutException extends RuntimeException {
        SarvamTimeoutException(String msg) { super(msg); }
    }
}
