package com.bizgrow.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The single error envelope returned by every non-2xx response.
 *
 * <p>{@code fieldErrors} and the top-level fields other than status/error/message/path
 * are omitted when empty so successful-looking payloads stay clean, and internal
 * details (stack traces, SQL, exception class names) are never included.</p>
 *
 * @param timestamp  UTC ISO-8601 instant when the error occurred
 * @param status     HTTP status code (mirrored for non-HTTP clients)
 * @param error      Stable machine-readable code, e.g. VALIDATION_ERROR
 * @param message    Human-safe summary
 * @param path       Request path that produced the error
 * @param fieldErrors Per-field validation messages, present only for validation failures
 * @param traceId    Correlation id (also returned in the X-Trace-Id header) for support
 */
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors,
        String traceId) {
}
