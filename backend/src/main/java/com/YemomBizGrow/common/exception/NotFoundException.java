package com.YemomBizGrow.common.exception;

import org.springframework.http.HttpStatus;

/** 404 — resource does not exist, or exists but is not visible to this caller. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static NotFoundException resource(String resource) {
        return new NotFoundException(resource + " not found.");
    }
}
