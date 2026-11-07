package com.bizgrow.common.security;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.config.RequestTraceFilter;
import com.bizgrow.common.exception.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes security-layer errors (401/403 raised inside the filter chain, before
 * the MVC handler-advice exists) in exactly the same envelope as every other
 * error response — one error format regardless of which layer rejected a request.
 */
@Component
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String traceId = MDC.get(RequestTraceFilter.MDC_KEY);
        ErrorResponse body = new ErrorResponse(
                Instant.now().toString(),
                status.value(),
                code,
                message,
                null,
                null,
                traceId != null ? traceId : "unknown");
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(toJson(body));
    }

    private String toJson(ErrorResponse body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            // Extremely unlikely (record of simple types); never leak the real cause.
            return "{\"status\":500,\"error\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred.\"}";
        }
    }
}
