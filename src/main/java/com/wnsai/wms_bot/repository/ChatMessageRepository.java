package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByWarehouseId(String warehouseId);

    List<ChatMessage> findBySessionId(String sessionId);

    List<ChatMessage> findTop10ByWarehouseIdOrderByCreatedAtDesc(String warehouseId);

    List<ChatMessage> findTop10ByOrderByCreatedAtDesc();

    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.wasHelpful = :helpful WHERE m.id = :id")
    void updateFeedback(@Param("id") UUID id, @Param("helpful") Boolean helpful);
}
