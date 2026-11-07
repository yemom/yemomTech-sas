package com.bizgrow.common.exception;

import org.springframework.http.HttpStatus;

/** 400 — syntactically valid request that violates a business precondition. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
