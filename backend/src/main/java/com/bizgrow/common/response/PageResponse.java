package com.YemomBizGrow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Envelope for every paginated collection endpoint.
 *
 * <p>Pages are always explicit (page index + size) because several collections —
 * members, invitations, websites, audit logs — will grow without bound, and
 * unbounded queries are forbidden by the API conventions.</p>
 *
 * @param content        one page of items, already mapped to DTOs
 * @param page           zero-based page index actually returned
 * @param size           requested page size
 * @param totalElements  total matching rows across all pages
 * @param totalPages     total number of pages
 * @param first          convenience flags for frontend pagination widgets
 * @param last
 * @param empty          true when this page has no items
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size,
                                         long totalElements, int totalPages,
                                         boolean first, boolean last, boolean empty) {
        return new PageResponse<>(content, page, size, totalElements, totalPages, first, last, empty);
    }

    /** Adapts a Spring Data page of already-mapped DTOs. */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast(),
                source.isEmpty());
    }
}
