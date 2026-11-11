package com.YemomBizGrow.integration.google.dto;

import java.util.List;

public record GoogleAccessibleResourcesResponse(
        List<Ga4PropertyOption> ga4Properties,
        List<GscSiteOption> searchConsoleSites,
        String selectedGa4PropertyId,
        String selectedGscSiteUrl
) {
    public record Ga4PropertyOption(String propertyId, String displayName) {}
    public record GscSiteOption(String siteUrl, String permissionLevel) {}
}
