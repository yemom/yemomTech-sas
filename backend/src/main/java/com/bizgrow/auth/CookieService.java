package com.bizgrow.auth;

import com.bizgrow.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The ONLY place session cookies are created, read, or cleared.
 *
 * <p>Cookie policy (enforced here, documented in AUTHENTICATION_AND_SECURITY.md):
 * <ul>
 *   <li>{@code bizgrow_refresh} — HttpOnly (JavaScript can never read the refresh
 *       token), scoped to {@code /api/v1/auth} so it is not attached to ordinary
 *       API calls, SameSite per environment (Lax dev / None+Secure prod).</li>
 *   <li>{@code bizgrow_csrf} — readable by the frontend on purpose: it is the
 *       double-submit value echoed in {@code X-CSRF-TOKEN} for refresh/logout.</li>
 *   <li>Clearing uses identical attributes + Max-Age=0 so browsers reliably
 *       delete both cookies.</li>
 * </ul></p>
 */
@Service
public class CookieService {

    public static final String REFRESH_COOKIE = "bizgrow_refresh";
    public static final String CSRF_COOKIE = "bizgrow_csrf";
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";
    /** Cookie path = the auth surface only; ordinary /organizations/** requests never carry it. */
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AppProperties properties;

    public CookieService(AppProperties properties) {
        this.properties = properties;
    }

    /* ------------------------------ writing ------------------------------ */

    public void attachSessionCookies(HttpServletResponse response, String refreshToken, String csrfToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(csrfToken).toString());
    }

    public void clearSessionCookies(HttpServletResponse response) {
        // Identical attributes + Max-Age=0 is the only reliable cross-browser delete.
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie("", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                csrfCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(properties.getSecurity().getCookie().isSecure())
                .sameSite(properties.getSecurity().getCookie().getSameSite())
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie csrfCookie(String value, Duration maxAge) {
        return ResponseCookie.from(CSRF_COOKIE, value)
                .path(COOKIE_PATH)
                .httpOnly(false)
                .secure(properties.getSecurity().getCookie().isSecure())
                .sameSite(properties.getSecurity().getCookie().getSameSite())
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie refreshCookie(String value) {
        return refreshCookie(value, sessionLifetime());
    }

    private ResponseCookie csrfCookie(String value) {
        return csrfCookie(value, sessionLifetime());
    }

    private Duration sessionLifetime() {
        return Duration.ofDays(properties.getSecurity().getJwt().getRefreshExpirationDays());
    }

    /* ------------------------------ reading ------------------------------ */

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE);
    }

    public Optional<String> readCsrfCookie(HttpServletRequest request) {
        return readCookie(request, CSRF_COOKIE);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
