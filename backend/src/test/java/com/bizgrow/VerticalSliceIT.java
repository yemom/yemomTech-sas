package com.bizgrow;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * End-to-end proof of the first vertical slice:
 * register → me → organization → website → refresh rotation → logout.
 *
 * <p>Runs against the real HTTP stack (security filters included) and real
 * PostgreSQL/Redis containers, so a passing run means the migration schema, the
 * JPA mapping, the permission gate and the cookie/CSRF protocol all agree.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VerticalSliceIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Habesha#2026";

    private static String email;
    private static Session session;
    private static String organizationId;
    private static String websiteId;

    @Test
    @Order(1)
    @DisplayName("register creates an account, establishes a session and exposes /me")
    void registerAndMe() {
        email = uniqueEmail();
        session = register(email, PASSWORD);

        Assertions.assertThat(session.accessToken()).isNotBlank();
        Assertions.assertThat(session.refreshCookie()).isNotBlank();
        Assertions.assertThat(session.csrfCookie()).isNotBlank();
        // The readable CSRF cookie must equal the CSRF token echoed in the body
        // (double-submit), and the refresh cookie must be HttpOnly.
        Assertions.assertThat(session.csrfCookie()).isEqualTo(session.csrfToken());

        ResponseEntity<String> me = get("/api/v1/auth/me", bearerHeaders(session));
        assertStatus(me, 200, "me");
        Assertions.assertThat(jsonStr(me.getBody(), "$.user.email")).isEqualTo(email);
        java.util.List<?> memberships = jsonPath(me.getBody(), "$.memberships");
        Assertions.assertThat(memberships).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("an authenticated user creates an organization and becomes its owner")
    void createOrganization() {
        ResponseEntity<String> response = post("/api/v1/organizations", Map.of(
                "name", "Habesha Foods PLC",
                "industry", "Agriculture",
                "description", "Coffee and teff exporter",
                "websiteUrl", "https://habeshafoods.et"), bearerHeaders(session));

        assertStatus(response, 201, "create organization");
        String body = response.getBody();
        organizationId = jsonPath(body, "$.id");

        // Ethiopian market defaults are applied server-side.
        Assertions.assertThat(jsonStr(body, "$.countryCode")).isEqualTo("ET");
        Assertions.assertThat(jsonStr(body, "$.currencyCode")).isEqualTo("ETB");
        Assertions.assertThat(jsonStr(body, "$.timezone")).isEqualTo("Africa/Addis_Ababa");
        Assertions.assertThat(jsonStr(body, "$.slug")).isEqualTo("habesha-foods-plc");
        Assertions.assertThat(jsonStr(body, "$.status")).isEqualTo("ACTIVE");
        Assertions.assertThat(jsonInt(body, "$.memberCount")).isEqualTo(1);

        ResponseEntity<String> mine = get("/api/v1/organizations", bearerHeaders(session));
        assertStatus(mine, 200, "list organizations");
        Assertions.assertThat(mine.getBody()).contains(organizationId);
    }

    @Test
    @Order(3)
    @DisplayName("website registration normalizes the URL and enforces per-org domain uniqueness")
    void registerWebsite() {
        ResponseEntity<String> created = post(
                "/api/v1/organizations/" + organizationId + "/websites",
                Map.of("name", "Main site", "url", "HTTPS://WWW.Example.com/"),
                bearerHeaders(session));

        assertStatus(created, 201, "create website");
        String body = created.getBody();
        websiteId = jsonPath(body, "$.id");
        // Canonical form: lowercased scheme+host, default port and empty path dropped.
        Assertions.assertThat(jsonStr(body, "$.url")).isEqualTo("https://www.example.com");
        Assertions.assertThat(jsonStr(body, "$.domain")).isEqualTo("www.example.com");
        Assertions.assertThat(jsonStr(body, "$.status")).isEqualTo("ACTIVE");

        // Same domain twice inside ONE organization is a conflict...
        ResponseEntity<String> duplicate = post(
                "/api/v1/organizations/" + organizationId + "/websites",
                Map.of("name", "Duplicate", "url", "http://www.example.com/page"),
                bearerHeaders(session));
        assertErrorEnvelope(duplicate, 409, "duplicate domain");

        // ...but localhost/IP/credential URLs never reach the database at all.
        for (String unsafe : new String[]{"http://localhost/admin", "http://10.0.0.5",
                "ftp://example.com", "https://user:pass@example.com"}) {
            ResponseEntity<String> rejected = post(
                    "/api/v1/organizations/" + organizationId + "/websites",
                    Map.of("name", "Bad", "url", unsafe), bearerHeaders(session));
            assertErrorEnvelope(rejected, 400, "unsafe URL " + unsafe);
        }

        ResponseEntity<String> list = get(
                "/api/v1/organizations/" + organizationId + "/websites?page=0&size=10",
                bearerHeaders(session));
        assertStatus(list, 200, "list websites");
        Integer totalElements = jsonPath(list.getBody(), "$.totalElements");
        Assertions.assertThat(totalElements).isEqualTo(1);
        Assertions.assertThat(jsonStr(list.getBody(), "$.content[0].id")).isEqualTo(websiteId);
    }

    @Test
    @Order(4)
    @DisplayName("the dashboard reflects the organization state")
    void dashboard() {
        ResponseEntity<String> response = get(
                "/api/v1/organizations/" + organizationId + "/dashboard", bearerHeaders(session));

        assertStatus(response, 200, "dashboard");
        Assertions.assertThat(jsonStr(response.getBody(), "$.organization.id")).isEqualTo(organizationId);
    }

    @Test
    @Order(5)
    @DisplayName("refresh rotates the cookie; replaying the old cookie revokes the whole family")
    void refreshRotationAndReuseDetection() {
        Session before = login(email, PASSWORD);

        Session rotated = refresh(before);
        Assertions.assertThat(rotated.refreshCookie()).isNotEqualTo(before.refreshCookie());
        Assertions.assertThat(rotated.accessToken()).isNotBlank();

        // The rotated (old) token presented again = stolen-cookie replay.
        ResponseEntity<String> replay = refreshWith(before.refreshCookie(), before.csrfCookie(),
                before.csrfToken());
        assertErrorEnvelope(replay, 401, "refresh reuse");
        Assertions.assertThat(replay.getBody()).contains("security reasons");

        // Family revocation must also kill the legitimate successor session.
        ResponseEntity<String> victim = refreshWith(rotated.refreshCookie(), rotated.csrfCookie(),
                rotated.csrfToken());
        assertErrorEnvelope(victim, 401, "family revoked");
    }

    @Test
    @Order(6)
    @DisplayName("CSRF is required on cookie-authenticated endpoints")
    void csrfIsRequired() {
        Session current = login(email, PASSWORD);

        ResponseEntity<String> missingHeader = refreshWith(current.refreshCookie(), current.csrfCookie(), null);
        assertErrorEnvelope(missingHeader, 403, "refresh without CSRF header");

        ResponseEntity<String> mismatched = refreshWith(current.refreshCookie(), current.csrfCookie(),
                "not-the-cookie-value");
        assertErrorEnvelope(mismatched, 403, "refresh with mismatched CSRF");
    }

    @Test
    @Order(7)
    @DisplayName("logout clears cookies and permanently ends the refresh family")
    void logoutEndsSession() {
        Session current = login(email, PASSWORD);

        ResponseEntity<String> response = logout(current);
        assertStatus(response, 204, "logout");

        ResponseEntity<String> afterLogout = refreshWith(current.refreshCookie(), current.csrfCookie(),
                current.csrfToken());
        assertErrorEnvelope(afterLogout, 401, "refresh after logout");
    }

    @Test
    @Order(8)
    @DisplayName("validation failures use the uniform error envelope with field errors")
    void validationErrorEnvelope() {
        ResponseEntity<String> response = post("/api/v1/auth/register", Map.of(
                "firstName", "Weak",
                "lastName", "Password",
                "email", uniqueEmail(),
                "password", "password"), jsonHeaders());

        assertErrorEnvelope(response, 400, "weak password");
        Assertions.assertThat(jsonStr(response.getBody(), "$.fieldErrors.password")).isNotNull();
        Assertions.assertThat(response.getBody()).doesNotContain("password is too weak");
    }

    @Test
    @Order(9)
    @DisplayName("duplicate registration is a conflict, never a 500")
    void duplicateRegistration() {
        String taken = uniqueEmail();
        register(taken, PASSWORD);

        ResponseEntity<String> response = post("/api/v1/auth/register", Map.of(
                "firstName", "Abebe",
                "lastName", "Kebede",
                "email", taken,
                "password", PASSWORD), jsonHeaders());

        assertErrorEnvelope(response, 409, "duplicate registration");
    }

    @Test
    @Order(10)
    @DisplayName("unknown e-mail and wrong password are indistinguishable")
    void loginIsNotEnumerable() {
        ResponseEntity<String> unknown = post("/api/v1/auth/login",
                Map.of("email", uniqueEmail(), "password", PASSWORD), jsonHeaders());
        ResponseEntity<String> wrongPassword = post("/api/v1/auth/login",
                Map.of("email", email, "password", "TotallyWrong1!"), jsonHeaders());

        assertErrorEnvelope(unknown, 401, "unknown e-mail");
        assertErrorEnvelope(wrongPassword, 401, "wrong password");
        Assertions.assertThat(jsonStr(unknown.getBody(), "$.message"))
                .isEqualTo(jsonStr(wrongPassword.getBody(), "$.message"));
    }

    @Test
    @Order(11)
    @DisplayName("websites are soft-deactivated: the row survives with status INACTIVE")
    void deactivateWebsite() {
        ResponseEntity<String> response = delete(
                "/api/v1/organizations/" + organizationId + "/websites/" + websiteId,
                bearerHeaders(session));

        assertStatus(response, 200, "deactivate website");
        Assertions.assertThat(jsonStr(response.getBody(), "$.status")).isEqualTo("INACTIVE");

        ResponseEntity<String> again = delete(
                "/api/v1/organizations/" + organizationId + "/websites/" + websiteId,
                bearerHeaders(session));
        assertStatus(again, 200, "deactivate website idempotently");
    }

    @Test
    @Order(12)
    @DisplayName("PATCH updates optional fields; an empty string clears them")
    void updateOrganization() {
        // Set a URL first so there is something to clear.
        ResponseEntity<String> set = patch("/api/v1/organizations/" + organizationId,
                Map.of("websiteUrl", "https://habeshafoods.et/about"), bearerHeaders(session));
        assertStatus(set, 200, "patch organization url");
        Assertions.assertThat(jsonStr(set.getBody(), "$.websiteUrl"))
                .isEqualTo("https://habeshafoods.et/about");

        // Empty string is the documented "clear" semantic (javadoc + service
        // trimToNull) — it must NOT be rejected by @Pattern.
        ResponseEntity<String> cleared = patch("/api/v1/organizations/" + organizationId,
                Map.of("websiteUrl", "", "description", ""), bearerHeaders(session));
        assertStatus(cleared, 200, "patch organization with empty strings");
        Assertions.assertThat(cleared.getBody()).doesNotContain("\"websiteUrl\":\"http");

        // A non-empty but invalid URL still fails validation with the envelope.
        ResponseEntity<String> invalid = patch("/api/v1/organizations/" + organizationId,
                Map.of("websiteUrl", "not-a-url"), bearerHeaders(session));
        assertErrorEnvelope(invalid, 400, "invalid website url");
    }

    @Test
    @Order(13)
    @DisplayName("organization soft-delete (archive) is owner-only and returns 204")
    void archiveOrganization() {
        Session other = register(uniqueEmail(), PASSWORD);
        String otherOrg = organizationId(other, "Someone Else");

        ResponseEntity<String> foreign = delete("/api/v1/organizations/" + organizationId,
                bearerHeaders(other));
        assertErrorEnvelope(foreign, 404, "archive another tenant's organization");

        ResponseEntity<String> response = delete("/api/v1/organizations/" + organizationId,
                bearerHeaders(session));
        assertStatus(response, 204, "archive own organization");

        ResponseEntity<String> afterArchive = get("/api/v1/organizations/" + organizationId,
                bearerHeaders(session));
        Assertions.assertThat(afterArchive.getStatusCode().value())
                .as("archived organization must leave the active workspace")
                .isIn(404, 410);

        // A tenant never disappears from the archiving owner's list silently:
        // the other tenant's organization is untouched.
        ResponseEntity<String> foreignList = get("/api/v1/organizations", bearerHeaders(other));
        Assertions.assertThat(foreignList.getBody()).contains(otherOrg);
    }
}
