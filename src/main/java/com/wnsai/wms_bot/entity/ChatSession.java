package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "language")
    private String language;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private OffsetDateTime startedAt;

    @UpdateTimestamp
    @Column(name = "last_active")
    private OffsetDateTime lastActive;

    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    // ── Fields added by V2 migration ─────────────────────────────────────────

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "user_id")
    private UUID userId;
}
