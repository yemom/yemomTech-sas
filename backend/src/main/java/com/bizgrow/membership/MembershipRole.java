package com.YemomBizGrow.membership;

/**
 * Organization-scoped roles. Global platform administration (SUPER_ADMIN) is a
 * separate concept and is intentionally NOT representable here — a user cannot be
 * granted platform powers through any organization membership API.
 */
public enum MembershipRole {
    BUSINESS_OWNER,
    BUSINESS_ADMIN,
    MARKETING_MANAGER,
    SALES_MANAGER,
    SALES_AGENT,
    ANALYST,
    VIEWER;

    /** Ownership transfer and role assignment may never exceed the actor's own role. */
    public boolean isAtLeast(MembershipRole other) {
        return this.ordinal() <= other.ordinal();
    }
}
