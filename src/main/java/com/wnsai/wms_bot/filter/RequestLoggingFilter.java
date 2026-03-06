package com.wnsai.wms_bot.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Logs method, path, userId, and duration for every API request.
 * Runs after authentication so userId is available.
 */
@Slf4j
@Component
@Order(2)
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startMs = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path   = request.getPath().value();

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    var auth = ctx.getAuthentication();
                    return auth != null && auth.getPrincipal() != null
                            ? auth.getPrincipal().toString()
                            : "anonymous";
                })
                .defaultIfEmpty("anonymous")
                .flatMap(userId -> chain.filter(exchange)
                        .doFinally(signal -> {
                            long durationMs = System.currentTimeMillis() - startMs;
                            int  status     = exchange.getResponse().getStatusCode() != null
                                    ? exchange.getResponse().getStatusCode().value()
                                    : 0;
                            log.info("{} {} userId={} status={} {}ms",
                                    method, path, userId, status, durationMs);
                        })
                );
    }
}
