package com.wnsai.wms_bot.chat;

import com.wnsai.wms_bot.entity.ChatSession;
import com.wnsai.wms_bot.entity.User;
import com.wnsai.wms_bot.repository.ChatSessionRepository;
import com.wnsai.wms_bot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WarehouseContextResolver.
 * Mocks both repos; verifies precedence chain and validity guards.
 */
class WarehouseContextResolverTest {

    @Mock private ChatSessionRepository sessionRepo;
    @Mock private UserRepository        userRepo;

    private WarehouseContextResolver resolver;

    private static final String SESSION_ID    = "sess-abc-123";
    private static final String USER_ID       = UUID.randomUUID().toString();
    private static final String WAREHOUSE_WH1 = "WH-001";
    private static final String WAREHOUSE_WH2 = "WH-002";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        resolver = new WarehouseContextResolver(sessionRepo, userRepo);
    }

    // ─── isValid() guard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("isValid() — warehouse ID validity checks")
    class IsValidTests {

        @ParameterizedTest(name = "[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "UNKNOWN", "unknown", "Unknown"})
        void invalid_values(String value) {
            assertThat(resolver.isValid(value)).isFalse();
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"WH-001", "GODOWN-5", "warehouse-123", "1"})
        void valid_values(String value) {
            assertThat(resolver.isValid(value)).isTrue();
        }
    }

    // ─── Precedence: Step 1 — request warehouseId ────────────────────────────

    @Nested
    @DisplayName("Step 1 — request warehouseId takes priority")
    class RequestPrecedence {

        @Test
        @DisplayName("Valid request warehouseId → resolved from 'request', no DB calls")
        void validRequestWarehouseId_resolvedImmediately() {
            var result = resolver.resolve(WAREHOUSE_WH1, SESSION_ID, USER_ID);

            assertThat(result.isResolved()).isTrue();
            assertThat(result.warehouseId()).isEqualTo(WAREHOUSE_WH1);
            assertThat(result.source()).isEqualTo("request");
            verifyNoInteractions(sessionRepo, userRepo);
        }

        @Test
        @DisplayName("Request warehouseId overrides even when session has a different one")
        void requestOverridesSession() {
            mockSession(WAREHOUSE_WH2);

            var result = resolver.resolve(WAREHOUSE_WH1, SESSION_ID, USER_ID);

            assertThat(result.warehouseId()).isEqualTo(WAREHOUSE_WH1);
            assertThat(result.source()).isEqualTo("request");
            verifyNoInteractions(sessionRepo);   // session not consulted
        }
    }

    // ─── Precedence: Step 2 — session warehouseId ────────────────────────────

    @Nested
    @DisplayName("Step 2 — session warehouseId used when request omits it")
    class SessionPrecedence {

        @Test
        @DisplayName("Request blank, session has valid warehouseId → resolved from 'session'")
        void sessionWarehouseId_usedWhenRequestBlank() {
            mockSession(WAREHOUSE_WH1);

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.isResolved()).isTrue();
            assertThat(result.warehouseId()).isEqualTo(WAREHOUSE_WH1);
            assertThat(result.source()).isEqualTo("session");
            verifyNoInteractions(userRepo);
        }

        @Test
        @DisplayName("Session has UNKNOWN warehouseId → falls through to user-default")
        void sessionUnknown_fallsThrough() {
            mockSession("UNKNOWN");
            mockUser(WAREHOUSE_WH1);

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.source()).isEqualTo("user-default");
            assertThat(result.warehouseId()).isEqualTo(WAREHOUSE_WH1);
        }

        @Test
        @DisplayName("Session not found → falls through to user-default")
        void sessionNotFound_fallsThrough() {
            when(sessionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            mockUser(WAREHOUSE_WH1);

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.source()).isEqualTo("user-default");
        }

        @Test
        @DisplayName("Null sessionId → session step skipped, goes to user-default")
        void nullSessionId_skipsSessionLookup() {
            mockUser(WAREHOUSE_WH1);

            var result = resolver.resolve(null, null, USER_ID);

            assertThat(result.source()).isEqualTo("user-default");
            verifyNoInteractions(sessionRepo);
        }
    }

    // ─── Precedence: Step 3 — user default ───────────────────────────────────

    @Nested
    @DisplayName("Step 3 — user default warehouse used as last resort")
    class UserDefaultPrecedence {

        @Test
        @DisplayName("Request blank, no session, user has warehouseId → resolved from 'user-default'")
        void userDefaultWarehouse_resolvedFromUser() {
            when(sessionRepo.findBySessionId(anyString())).thenReturn(Optional.empty());
            mockUser(WAREHOUSE_WH1);

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.isResolved()).isTrue();
            assertThat(result.warehouseId()).isEqualTo(WAREHOUSE_WH1);
            assertThat(result.source()).isEqualTo("user-default");
        }

        @Test
        @DisplayName("User has no warehouseId → falls to unresolved")
        void userNoWarehouse_unresolved() {
            when(sessionRepo.findBySessionId(anyString())).thenReturn(Optional.empty());
            mockUser(null);

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.isResolved()).isFalse();
            assertThat(result.source()).isEqualTo("none");
        }

        @Test
        @DisplayName("User not found → unresolved")
        void userNotFound_unresolved() {
            when(sessionRepo.findBySessionId(anyString())).thenReturn(Optional.empty());
            when(userRepo.findById(any(UUID.class))).thenReturn(Optional.empty());

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.isResolved()).isFalse();
        }

        @Test
        @DisplayName("Null userId → user step skipped, returns unresolved")
        void nullUserId_skipsUserLookup() {
            when(sessionRepo.findBySessionId(anyString())).thenReturn(Optional.empty());

            var result = resolver.resolve(null, SESSION_ID, null);

            assertThat(result.isResolved()).isFalse();
            assertThat(result.source()).isEqualTo("none");
            verifyNoInteractions(userRepo);
        }
    }

    // ─── Step 4: unresolved ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Step 4 — unresolved when all sources exhausted")
    class Unresolved {

        @Test
        @DisplayName("All sources empty → Resolution.unresolved()")
        void allSourcesEmpty_unresolved() {
            when(sessionRepo.findBySessionId(anyString())).thenReturn(Optional.empty());
            when(userRepo.findById(any(UUID.class))).thenReturn(Optional.empty());

            var result = resolver.resolve(null, SESSION_ID, USER_ID);

            assertThat(result.isResolved()).isFalse();
            assertThat(result.warehouseId()).isNull();
            assertThat(result.source()).isEqualTo("none");
        }

        @Test
        @DisplayName("Completely empty request (null, null, null) → unresolved, no crash")
        void completelyEmpty_unresolved() {
            var result = resolver.resolve(null, null, null);

            assertThat(result.isResolved()).isFalse();
            verifyNoInteractions(sessionRepo, userRepo);
        }
    }

    // ─── Resolution helper methods ────────────────────────────────────────────

    @Nested
    @DisplayName("Resolution.orElse() fallback helper")
    class ResolutionHelpers {

        @Test
        @DisplayName("orElse() returns warehouseId when resolved")
        void orElse_resolvedReturnsValue() {
            mockSession(WAREHOUSE_WH1);
            var result = resolver.resolve(null, SESSION_ID, USER_ID);
            assertThat(result.orElse("UNKNOWN")).isEqualTo(WAREHOUSE_WH1);
        }

        @Test
        @DisplayName("orElse() returns fallback when unresolved")
        void orElse_unresolvedReturnsFallback() {
            var result = WarehouseContextResolver.Resolution.unresolved();
            assertThat(result.orElse("FALLBACK")).isEqualTo("FALLBACK");
        }
    }

    // ─── Session repo exception safety ───────────────────────────────────────

    @Test
    @DisplayName("Session repo throws exception → falls through to user-default gracefully")
    void sessionRepoException_gracefulFallthrough() {
        when(sessionRepo.findBySessionId(SESSION_ID))
                .thenThrow(new RuntimeException("DB timeout"));
        mockUser(WAREHOUSE_WH1);

        var result = resolver.resolve(null, SESSION_ID, USER_ID);

        assertThat(result.isResolved()).isTrue();
        assertThat(result.source()).isEqualTo("user-default");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void mockSession(String warehouseId) {
        ChatSession session = ChatSession.builder()
                .sessionId(SESSION_ID)
                .warehouseId(warehouseId)
                .build();
        when(sessionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void mockUser(String warehouseId) {
        User user = User.builder()
                .id(UUID.fromString(USER_ID))
                .warehouseId(warehouseId)
                .build();
        when(userRepo.findById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
    }
}
