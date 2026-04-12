package com.wnsai.wms_bot.config;

import com.wnsai.wms_bot.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String rawAllowedOrigins;

    /**
     * Standalone CorsWebFilter at highest precedence — runs BEFORE the security
     * filter chain so preflight OPTIONS requests get proper CORS headers even if
     * they are rejected by the security chain.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        List<String> origins = Arrays.stream(rawAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http,
                                               JwtAuthFilter jwtAuthFilter) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(ServerHttpSecurity.CorsSpec::disable)  // handled by corsWebFilter() bean above
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .addFilterBefore(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .authorizeExchange(ex -> ex

                // ── 0. Preflight — must be FIRST ──────────────────────────
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── 1. PUBLIC — auth endpoints (NO token needed) ──────────
                .pathMatchers(HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh"
                ).permitAll()
                .pathMatchers(HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/info",
                    "/api/v1/warehouses"   // needed on login/register page (no JWT yet)
                ).permitAll()
                .pathMatchers("/api/bot/**").permitAll()

                // ── 2. PROTECTED — auth management (token required) ───────
                .pathMatchers(HttpMethod.PUT,   "/api/v1/auth/change-password").authenticated()
                .pathMatchers(HttpMethod.POST,  "/api/v1/auth/logout").authenticated()
                .pathMatchers(HttpMethod.GET,   "/api/v1/auth/me").authenticated()

                // ── 3. ADMIN-ONLY endpoints ───────────────────────────────
                .pathMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                // ── 4. ROLE-BASED endpoints ───────────────────────────────
                .pathMatchers(HttpMethod.POST, "/api/v1/chat", "/api/v1/chat/stream")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                .pathMatchers(HttpMethod.GET, "/api/v1/dashboard/snapshot")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                .pathMatchers("/api/v1/gate-pass/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR", "GATE_STAFF")

                .pathMatchers("/api/v1/inward/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")

                .pathMatchers("/api/v1/outward/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")

                .pathMatchers("/api/v1/inventory/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR", "VIEWER")

                .pathMatchers("/api/v1/bonds/**")
                    .hasAnyRole("ADMIN", "MANAGER", "VIEWER")

                .pathMatchers("/api/v1/reports/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")

                .pathMatchers("/api/v1/chat/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                // ── 5. Warehouse lookup (all authenticated roles) ─────────
                .pathMatchers("/api/v1/warehouses/**")
                    .hasAnyRole("ADMIN", "MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                // ── 6. Everything else requires auth ──────────────────────
                .anyExchange().authenticated()
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
