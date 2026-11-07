package com.bizgrow.common.security;

import com.bizgrow.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Small helper controllers/services use to read the caller's identity
 * instead of parsing headers or accepting a user id parameter
 * (user ids in request bodies are attacker-controlled; this is not).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new UnauthorizedException("Authentication required.");
    }
}
