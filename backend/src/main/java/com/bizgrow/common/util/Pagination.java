package com.bizgrow.common.util;

import com.bizgrow.common.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;

/**
 * One place to validate page parameters so every paginated endpoint rejects
 * nonsense identically: no negative pages, page sizes bounded (1..100) to make
 * unbounded scans impossible even by accident.
 */
public final class Pagination {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    public static PageRequest of(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("Page size must be between 1 and " + MAX_SIZE + ".");
        }
        return PageRequest.of(page, size);
    }
}
