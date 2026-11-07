package com.bizgrow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 — caller is authenticated but lacks the organization permission
 * required for the operation. Deliberately gives no hint about what the
 * caller could see if they were authorized (tenant isolation).
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ForbiddenException defaultDenied() {
        return new ForbiddenException("You do not have permission to perform this action.");
    }
}
