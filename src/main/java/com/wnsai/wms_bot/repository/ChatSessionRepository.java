package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findBySessionId(String sessionId);

    List<ChatSession> findByWarehouseId(String warehouseId);
}
