package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.OutwardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutwardTransactionRepository extends JpaRepository<OutwardTransaction, UUID> {

    List<OutwardTransaction> findByWarehouseId(String warehouseId);

    List<OutwardTransaction> findByWarehouseIdAndStatus(String warehouseId, String status);

    long countByWarehouseIdAndStatus(String warehouseId, String status);

    List<OutwardTransaction> findTop10ByWarehouseIdOrderByCreatedAtDesc(String warehouseId);
}
