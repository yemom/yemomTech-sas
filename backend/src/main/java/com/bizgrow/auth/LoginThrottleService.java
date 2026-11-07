package com.YemomBizGrow.auth;

import com.YemomBizGrow.common.config.AppProperties;
import com.YemomBizGrow.common.exception.TooManyRequestsException;
import com.YemomBizGrow.common.util.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;

/**
 * Login throttling backed by Redis with atomic Lua script execution.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Account-based limit: {@code auth:login:failures:<sha256(email)>}</li>
 *   <li>IP-based limit: {@code auth:login:ip:<sha256(ip)>} to protect against distributed brute force</li>
 *   <li>Atomic rate limiting via Lua script (INCR + EXPIRE atomically, no TTL leak)</li>
 *   <li>Safe fail-open policy when Redis is unavailable (availability over lockout)</li>
 * </ul>
 * </p>
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);
    private static final String EMAIL_KEY_PREFIX = "auth:login:failures:";
    private static final String IP_KEY_PREFIX = "auth:login:ip:";
    private static final int MAX_IP_ATTEMPTS = 60; // 60 attempts per window per IP

    private static final RedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public LoginThrottleService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * Call before verifying credentials. Checks both email and IP limits.
     *
     * @throws TooManyRequestsException when the window is exhausted
     */
    public void precheck(String normalizedEmail) {
        precheck(normalizedEmail, null);
    }

    public void precheck(String normalizedEmail, String clientIp) {
        try {
            if (clientIp != null && !clientIp.isBlank()) {
                String ipFailures = redis.opsForValue().get(ipKey(clientIp));
                if (ipFailures != null && parseCount(ipFailures) >= MAX_IP_ATTEMPTS) {
                    throw new TooManyRequestsException("Too many requests from this network. Please try again in "
                            + properties.getSecurity().getLogin().getWindowMinutes() + " minutes.");
                }
            }

            if (normalizedEmail != null && !normalizedEmail.isBlank()) {
                String failures = redis.opsForValue().get(emailKey(normalizedEmail));
                if (failures != null && parseCount(failures) >= properties.getSecurity().getLogin().getMaxAttempts()) {
                    throw new TooManyRequestsException("Too many failed login attempts. Please try again in "
                            + properties.getSecurity().getLogin().getWindowMinutes() + " minutes.");
                }
            }
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Login throttle unavailable, failing open: {}", e.getMessage());
        }
    }

    private long parseCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Call after a failed verification: atomically increments failure counts for email and IP. */
    public void recordFailure(String normalizedEmail) {
        recordFailure(normalizedEmail, null);
    }

    public void recordFailure(String normalizedEmail, String clientIp) {
        long ttlSeconds = properties.getSecurity().getLogin().getWindowMinutes() * 60L;
        if (ttlSeconds <= 0) {
            ttlSeconds = 900L;
        }
        try {
            if (normalizedEmail != null && !normalizedEmail.isBlank()) {
                String key = emailKey(normalizedEmail);
                redis.execute(INCR_EXPIRE_SCRIPT, Collections.singletonList(key), String.valueOf(ttlSeconds));
            }
            if (clientIp != null && !clientIp.isBlank()) {
                String key = ipKey(clientIp);
                redis.execute(INCR_EXPIRE_SCRIPT, Collections.singletonList(key), String.valueOf(ttlSeconds));
            }
        } catch (RuntimeException e) {
            log.warn("Login throttle increment failed: {}", e.getMessage());
        }
    }

    /** Call after successful authentication to reset the account window. */
    public void recordSuccess(String normalizedEmail) {
        try {
            if (normalizedEmail != null && !normalizedEmail.isBlank()) {
                redis.delete(emailKey(normalizedEmail));
            }
        } catch (RuntimeException e) {
            log.warn("Login throttle reset failed: {}", e.getMessage());
        }
    }

    private String emailKey(String normalizedEmail) {
        String hashed = TokenHasher.sha256Hex(normalizedEmail == null ? "" : normalizedEmail.toLowerCase(Locale.ROOT));
        return EMAIL_KEY_PREFIX + hashed;
    }

    private String ipKey(String clientIp) {
        String hashed = TokenHasher.sha256Hex(clientIp == null ? "" : clientIp.trim());
        return IP_KEY_PREFIX + hashed;
    }
}
