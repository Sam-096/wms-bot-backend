package com.wnsai.wms_bot.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

/**
 * Rate limiting WebFilter — 60 requests per minute per IP.
 * Login endpoint gets a tighter limit of 5 req/min for brute-force protection.
 *
 * Buckets are stored in a Caffeine cache with 2-minute expiry after last access —
 * prevents unbounded memory growth from accumulating unique IPs over time.
 *
 * X-Forwarded-For is only trusted when the real remote address is a private/loopback
 * address (i.e. request came through a reverse proxy on the same host/network).
 * Direct connections from public IPs use the TCP remote address, which cannot be spoofed.
 */
@Slf4j
@Component
@Order(1)
public class RateLimitFilter implements WebFilter {

    private static final int      CAPACITY       = 60;
    private static final int      REFILL_RATE    = 60;
    private static final int      LOGIN_CAPACITY = 5;
    private static final String   LOGIN_PATH     = "/api/v1/auth/login";

    // Caffeine caches — buckets expire 2 min after last access, bounding memory usage
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(50_000)
            .build();

    private final Cache<String, Bucket> loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(50_000)
            .build();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String ip = resolveClientIp(exchange);

        if (LOGIN_PATH.equals(path)) {
            Bucket loginBucket = loginBuckets.get(ip, k -> newLoginBucket());
            if (!loginBucket.tryConsume(1)) {
                log.warn("Login rate limit exceeded IP={} — max {} attempts/min", ip, LOGIN_CAPACITY);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
                exchange.getResponse().getHeaders().add("X-RateLimit-Limit",
                        String.valueOf(LOGIN_CAPACITY));
                return exchange.getResponse().setComplete();
            }
        }

        Bucket bucket = buckets.get(ip, k -> newBucket());
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit exceeded for IP={} path={}", ip, path);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
        return exchange.getResponse().setComplete();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY,
                Refill.greedy(REFILL_RATE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newLoginBucket() {
        Bandwidth limit = Bandwidth.classic(LOGIN_CAPACITY,
                Refill.greedy(LOGIN_CAPACITY, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves client IP safely.
     * X-Forwarded-For is only trusted when the TCP remote address is a loopback
     * or private IP — meaning the connection came through a trusted proxy (Render, Nginx).
     * A public IP in remoteAddress means a direct connection; use it as-is.
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        var addr = exchange.getRequest().getRemoteAddress();
        String remoteIp = addr != null ? addr.getAddress().getHostAddress() : "unknown";

        if (isTrustedProxy(remoteIp)) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }

        return remoteIp;
    }

    private boolean isTrustedProxy(String ip) {
        return ip.equals("127.0.0.1")
            || ip.equals("::1")                // IPv6 loopback (compact form)
            || ip.equals("0:0:0:0:0:0:0:1")   // IPv6 loopback (expanded form)
            || ip.startsWith("10.")
            || ip.startsWith("172.16.") || ip.startsWith("172.17.")
            || ip.startsWith("172.18.") || ip.startsWith("172.19.")
            || ip.startsWith("172.2")   || ip.startsWith("172.3")
            || ip.startsWith("192.168.");
    }
}
