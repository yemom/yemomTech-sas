package com.YemomBizGrow;

import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for every integration test that runs under the {@code test}
 * profile: boots the FULL application (filters, security chain, JPA, Flyway,
 * Redis) against the throwaway PostgreSQL and Redis containers.
 *
 * <p>The containers live in {@link AbstractContainerTest} so that a test with
 * a different Spring profile (e.g. {@link ProductionProfileIT}) can share the
 * same containers without inheriting this class's {@code test} profile.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest extends AbstractContainerTest {

    @Autowired
    protected TestRestTemplate rest;

    /** Everything a browser session consists of: bearer access token + cookie/CSRF pair. */
    protected record Session(String accessToken, String csrfToken, String refreshCookie, String csrfCookie) {
    }

    /* ------------------------------ HTTP helpers ------------------------------ */

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders bearerHeaders(Session session) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(session.accessToken());
        return headers;
    }

    protected ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> post(String path, Object body, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> put(String path, Object body, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> patch(String path, Object body, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> delete(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    protected static <T> T jsonPath(String jsonBody, String path) {
        return com.jayway.jsonpath.JsonPath.read(jsonBody, path);
    }

    /** Typed accessors: keeping the static type concrete avoids AssertJ overload ambiguity. */
    protected static String jsonStr(String jsonBody, String path) {
        return com.jayway.jsonpath.JsonPath.read(jsonBody, path);
    }

    protected static int jsonInt(String jsonBody, String path) {
        Number value = com.jayway.jsonpath.JsonPath.read(jsonBody, path);
        return value.intValue();
    }

    /* ---------------------------- session helpers ----------------------------- */

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.et";
    }

    protected Session register(String email, String password) {
        ResponseEntity<String> response = post("/api/v1/auth/register", Map.of(
                "firstName", "Abebe",
                "lastName", "Kebede",
                "email", email,
                "password", password), jsonHeaders());
        assertStatus(response, 201, "register");
        return sessionFrom(response);
    }

    protected Session login(String email, String password) {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", email, "password", password), jsonHeaders());
        assertStatus(response, 200, "login");
        return sessionFrom(response);
    }

    /** Rotates the refresh cookie and returns the resulting (new) session. */
    protected Session refresh(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "YemomBizGrow_refresh=" + session.refreshCookie()
                + "; YemomBizGrow_csrf=" + session.csrfCookie());
        headers.set("X-CSRF-TOKEN", session.csrfToken());
        ResponseEntity<String> response = post("/api/v1/auth/refresh", null, headers);
        assertStatus(response, 200, "refresh");
        return sessionFrom(response);
    }

    /** Posts to refresh/logout with an explicit cookie pair (for negative cases). */
    protected ResponseEntity<String> refreshWith(String refreshCookie, String csrfCookie, String csrfHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "YemomBizGrow_refresh=" + refreshCookie
                + "; YemomBizGrow_csrf=" + csrfCookie);
        if (csrfHeader != null) {
            headers.set("X-CSRF-TOKEN", csrfHeader);
        }
        return post("/api/v1/auth/refresh", null, headers);
    }

    protected ResponseEntity<String> logout(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "YemomBizGrow_refresh=" + session.refreshCookie()
                + "; YemomBizGrow_csrf=" + session.csrfCookie());
        headers.set("X-CSRF-TOKEN", session.csrfToken());
        return post("/api/v1/auth/logout", null, headers);
    }

    protected Session sessionFrom(ResponseEntity<String> response) {
        String body = response.getBody();
        String accessToken = jsonPath(body, "$.accessToken");
        String csrfToken = jsonPath(body, "$.csrfToken");
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return new Session(accessToken, csrfToken,
                cookieValue(setCookies, "YemomBizGrow_refresh"),
                cookieValue(setCookies, "YemomBizGrow_csrf"));
    }

    private static String cookieValue(List<String> setCookies, String name) {
        return setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1).split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Cookie " + name + " missing. Got: " + setCookies));
    }

    /* ---------------------------- org/website helpers ------------------------- */

    protected Map<String, Object> createOrganization(Session owner, String name) {
        ResponseEntity<String> response = post("/api/v1/organizations",
                Map.of("name", name), bearerHeaders(owner));
        assertStatus(response, 201, "create organization");
        String body = response.getBody();
        return Map.of(
                "id", jsonPath(body, "$.id"),
                "slug", jsonPath(body, "$.slug"),
                "body", body);
    }

    protected String organizationId(Session owner, String name) {
        return (String) createOrganization(owner, name).get("id");
    }

    /* ------------------------------ assertions -------------------------------- */

    protected static void assertStatus(ResponseEntity<?> response, int expected, String operation) {
        Assertions.assertThat(response.getStatusCode().value())
                .as("%s -> unexpected status. Body: %s", operation, response.getBody())
                .isEqualTo(expected);
    }

    protected static void assertErrorEnvelope(ResponseEntity<String> response, int expectedStatus,
                                              String operation) {
        assertStatus(response, expectedStatus, operation);
        String body = response.getBody();
        Assertions.assertThat(body).as("error envelope must carry a message (%s)", operation)
                .contains("\"message\"");
        Number status = com.jayway.jsonpath.JsonPath.read(body, "$.status");
        Assertions.assertThat(status.intValue()).isEqualTo(expectedStatus);
        String path = com.jayway.jsonpath.JsonPath.read(body, "$.path");
        Assertions.assertThat(path).startsWith("/api/v1/");
    }
}
