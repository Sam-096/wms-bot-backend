package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.InwardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InwardTransactionRepository extends JpaRepository<InwardTransaction, UUID> {

    List<InwardTransaction> findByWarehouseId(String warehouseId);

    List<InwardTransaction> findByWarehouseIdAndStatus(String warehouseId, String status);

    long countByWarehouseIdAndStatus(String warehouseId, String status);

    List<InwardTransaction> findTop10ByWarehouseIdOrderByCreatedAtDesc(String warehouseId);
}
