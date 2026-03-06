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
@Table(name = "inward_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InwardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "grn_number")
    private String grnNumber;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "quantity", precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit")
    private String unit;

    @Column(name = "status")
    private String status;

    @Column(name = "inward_date")
    private LocalDate inwardDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
