package com.YemomBizGrow.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards AI recommendation generation.
 *
 * <p>Generation is an expensive operation: it reads the organization's data,
 * asks the provider for insights and writes a batch of recommendations. Two
 * properties matter:</p>
 *
 * <ul>
 *   <li><b>Only one generation per organization at a time.</b> Without the
 *       guard, two operators clicking "Generate" (or the same operator
 *       double-clicking) run the provider twice and store two near-identical
 *       batches.</li>
 *   <li><b>Generation is rate limited.</b> The provider has a cost and a
 *       rate limit of its own; an organization must not be able to hammer it.</li>
 * </ul>
 *
 * <p><b>Redis outage policy.</b> Redis is the authoritative, cross-instance
 * limiter. When it is unavailable the guard degrades to a bounded in-process
 * equivalent and logs a {@code SECURITY CONTROL DEGRADED} warning so the
 * degradation is alertable rather than silent. The in-process fallback is per
 * instance — the documented trade-off for keeping the feature available.</p>
 */
@Component
public class AiGenerationGuard {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationGuard.class);
    private static final String LOCK_PREFIX = "ai:generate:lock:";
    private static final String RATE_PREFIX = "ai:generate:rate:";

    /** How long a generation lock is held before it expires on its own. */
    private static final int LOCK_TTL_SECONDS = 120;
    /** Maximum generations per organization per hour. */
    private static final int MAX_GENERATIONS_PER_HOUR = 20;
    /** Upper bound for the degraded in-process maps. */
    private static final int MAX_LOCAL_KEYS = 5_000;

    private static final RedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then "
                    + "    redis.call('EXPIRE', KEYS[1], ARGV[1]) "
                    + "end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redis;

    /** Degraded-mode lock expiry (epoch millis) per organization. */
    private final ConcurrentHashMap<String, Long> localLocks = new ConcurrentHashMap<>();
    /** Degraded-mode generation counters per organization. */
    private final ConcurrentHashMap<String, long[]> localCounters = new ConcurrentHashMap<>();

    public AiGenerationGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Try to reserve the single generation slot for an organization.
     *
     * @return {@code true} when the caller may generate, {@code false} when a
     *         generation is already running for that organization
     */
    public boolean tryAcquire(UUID organizationId) {
        String key = LOCK_PREFIX + organizationId;
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            // Redis answered: a generation really is in flight.
            return false;
        } catch (RuntimeException e) {
            log.warn("SECURITY CONTROL DEGRADED: Redis-backed AI generation lock unavailable ({}). "
                    + "Using the in-process lock for this instance.", e.getMessage());
            return acquireLocalLock(key);
        }
    }

    /** Release the generation slot. Safe to call even if the lock expired. */
    public void release(UUID organizationId) {
        String key = LOCK_PREFIX + organizationId;
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("SECURITY CONTROL DEGRADED: could not release the AI generation lock ({}).", e.getMessage());
        }
        localLocks.remove(key);
    }

    /**
     * @return {@code true} when the organization has exhausted its hourly
     *         generation budget
     */
    public boolean isRateLimited(UUID organizationId) {
        String key = RATE_PREFIX + organizationId;
        try {
            Long count = redis.execute(INCR_EXPIRE_SCRIPT, Collections.singletonList(key),
                    String.valueOf(3600));
            return count != null && count > MAX_GENERATIONS_PER_HOUR;
        } catch (RuntimeException e) {
            log.warn("SECURITY CONTROL DEGRADED: Redis-backed AI generation rate limit unavailable ({}). "
                    + "Using the in-process limiter for this instance.", e.getMessage());
            return isLocalRateLimited(key);
        }
    }

    private boolean acquireLocalLock(String key) {
        long now = System.currentTimeMillis();
        localLocks.entrySet().removeIf(entry -> entry.getValue() < now);
        if (localLocks.size() > MAX_LOCAL_KEYS) {
            localLocks.clear();
        }
        Long existing = localLocks.putIfAbsent(key, now + LOCK_TTL_SECONDS * 1000L);
        return existing == null;
    }

    private boolean isLocalRateLimited(String key) {
        long now = System.currentTimeMillis();
        if (localCounters.size() > MAX_LOCAL_KEYS) {
            localCounters.clear();
        }
        long[] counter = localCounters.compute(key, (k, current) -> {
            if (current == null || now - current[1] > 3_600_000L) {
                return new long[]{1L, now};
            }
            current[0]++;
            return current;
        });
        return counter[0] > MAX_GENERATIONS_PER_HOUR;
    }

    /** Exposed for tests and operational visibility. */
    public int localTrackedKeyCount() {
        return localLocks.size() + localCounters.size();
    }

    /** The hourly budget, exposed for the configuration message. */
    public int maxGenerationsPerHour() {
        return MAX_GENERATIONS_PER_HOUR;
    }
}
