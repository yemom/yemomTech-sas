package com.YemomBizGrow.auth;

import com.YemomBizGrow.auth.dto.AuthSessionResponse;
import com.YemomBizGrow.auth.dto.ForgotPasswordRequest;
import com.YemomBizGrow.auth.dto.LoginRequest;
import com.YemomBizGrow.auth.dto.MeResponse;
import com.YemomBizGrow.auth.dto.RegisterRequest;
import com.YemomBizGrow.auth.dto.ResetPasswordRequest;
import com.YemomBizGrow.auth.dto.SessionResult;
import com.YemomBizGrow.auth.dto.VerifyEmailRequest;
import com.YemomBizGrow.common.response.MessageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
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
 * HTTP surface of authentication: registration, login, refresh, logout,
 * password reset, email verification, and session management.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token refresh, logout, password recovery, session revocation")
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
        SessionResult result = authService.login(request, deviceInformation(httpRequest), clientIp(httpRequest));
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

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(new MessageResponse("If the account exists, password reset instructions have been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                         HttpServletResponse response) {
        authService.resetPassword(request.token(), request.newPassword());
        cookieService.clearSessionCookies(response);
        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully. Please sign in with your new password."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(new MessageResponse("Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification() {
        authService.resendVerificationEmail(CurrentUser.require().id());
        return ResponseEntity.ok(new MessageResponse("Verification email dispatched."));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<MessageResponse> revokeOtherSessions(HttpServletRequest request) {
        String currentRefresh = cookieService.readRefreshToken(request).orElse(null);
        authService.revokeOtherSessions(CurrentUser.require().id(), currentRefresh);
        return ResponseEntity.ok(new MessageResponse("Other sessions revoked successfully."));
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<MessageResponse> revokeAllSessions(HttpServletResponse response) {
        authService.revokeAllSessions(CurrentUser.require().id());
        cookieService.clearSessionCookies(response);
        return ResponseEntity.ok(new MessageResponse("All sessions revoked."));
    }

    private String deviceInformation(HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT_HEADER);
        if (userAgent == null || userAgent.isBlank()) {
            return RefreshTokenService.DEVICE_UNKNOWN;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String clientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
