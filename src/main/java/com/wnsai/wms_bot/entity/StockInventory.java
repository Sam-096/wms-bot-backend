package com.wnsai.wms_bot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "current_stock", precision = 15, scale = 3)
    private BigDecimal currentStock;

    @Column(name = "min_threshold", precision = 15, scale = 3)
    private BigDecimal minThreshold;

    @Column(name = "unit")
    private String unit;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private OffsetDateTime lastUpdated;
}
