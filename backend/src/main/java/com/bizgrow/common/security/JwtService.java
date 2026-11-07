package com.bizgrow.common.security;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and verifies short-lived access tokens (HS256).
 *
 * <p>Claims policy — minimal by design:
 * <ul>
 *   <li>{@code sub} = user id (the only identity claim; no name, e-mail, phone,
 *       or organization data in the token — JWT payloads are readable by anyone
 *       holding the token and travel in logs/proxies).</li>
 *   <li>{@code jti} = token id (supports future deny-listing/telemetry).</li>
 *   <li>{@code iat}/{@code exp} = standard lifetime bookkeeping.</li>
 * </ul>
 * Authorization data is NOT in the token: every request reloads the user row, and
 * every tenant operation reloads membership rows.</p>
 */
@Service
public class JwtService {

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        String secret = properties.getSecurity().getJwt().getSecret();
        // StartupValidator guarantees >= 32 bytes, which HMAC-SHA256 requires.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getSecurity().getJwt().getAccessExpirationSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.getSecurity().getJwt().getAccessExpirationSeconds();
    }

    /**
     * @return the user id embedded in a syntactically valid, correctly signed,
     *         unexpired token
     * @throws UnauthorizedException for any malformed, tampered, or expired token
     */
    public UUID parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            // One generic message: never echo parsing internals back to clients.
            throw new UnauthorizedException("Invalid or expired access token.");
        }
    }
}
