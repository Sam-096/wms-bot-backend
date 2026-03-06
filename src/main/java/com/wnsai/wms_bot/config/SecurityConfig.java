package com.wnsai.wms_bot.config;

import com.wnsai.wms_bot.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String rawAllowedOrigins;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http,
                                               JwtAuthFilter jwtAuthFilter) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsSource()))
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .addFilterBefore(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .authorizeExchange(ex -> ex

                // ── 1. PUBLIC — auth endpoints (NO token needed) ──────────
                .pathMatchers(HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh"
                ).permitAll()
                .pathMatchers(HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                .pathMatchers("/api/bot/**").permitAll()

                // ── 2. PROTECTED — auth management (token required) ───────
                .pathMatchers(HttpMethod.POST,  "/api/v1/auth/logout").authenticated()
                .pathMatchers(HttpMethod.GET,   "/api/v1/auth/me").authenticated()

                // ── 3. ROLE-BASED endpoints ───────────────────────────────
                .pathMatchers(HttpMethod.POST,
                    "/api/v1/chat"
                ).hasAnyRole("MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                .pathMatchers(HttpMethod.GET,
                    "/api/v1/dashboard/snapshot"
                ).hasAnyRole("MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                .pathMatchers("/api/v1/gate-pass/**")
                    .hasAnyRole("MANAGER", "OPERATOR", "GATE_STAFF")

                .pathMatchers("/api/v1/inward/**")
                    .hasAnyRole("MANAGER", "OPERATOR")

                .pathMatchers("/api/v1/outward/**")
                    .hasAnyRole("MANAGER", "OPERATOR")

                .pathMatchers("/api/v1/inventory/**")
                    .hasAnyRole("MANAGER", "OPERATOR", "VIEWER")

                .pathMatchers("/api/v1/bonds/**")
                    .hasAnyRole("MANAGER", "VIEWER")

                .pathMatchers("/api/v1/reports/**")
                    .hasAnyRole("MANAGER", "OPERATOR")

                .pathMatchers("/api/v1/chat/**")
                    .hasAnyRole("MANAGER", "OPERATOR", "GATE_STAFF", "VIEWER")

                // ── 4. Everything else requires auth ──────────────────────
                .anyExchange().authenticated()
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        List<String> origins = Arrays.stream(rawAllowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
