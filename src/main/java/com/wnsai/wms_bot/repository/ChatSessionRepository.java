package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findBySessionId(String sessionId);

    List<ChatSession> findByWarehouseId(String warehouseId);

    Page<ChatSession> findByWarehouseIdAndIsDeletedFalseOrderByLastActiveDesc(String warehouseId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE ChatSession s SET s.title = :title WHERE s.sessionId = :sessionId")
    void updateTitle(@Param("sessionId") String sessionId, @Param("title") String title);

    @Modifying
    @Transactional
    @Query("UPDATE ChatSession s SET s.isDeleted = true WHERE s.sessionId = :sessionId")
    void softDelete(@Param("sessionId") String sessionId);
}
