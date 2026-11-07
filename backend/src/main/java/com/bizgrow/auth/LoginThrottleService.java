package com.bizgrow.auth;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.exception.TooManyRequestsException;
import com.bizgrow.common.util.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Login throttling backed by Redis (fixed window per normalized e-mail).
 *
 * <p>Design points:
 * <ul>
 *   <li>Keys hold {@code SHA-256(email)} — Redis operators and AOF/RDB dumps never
 *       contain plain e-mail addresses (data minimization).</li>
 *   <li>Counting is by e-mail, not IP: Ethiopian mobile users rotate IPs often, so
 *       IP-based limits would mostly punish legitimate users, while the account-keyed
 *       window directly protects credentials from password guessing.</li>
 *   <li><b>Fails open:</b> if Redis is unreachable, attempts are allowed and logged.
 *       Availability of authentication is preferred over strict throttling; the
 *       outage is loudly logged so operators notice. (Trade-off documented in
 *       AUTHENTICATION_AND_SECURITY.md.)</li>
 *   <li>The window counter is deleted on successful login so legitimate users who
 *       mistyped once start from a clean state.</li>
 * </ul></p>
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);
    private static final String KEY_PREFIX = "auth:login:failures:";

    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public LoginThrottleService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * Call before verifying credentials.
     *
     * @throws TooManyRequestsException when the window is exhausted
     */
    public void precheck(String normalizedEmail) {
        try {
            String failures = redis.opsForValue().get(key(normalizedEmail));
            if (failures != null && parseCount(failures) >= properties.getSecurity().getLogin().getMaxAttempts()) {
                throw new TooManyRequestsException("Too many failed login attempts. Please try again in "
                        + properties.getSecurity().getLogin().getWindowMinutes() + " minutes.");
            }
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Login throttle unavailable, failing open: {}", e.getMessage());
        }
    }

    /** Redis counters are strings; corrupted values count as zero rather than blocking logins. */
    private long parseCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Call after a failed verification (wrong password OR unknown e-mail — both count). */
    public void recordFailure(String normalizedEmail) {
        try {
            String key = key(normalizedEmail);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(properties.getSecurity().getLogin().getWindowMinutes()));
            }
        } catch (RuntimeException e) {
            log.warn("Login throttle increment failed: {}", e.getMessage());
        }
    }

    /** Call after successful authentication to reset the window. */
    public void recordSuccess(String normalizedEmail) {
        try {
            redis.delete(key(normalizedEmail));
        } catch (RuntimeException e) {
            log.warn("Login throttle reset failed: {}", e.getMessage());
        }
    }

    private String key(String normalizedEmail) {
        String hashed = TokenHasher.sha256Hex(normalizedEmail == null ? "" : normalizedEmail.toLowerCase(Locale.ROOT));
        return KEY_PREFIX + hashed;
    }
}
