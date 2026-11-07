package com.bizgrow.auth;

import com.bizgrow.audit.AuditService;
import com.bizgrow.auth.dto.LoginRequest;
import com.bizgrow.auth.dto.SessionResult;
import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.exception.ForbiddenException;
import com.bizgrow.common.exception.UnauthorizedException;
import com.bizgrow.common.security.JwtService;
import com.bizgrow.common.util.TokenHasher;
import com.bizgrow.membership.MembershipService;
import com.bizgrow.organization.OrganizationReadService;
import com.bizgrow.user.User;
import com.bizgrow.user.UserStatus;
import com.bizgrow.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auth flow rules that must never regress: generic login errors (no account
 * enumeration), disabled accounts revealed only to correct password holders,
 * CSRF on cookie endpoints, and reuse detection.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "owner@example.et";

    @Mock private UserService userService;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private JwtService jwtService;
    @Mock private LoginThrottleService throttle;
    @Mock private MembershipService membershipService;
    @Mock private OrganizationReadService organizationReadService;
    @Mock private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userService, refreshTokens, jwtService, throttle,
                membershipService, organizationReadService, auditService);
    }

    /* --------------------------------- login --------------------------------- */

    @Test
    @DisplayName("unknown e-mail and wrong password return byte-identical errors")
    void loginErrorsDoNotEnumerateAccounts() {
        // First stubbing must accept ANY e-mail: the unknown-account attempt uses
        // a different address than the wrong-password attempt.
        when(userService.findByEmail(anyString())).thenReturn(Optional.empty());
        String unknownEmailMessage = loginFailureMessage("does-not-exist@example.et", "Whatever1!");

        User wrongPassword = user(UserStatus.ACTIVE);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(wrongPassword));
        when(userService.matchesPassword(wrongPassword, "WrongPass1!")).thenReturn(false);
        String wrongPasswordMessage = loginFailureMessage(EMAIL, "WrongPass1!");

        assertThat(unknownEmailMessage)
                .isEqualTo(wrongPasswordMessage)
                .isEqualTo("Invalid email or password.");
        verify(refreshTokens, never()).issueNewFamily(any(), anyString());
    }

    @Test
    @DisplayName("e-mail is normalized before the throttle and lookup are consulted")
    void normalizesEmailBeforeLookup() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("  OWNER@Example.ET ", "Whatever1!"), "device"))
                .isInstanceOf(UnauthorizedException.class);

        verify(throttle).precheck(EMAIL);
        verify(throttle).recordFailure(EMAIL);
    }

    @Test
    @DisplayName("a disabled account is only explained after the password is correct")
    void disabledAccountNeedsCorrectPasswordFirst() {
        User disabled = user(UserStatus.SUSPENDED);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(disabled));
        when(userService.matchesPassword(disabled, "CorrectPass1!")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "CorrectPass1!"), "device"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("disabled");

        // A disabled account must not leak its state through the failure counter,
        // and no session may be issued.
        verify(throttle, never()).recordFailure(anyString());
        verify(refreshTokens, never()).issueNewFamily(any(), anyString());
    }

    @Test
    @DisplayName("a successful login throttles nothing, records login and issues a session")
    void successfulLoginIssuesSession() {
        User active = user(UserStatus.ACTIVE);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(active));
        when(userService.matchesPassword(active, "GoodPass1!")).thenReturn(true);
        when(refreshTokens.issueNewFamily(active.getId(), "device"))
                .thenReturn(new RefreshTokenService.IssuedToken(
                        UUID.randomUUID(), UUID.randomUUID(), "raw-refresh", Instant.now().plusSeconds(3600)));
        when(jwtService.createAccessToken(active.getId())).thenReturn("access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        SessionResult result = authService.login(new LoginRequest(EMAIL, "GoodPass1!"), "device");

        assertThat(result.session().accessToken()).isEqualTo("access-token");
        assertThat(result.session().tokenType()).isEqualTo("Bearer");
        assertThat(result.session().expiresInSeconds()).isEqualTo(900);
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        assertThat(result.csrfToken()).isNotBlank();
        verify(throttle).precheck(EMAIL);
        verify(throttle).recordSuccess(EMAIL);
        verify(userService).markLogin(active.getId());
        verify(throttle, never()).recordFailure(anyString());
    }

    /* -------------------------------- refresh -------------------------------- */

    @Test
    @DisplayName("refresh without a matching CSRF header/cookie pair is refused")
    void refreshRequiresCsrf() {
        assertThatThrownBy(() -> authService.refresh("raw", null, "raw", "device"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> authService.refresh("raw", "raw", "other", "device"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> authService.refresh("raw", "raw", " ", "device"))
                .isInstanceOf(ForbiddenException.class);

        verify(refreshTokens, never()).findByRawToken(anyString());
    }

    @Test
    @DisplayName("an unknown refresh cookie ends the session without details")
    void unknownRefreshTokenIsRejected() {
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw", "raw", "raw", "device"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid session. Please sign in again.");
    }

    @Test
    @DisplayName("replaying an already-rotated token revokes the entire family (theft response)")
    void reuseDetectionRevokesFamily() {
        RefreshToken revoked = token();
        revoked.setRevokedAt(Instant.now());
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("raw", "raw", "raw", "device"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session ended for security reasons. Please sign in again.");

        verify(refreshTokens).revokeFamily(revoked.getFamilyId());
    }

    @Test
    @DisplayName("an expired refresh token is rejected without a family sweep")
    void expiredTokenRejected() {
        RefreshToken expired = token();
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw", "raw", "raw", "device"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session expired. Please sign in again.");

        verify(refreshTokens, never()).revokeFamily(any());
    }

    @Test
    @DisplayName("an active token rotates and returns a brand-new access token and CSRF pair")
    void activeTokenRotates() {
        RefreshToken current = token();
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(current));
        User active = user(UserStatus.ACTIVE);
        when(userService.getById(current.getUserId())).thenReturn(active);
        when(refreshTokens.rotate(eq(current), eq("device")))
                .thenReturn(new RefreshTokenService.IssuedToken(
                        UUID.randomUUID(), current.getFamilyId(), "rotated-raw", Instant.now().plusSeconds(3600)));
        when(jwtService.createAccessToken(active.getId())).thenReturn("new-access");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        SessionResult result = authService.refresh("raw", "raw", "raw", "device");

        assertThat(result.refreshToken()).isEqualTo("rotated-raw");
        assertThat(result.session().accessToken()).isEqualTo("new-access");
        assertThat(result.csrfToken()).isNotBlank();
        verify(refreshTokens, never()).revokeFamily(any());
    }

    @Test
    @DisplayName("a disabled user's session family is revoked on refresh")
    void disabledUserFamilyRevoked() {
        RefreshToken current = token();
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(current));
        when(userService.getById(current.getUserId())).thenReturn(user(UserStatus.SUSPENDED));

        assertThatThrownBy(() -> authService.refresh("raw", "raw", "raw", "device"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokens).revokeFamily(current.getFamilyId());
        verify(refreshTokens, never()).rotate(any(), anyString());
    }

    /* -------------------------------- logout --------------------------------- */

    @Test
    @DisplayName("logging out with no cookie is a silent no-op (cookies already cleared)")
    void logoutWithoutTokenIsNoOp() {
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatCode(
                () -> authService.logout("raw", null, null)).doesNotThrowAnyException();

        verify(refreshTokens, never()).revokeFamily(any());
    }

    @Test
    @DisplayName("logout with a valid CSRF pair revokes the family")
    void logoutRevokesFamily() {
        RefreshToken active = token();
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(active));

        authService.logout("raw", "csrf", "csrf");

        verify(refreshTokens).revokeFamily(active.getFamilyId());
    }

    @Test
    @DisplayName("logout without CSRF never revokes (a cross-site request cannot kill a session)")
    void logoutWithoutCsrfDoesNotRevoke() {
        RefreshToken active = token();
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> authService.logout("raw", "csrf", "different"))
                .isInstanceOf(ForbiddenException.class);

        verify(refreshTokens, never()).revokeFamily(any());
    }

    /* -------------------------------- helpers -------------------------------- */

    private String loginFailureMessage(String email, String password) {
        try {
            authService.login(new LoginRequest(email, password), "device");
        } catch (UnauthorizedException e) {
            return e.getMessage();
        }
        throw new AssertionError("Expected UnauthorizedException");
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setFirstName("Selam");
        user.setLastName("Tesfaye");
        user.setPasswordHash(TokenHasher.sha256Hex("irrelevant"));
        user.setStatus(status);
        return user;
    }

    private RefreshToken token() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(UUID.randomUUID());
        token.setFamilyId(UUID.randomUUID());
        token.setTokenHash(TokenHasher.sha256Hex("raw"));
        token.setExpiresAt(Instant.now().plusSeconds(86_400));
        return token;
    }
}
