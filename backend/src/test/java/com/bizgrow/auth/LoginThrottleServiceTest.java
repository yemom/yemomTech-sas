package com.bizgrow.auth;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginThrottleServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AppProperties properties;
    private LoginThrottleService throttle;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getSecurity().getLogin().setMaxAttempts(3);
        properties.getSecurity().getLogin().setWindowMinutes(15);
        throttle = new LoginThrottleService(redis, properties);
    }

    @Test
    @DisplayName("precheck blocks once the window count reaches the maximum")
    void blocksWhenWindowExhausted() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("3");

        assertThatCode(() -> throttle.precheck("user@example.com"))
                .isInstanceOfSatisfying(com.bizgrow.common.exception.TooManyRequestsException.class,
                        e -> assertThat(e.getMessage()).contains("15 minutes"));
    }

    @Test
    @DisplayName("precheck allows attempts below the maximum")
    void allowsBelowMaximum() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("2");

        assertThatCode(() -> throttle.precheck("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis outage fails open — authentication stays available")
    void failsOpenOnRedisOutage() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> throttle.precheck("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a corrupted counter is treated as zero instead of locking the account out")
    void corruptedCountIsZero() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("not-a-number");

        assertThatCode(() -> throttle.precheck("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("first failure creates the key with an expiry; later failures only increment")
    void failureSetsWindowOnce() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        throttle.recordFailure("user@example.com");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        verify(redis).expire(eq(keyCaptor.getValue()), eq(Duration.ofMinutes(15)));

        // The key must never contain the plain e-mail (data minimization in Redis dumps).
        assertThat(keyCaptor.getValue())
                .startsWith("auth:login:failures:")
                .doesNotContain("user@example.com")
                .endsWith(TokenHasher.sha256Hex("user@example.com"));
    }

    @Test
    @DisplayName("a success clears the window so a fresh mistake starts over")
    void successClearsWindow() {
        throttle.recordSuccess("user@example.com");

        verify(redis).delete("auth:login:failures:" + TokenHasher.sha256Hex("user@example.com"));
    }

    @Test
    @DisplayName("Redis failures during counting are swallowed, never propagated to the login flow")
    void recordFailureSwallowsOutage() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("down"));

        assertThatCode(() -> throttle.recordFailure("user@example.com")).doesNotThrowAnyException();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }
}
