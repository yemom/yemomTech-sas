package com.bizgrow.auth;

import com.bizgrow.auth.dto.AuthSessionResponse;
import com.bizgrow.auth.dto.LoginRequest;
import com.bizgrow.auth.dto.MeResponse;
import com.bizgrow.auth.dto.RegisterRequest;
import com.bizgrow.auth.dto.SessionResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of authentication. Controllers here do three things only:
 * validate input with {@code @Valid}, translate servlet details (cookies,
 * User-Agent) into service parameters, and attach session cookies to responses.
 * No business rules, no direct repository access.
 *
 * <p>Status codes: 201 for account creation (register), 200 for session
 * establishment (login/refresh/me), 204 for logout (idempotent).</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token refresh, logout, current user")
public class AuthController {

    private static final String USER_AGENT_HEADER = "User-Agent";

    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        SessionResult result = authService.register(request, deviceInformation(httpRequest));
        cookieService.attachSessionCookies(httpResponse, result.refreshToken(), result.csrfToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.session());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        SessionResult result = authService.login(request, deviceInformation(httpRequest));
        cookieService.attachSessionCookies(httpResponse, result.refreshToken(), result.csrfToken());
        return ResponseEntity.ok(result.session());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        SessionResult result = authService.refresh(
                cookieService.readRefreshToken(request).orElse(null),
                request.getHeader(CookieService.CSRF_HEADER),
                cookieService.readCsrfCookie(request).orElse(null),
                deviceInformation(request));
        // Rotation: old cookie values are overwritten with the successor pair.
        cookieService.attachSessionCookies(response, result.refreshToken(), result.csrfToken());
        return ResponseEntity.ok(result.session());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(
                cookieService.readRefreshToken(request).orElse(null),
                request.getHeader(CookieService.CSRF_HEADER),
                cookieService.readCsrfCookie(request).orElse(null));
        cookieService.clearSessionCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(authService.me());
    }

    /**
     * Coarse session metadata for future "active sessions" views.
     * Stored truncated, never used for security decisions, never an IP address.
     */
    private String deviceInformation(HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT_HEADER);
        if (userAgent == null || userAgent.isBlank()) {
            return RefreshTokenService.DEVICE_UNKNOWN;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }
}
