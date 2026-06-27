package com.wnsai.wms_bot.chat;

import com.wnsai.wms_bot.repository.ChatSessionRepository;
import com.wnsai.wms_bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves warehouseId using a deterministic precedence chain.
 *
 * Resolution order:
 *   1. explicit request warehouseId          (highest priority)
 *   2. persisted session warehouseId
 *   3. authenticated user's default warehouse (from users.warehouse_id)
 *   4. unresolved                             (caller decides how to handle)
 *
 * All methods are synchronous/blocking. Callers in WebFlux must invoke
 * this inside Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic()).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseContextResolver {

    private final ChatSessionRepository sessionRepo;
    private final UserRepository        userRepo;

    /**
     * Resolve warehouseId for an incoming chat request or session operation.
     *
     * @param requestWarehouseId warehouseId from the request body (may be null/blank)
     * @param sessionId          chat session identifier (may be null)
     * @param userId             authenticated user's UUID string from JWT (may be null)
     * @return Resolution record with the resolved value and the source that provided it,
     *         or Resolution.unresolved() if no valid source was found
     */
    public Resolution resolve(String requestWarehouseId, String sessionId, String userId) {

        // ── Step 1: explicit request-level warehouseId ────────────────────────
        if (isValid(requestWarehouseId)) {
            log.info("warehouseId resolved from=request value={}", requestWarehouseId);
            return new Resolution(requestWarehouseId, "request");
        }

        // ── Step 2: persisted session warehouseId ─────────────────────────────
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                var sessionOpt = sessionRepo.findBySessionId(sessionId);
                if (sessionOpt.isPresent() && isValid(sessionOpt.get().getWarehouseId())) {
                    String wid = sessionOpt.get().getWarehouseId();
                    log.info("warehouseId resolved from=session value={}", wid);
                    return new Resolution(wid, "session");
                }
            } catch (Exception e) {
                log.warn("Session lookup failed during warehouse resolution sessionId={}: {}",
                        sessionId, e.getMessage());
            }
        }

        // ── Step 3: authenticated user's default warehouse ────────────────────
        if (userId != null && !userId.isBlank()) {
            try {
                UUID uuid = UUID.fromString(userId);
                var userOpt = userRepo.findById(uuid);
                if (userOpt.isPresent() && isValid(userOpt.get().getWarehouseId())) {
                    String wid = userOpt.get().getWarehouseId();
                    log.info("warehouseId resolved from=user-default value={}", wid);
                    return new Resolution(wid, "user-default");
                }
            } catch (IllegalArgumentException e) {
                log.warn("Cannot parse userId={} as UUID during warehouse resolution", userId);
            } catch (Exception e) {
                log.warn("User lookup failed during warehouse resolution userId={}: {}",
                        userId, e.getMessage());
            }
        }

        // ── Step 4: unresolved ────────────────────────────────────────────────
        log.warn("warehouseId missing for sessionId={} userId={}", sessionId, userId);
        return Resolution.unresolved();
    }

    /**
     * A warehouseId is valid if it is non-null, non-blank, and not the legacy "UNKNOWN" sentinel.
     * Case-insensitive to guard against "unknown" / "Unknown" variants.
     */
    public boolean isValid(String warehouseId) {
        return warehouseId != null
                && !warehouseId.isBlank()
                && !"UNKNOWN".equalsIgnoreCase(warehouseId.trim());
    }

    // ─── Result type ──────────────────────────────────────────────────────────

    /**
     * Immutable result of a warehouse resolution attempt.
     *
     * @param warehouseId the resolved ID, or null if unresolved
     * @param source      one of: "request", "session", "user-default", "none"
     */
    public record Resolution(String warehouseId, String source) {

        public boolean isResolved() {
            return warehouseId != null;
        }

        /** Returns the resolved warehouseId, or the supplied fallback if unresolved. */
        public String orElse(String fallback) {
            return isResolved() ? warehouseId : fallback;
        }

        public static Resolution unresolved() {
            return new Resolution(null, "none");
        }
    }
}
