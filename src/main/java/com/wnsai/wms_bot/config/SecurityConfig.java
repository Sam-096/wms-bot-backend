package com.wnsai.wms_bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Injected as a single comma-separated String from application.yml.
     *
     * Why not List<String>?
     * @Value cannot inject a YAML sequence (- item) into List<String> directly —
     * Spring resolves the placeholder against individual indexed keys
     * (cors.allowed-origins[0], cors.allowed-origins[1]) rather than the
     * parent key, so the injection always fails with PlaceholderResolutionException.
     * A comma-separated string in YAML is the reliable pattern for @Value lists.
     */
    @Value("${cors.allowed-origins}")
    private String rawAllowedOrigins;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsSource()))
            .authorizeExchange(ex -> ex.anyExchange().permitAll())
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        List<String> origins = Arrays.stream(rawAllowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
