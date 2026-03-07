package com.wnsai.wms_bot.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting WebFilter — 60 requests per minute per IP.
 * Auth endpoints and health checks bypass the limit.
 */
@Slf4j
@Component
@Order(1)
public class RateLimitFilter implements WebFilter {

    // General: 60 req/min per IP
    private final ConcurrentHashMap<String, Bucket> buckets      = new ConcurrentHashMap<>();
    // Login: 5 req/min per IP — brute-force protection
    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private static final int CAPACITY       = 60;
    private static final int REFILL_RATE    = 60;
    private static final int LOGIN_CAPACITY = 5;   // max 5 login attempts per minute per IP

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Bypass rate limiting for health check
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String ip = resolveClientIp(exchange);

        // Strict login rate limit checked first
        if (LOGIN_PATH.equals(path)) {
            Bucket loginBucket = loginBuckets.computeIfAbsent(ip, this::newLoginBucket);
            if (!loginBucket.tryConsume(1)) {
                log.warn("Login rate limit exceeded IP={} — max {} attempts/min", ip, LOGIN_CAPACITY);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
                exchange.getResponse().getHeaders().add("X-RateLimit-Limit",
                        String.valueOf(LOGIN_CAPACITY));
                return exchange.getResponse().setComplete();
            }
        }

        // General rate limit
        Bucket bucket = buckets.computeIfAbsent(ip, this::newBucket);
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit exceeded for IP={} path={}", ip, path);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
        return exchange.getResponse().setComplete();
    }

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(CAPACITY,
                Refill.greedy(REFILL_RATE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newLoginBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(LOGIN_CAPACITY,
                Refill.greedy(LOGIN_CAPACITY, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        // Respect X-Forwarded-For from Render/Nginx proxy
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }
}
