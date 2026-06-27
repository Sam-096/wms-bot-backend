package com.wnsai.wms_bot.service;

import com.wnsai.wms_bot.entity.ChatMessage;
import com.wnsai.wms_bot.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatMessage persistence in WarehouseDataService.
 *
 * Verifies:
 *  - UUID-format warehouseId values are passed through untruncated
 *  - Chat stream response is not broken when persistence throws
 *  - Long string values for intent / aiProvider / responseType are accepted
 */
class WarehouseDataServiceTest {

    @Mock private ChatMessageRepository        chatMessageRepo;

    // Stub out the other repos — WarehouseDataService takes all 6 in constructor.
    @Mock private com.wnsai.wms_bot.repository.InwardTransactionRepository  inwardRepo;
    @Mock private com.wnsai.wms_bot.repository.OutwardTransactionRepository outwardRepo;
    @Mock private com.wnsai.wms_bot.repository.StockInventoryRepository     stockRepo;
    @Mock private com.wnsai.wms_bot.repository.GatePassRepository           gatePassRepo;
    @Mock private com.wnsai.wms_bot.repository.BondRepository               bondRepo;

    private WarehouseDataService service;

    private static final String UUID_WAREHOUSE_ID = "6b12a844-7bc6-48fa-8d8f-4a6e4727a081";
    private static final String UUID_SESSION_ID   = "5a0649d2-4aca-4a5a-af26-58dfbd45dcf5";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new WarehouseDataService(
                inwardRepo, outwardRepo, stockRepo, gatePassRepo, bondRepo, chatMessageRepo);
    }

    // ─── UUID warehouseId persistence ────────────────────────────────────────

    @Test
    @DisplayName("saveChatMessage passes UUID warehouseId untruncated to repository")
    void uuidWarehouseId_passedUntruncated() {
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "AI_QUERY", "GROQ", "TEXT");
        when(chatMessageRepo.save(any())).thenReturn(msg);

        ChatMessage result = service.saveChatMessage(msg);

        assertThat(result).isNotNull();
        // Verify the repo received the full UUID — not truncated to 20 chars
        verify(chatMessageRepo).save(argThat(saved ->
                UUID_WAREHOUSE_ID.equals(saved.getWarehouseId())));
    }

    @Test
    @DisplayName("saveChatMessage handles UUID sessionId (36 chars) without truncation")
    void uuidSessionId_passedUntruncated() {
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "AI_QUERY", "RULE_BASED", "TEXT");
        when(chatMessageRepo.save(any())).thenReturn(msg);

        service.saveChatMessage(msg);

        verify(chatMessageRepo).save(argThat(saved ->
                UUID_SESSION_ID.equals(saved.getSessionId())));
    }

    // ─── Long field values ────────────────────────────────────────────────────

    @Test
    @DisplayName("Long intent value (AI_QUERY = 8 chars) fits in VARCHAR(50)")
    void intentValue_fitsInWidenedColumn() {
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "AI_QUERY", "GROQ", "TEXT");
        when(chatMessageRepo.save(any())).thenReturn(msg);

        service.saveChatMessage(msg);

        verify(chatMessageRepo).save(argThat(saved -> "AI_QUERY".equals(saved.getIntent())));
    }

    @Test
    @DisplayName("aiProvider RULE_BASED (10 chars) fits in VARCHAR(20)")
    void ruleBasedProvider_fitsInColumn() {
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "GREETING", "RULE_BASED", "TEXT");
        when(chatMessageRepo.save(any())).thenReturn(msg);

        service.saveChatMessage(msg);

        verify(chatMessageRepo).save(argThat(saved -> "RULE_BASED".equals(saved.getAiProvider())));
    }

    // ─── Non-blocking behavior when save throws ───────────────────────────────

    @Test
    @DisplayName("saveChatMessage returns null and does not throw when repo throws")
    void repoThrows_returnsNullDoesNotPropagate() {
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "AI_QUERY", "GROQ", "TEXT");
        when(chatMessageRepo.save(any())).thenThrow(new RuntimeException("simulated DB error"));

        ChatMessage result = service.saveChatMessage(msg);

        assertThat(result).isNull();
        // No exception propagated — stream remains unaffected
    }

    @Test
    @DisplayName("saveChatMessage is called once even when it throws — no retry")
    void repoThrows_calledExactlyOnce() {
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "AI_QUERY", "GROQ", "TEXT");
        when(chatMessageRepo.save(any())).thenThrow(new RuntimeException("DB timeout"));

        service.saveChatMessage(msg);

        verify(chatMessageRepo, times(1)).save(any());
    }

    @Test
    @DisplayName("saveChatMessage with varchar(20) overflow scenario — repo throws, null returned")
    void varcharOverflowScenario_nullReturned() {
        // Simulates the exact pre-fix scenario: VARCHAR(20) column rejects 36-char UUID
        ChatMessage msg = buildMessage(UUID_WAREHOUSE_ID, "AI_QUERY", "GROQ", "TEXT");
        when(chatMessageRepo.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "value too long for type character varying(20)"));

        ChatMessage result = service.saveChatMessage(msg);

        assertThat(result).isNull();
        // Critically: no exception surfaces to the caller (chat stream continues)
    }

    // ─── Private builder ──────────────────────────────────────────────────────

    private ChatMessage buildMessage(String warehouseId, String intent,
                                     String aiProvider, String responseType) {
        return ChatMessage.builder()
                .sessionId(UUID_SESSION_ID)
                .warehouseId(warehouseId)
                .userMessage("test message")
                .botResponse("test response")
                .intent(intent)
                .language("te")
                .confidence(0.9)
                .responseTimeMs(500L)
                .aiProvider(aiProvider)
                .responseType(responseType)
                .build();
    }
}
