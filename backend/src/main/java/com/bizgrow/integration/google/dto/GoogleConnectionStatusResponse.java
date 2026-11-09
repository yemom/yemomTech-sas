package com.YemomBizGrow.integration.google.dto;

import java.time.Instant;
import java.util.UUID;

public record GoogleConnectionStatusResponse(
        boolean connected,
        UUID websiteId,
        String googleAccountId,
        Instant connectedAt,
        Instant tokenExpiry,
        String scopes,
        String ga4PropertyId,
        String gscSiteUrl,
        Instant lastGa4SyncAt,
        Instant lastGscSyncAt
) {
    public GoogleConnectionStatusResponse(
            boolean connected,
            UUID websiteId,
            String googleAccountId,
            Instant connectedAt,
            Instant tokenExpiry,
            String scopes
    ) {
        this(connected, websiteId, googleAccountId, connectedAt, tokenExpiry, scopes, null, null, null, null);
    }

    public static GoogleConnectionStatusResponse notConnected(UUID websiteId) {
        return new GoogleConnectionStatusResponse(false, websiteId, null, null, null, null, null, null, null, null);
    }
}
