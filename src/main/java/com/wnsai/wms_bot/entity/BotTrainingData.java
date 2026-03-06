package com.wnsai.wms_bot.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bot_training_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotTrainingData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "intent", nullable = false)
    private String intent;

    @Column(name = "language")
    private String language;

    @Column(name = "user_input", columnDefinition = "TEXT")
    private String userInput;

    @Column(name = "expected_response", columnDefinition = "TEXT")
    private String expectedResponse;

    /** PostgreSQL TEXT[] column mapped via hypersistence-utils */
    @Type(StringArrayType.class)
    @Column(name = "keywords", columnDefinition = "text[]")
    private String[] keywords;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
