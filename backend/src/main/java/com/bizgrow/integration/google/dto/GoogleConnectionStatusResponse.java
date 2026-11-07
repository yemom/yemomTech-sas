package com.YemomBizGrow.integration.google.dto;

import java.time.Instant;
import java.util.UUID;

public record GoogleConnectionStatusResponse(
        boolean connected,
        UUID websiteId,
        String googleAccountId,
        Instant connectedAt,
        Instant tokenExpiry,
        String scopes
) {
    public static GoogleConnectionStatusResponse notConnected(UUID websiteId) {
        return new GoogleConnectionStatusResponse(false, websiteId, null, null, null, null);
    }
}
