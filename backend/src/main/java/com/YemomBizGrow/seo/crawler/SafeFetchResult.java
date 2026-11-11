package com.YemomBizGrow.seo.crawler;

import java.net.URI;
import java.util.List;

public record SafeFetchResult(
        URI originalUri,
        URI finalUri,
        int statusCode,
        String contentType,
        String body,
        long loadTimeMs,
        List<URI> redirectChain,
        boolean success
) {}
