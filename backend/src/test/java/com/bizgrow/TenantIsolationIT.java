package com.bizgrow;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * MANDATORY tenant-isolation suite (spec): two independent tenants, every
 * cross-tenant probe asserted, membership as the only authorization source.
 *
 * <p>Rules under test:
 * <ul>
 *   <li>a non-member gets <b>404</b> (never 403) for another tenant's resources —
 *       existence itself must not leak;</li>
 *   <li>access levels follow the membership row, not anything in the token or
 *       request body;</li>
 *   <li>role rules: no self-edit, no privilege escalation above one's own rank.</li>
 * </ul></p>
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Habesha#2026";

    @Test
    @DisplayName("a non-member receives 404 on every endpoint of another tenant")
    void crossTenantProbesAreInvisible() {
        Session alice = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(alice, "Alpha Trading PLC");

        Session bob = register(uniqueEmail(), PASSWORD);
        String orgB = organizationId(bob, "Beta Logistics PLC");

        // Bob's own tenant works (control).
        assertStatus(get("/api/v1/organizations/" + orgB, bearerHeaders(bob)), 200, "own organization");

        // Every probe into Alice's tenant must be indistinguishable from "no such row".
        assertErrorEnvelope(get("/api/v1/organizations/" + orgA, bearerHeaders(bob)), 404,
                "GET foreign organization");
        assertErrorEnvelope(get("/api/v1/organizations/" + orgA + "/dashboard", bearerHeaders(bob)), 404,
                "GET foreign dashboard");
        assertErrorEnvelope(get("/api/v1/organizations/" + orgA + "/websites", bearerHeaders(bob)), 404,
                "LIST foreign websites");
        assertErrorEnvelope(post("/api/v1/organizations/" + orgA + "/websites",
                Map.of("name", "Injected", "url", "https://evil.example"), bearerHeaders(bob)), 404,
                "CREATE website in foreign organization");
        assertErrorEnvelope(patch("/api/v1/organizations/" + orgA,
                Map.of("name", "Hijacked"), bearerHeaders(bob)), 404,
                "PATCH foreign organization");
        assertErrorEnvelope(delete("/api/v1/organizations/" + orgA, bearerHeaders(bob)), 404,
                "DELETE foreign organization");
        assertErrorEnvelope(get("/api/v1/organizations/" + orgA + "/members", bearerHeaders(bob)), 404,
                "LIST foreign members");
        assertErrorEnvelope(post("/api/v1/organizations/" + orgA + "/invitations",
                Map.of("email", uniqueEmail(), "role", "VIEWER"), bearerHeaders(bob)), 404,
                "INVITE into foreign organization");

        // Cross-tenant id confusion: Bob's own website id must not resolve under Alice's org.
        String bobWebsite = (String) createWebsite(bob, orgB, "Beta site", "https://beta.example");
        assertErrorEnvelope(get("/api/v1/organizations/" + orgA + "/websites/" + bobWebsite,
                bearerHeaders(alice)), 404, "foreign website id under foreign org path");

        // Alice sees exactly one organization; Bob's is not in it.
        ResponseEntity<String> aliceList = get("/api/v1/organizations", bearerHeaders(alice));
        assertStatus(aliceList, 200, "list own organizations");
        Assertions.assertThat(aliceList.getBody()).contains(orgA).doesNotContain(orgB);

        // And Bob's membership never appears in /me for Alice.
        ResponseEntity<String> aliceMe = get("/api/v1/auth/me", bearerHeaders(alice));
        Assertions.assertThat(aliceMe.getBody()).doesNotContain(orgB);
    }

    @Test
    @DisplayName("membership role decides what a member may do in the tenant")
    void membershipDrivesAccessLevel() {
        String viewerEmail = uniqueEmail();
        Session owner = register(uniqueEmail(), PASSWORD);
        String orgId = organizationId(owner, "Scoped Testing Co");

        Session viewer = register(viewerEmail, PASSWORD);

        // Owner invites the viewer (dev profile exposes the raw token for tests).
        ResponseEntity<String> invite = post(
                "/api/v1/organizations/" + orgId + "/invitations",
                Map.of("email", viewerEmail, "role", "VIEWER"), bearerHeaders(owner));
        assertStatus(invite, 201, "create invitation");
        String token = jsonPath(invite.getBody(), "$.devAcceptToken");
        Assertions.assertThat(token).isNotBlank();

        // Duplicate invitation for the same e-mail conflicts.
        ResponseEntity<String> duplicateInvite = post(
                "/api/v1/organizations/" + orgId + "/invitations",
                Map.of("email", viewerEmail, "role", "VIEWER"), bearerHeaders(owner));
        assertErrorEnvelope(duplicateInvite, 409, "duplicate invitation");

        // Acceptance binds the token to the invited e-mail: a stranger cannot redeem it.
        Session stranger = register(uniqueEmail(), PASSWORD);
        assertErrorEnvelope(post("/api/v1/invitations/accept", Map.of("token", token),
                bearerHeaders(stranger)), 403, "accept someone else's invitation");

        ResponseEntity<String> accept = post("/api/v1/invitations/accept",
                Map.of("token", token), bearerHeaders(viewer));
        assertStatus(accept, 200, "accept invitation");
        Assertions.assertThat(jsonStr(accept.getBody(), "$.organizationId")).isEqualTo(orgId);
        Assertions.assertThat(jsonStr(accept.getBody(), "$.role")).isEqualTo("VIEWER");

        // Single use: the same token is dead afterwards.
        assertErrorEnvelope(post("/api/v1/invitations/accept", Map.of("token", token),
                bearerHeaders(viewer)), 400, "reuse invitation token");

        // The new membership shows up in the viewer's session bootstrap.
        ResponseEntity<String> viewerMe = get("/api/v1/auth/me", bearerHeaders(viewer));
        Assertions.assertThat(viewerMe.getBody()).contains(orgId);

        // VIEWER: read yes...
        assertStatus(get("/api/v1/organizations/" + orgId, bearerHeaders(viewer)), 200,
                "viewer reads organization");
        assertStatus(get("/api/v1/organizations/" + orgId + "/members", bearerHeaders(viewer)), 200,
                "viewer lists members");

        // ...write no (403, not 404: the viewer IS a member, only the permission is missing).
        assertErrorEnvelope(post("/api/v1/organizations/" + orgId + "/websites",
                Map.of("name", "Nope", "url", "https://viewer.example"), bearerHeaders(viewer)), 403,
                "viewer creates website");
        assertErrorEnvelope(patch("/api/v1/organizations/" + orgId,
                Map.of("name", "Renamed"), bearerHeaders(viewer)), 403,
                "viewer updates organization");
        assertErrorEnvelope(post("/api/v1/organizations/" + orgId + "/invitations",
                Map.of("email", uniqueEmail(), "role", "VIEWER"), bearerHeaders(viewer)), 403,
                "viewer invites");
        assertErrorEnvelope(patch("/api/v1/organizations/" + orgId + "/members/" + UUID_ZERO,
                Map.of("role", "BUSINESS_ADMIN"), bearerHeaders(viewer)), 403,
                "viewer manages members");

        // Owner promotes the member to a role that may manage websites.
        String viewerMembershipId = membershipIdOf(owner, orgId, viewer);
        ResponseEntity<String> promoted = patch(
                "/api/v1/organizations/" + orgId + "/members/" + viewerMembershipId,
                Map.of("role", "MARKETING_MANAGER"), bearerHeaders(owner));
        assertStatus(promoted, 200, "promote member");
        Assertions.assertThat(jsonStr(promoted.getBody(), "$.role")).isEqualTo("MARKETING_MANAGER");

        // Now website registration succeeds for the promoted member.
        assertStatus(post("/api/v1/organizations/" + orgId + "/websites",
                Map.of("name", "Promoted site", "url", "https://promoted.example"),
                bearerHeaders(viewer)), 201, "promoted member creates website");

        // Self-edit is structurally blocked (blocks self-escalation for everyone).
        String ownerMembershipId = membershipIdOf(owner, orgId, owner);
        assertErrorEnvelope(patch("/api/v1/organizations/" + orgId + "/members/" + ownerMembershipId,
                Map.of("role", "VIEWER"), bearerHeaders(owner)), 403,
                "owner edits own membership");

        // Nobody may grant a rank above their own: a marketing manager cannot hand out owner.
        assertErrorEnvelope(post("/api/v1/organizations/" + orgId + "/invitations",
                Map.of("email", uniqueEmail(), "role", "BUSINESS_OWNER"), bearerHeaders(viewer)), 403,
                "member invites above own rank");
    }

    @Test
    @DisplayName("an already-active member cannot be invited again")
    void existingMemberConflict() {
        String memberEmail = uniqueEmail();
        Session owner = register(uniqueEmail(), PASSWORD);
        String orgId = organizationId(owner, "Duplicate Invite Co");

        Session member = register(memberEmail, PASSWORD);
        String token = jsonPath(post("/api/v1/organizations/" + orgId + "/invitations",
                Map.of("email", memberEmail, "role", "ANALYST"), bearerHeaders(owner))
                .getBody(), "$.devAcceptToken");
        assertStatus(post("/api/v1/invitations/accept", Map.of("token", token),
                bearerHeaders(member)), 200, "accept invitation");

        assertErrorEnvelope(post("/api/v1/organizations/" + orgId + "/invitations",
                Map.of("email", memberEmail, "role", "ANALYST"), bearerHeaders(owner)), 409,
                "invite existing member");
    }

    /* -------------------------------- helpers -------------------------------- */

    private static final String UUID_ZERO = "00000000-0000-0000-0000-000000000000";

    private String createWebsite(Session actor, String orgId, String name, String url) {
        ResponseEntity<String> response = post(
                "/api/v1/organizations/" + orgId + "/websites",
                Map.of("name", name, "url", url), bearerHeaders(actor));
        assertStatus(response, 201, "create website");
        return jsonPath(response.getBody(), "$.id");
    }

    /** Resolves the membership row id of a user inside an organization (membership ids are
     *  opaque to clients, so the test derives them the way a members table would). */
    private String membershipIdOf(Session actor, String orgId, Session target) {
        ResponseEntity<String> members = get(
                "/api/v1/organizations/" + orgId + "/members?size=50", bearerHeaders(actor));
        assertStatus(members, 200, "list members");

        String targetUserId = jsonPath(get("/api/v1/auth/me", bearerHeaders(target)).getBody(),
                "$.user.id");
        java.util.List<Map<String, Object>> content = jsonPath(members.getBody(), "$.content");
        return content.stream()
                .filter(m -> targetUserId.equals(String.valueOf(m.get("userId"))))
                .map(m -> String.valueOf(m.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No membership found for user " + targetUserId));
    }
}
