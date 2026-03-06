package com.wnsai.wms_bot.security;

import com.wnsai.wms_bot.constants.AppConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long      accessExpiryMs;
    private final long      refreshExpiryMs;

    public JwtUtil(
            @Value("${jwt.secret}")                String secret,
            @Value("${jwt.expiry-hours:24}")       long expiryHours,
            @Value("${jwt.refresh-expiry-days:7}") long refreshDays) {

        this.signingKey      = Keys.hmacShaKeyFor(resolveKeyBytes(secret));
        this.accessExpiryMs  = expiryHours * 3600 * 1000L;
        this.refreshExpiryMs = refreshDays * 24 * 3600 * 1000L;

        log.info("JwtUtil initialized — accessExpiry={}h refreshExpiry={}d",
                expiryHours, refreshDays);
    }

    /**
     * Resolves secret to key bytes.
     * Tries Base64 decode first; falls back to raw UTF-8 bytes.
     * Validates minimum 32-byte (256-bit) length for HS256.
     */
    private static byte[] resolveKeyBytes(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
            log.debug("JwtUtil: secret decoded as Base64 ({} bytes)", keyBytes.length);
        } catch (IllegalArgumentException e) {
            // Plain-text secret — use UTF-8 bytes directly
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            log.debug("JwtUtil: secret used as plain UTF-8 ({} bytes)", keyBytes.length);
        }

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT_SECRET too short (" + keyBytes.length +
                " bytes). Minimum 32 bytes (256 bits) required for HS256.");
        }

        return keyBytes;
    }

    /** Generate a short-lived access token. */
    public String generateAccessToken(UUID userId, String email,
                                       String role, String warehouseId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(AppConstants.CLAIM_EMAIL,        email)
                .claim(AppConstants.CLAIM_ROLE,         role)
                .claim(AppConstants.CLAIM_WAREHOUSE_ID, warehouseId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    /** Generate a long-lived refresh token (opaque UUID — stored in DB). */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "");
    }

    /** Validate token and return claims. Throws JwtException on failure. */
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String extractUserId(String token)      { return validateAndExtract(token).getSubject(); }
    public String extractRole(String token)        { return validateAndExtract(token).get(AppConstants.CLAIM_ROLE, String.class); }
    public String extractEmail(String token)       { return validateAndExtract(token).get(AppConstants.CLAIM_EMAIL, String.class); }
    public String extractWarehouseId(String token) { return validateAndExtract(token).get(AppConstants.CLAIM_WAREHOUSE_ID, String.class); }
    public long   getAccessExpiryMs()              { return accessExpiryMs; }
}
