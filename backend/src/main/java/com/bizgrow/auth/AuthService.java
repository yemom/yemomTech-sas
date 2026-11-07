package com.YemomBizGrow.auth;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.auth.dto.AuthSessionResponse;
import com.YemomBizGrow.auth.dto.LoginRequest;
import com.YemomBizGrow.auth.dto.MeResponse;
import com.YemomBizGrow.auth.dto.RegisterRequest;
import com.YemomBizGrow.auth.dto.SessionResult;
import com.YemomBizGrow.common.exception.ForbiddenException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.common.exception.UnauthorizedException;
import com.YemomBizGrow.common.security.AuthenticatedUser;
import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.common.security.JwtService;
import com.YemomBizGrow.common.util.TokenHasher;
import com.YemomBizGrow.membership.MembershipService;
import com.YemomBizGrow.membership.OrganizationMembership;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationReadService;
import com.YemomBizGrow.organization.OrganizationStatus;
import com.YemomBizGrow.user.User;
import com.YemomBizGrow.user.UserService;
import com.YemomBizGrow.user.UserStatus;
import com.YemomBizGrow.user.dto.UserResponse;
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
 * reuse detection), logout, session management, email verification, password reset,
 * and current-user state.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final LoginThrottleService throttle;
    private final UserTokenService userTokenService;
    private final MembershipService membershipService;
    private final OrganizationReadService organizationReadService;
    private final AuditService auditService;

    public AuthService(UserService userService,
                       RefreshTokenService refreshTokens,
                       JwtService jwtService,
                       LoginThrottleService throttle,
                       UserTokenService userTokenService,
                       MembershipService membershipService,
                       OrganizationReadService organizationReadService,
                       AuditService auditService) {
        this.userService = userService;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.throttle = throttle;
        this.userTokenService = userTokenService;
        this.membershipService = membershipService;
        this.organizationReadService = organizationReadService;
        this.auditService = auditService;
    }

    /* ------------------------------- register ------------------------------- */

    public SessionResult register(RegisterRequest request, String deviceInformation) {
        User user = userService.create(request);
        auditService.record(user.getId(), null, AuditAction.ACCOUNT_REGISTERED, "user", user.getId(), null);

        // Issue initial verification token via email delivery
        try {
            userTokenService.issueEmailVerificationToken(user.getId(), user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to dispatch initial verification email for {}: {}", user.getEmail(), e.getMessage());
        }

        return issueSession(user, deviceInformation);
    }

    /* -------------------------------- login --------------------------------- */

    public SessionResult login(LoginRequest request, String deviceInformation) {
        return login(request, deviceInformation, null);
    }

    public SessionResult login(LoginRequest request, String deviceInformation, String clientIp) {
        String email = UserService.normalizeEmail(request.email());
        throttle.precheck(email, clientIp);

        Optional<User> found = userService.findByEmail(email);
        if (found.isEmpty() || !userService.matchesPassword(found.get(), request.password())) {
            throttle.recordFailure(email, clientIp);
            throw new UnauthorizedException("Invalid email or password.");
        }

        User user = found.get();
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ForbiddenException("This account is disabled. Please contact support.");
        }

        throttle.recordSuccess(email);
        userService.markLogin(user.getId());
        return issueSession(user, deviceInformation);
    }

    /* ------------------------------- refresh -------------------------------- */

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
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            refreshTokens.revokeFamily(token.getFamilyId());
            throw new UnauthorizedException("Account disabled. Please contact support.");
        }

        RefreshTokenService.IssuedToken rotated = refreshTokens.rotate(token, deviceInformation);
        return buildSession(user, rotated.rawToken());
    }

    /* -------------------------------- logout -------------------------------- */

    public void logout(String rawRefreshToken, String csrfHeader, String csrfCookie) {
        Optional<RefreshToken> token = refreshTokens.findByRawToken(rawRefreshToken);
        if (token.isEmpty()) {
            return;
        }
        validateCsrf(csrfHeader, csrfCookie);
        if (!token.get().isRevoked()) {
            refreshTokens.revokeFamily(token.get().getFamilyId());
        }
    }

    /* -------------------------- session revocation -------------------------- */

    public void revokeOtherSessions(UUID userId, String rawCurrentRefreshToken) {
        Optional<RefreshToken> current = refreshTokens.findByRawToken(rawCurrentRefreshToken);
        if (current.isPresent()) {
            refreshTokens.revokeOthersForUser(userId, current.get().getFamilyId());
        } else {
            refreshTokens.revokeAllForUser(userId);
        }
        auditService.record(userId, null, AuditAction.SESSIONS_REVOKED, "user", userId, null);
    }

    public void revokeAllSessions(UUID userId) {
        refreshTokens.revokeAllForUser(userId);
        auditService.record(userId, null, AuditAction.SESSIONS_REVOKED, "user", userId, null);
    }

    /* ------------------------- email verification -------------------------- */

    public void resendVerificationEmail(UUID userId) {
        User user = userService.getById(userId);
        if (user.isEmailVerified()) {
            return;
        }
        userTokenService.issueEmailVerificationToken(user.getId(), user.getEmail());
    }

    public void verifyEmail(String rawToken) {
        UUID userId = userTokenService.verifyEmailToken(rawToken);
        userService.verifyEmail(userId);
        auditService.record(userId, null, AuditAction.ACCOUNT_EMAIL_VERIFIED, "user", userId, null);
    }

    /* --------------------------- password reset ----------------------------- */

    public void forgotPassword(String email) {
        Optional<User> found = userService.findByEmail(email);
        if (found.isPresent()) {
            User user = found.get();
            if (user.getStatus() == UserStatus.ACTIVE || user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                userTokenService.issuePasswordResetToken(user.getId(), user.getEmail());
            }
        }
        // Always finish cleanly without leaking whether the email exists
    }

    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = userTokenService.consumePasswordResetToken(rawToken);
        userService.updatePassword(userId, newPassword);
        refreshTokens.revokeAllForUser(userId);
        auditService.record(userId, null, AuditAction.ACCOUNT_PASSWORD_RESET, "user", userId, null);
    }

    /* ---------------------------------- me ----------------------------------- */

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
                        return null;
                    }
                    if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
                        return null;
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
