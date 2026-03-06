package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "bot_response", columnDefinition = "TEXT")
    private String botResponse;

    @Column(name = "intent")
    private String intent;

    @Column(name = "language")
    private String language;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /** Nullable — set later via feedback endpoint */
    @Column(name = "was_helpful")
    private Boolean wasHelpful;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    // ── Fields added by V2 migration ─────────────────────────────────────────

    /** TEXT | TABLE | CHART | REPORT | ACTION | ALERT | LIST */
    @Column(name = "response_type", length = 20)
    @Builder.Default
    private String responseType = "TEXT";

    /** OLLAMA | GROQ | RULE_BASED */
    @Column(name = "ai_provider", length = 20)
    private String aiProvider;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;
}
