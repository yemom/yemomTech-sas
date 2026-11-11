package com.YemomBizGrow.integration.google.dto;

public record GoogleConnectUrlResponse(
        String authUrl,
        String state
) {}
