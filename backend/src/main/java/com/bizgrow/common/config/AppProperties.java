package com.YemomBizGrow.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe access to every {@code app.*} property (see application.yml).
 *
 * <p>Centralizing configuration here means services never scatter
 * {@code @Value} strings through the codebase, and the
 * {@link StartupValidator} can verify production safety in one place.</p>
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Security security = new Security();
    private Frontend frontend = new Frontend();

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
        private Cookie cookie = new Cookie();
        private Cors cors = new Cors();
        private Login login = new Login();
        private Invitation invitation = new Invitation();
    }

    @Getter
    @Setter
    public static class Jwt {
        /** HMAC-SHA256 signing secret. Must be >= 32 bytes; never a dev/test value in production. */
        private String secret = "";
        /** Access-token lifetime; intentionally short because refresh rotation is cheap. */
        private long accessExpirationSeconds = 900;
        /** Refresh-token lifetime; stored hashed server-side, revocable. */
        private long refreshExpirationDays = 30;
    }

    @Getter
    @Setter
    public static class Cookie {
        /** Lax (dev/same-site) or None (prod/cross-site; requires secure=true). */
        private String sameSite = "Lax";
        private boolean secure = false;
    }

    @Getter
    @Setter
    public static class Cors {
        /** Exact allowed origins (never a wildcard when credentials are enabled). */
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Login {
        private int maxAttempts = 10;
        private int windowMinutes = 15;
    }

    @Getter
    @Setter
    public static class Invitation {
        /** Must stay false outside dev/test: controls raw-token exposure in API responses. */
        private boolean exposeDevToken = false;
    }

    @Getter
    @Setter
    public static class Frontend {
        private String baseUrl = "http://localhost:3000";
    }
}
