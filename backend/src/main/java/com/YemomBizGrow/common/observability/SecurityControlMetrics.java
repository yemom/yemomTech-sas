package com.YemomBizGrow.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Publishes a metric every time a security control degrades to its in-process
 * fallback.
 *
 * <p>The application prefers to stay available when Redis is unavailable, and
 * it says so loudly: a log marker (<code>SECURITY CONTROL DEGRADED</code>) and
 * a counter. The log marker is what a human reads; the counter is what the
 * monitoring system alerts on. A log line is not an alert, and without the
 * metric the degradation is indistinguishable from a healthy instance.</p>
 *
 * <p>Exposed at <code>/actuator/metrics/bizgrow.security.controls.degraded</code>
 * (bound in the {@code dev}/{@code test} profiles). Alert rule examples live
 * in docs/DEPLOYMENT.md.</p>
 */
@Component
public class SecurityControlMetrics {

    private final MeterRegistry meterRegistry;

    public SecurityControlMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record one degradation of a security control.
     *
     * @param control a stable identifier, e.g. {@code login-throttle}
     */
    public void degraded(String control) {
        Counter.builder("bizgrow.security.controls.degraded")
                .description("How often a security control fell back to its in-process limiter")
                .tag("control", control)
                .register(meterRegistry)
                .increment();
    }
}
