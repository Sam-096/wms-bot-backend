package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByIsActiveTrue();

    Optional<Warehouse> findByWarehouseId(String warehouseId);
}
