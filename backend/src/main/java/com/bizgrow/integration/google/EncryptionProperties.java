package com.YemomBizGrow.integration.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM key configuration.
 *
 * <p>Set {@code ENCRYPTION_KEY} (or {@code app.security.encryption.key}) to a Base64-encoded 32-byte key.
 * Generate: {@code openssl rand -base64 32}</p>
 */
@Component
@ConfigurationProperties(prefix = "app.security.encryption")
@Getter
@Setter
public class EncryptionProperties {
    /** Base64-encoded 32-byte (256-bit) AES key. No hardcoded production secret. */
    private String key = "";
}
