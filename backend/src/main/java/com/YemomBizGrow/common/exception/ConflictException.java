package com.YemomBizGrow.common.exception;

import org.springframework.http.HttpStatus;

/** 409 — request conflicts with existing state (duplicate email, duplicate domain, ...). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
