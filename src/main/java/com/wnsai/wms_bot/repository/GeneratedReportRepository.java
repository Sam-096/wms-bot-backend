package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.GeneratedReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, UUID> {

    List<GeneratedReport> findTop20ByWarehouseIdOrderByGeneratedAtDesc(String warehouseId);

    @Modifying
    @Transactional
    @Query("UPDATE GeneratedReport r SET r.status = :status, r.filePath = :filePath WHERE r.id = :id")
    void markReady(@Param("id") UUID id, @Param("status") String status, @Param("filePath") String filePath);

    @Modifying
    @Transactional
    @Query("UPDATE GeneratedReport r SET r.status = 'FAILED', r.errorMessage = :msg WHERE r.id = :id")
    void markFailed(@Param("id") UUID id, @Param("msg") String errorMessage);
}
