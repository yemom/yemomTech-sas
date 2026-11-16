package com.YemomBizGrow.auth;

import com.YemomBizGrow.common.config.AppProperties;
import com.YemomBizGrow.common.exception.TooManyRequestsException;
import com.YemomBizGrow.common.observability.SecurityControlMetrics;
import com.YemomBizGrow.common.util.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login throttling backed by Redis with atomic Lua script execution.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Account-based limit: {@code auth:login:failures:<sha256(email)>}</li>
 *   <li>IP-based limit: {@code auth:login:ip:<sha256(ip)>} to protect against distributed brute force</li>
 *   <li>Atomic rate limiting via Lua script (INCR + EXPIRE atomically, no TTL leak)</li>
 * </ul>
 * </p>
 *
 * <p><b>Redis outage policy (explicit, deliberate trade-off).</b> Redis is the
 * shared source of truth: it is the only way a limit survives across backend
 * instances. When Redis is unavailable the service does <em>not</em> silently
 * disable protection, and it does not hard-lock every user out either. It
 * degrades to a <b>bounded, conservative, in-process</b> limit:</p>
 * <ul>
 *   <li>Lower thresholds than the Redis path (at most {@link #FALLBACK_MAX_EMAIL_ATTEMPTS}
 *       per account and {@link #FALLBACK_MAX_IP_ATTEMPTS} per IP, never more
 *       permissive than the configured production limits), so a degraded
 *       instance still stops a brute-force run.</li>
 *   <li>The fallback counter map is bounded ({@link #FALLBACK_MAX_TRACKED_KEYS})
 *       so an outage cannot be used to exhaust the heap.</li>
 *   <li>Every degradation is logged at WARN with an explicit
 *       "SECURITY CONTROL DEGRADED" marker so operations can alert on it.</li>
 *   <li>As soon as Redis answers again the fallback is bypassed entirely and
 *       the shared counters resume governing the limit — the fallback never
 *       outlives the outage.</li>
 * </ul>
 *
 * <p>Known limitation: the in-process fallback is per instance, so during an
 * outage a horizontally scaled deployment enforces the limit per instance
 * rather than globally. This is deliberate — availability of the login path
 * matters more than perfect brute-force accounting during a Redis outage, and
 * the degradation is logged and alertable.</p>
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);
    private static final String EMAIL_KEY_PREFIX = "auth:login:failures:";
    private static final String IP_KEY_PREFIX = "auth:login:ip:";
    private static final int MAX_IP_ATTEMPTS = 60; // 60 attempts per window per IP

    /** Conservative per-account ceiling while Redis is unavailable. */
    private static final int FALLBACK_MAX_EMAIL_ATTEMPTS = 5;
    /** Conservative per-IP ceiling while Redis is unavailable. */
    private static final int FALLBACK_MAX_IP_ATTEMPTS = 20;
    /** Upper bound on tracked fallback keys, so an outage cannot exhaust the heap. */
    private static final int FALLBACK_MAX_TRACKED_KEYS = 10_000;

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
    private final SecurityControlMetrics metrics;

    /**
     * Degraded-mode counters: key -&gt; {count, windowStartMillis}.
     * Only used while Redis is unreachable.
     */
    private final ConcurrentHashMap<String, long[]> fallbackCounters = new ConcurrentHashMap<>();

    public LoginThrottleService(StringRedisTemplate redis, AppProperties properties) {
        this(redis, properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LoginThrottleService(StringRedisTemplate redis, AppProperties properties,
                                SecurityControlMetrics metrics) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Record a degradation of the login throttle and publish the metric. */
    private void degraded(String reason) {
        log.warn("SECURITY CONTROL DEGRADED: Redis-backed login throttle unavailable ({}). "
                + "Falling back to a conservative in-process limit; brute-force protection is reduced "
                + "for this instance until Redis recovers.", reason);
        if (metrics != null) {
            metrics.degraded("login-throttle");
        }
    }

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
            // Redis answered: the shared counters are authoritative and the
            // degraded in-process counters are ignored entirely.
            return;
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (RuntimeException e) {
            degraded(e.getMessage());
        }
        enforceFallbackLimit(normalizedEmail, clientIp);
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
            log.warn("SECURITY CONTROL DEGRADED: Redis-backed login throttle increment failed ({}). "
                    + "Counting the failure in the conservative in-process limiter instead.", e.getMessage());
            if (metrics != null) {
                metrics.degraded("login-throttle");
            }
            incrementFallback(normalizedEmail, clientIp);
        }
    }

    /** Call after successful authentication to reset the account window. */
    public void recordSuccess(String normalizedEmail) {
        try {
            if (normalizedEmail != null && !normalizedEmail.isBlank()) {
                redis.delete(emailKey(normalizedEmail));
            }
        } catch (RuntimeException e) {
            log.warn("SECURITY CONTROL DEGRADED: Redis-backed login throttle reset failed ({}).", e.getMessage());
            if (metrics != null) {
                metrics.degraded("login-throttle");
            }
        }
        // A successful login clears the degraded counter too, so a user who
        // mistyped their password a few times during an outage is not left
        // locked out once Redis is healthy again.
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            fallbackCounters.remove(fallbackEmailKey(normalizedEmail));
        }
    }

    /* --------------------------- degraded-mode limiter --------------------------- */

    /**
     * Enforce the conservative in-process limit used while Redis is unavailable.
     */
    private void enforceFallbackLimit(String normalizedEmail, String clientIp) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1L, properties.getSecurity().getLogin().getWindowMinutes()) * 60_000L;

        if (clientIp != null && !clientIp.isBlank()) {
            long ipCount = readFallback(fallbackIpKey(clientIp), now, windowMillis);
            if (ipCount >= fallbackIpLimit()) {
                throw new TooManyRequestsException("Too many requests from this network. Please try again in "
                        + properties.getSecurity().getLogin().getWindowMinutes() + " minutes.");
            }
        }
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            long emailCount = readFallback(fallbackEmailKey(normalizedEmail), now, windowMillis);
            if (emailCount >= fallbackEmailLimit()) {
                throw new TooManyRequestsException("Too many failed login attempts. Please try again in "
                        + properties.getSecurity().getLogin().getWindowMinutes() + " minutes.");
            }
        }
    }

    private void incrementFallback(String normalizedEmail, String clientIp) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1L, properties.getSecurity().getLogin().getWindowMinutes()) * 60_000L;
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            bumpFallback(fallbackEmailKey(normalizedEmail), now, windowMillis);
        }
        if (clientIp != null && !clientIp.isBlank()) {
            bumpFallback(fallbackIpKey(clientIp), now, windowMillis);
        }
    }

    private long readFallback(String key, long now, long windowMillis) {
        long[] stats = fallbackCounters.get(key);
        if (stats == null) {
            return 0L;
        }
        if (now - stats[1] > windowMillis) {
            fallbackCounters.remove(key, stats);
            return 0L;
        }
        return stats[0];
    }

    private void bumpFallback(String key, long now, long windowMillis) {
        fallbackCounters.compute(key, (k, current) -> {
            if (current == null || now - current[1] > windowMillis) {
                return new long[]{1L, now};
            }
            current[0]++;
            return current;
        });
        evictFallbackIfNeeded(now, windowMillis);
    }

    /** Keep the degraded-mode map bounded so an outage cannot exhaust the heap. */
    private void evictFallbackIfNeeded(long now, long windowMillis) {
        if (fallbackCounters.size() <= FALLBACK_MAX_TRACKED_KEYS) {
            return;
        }
        fallbackCounters.entrySet().removeIf(entry -> now - entry.getValue()[1] > windowMillis);
        if (fallbackCounters.size() <= FALLBACK_MAX_TRACKED_KEYS) {
            return;
        }
        // Still too large: drop the oldest entries until the bound holds.
        int toRemove = fallbackCounters.size() - FALLBACK_MAX_TRACKED_KEYS;
        for (Map.Entry<String, long[]> entry : fallbackCounters.entrySet()) {
            if (toRemove-- <= 0) {
                break;
            }
            fallbackCounters.remove(entry.getKey(), entry.getValue());
        }
    }

    /** The degraded ceiling is never more permissive than the configured one. */
    private int fallbackEmailLimit() {
        return Math.min(FALLBACK_MAX_EMAIL_ATTEMPTS,
                Math.max(1, properties.getSecurity().getLogin().getMaxAttempts()));
    }

    private int fallbackIpLimit() {
        return Math.min(FALLBACK_MAX_IP_ATTEMPTS, MAX_IP_ATTEMPTS);
    }

    private String emailKey(String normalizedEmail) {
        String hashed = TokenHasher.sha256Hex(normalizedEmail == null ? "" : normalizedEmail.toLowerCase(Locale.ROOT));
        return EMAIL_KEY_PREFIX + hashed;
    }

    private String ipKey(String clientIp) {
        String hashed = TokenHasher.sha256Hex(clientIp == null ? "" : clientIp.trim());
        return IP_KEY_PREFIX + hashed;
    }

    /** Degraded-mode keys are namespaced so they can never collide with Redis keys. */
    private String fallbackEmailKey(String normalizedEmail) {
        return "fallback:" + emailKey(normalizedEmail);
    }

    private String fallbackIpKey(String clientIp) {
        return "fallback:" + ipKey(clientIp);
    }

    /**
     * Number of accounts/IPs currently tracked by the degraded in-process
     * limiter. Exposed for operational visibility (and asserted in tests) so a
     * Redis outage can be detected from metrics rather than only from logs.
     */
    int degradedTrackedKeyCount() {
        return fallbackCounters.size();
    }
}
