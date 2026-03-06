package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "gate_pass")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatePass {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "pass_number")
    private String passNumber;

    @Column(name = "vehicle_number")
    private String vehicleNumber;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "pass_type")
    private String passType;   // IN | OUT | TRANSFER

    @Column(name = "status")
    private String status;     // OPEN | CLOSED | CANCELLED

    @Column(name = "entry_time")
    private OffsetDateTime entryTime;

    @Column(name = "exit_time")
    private OffsetDateTime exitTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
