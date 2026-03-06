package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "intent_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** FK to chat_messages.id — stored as UUID, no JPA @ManyToOne to keep it simple */
    @Column(name = "chat_message_id")
    private UUID chatMessageId;

    @Column(name = "detected_intent")
    private String detectedIntent;

    @Column(name = "correct_intent")
    private String correctIntent;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "reviewed")
    @Builder.Default
    private Boolean reviewed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
