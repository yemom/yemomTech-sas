package com.YemomBizGrow.membership;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The organization permission matrix — the single source of truth for what each
 * role may do. Services ask {@link #roles()} "does this role have permission X?"
 * and never re-implement role checks inline.
 *
 * <p>The matrix is the backend enforcement of the product policy:
 *
 * <pre>
 * Capability            OWNER ADMIN MKT   SALES-MGR SALES-AGENT ANALYST VIEWER
 * View organization      x     x     x       x          x         x       x
 * Update organization    x     x     -       -          -         -       -
 * Manage members         x     x     -       -          -         -       -
 * Invite members         x     x     -       -          -         -       -
 * Register/manage sites  x     x     x       -          -         -       -
 * Manage SEO             x     x     x       -          -         -       -
 * Manage leads           x     x     -       x          x         -       -
 * View analytics         x     x     x       x          limited*  x       read-only
 * Delete organization    x     -     -       -          -         -       -
 * </pre>
 *
 * *SALES_AGENT analytics access is "limited" (no cross-team lead detail); the
 * analytics module does not exist yet, so the current grant is view-only and the
 * narrowing rule will be applied when that module lands.
 */
public enum Permission {

    /** Read organization profile. */
    ORG_VIEW,
    /** Update organization profile (name, industry, description, website). */
    ORG_UPDATE,
    /** Soft-delete (archive) the organization. */
    ORG_DELETE,
    /** List members. */
    MEMBER_VIEW,
    /** Change member roles, suspend/remove members. */
    MEMBER_MANAGE,
    /** Create and revoke invitations. */
    INVITE_MANAGE,
    /** List websites. */
    WEBSITE_VIEW,
    /** Register, update, deactivate websites. */
    WEBSITE_MANAGE,
    /** SEO auditing and issue management (future module). */
    SEO_MANAGE,
    /** Lead CRUD (future module). */
    LEAD_MANAGE,
    /** Analytics dashboards (future module). */
    ANALYTICS_VIEW;

    private static final Map<Permission, Set<MembershipRole>> MATRIX = buildMatrix();

    private static Map<Permission, Set<MembershipRole>> buildMatrix() {
        Map<Permission, Set<MembershipRole>> m = new EnumMap<>(Permission.class);

        Set<MembershipRole> all = EnumSet.allOf(MembershipRole.class);
        m.put(ORG_VIEW, all);
        m.put(MEMBER_VIEW, all);
        m.put(WEBSITE_VIEW, all);
        m.put(ANALYTICS_VIEW, all);

        Set<MembershipRole> ownerAdmin = EnumSet.of(MembershipRole.BUSINESS_OWNER, MembershipRole.BUSINESS_ADMIN);
        m.put(ORG_UPDATE, ownerAdmin);
        m.put(MEMBER_MANAGE, ownerAdmin);
        m.put(INVITE_MANAGE, ownerAdmin);
        m.put(ORG_DELETE, EnumSet.of(MembershipRole.BUSINESS_OWNER));

        Set<MembershipRole> webManagers = EnumSet.of(MembershipRole.BUSINESS_OWNER,
                MembershipRole.BUSINESS_ADMIN, MembershipRole.MARKETING_MANAGER);
        m.put(WEBSITE_MANAGE, webManagers);
        m.put(SEO_MANAGE, webManagers);

        m.put(LEAD_MANAGE, EnumSet.of(MembershipRole.BUSINESS_OWNER, MembershipRole.BUSINESS_ADMIN,
                MembershipRole.SALES_MANAGER, MembershipRole.SALES_AGENT));

        return Map.copyOf(m);
    }

    /** Roles allowed to perform this permission (never empty). */
    public Set<MembershipRole> roles() {
        Set<MembershipRole> roles = MATRIX.get(this);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("Permission " + this + " has no assigned roles");
        }
        return roles;
    }

    public boolean allowedFor(MembershipRole role) {
        return roles().contains(role);
    }
}
