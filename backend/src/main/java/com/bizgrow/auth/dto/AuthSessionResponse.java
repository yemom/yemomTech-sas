package com.bizgrow.auth.dto;

import com.bizgrow.user.dto.UserResponse;

/**
 * Response of register/login/refresh.
 *
 * <p>The access token is returned in the body ONCE and must be held in frontend
 * memory (never localStorage). The refresh token is NOT in this body — it travels
 * only as an HttpOnly cookie set by the controller. {@code csrfToken} is a
 * double-submit value the frontend must echo in {@code X-CSRF-TOKEN} on refresh
 * and logout requests.</p>
 *
 * @param accessToken      short-lived JWT (Bearer)
 * @param tokenType        always "Bearer"
 * @param expiresInSeconds access-token lifetime, lets the frontend schedule proactive refresh
 * @param csrfToken        double-submit token for cookie-authenticated endpoints
 * @param user             the authenticated account (safe fields only)
 */
public record AuthSessionResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String csrfToken,
        UserResponse user) {
}
