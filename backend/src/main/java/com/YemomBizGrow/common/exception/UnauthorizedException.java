package com.YemomBizGrow.common.exception;

import org.springframework.http.HttpStatus;

/** 401 — missing, invalid, or expired credentials. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
