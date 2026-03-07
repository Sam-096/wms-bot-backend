package com.wnsai.wms_bot.service.auth.impl;

import com.wnsai.wms_bot.dto.auth.*;
import com.wnsai.wms_bot.entity.User;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.exception.InvalidCredentialsException;
import com.wnsai.wms_bot.repository.UserRepository;
import com.wnsai.wms_bot.security.JwtUtil;
import com.wnsai.wms_bot.service.AuditLogService;
import com.wnsai.wms_bot.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository  userRepository;
    private final JwtUtil         jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public Mono<AuthResponse> register(RegisterRequest req) {
        return Mono.fromCallable(() -> {
            log.debug("Registering user: email={}", req.email());

            if (userRepository.existsByEmail(req.email())) {
                throw new IllegalArgumentException("Email already registered");
            }
            if (userRepository.existsByUsername(req.username())) {
                throw new IllegalArgumentException("Username already taken");
            }

            String refreshToken = jwtUtil.generateRefreshToken();
            User user = User.builder()
                    .username(req.username())
                    .email(req.email())
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .role(req.role())
                    .warehouseId(req.warehouseId())
                    .isActive(true)
                    .refreshToken(refreshToken)
                    .build();

            user = userRepository.save(user);
            auditLogService.logRegistration(user.getId().toString(), user.getEmail(), user.getRole(), "service");
            log.info("User registered: userId={} role={}", user.getId(), user.getRole());

            String accessToken = jwtUtil.generateAccessToken(
                    user.getId(), user.getEmail(), user.getRole(), user.getWarehouseId());

            return buildAuthResponse(user, accessToken, refreshToken);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AuthResponse> login(LoginRequest req) {
        return Mono.fromCallable(() -> {
            log.debug("Login attempt: email={}", req.email());

            User user = userRepository.findByEmail(req.email())
                    .orElseThrow(() -> {
                        auditLogService.logLoginFailure(req.email(), "service");
                        return new InvalidCredentialsException();
                    });

            if (!Boolean.TRUE.equals(user.getIsActive())) {
                auditLogService.logLoginFailure(req.email(), "service");
                throw new InvalidCredentialsException();
            }
            if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                auditLogService.logLoginFailure(req.email(), "service");
                throw new InvalidCredentialsException();
            }

            String refreshToken = jwtUtil.generateRefreshToken();
            userRepository.updateRefreshToken(user.getId(), refreshToken);
            userRepository.updateLastLogin(user.getId(), OffsetDateTime.now());

            String accessToken = jwtUtil.generateAccessToken(
                    user.getId(), user.getEmail(), user.getRole(), user.getWarehouseId());

            auditLogService.logLoginSuccess(user.getId().toString(), user.getEmail(), user.getRole(), "service");
            log.info("Login successful: userId={}", user.getId());
            return buildAuthResponse(user, accessToken, refreshToken);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AuthResponse> refresh(RefreshRequest req) {
        return Mono.fromCallable(() -> {
            User user = userRepository.findByRefreshToken(req.refreshToken())
                    .orElseThrow(() -> new InvalidCredentialsException());

            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new InvalidCredentialsException();
            }

            String newRefresh = jwtUtil.generateRefreshToken();
            userRepository.updateRefreshToken(user.getId(), newRefresh);

            String accessToken = jwtUtil.generateAccessToken(
                    user.getId(), user.getEmail(), user.getRole(), user.getWarehouseId());

            return buildAuthResponse(user, accessToken, newRefresh);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> changePassword(String userId, ChangePasswordRequest req) {
        return Mono.fromRunnable(() -> {
            UUID id   = UUID.fromString(userId);
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("User", userId));

            if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw new InvalidCredentialsException();
            }

            String newHash = passwordEncoder.encode(req.newPassword());
            userRepository.updatePassword(id, newHash);
            auditLogService.logPasswordChange(userId, "service");
            log.info("Password changed: userId={}", userId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> logout(String userId) {
        return Mono.fromRunnable(() -> {
            try {
                UUID id = UUID.fromString(userId);
                userRepository.updateRefreshToken(id, null);
                log.info("User logged out: userId={}", userId);
            } catch (Exception e) {
                log.warn("Logout error for userId={}: {}", userId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<MeResponse> me(String userId) {
        return Mono.fromCallable(() -> {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new EntityNotFoundException("User", userId));
            return new MeResponse(
                    user.getId().toString(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole(),
                    user.getWarehouseId(),
                    user.getIsActive(),
                    user.getCreatedAt(),
                    user.getLastLogin()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return new AuthResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getWarehouseId(),
                null,  // warehouseName — fetched separately if needed
                accessToken,
                refreshToken,
                jwtUtil.getAccessExpiryMs() / 1000
        );
    }
}
