package com.wnsai.wms_bot.service.auth;

import com.wnsai.wms_bot.dto.auth.*;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<AuthResponse> register(RegisterRequest request);
    Mono<AuthResponse> login(LoginRequest request);
    Mono<AuthResponse> refresh(RefreshRequest request);
    Mono<Void>         logout(String userId);
    Mono<MeResponse>   me(String userId);
}
