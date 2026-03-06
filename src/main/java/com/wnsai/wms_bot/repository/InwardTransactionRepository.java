package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.InwardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InwardTransactionRepository extends JpaRepository<InwardTransaction, UUID> {

    List<InwardTransaction> findByWarehouseId(String warehouseId);

    Page<InwardTransaction> findByWarehouseId(String warehouseId, Pageable pageable);

    Page<InwardTransaction> findByWarehouseIdAndStatus(String warehouseId, String status, Pageable pageable);

    List<InwardTransaction> findByWarehouseIdAndStatus(String warehouseId, String status);

    long countByWarehouseIdAndStatus(String warehouseId, String status);

    List<InwardTransaction> findTop10ByWarehouseIdOrderByCreatedAtDesc(String warehouseId);

    @Query("SELECT i FROM InwardTransaction i WHERE i.warehouseId = :wid " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (:dateFrom IS NULL OR i.inwardDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR i.inwardDate <= :dateTo)")
    Page<InwardTransaction> findFiltered(
            @Param("wid")      String warehouseId,
            @Param("status")   String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo")   LocalDate dateTo,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE InwardTransaction i SET i.status = :status, i.approvedBy = :approvedBy, " +
           "i.approvedAt = CURRENT_TIMESTAMP WHERE i.id = :id")
    void updateApproval(@Param("id") UUID id, @Param("status") String status, @Param("approvedBy") UUID approvedBy);
}
