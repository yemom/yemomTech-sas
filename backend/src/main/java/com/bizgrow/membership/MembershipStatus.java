package com.bizgrow.membership;

/**
 * Membership lifecycle. Only ACTIVE memberships grant access; REMOVED rows are
 * kept for audit history (who belonged when), which is why removal is an update,
 * not a row delete.
 */
public enum MembershipStatus {
    ACTIVE,
    SUSPENDED,
    REMOVED
}
