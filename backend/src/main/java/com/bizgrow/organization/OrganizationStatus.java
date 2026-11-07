package com.YemomBizGrow.organization;

/**
 * Organization lifecycle. Deletion is deliberately soft: ARCHIVED organizations
 * keep their rows (and audit trail) and are excluded from normal listings,
 * which gives a documented retention/recovery path instead of cascading data loss.
 */
public enum OrganizationStatus {
    ACTIVE,
    /** Administratively frozen; members retain read access until restored. */
    SUSPENDED,
    /** Soft-deleted. Hidden from listings, recoverable until a future retention purge. */
    ARCHIVED
}
