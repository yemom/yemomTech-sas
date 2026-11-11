package com.YemomBizGrow.user;

/**
 * Lifecycle of a user account. Account status is checked at login AND on every
 * authenticated request, so suspending a user takes effect within one access-token
 * lifetime instead of requiring a token deny-list.
 */
public enum UserStatus {
    /** Registered, allowed to authenticate. */
    ACTIVE,
    /** Registered but blocked from authenticating until e-mail verification completes (future flow). */
    PENDING_VERIFICATION,
    /** Temporarily blocked by an administrator or security action. */
    SUSPENDED,
    /** User-initiated or administrative deactivation; kept for data retention, never authenticates. */
    DEACTIVATED
}
