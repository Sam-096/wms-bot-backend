package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "warehouse_id", length = 50)
    private String warehouseId;

    @Column(name = "user_id")
    private UUID userId;

    /** STOCK_SUMMARY | INWARD_SUMMARY | OUTWARD_SUMMARY | GATE_PASS_LOG | BOND_STATUS | DAILY_ACTIVITY */
    @Column(name = "report_type", length = 50)
    private String reportType;

    /** CSV | PDF */
    @Column(name = "format", length = 10)
    private String format;

    /** GENERATING | READY | FAILED */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "GENERATING";

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
