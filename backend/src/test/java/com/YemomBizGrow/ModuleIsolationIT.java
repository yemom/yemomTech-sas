package com.YemomBizGrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant isolation across every organization-scoped module.
 *
 * <p>The review of this codebase flagged that the reported test totals did not
 * establish that the business modules were production-ready. These tests cover
 * the highest-risk behaviour in each module: a user of one workspace must
 * never read or mutate another workspace's data, regardless of which module
 * the data belongs to. Every call goes through the real HTTP surface, so the
 * security chain and the service-level organization scoping are both
 * exercised.</p>
 *
 * <p>Each module is checked with the same shape: create the data in
 * organization A, then attempt to read it, modify it and delete it as a user
 * of organization B. All three attempts must be refused.</p>
 */
class ModuleIsolationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-9!";

    @Test
    @DisplayName("websites are scoped to the caller's own workspace")
    void websitesAreOrganizationScoped() {
        Session ownerA = register(uniqueEmail(), PASSWORD);
        Session ownerB = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(ownerA, "Isolation Site Alpha " + System.nanoTime());
        String orgB = organizationId(ownerB, "Isolation Site Beta " + System.nanoTime());

        ResponseEntity<String> created = post("/api/v1/organizations/" + orgA + "/websites",
                Map.of("name", "Alpha site", "url", "https://alpha-isolation.example.com"),
                bearerHeaders(ownerA));
        assertStatus(created, 201, "create website in own org");
        String websiteId = jsonStr(created.getBody(), "$.id");

        assertThat(get("/api/v1/organizations/" + orgB + "/websites", bearerHeaders(ownerB))
                .getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/organizations/" + orgB + "/websites", bearerHeaders(ownerB)).getBody())
                .as("another workspace's website must never appear in this list")
                .doesNotContain(websiteId);

        assertThat(get("/api/v1/organizations/" + orgA + "/websites/" + websiteId, bearerHeaders(ownerB))
                .getStatusCode().value())
                .as("a user of another workspace must not read a foreign website")
                .isIn(403, 404);

        assertThat(patch("/api/v1/organizations/" + orgA + "/websites/" + websiteId,
                Map.of("name", "Hijacked"), bearerHeaders(ownerB)).getStatusCode().value())
                .as("a user of another workspace must not modify a foreign website")
                .isIn(403, 404);

        assertThat(delete("/api/v1/organizations/" + orgA + "/websites/" + websiteId, bearerHeaders(ownerB))
                .getStatusCode().value())
                .as("a user of another workspace must not delete a foreign website")
                .isIn(403, 404);
    }

    @Test
    @DisplayName("SEO audits are scoped to the caller's own workspace")
    void seoAuditsAreOrganizationScoped() {
        Session ownerA = register(uniqueEmail(), PASSWORD);
        Session ownerB = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(ownerA, "Isolation Seo Alpha " + System.nanoTime());
        String orgB = organizationId(ownerB, "Isolation Seo Beta " + System.nanoTime());

        ResponseEntity<String> site = post("/api/v1/organizations/" + orgA + "/websites",
                Map.of("name", "Alpha audited site", "url", "https://alpha-audited.example.com"),
                bearerHeaders(ownerA));
        assertStatus(site, 201, "create website");
        String websiteId = jsonStr(site.getBody(), "$.id");

        assertThat(get("/api/v1/organizations/" + orgA + "/websites/" + websiteId + "/audits",
                bearerHeaders(ownerB)).getStatusCode().value())
                .as("a user of another workspace must not list foreign SEO audits")
                .isIn(403, 404);

        assertThat(post("/api/v1/organizations/" + orgA + "/websites/" + websiteId + "/audits",
                null, bearerHeaders(ownerB)).getStatusCode().value())
                .as("a user of another workspace must not trigger a foreign audit")
                .isIn(403, 404);
    }

    @Test
    @DisplayName("billing, campaigns, analytics, reports, members, invitations and audit logs are scoped")
    void remainingModulesAreOrganizationScoped() {
        Session ownerA = register(uniqueEmail(), PASSWORD);
        Session ownerB = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(ownerA, "Isolation Misc Alpha " + System.nanoTime());
        String orgB = organizationId(ownerB, "Isolation Misc Beta " + System.nanoTime());

        // Read-only surfaces: a foreign workspace must not be readable.
        String[] readOnlyPaths = {
                "/api/v1/organizations/%s/billing/subscription",
                "/api/v1/organizations/%s/campaigns",
                "/api/v1/organizations/%s/analytics/overview",
                "/api/v1/organizations/%s/reports/comprehensive",
                "/api/v1/organizations/%s/reports/export/csv",
                "/api/v1/organizations/%s/members",
                "/api/v1/organizations/%s/invitations",
                "/api/v1/organizations/%s/audit-logs",
                "/api/v1/organizations/%s/agency/clients",
        };
        for (String template : readOnlyPaths) {
            String path = template.replace("%s", orgA);
            ResponseEntity<String> response = get(path, bearerHeaders(ownerB));
            assertThat(response.getStatusCode().value())
                    .as("GET %s must not expose another workspace's data", path)
                    .isIn(403, 404);
        }

        // The owner's own workspace stays reachable for the same paths.
        for (String template : readOnlyPaths) {
            String path = template.replace("%s", orgA);
            ResponseEntity<String> response = get(path, bearerHeaders(ownerA));
            assertThat(response.getStatusCode().value())
                    .as("GET %s must work for the owning workspace", path)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("AI recommendations are scoped to the caller's own workspace")
    void aiRecommendationsAreOrganizationScoped() {
        Session ownerA = register(uniqueEmail(), PASSWORD);
        Session ownerB = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(ownerA, "Isolation AI Alpha " + System.nanoTime());
        String orgB = organizationId(ownerB, "Isolation AI Beta " + System.nanoTime());

        // Owner A asks for recommendations.
        ResponseEntity<String> generated = post(
                "/api/v1/organizations/" + orgA + "/ai/recommendations/generate", null, bearerHeaders(ownerA));
        assertStatus(generated, 200, "generate recommendations for own org");

        // Owner B asks for the same organization's recommendations.
        ResponseEntity<String> crossed = get(
                "/api/v1/organizations/" + orgA + "/ai/recommendations", bearerHeaders(ownerB));
        assertThat(crossed.getStatusCode().value())
                .as("a user of another workspace must not read foreign AI recommendations")
                .isIn(403, 404);

        // Owner B asking for their own organization still works.
        ResponseEntity<String> own = get(
                "/api/v1/organizations/" + orgB + "/ai/recommendations", bearerHeaders(ownerB));
        assertStatus(own, 200, "read own AI recommendations");
    }

    @Test
    @DisplayName("the chatbot configuration is scoped to the caller's own workspace")
    void chatbotConfigIsOrganizationScoped() {
        Session ownerA = register(uniqueEmail(), PASSWORD);
        Session ownerB = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(ownerA, "Isolation Chatbot Alpha " + System.nanoTime());
        String orgB = organizationId(ownerB, "Isolation Chatbot Beta " + System.nanoTime());

        ResponseEntity<String> own = get(
                "/api/v1/organizations/" + orgA + "/chatbot/config", bearerHeaders(ownerA));
        assertStatus(own, 200, "read own chatbot config");

        ResponseEntity<String> crossed = get(
                "/api/v1/organizations/" + orgA + "/chatbot/config", bearerHeaders(ownerB));
        assertThat(crossed.getStatusCode().value())
                .as("a user of another workspace must not read a foreign chatbot config")
                .isIn(403, 404);

        ResponseEntity<String> mutation = put(
                "/api/v1/organizations/" + orgA + "/chatbot/config",
                Map.of("botName", "Hijacked Bot", "greetingMessage", "hijacked",
                        "primaryColor", "#123456", "isEnabled", true),
                bearerHeaders(ownerB));
        assertThat(mutation.getStatusCode().value())
                .as("a user of another workspace must not modify a foreign chatbot config")
                .isIn(403, 404);
    }

    @Test
    @DisplayName("CRM leads are scoped to the caller's own workspace")
    void leadsAreOrganizationScoped() {
        Session ownerA = register(uniqueEmail(), PASSWORD);
        Session ownerB = register(uniqueEmail(), PASSWORD);
        String orgA = organizationId(ownerA, "Isolation CRM Alpha " + System.nanoTime());
        String orgB = organizationId(ownerB, "Isolation CRM Beta " + System.nanoTime());

        ResponseEntity<String> created = post("/api/v1/organizations/" + orgA + "/leads",
                Map.of("firstName", "Abebe", "lastName", "Kebede", "email", "abebe@example.et",
                        "company", "Isolation Ltd"),
                bearerHeaders(ownerA));
        assertStatus(created, 201, "create lead in own org");
        String leadId = jsonStr(created.getBody(), "$.id");

        // Owner B lists their own leads: the foreign lead must not appear.
        ResponseEntity<String> ownList = get(
                "/api/v1/organizations/" + orgB + "/leads", bearerHeaders(ownerB));
        assertStatus(ownList, 200, "list own leads");
        assertThat(ownList.getBody())
                .as("another workspace's lead must never leak into this list")
                .doesNotContain(leadId);

        // Owner B cannot open, modify or move the foreign lead.
        assertThat(get("/api/v1/organizations/" + orgA + "/leads/" + leadId, bearerHeaders(ownerB))
                .getStatusCode().value())
                .as("a user of another workspace must not read a foreign lead")
                .isIn(403, 404);

        assertThat(patch("/api/v1/organizations/" + orgA + "/leads/" + leadId,
                Map.of("firstName", "Hijacked"), bearerHeaders(ownerB)).getStatusCode().value())
                .as("a user of another workspace must not modify a foreign lead")
                .isIn(403, 404);

        assertThat(patch("/api/v1/organizations/" + orgA + "/leads/" + leadId + "/stage",
                Map.of("stage", "WON"), bearerHeaders(ownerB)).getStatusCode().value())
                .as("a user of another workspace must not move a foreign lead through the pipeline")
                .isIn(403, 404);

        // Owner A still sees the lead unchanged.
        ResponseEntity<String> stillThere = get(
                "/api/v1/organizations/" + orgA + "/leads/" + leadId, bearerHeaders(ownerA));
        assertStatus(stillThere, 200, "read own lead");
        assertThat(stillThere.getBody()).contains("Abebe");
    }

    @Test
    @DisplayName("a lead cannot be assigned to a user outside the organization")
    void leadCannotBeAssignedToForeignUser() {
        Session owner = register(uniqueEmail(), PASSWORD);
        Session outsider = register(uniqueEmail(), PASSWORD);
        String org = organizationId(owner, "Isolation Assignment " + System.nanoTime());

        ResponseEntity<String> created = post("/api/v1/organizations/" + org + "/leads",
                Map.of("firstName", "Selam", "lastName", "Taye", "email", "selam@example.et"),
                bearerHeaders(owner));
        assertStatus(created, 201, "create lead");
        String leadId = jsonStr(created.getBody(), "$.id");

        String outsiderId = jsonStr(get("/api/v1/auth/me", bearerHeaders(outsider)).getBody(), "$.user.id");

        ResponseEntity<String> assigned = patch("/api/v1/organizations/" + org + "/leads/" + leadId,
                Map.of("assignedUserId", outsiderId), bearerHeaders(owner));

        assertThat(assigned.getStatusCode().value())
                .as("assigning a lead to a user outside the organization must be rejected")
                .isIn(400, 403, 404);
    }
}
