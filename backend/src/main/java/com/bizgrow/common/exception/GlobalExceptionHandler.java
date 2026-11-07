package com.bizgrow.common.exception;

import com.bizgrow.common.config.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts every handled exception into the platform-wide error envelope.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>Only messages we author reach the client — never raw exception messages
 *       from frameworks or the database.</li>
 *   <li>Unexpected exceptions are logged in full server-side (with traceId) but
 *       return a generic 500 body.</li>
 *   <li>Every response carries the traceId so a user-reported error can be
 *       located in logs without asking for screenshots of internals.</li>
 * </ul></p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ------------------------- errors we authored ------------------------- */

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("[{}] {}: {}", traceId(), ex.getErrorCode(), ex.getMessage(), ex);
        }
        return respond(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request, null);
    }

    /* ------------------------- validation errors -------------------------- */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            // First message per field is enough and avoids leaking validator internals.
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "The submitted data is invalid.", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                fieldErrors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "The submitted data is invalid.", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is malformed or missing.", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = Map.of(ex.getName(), "Invalid value.");
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "One of the request parameters is invalid.", request, fieldErrors);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "The HTTP method is not supported for this resource.", request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.", request, null);
    }

    /* ---------------- security layer errors (belt & braces) --------------- */

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.", request, null);
    }

    /* ---------------------- anything unexpected --------------------------- */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Full stack trace stays server-side only.
        log.error("[{}] Unhandled exception on {} {}", traceId(), request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", request, null);
    }

    /* ------------------------------- helpers ------------------------------ */

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message,
                                                  HttpServletRequest request, Map<String, String> fieldErrors) {
        ErrorResponse body = new ErrorResponse(
                Instant.now().toString(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors,
                traceId());
        return ResponseEntity.status(status).body(body);
    }

    private String traceId() {
        String id = MDC.get(RequestTraceFilter.MDC_KEY);
        return id != null ? id : "unknown";
    }
}
