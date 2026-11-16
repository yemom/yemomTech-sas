package com.YemomBizGrow.common.observability;

import com.YemomBizGrow.auth.LoginThrottleService;
import com.YemomBizGrow.common.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The degradation of a security control must be observable through a metric,
 * not only through a log line — a log line is not an alert.
 */
@ExtendWith(MockitoExtension.class)
class SecurityControlMetricsTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("degraded controls are counted per control tag")
    void degradedControlsAreCounted() {
        SecurityControlMetrics metrics = new SecurityControlMetrics(meterRegistry);

        metrics.degraded("login-throttle");
        metrics.degraded("login-throttle");
        metrics.degraded("lead-rate-limit");

        assertThat(meterRegistry.get("bizgrow.security.controls.degraded")
                .tag("control", "login-throttle")
                .counter()
                .count()).isEqualTo(2.0);
        assertThat(meterRegistry.get("bizgrow.security.controls.degraded")
                .tag("control", "lead-rate-limit")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the login throttle increments the metric when Redis is unavailable")
    void loginThrottleIncrementsMetricWhenRedisIsUnavailable() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        AppProperties properties = new AppProperties();
        properties.getSecurity().getLogin().setMaxAttempts(3);
        properties.getSecurity().getLogin().setWindowMinutes(15);

        SecurityControlMetrics metrics = new SecurityControlMetrics(meterRegistry);
        LoginThrottleService throttle = new LoginThrottleService(redis, properties, metrics);

        throttle.precheck("user@example.com");

        assertThat(meterRegistry.get("bizgrow.security.controls.degraded")
                .tag("control", "login-throttle")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
