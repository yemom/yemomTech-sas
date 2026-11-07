package com.bizgrow.common.exception;

import org.springframework.http.HttpStatus;

/** 429 — caller exceeded a rate limit (login throttling in the first slice). */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }
}
