package com.bizgrow.common.security;

import com.bizgrow.user.UserStatus;

import java.util.UUID;

/**
 * The authenticated principal stored in the SecurityContext.
 *
 * <p>Deliberately minimal: id + email. Roles/permissions are NEVER copied here at
 * login time — organization permissions are resolved from the database on every
 * tenant operation, so a role change or suspension takes effect immediately instead
 * of waiting for token expiry.</p>
 */
public record AuthenticatedUser(UUID id, String email, UserStatus status) {
}
