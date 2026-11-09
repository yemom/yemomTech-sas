package com.YemomBizGrow.integration.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.google")
@Getter
@Setter
public class GoogleIntegrationProperties {
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "http://localhost:3000/api/auth/callback/google";
    private String ga4PropertyId = "";
}
