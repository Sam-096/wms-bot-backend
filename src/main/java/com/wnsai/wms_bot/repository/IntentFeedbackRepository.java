package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.IntentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntentFeedbackRepository extends JpaRepository<IntentFeedback, UUID> {

    List<IntentFeedback> findByChatMessageId(UUID chatMessageId);

    List<IntentFeedback> findByReviewedFalseOrderByCreatedAtAsc();

    long countByReviewedFalse();
}
