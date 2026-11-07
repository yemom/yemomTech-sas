package com.bizgrow.audit;

/**
 * Machine-readable action names recorded in audit_logs.
 * Naming convention (enforced by a DB check constraint): UPPER_SNAKE_CASE.
 */
public enum AuditAction {
    ORGANIZATION_CREATED,
    ORGANIZATION_UPDATED,
    ORGANIZATION_ARCHIVED,
    MEMBER_INVITED,
    INVITATION_REVOKED,
    INVITATION_ACCEPTED,
    MEMBER_ROLE_CHANGED,
    MEMBER_SUSPENDED,
    MEMBER_REACTIVATED,
    MEMBER_REMOVED,
    WEBSITE_CREATED,
    WEBSITE_UPDATED,
    WEBSITE_DEACTIVATED,
    WEBSITE_REACTIVATED,
    ACCOUNT_REGISTERED
}
