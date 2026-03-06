package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.auth.*;
import com.wnsai.wms_bot.security.JwtUtil;
import com.wnsai.wms_bot.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil     jwtUtil;

    /** POST /api/v1/auth/register */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /api/v1/auth/register username={}", request.username());
        return authService.register(request);
    }

    /** POST /api/v1/auth/login */
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.debug("POST /api/v1/auth/login email={}", request.email());
        return authService.login(request)
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> {
                    log.warn("Login failed for {}: {}", request.email(), ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                });
    }

    /** POST /api/v1/auth/refresh */
    @PostMapping("/refresh")
    public Mono<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.debug("POST /api/v1/auth/refresh");
        return authService.refresh(request);
    }

    /** POST /api/v1/auth/logout */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@AuthenticationPrincipal String userId) {
        log.debug("POST /api/v1/auth/logout userId={}", userId);
        return authService.logout(userId);
    }

    /** GET /api/v1/auth/me */
    @GetMapping("/me")
    public Mono<MeResponse> me(@AuthenticationPrincipal String userId) {
        log.debug("GET /api/v1/auth/me userId={}", userId);
        return authService.me(userId);
    }
}
