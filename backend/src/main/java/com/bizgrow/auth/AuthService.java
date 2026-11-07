package com.bizgrow.auth;

import com.bizgrow.audit.AuditAction;
import com.bizgrow.audit.AuditService;
import com.bizgrow.auth.dto.AuthSessionResponse;
import com.bizgrow.auth.dto.LoginRequest;
import com.bizgrow.auth.dto.MeResponse;
import com.bizgrow.auth.dto.RegisterRequest;
import com.bizgrow.auth.dto.SessionResult;
import com.bizgrow.common.exception.ForbiddenException;
import com.bizgrow.common.exception.NotFoundException;
import com.bizgrow.common.exception.UnauthorizedException;
import com.bizgrow.common.security.AuthenticatedUser;
import com.bizgrow.common.security.CurrentUser;
import com.bizgrow.common.security.JwtService;
import com.bizgrow.common.util.TokenHasher;
import com.bizgrow.membership.MembershipService;
import com.bizgrow.membership.OrganizationMembership;
import com.bizgrow.organization.Organization;
import com.bizgrow.organization.OrganizationReadService;
import com.bizgrow.organization.OrganizationStatus;
import com.bizgrow.user.User;
import com.bizgrow.user.UserService;
import com.bizgrow.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentication use cases: registration, login, refresh (with rotation and
 * reuse detection), logout, and the current-user view.
 *
 * <p>Session model (full detail in docs/AUTHENTICATION_AND_SECURITY.md):
 * <pre>
 * access token  = short-lived JWT, response body, frontend memory, Authorization header
 * refresh token = opaque random string, HttpOnly cookie, SHA-256 only in DB
 * CSRF token    = double-submit value, readable cookie, X-CSRF-TOKEN header on
 *                 cookie-authenticated endpoints (refresh/logout)
 * </pre>
 *
 * <p>Error-message policy: login failures for unknown e-mail and wrong password are
 * byte-identical ("Invalid email or password.") so the API cannot be used to
 * enumerate registered addresses. Account-disabled is only revealed AFTER correct
 * credentials — an attacker without the password learns nothing.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final LoginThrottleService throttle;
    private final MembershipService membershipService;
    private final OrganizationReadService organizationReadService;
    private final AuditService auditService;

    public AuthService(UserService userService,
                       RefreshTokenService refreshTokens,
                       JwtService jwtService,
                       LoginThrottleService throttle,
                       MembershipService membershipService,
                       OrganizationReadService organizationReadService,
                       AuditService auditService) {
        this.userService = userService;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.throttle = throttle;
        this.membershipService = membershipService;
        this.organizationReadService = organizationReadService;
        this.auditService = auditService;
    }

    /* ------------------------------- register ------------------------------- */

    /**
     * Creates the account and immediately establishes a session (the user goes
     * straight to onboarding without a second login round-trip).
     *
     * @throws com.bizgrow.common.exception.ConflictException duplicate e-mail
     */
    public SessionResult register(RegisterRequest request, String deviceInformation) {
        User user = userService.create(request);
        auditService.record(user.getId(), null, AuditAction.ACCOUNT_REGISTERED, "user", user.getId(), null);
        return issueSession(user, deviceInformation);
    }

    /* -------------------------------- login --------------------------------- */

    public SessionResult login(LoginRequest request, String deviceInformation) {
        String email = UserService.normalizeEmail(request.email());
        throttle.precheck(email);

        Optional<User> found = userService.findByEmail(email);
        if (found.isEmpty() || !userService.matchesPassword(found.get(), request.password())) {
            throttle.recordFailure(email);
            throw new UnauthorizedException("Invalid email or password.");
        }

        User user = found.get();
        if (user.getStatus() != com.bizgrow.user.UserStatus.ACTIVE) {
            // Reached only with a CORRECT password: safe to explain the real reason.
            throw new ForbiddenException("This account is disabled. Please contact support.");
        }

        throttle.recordSuccess(email);
        userService.markLogin(user.getId());
        return issueSession(user, deviceInformation);
    }

    /* ------------------------------- refresh -------------------------------- */

    /**
     * Validates the presented refresh cookie, rotates it, and issues a new access
     * token + new CSRF pair.
     *
     * <p>Reuse detection: a token that was already rotated (revoked) appearing again
     * means the cookie value leaked (theft). The entire family is revoked — the
     * legitimate session included — forcing a clean re-authentication, which is the
     * only safe response when you cannot tell attacker from victim.</p>
     *
     * <p>Intentionally NOT wrapped in one outer transaction: the family-revocation
     * write must survive the exception that follows it.</p>
     */
    public SessionResult refresh(String rawRefreshToken, String csrfHeader, String csrfCookie,
                                 String deviceInformation) {
        validateCsrf(csrfHeader, csrfCookie);

        RefreshToken token = refreshTokens.findByRawToken(rawRefreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid session. Please sign in again."));

        if (token.isRevoked()) {
            log.warn("Refresh-token reuse detected; revoking family {}", token.getFamilyId());
            refreshTokens.revokeFamily(token.getFamilyId());
            throw new UnauthorizedException("Session ended for security reasons. Please sign in again.");
        }
        if (token.isExpired()) {
            throw new UnauthorizedException("Session expired. Please sign in again.");
        }

        User user;
        try {
            user = userService.getById(token.getUserId());
        } catch (NotFoundException e) {
            refreshTokens.revokeFamily(token.getFamilyId());
            throw new UnauthorizedException("Invalid session. Please sign in again.");
        }
        if (user.getStatus() != com.bizgrow.user.UserStatus.ACTIVE) {
            refreshTokens.revokeFamily(token.getFamilyId());
            throw new UnauthorizedException("Account disabled. Please contact support.");
        }

        RefreshTokenService.IssuedToken rotated = refreshTokens.rotate(token, deviceInformation);
        return buildSession(user, rotated.rawToken());
    }

    /* -------------------------------- logout -------------------------------- */

    /**
     * Revokes the presented refresh session (whole family). Safe to call repeatedly:
     * missing/already-revoked tokens are no-ops, and the controller always clears
     * cookies. CSRF is required only when a revocation would actually happen —
     * a second logout after cookies were cleared still returns 204.
     */
    public void logout(String rawRefreshToken, String csrfHeader, String csrfCookie) {
        Optional<RefreshToken> token = refreshTokens.findByRawToken(rawRefreshToken);
        if (token.isEmpty()) {
            return; // Nothing to revoke (already logged out / cookie expired).
        }
        validateCsrf(csrfHeader, csrfCookie);
        if (!token.get().isRevoked()) {
            refreshTokens.revokeFamily(token.get().getFamilyId());
        }
    }

    /* ---------------------------------- me ----------------------------------- */

    /**
     * Session bootstrap for the frontend: the account plus every ACTIVE membership
     * with its organization summary — read live from the database so suspended
     * memberships or archived organizations cannot linger in the UI.
     */
    public MeResponse me() {
        AuthenticatedUser principal = CurrentUser.require();
        User user = userService.getById(principal.id());

        List<OrganizationMembership> memberships = membershipService.listActiveForUser(user.getId());
        Map<UUID, Organization> organizations = organizationReadService.findMap(
                memberships.stream().map(OrganizationMembership::getOrganizationId).toList());

        List<MeResponse.MembershipBrief> briefs = memberships.stream()
                .map(membership -> {
                    Organization organization = organizations.get(membership.getOrganizationId());
                    if (organization == null) {
                        return null; // org purged: hide instead of failing the whole payload
                    }
                    if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
                        return null; // archived workspaces are closed: never offer them for re-entry
                    }
                    return new MeResponse.MembershipBrief(
                            organization.getId(),
                            organization.getName(),
                            organization.getSlug(),
                            organization.getStatus(),
                            membership.getRole());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new MeResponse(UserResponse.from(user), briefs);
    }

    /* ------------------------------- internals ------------------------------- */

    private SessionResult issueSession(User user, String deviceInformation) {
        RefreshTokenService.IssuedToken refresh = refreshTokens.issueNewFamily(user.getId(), deviceInformation);
        return buildSession(user, refresh.rawToken());
    }

    private SessionResult buildSession(User user, String rawRefreshToken) {
        String accessToken = jwtService.createAccessToken(user.getId());
        String csrfToken = TokenHasher.newRawToken();
        AuthSessionResponse session = new AuthSessionResponse(
                accessToken,
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                csrfToken,
                UserResponse.from(user));
        return new SessionResult(session, rawRefreshToken, csrfToken);
    }

    /**
     * Double-submit CSRF check for cookie-authenticated endpoints: the request must
     * echo, in {@code X-CSRF-TOKEN}, the value of the readable {@code bizgrow_csrf}
     * cookie. A cross-site attacker can cause the cookie to be sent but can neither
     * read it nor set a custom header on a CORS-preflighted request.
     */
    private void validateCsrf(String headerValue, String cookieValue) {
        boolean headerPresent = headerValue != null && !headerValue.isBlank();
        boolean cookiePresent = cookieValue != null && !cookieValue.isBlank();
        if (!headerPresent || !cookiePresent
                || !MessageDigest.isEqual(headerValue.getBytes(StandardCharsets.UTF_8),
                                           cookieValue.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("CSRF validation failed.");
        }
    }
}
