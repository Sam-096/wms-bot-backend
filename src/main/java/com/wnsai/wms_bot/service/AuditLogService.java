package com.wnsai.wms_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Structured security audit logging.
 *
 * All events are written to the standard logger at INFO/WARN level.
 * Format: AUDIT <ACTION> key=value ...
 * Use a log aggregator (e.g. Render log drains → Datadog/Logtail) to
 * parse and alert on these lines in production.
 */
@Slf4j
@Service
public class AuditLogService {

    /** Successful login — user authenticated. */
    public void logLoginSuccess(String userId, String email, String role, String ip) {
        log.info("AUDIT LOGIN_SUCCESS userId={} email={} role={} ip={} timestamp={}",
                userId, email, role, ip, Instant.now());
    }

    /** Failed login attempt — bad credentials or inactive account. */
    public void logLoginFailure(String email, String ip) {
        log.warn("AUDIT LOGIN_FAILURE email={} ip={} timestamp={}",
                email, ip, Instant.now());
    }

    /** User explicitly logged out. */
    public void logLogout(String userId, String ip) {
        log.info("AUDIT LOGOUT userId={} ip={} timestamp={}",
                userId, ip, Instant.now());
    }

    /** Password changed successfully. */
    public void logPasswordChange(String userId, String ip) {
        log.info("AUDIT PASSWORD_CHANGE userId={} ip={} timestamp={}",
                userId, ip, Instant.now());
    }

    /** New user registered. */
    public void logRegistration(String userId, String email, String role, String ip) {
        log.info("AUDIT REGISTER userId={} email={} role={} ip={} timestamp={}",
                userId, email, role, ip, Instant.now());
    }

    /** Request blocked — rate limit hit. */
    public void logRateLimitHit(String ip, String path) {
        log.warn("AUDIT RATE_LIMIT_HIT ip={} path={} timestamp={}",
                ip, path, Instant.now());
    }

    /** Unauthorized access — valid token but insufficient role. */
    public void logAccessDenied(String userId, String role, String path, String ip) {
        log.warn("AUDIT ACCESS_DENIED userId={} role={} path={} ip={} timestamp={}",
                userId, role, path, ip, Instant.now());
    }
}
