package com.YemomBizGrow.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The permission matrix is an authorization contract; these tests make any
 * future role/permission change a deliberate, visible edit.
 */
class PermissionTest {

    private static final MembershipRole OWNER = MembershipRole.BUSINESS_OWNER;

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("every permission grants access to at least one role")
    void everyPermissionHasRoles(Permission permission) {
        assertThat(permission.roles()).isNotEmpty();
        assertThat(permission.roles()).contains(OWNER);
    }

    @Test
    @DisplayName("only the owner may delete an organization")
    void onlyOwnerMayDeleteOrganization() {
        assertThat(Permission.ORG_DELETE.roles())
                .containsExactly(MembershipRole.BUSINESS_OWNER);
    }

    @Test
    @DisplayName("organization and member management are owner/admin only")
    void orgAndMemberManagementRestricted() {
        for (Permission permission : new Permission[]{Permission.ORG_UPDATE,
                Permission.MEMBER_MANAGE, Permission.INVITE_MANAGE}) {
            assertThat(permission.roles())
                    .containsExactlyInAnyOrder(MembershipRole.BUSINESS_OWNER,
                            MembershipRole.BUSINESS_ADMIN);
        }
    }

    @Test
    @DisplayName("every member may view the organization, members, sites and analytics")
    void viewPermissionsAreUniversal() {
        for (Permission permission : new Permission[]{Permission.ORG_VIEW,
                Permission.MEMBER_VIEW, Permission.WEBSITE_VIEW, Permission.ANALYTICS_VIEW}) {
            assertThat(permission.roles()).containsExactlyInAnyOrder(MembershipRole.values());
        }
    }

    @Test
    @DisplayName("website management includes the marketing manager but not sales or viewer")
    void websiteManagementRoles() {
        assertThat(Permission.WEBSITE_MANAGE.roles()).containsExactlyInAnyOrder(
                MembershipRole.BUSINESS_OWNER,
                MembershipRole.BUSINESS_ADMIN,
                MembershipRole.MARKETING_MANAGER);
        assertThat(Permission.WEBSITE_MANAGE.allowedFor(MembershipRole.VIEWER)).isFalse();
        assertThat(Permission.WEBSITE_MANAGE.allowedFor(MembershipRole.SALES_MANAGER)).isFalse();
    }

    @Test
    @DisplayName("lead management includes both sales roles but not marketing or viewer")
    void leadManagementRoles() {
        assertThat(Permission.LEAD_MANAGE.roles()).containsExactlyInAnyOrder(
                MembershipRole.BUSINESS_OWNER,
                MembershipRole.BUSINESS_ADMIN,
                MembershipRole.SALES_MANAGER,
                MembershipRole.SALES_AGENT);
        assertThat(Permission.LEAD_MANAGE.allowedFor(MembershipRole.MARKETING_MANAGER)).isFalse();
        assertThat(Permission.LEAD_MANAGE.allowedFor(MembershipRole.ANALYST)).isFalse();
    }

    @Test
    @DisplayName("isAtLeast follows the declared rank (owner is highest)")
    void roleRank() {
        assertThat(MembershipRole.BUSINESS_OWNER.isAtLeast(MembershipRole.VIEWER)).isTrue();
        assertThat(MembershipRole.BUSINESS_ADMIN.isAtLeast(MembershipRole.MARKETING_MANAGER)).isTrue();
        assertThat(MembershipRole.VIEWER.isAtLeast(MembershipRole.ANALYST)).isFalse();
        assertThat(MembershipRole.MARKETING_MANAGER.isAtLeast(MembershipRole.BUSINESS_OWNER)).isFalse();
        assertThat(MembershipRole.BUSINESS_OWNER.isAtLeast(MembershipRole.BUSINESS_OWNER)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MembershipRole.class)
    @DisplayName("no role can grant itself ORG_DELETE except the owner")
    void deletionIsOwnerOnly(MembershipRole role) {
        assertThat(Permission.ORG_DELETE.allowedFor(role))
                .isEqualTo(role == MembershipRole.BUSINESS_OWNER);
    }
}
