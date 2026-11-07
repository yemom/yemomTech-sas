package com.YemomBizGrow.auth.dto;

/**
 * Internal hand-off from AuthService to AuthController: the response body plus
 * the two cookie values only the controller may place into HTTP headers.
 *
 * <p>Keeping raw cookie material out of the service return types used elsewhere
 * (e.g. {@code MeResponse}) reduces the number of places a raw refresh token
 * exists in memory.</p>
 */
public record SessionResult(AuthSessionResponse session, String refreshToken, String csrfToken) {
}
