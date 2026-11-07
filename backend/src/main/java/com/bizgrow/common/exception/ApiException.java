package com.bizgrow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all errors that are safe to surface to API clients.
 *
 * <p>Business modules throw subclasses ({@link NotFoundException}, {@link ConflictException}, ...)
 * from services; the {@link GlobalExceptionHandler} converts them into the standard
 * error envelope. Internal causes (SQL errors, NPEs) never extend this class, so they
 * can never leak to clients accidentally.</p>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
