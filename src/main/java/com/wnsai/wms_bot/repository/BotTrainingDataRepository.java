package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.BotTrainingData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BotTrainingDataRepository extends JpaRepository<BotTrainingData, UUID> {

    List<BotTrainingData> findByIntentAndIsActiveTrue(String intent);

    List<BotTrainingData> findByLanguageAndIsActiveTrue(String language);

    List<BotTrainingData> findByIntentAndLanguageAndIsActiveTrue(String intent, String language);
}
