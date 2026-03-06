package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outward_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutwardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "dispatch_number")
    private String dispatchNumber;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "quantity", precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit")
    private String unit;

    @Column(name = "status")
    private String status;

    @Column(name = "outward_date")
    private LocalDate outwardDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ── Fields added by V2 migration ─────────────────────────────────────────

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @Column(name = "quantity_bags")
    private Integer quantityBags;

    @Column(name = "unit_weight", precision = 10, scale = 3)
    private BigDecimal unitWeight;

    @Column(name = "total_weight", precision = 12, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;
}
