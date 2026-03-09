package com.wnsai.wms_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive Redis cache service for warehouse context data.
 *
 * Pattern: getOrFetch — reads from Redis first; on miss, calls dbCall,
 * stores the result with TTL, then returns it.
 *
 * Key schema (all prefixed "wms:"):
 *   wms:stock:{warehouseId}    5 min
 *   wms:inward:{warehouseId}   2 min
 *   wms:outward:{warehouseId}  2 min
 *   wms:gate:{warehouseId}     1 min
 *   wms:bond:{warehouseId}     10 min
 *
 * All methods are safe: Redis errors fall through to the DB call silently.
 * Redis is a performance layer, NOT a source of truth.
 */
@Slf4j
@Service
@ConditionalOnBean(ReactiveRedisTemplate.class)
public class CacheService {

    public static final Duration TTL_STOCK   = Duration.ofMinutes(5);
    public static final Duration TTL_INWARD  = Duration.ofMinutes(2);
    public static final Duration TTL_OUTWARD = Duration.ofMinutes(2);
    public static final Duration TTL_GATE    = Duration.ofMinutes(1);
    public static final Duration TTL_BOND    = Duration.ofMinutes(10);

    private final ReactiveRedisTemplate<String, String> redis;

    public CacheService(
            @Qualifier("reactiveStringRedisTemplate")
            ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    // ─── Key builders ─────────────────────────────────────────────────────────

    public static String stockKey(String warehouseId)   { return "wms:stock:"   + warehouseId; }
    public static String inwardKey(String warehouseId)  { return "wms:inward:"  + warehouseId; }
    public static String outwardKey(String warehouseId) { return "wms:outward:" + warehouseId; }
    public static String gateKey(String warehouseId)    { return "wms:gate:"    + warehouseId; }
    public static String bondKey(String warehouseId)    { return "wms:bond:"    + warehouseId; }

    // ─── Core: get from Redis, else fetch from DB and cache ──────────────────

    /**
     * Returns cached value if present and non-empty; otherwise invokes dbCall,
     * caches the result with the given TTL, and returns it.
     *
     * Redis errors are swallowed — the DB call always runs as fallback.
     */
    public Mono<String> getOrFetch(String key, Duration ttl, Mono<String> dbCall) {
        return redis.opsForValue()
            .get(key)
            .filter(v -> v != null && !v.isBlank())
            .doOnNext(v -> log.debug("Cache HIT key={} len={}", key, v.length()))
            .switchIfEmpty(
                dbCall.flatMap(value -> {
                    if (value == null || value.isBlank()) return Mono.just("");
                    return redis.opsForValue()
                        .set(key, value, ttl)
                        .doOnSuccess(ok -> log.debug("Cache SET key={} ttl={}", key, ttl))
                        .thenReturn(value);
                })
            )
            .onErrorResume(e -> {
                log.warn("Redis error on key={}: {} — falling through to DB", key, e.getMessage());
                return dbCall;
            });
    }

    // ─── Invalidation ────────────────────────────────────────────────────────

    /**
     * Deletes a single cache key — call after write operations (inward save, outward save, etc.).
     * Errors are silently ignored.
     */
    public Mono<Void> invalidate(String key) {
        return redis.opsForValue()
            .delete(key)
            .doOnSuccess(deleted -> {
                if (Boolean.TRUE.equals(deleted))
                    log.debug("Cache INVALIDATED key={}", key);
            })
            .onErrorResume(e -> {
                log.warn("Cache invalidation failed for key={}: {}", key, e.getMessage());
                return Mono.just(false);
            })
            .then();
    }

    /**
     * Deletes all cache keys for a warehouse (stock + inward + outward + gate + bond).
     * Call after any bulk write that touches multiple tables.
     */
    public Mono<Void> invalidateWarehouse(String warehouseId) {
        return Mono.when(
            invalidate(stockKey(warehouseId)),
            invalidate(inwardKey(warehouseId)),
            invalidate(outwardKey(warehouseId)),
            invalidate(gateKey(warehouseId)),
            invalidate(bondKey(warehouseId))
        );
    }
}
