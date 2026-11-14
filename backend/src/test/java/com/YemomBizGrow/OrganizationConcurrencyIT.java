package com.YemomBizGrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency guarantees around organization creation.
 *
 * <p>Organization creation is a three-step transaction: allocate a slug,
 * insert the organization, insert the owner membership. The slug is chosen by
 * a read-then-write sequence ({@code existsBySlug} followed by the insert), so
 * two concurrent creates of the same name can pick the same candidate. The
 * database constraint {@code uq_organizations_slug} is the final authority —
 * these tests prove that (a) no duplicate slug ever survives, (b) a rejected
 * create leaves no partial state behind, and (c) no organization exists
 * without an owner membership.</p>
 */
class OrganizationConcurrencyIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-9!";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("concurrent creations of the same name never produce a duplicate slug")
    void concurrentCreatesNeverDuplicateSlugs() throws Exception {
        int users = 6;
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            sessions.add(register(uniqueEmail(), PASSWORD));
        }

        String sharedName = "Concurrent Trading PLC " + UUID.randomUUID().toString().substring(0, 8);

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<org.springframework.http.ResponseEntity<String>>> futures = new ArrayList<>();
        try {
            for (Session session : sessions) {
                Callable<org.springframework.http.ResponseEntity<String>> task = () -> {
                    start.await();
                    return post("/api/v1/organizations", Map.of("name", sharedName), bearerHeaders(session));
                };
                futures.add(pool.submit(task));
            }
            start.countDown();

            List<org.springframework.http.ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<org.springframework.http.ResponseEntity<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }

            List<String> slugs = new ArrayList<>();
            int created = 0;
            for (org.springframework.http.ResponseEntity<String> response : responses) {
                int status = response.getStatusCode().value();
                if (status == 201) {
                    created++;
                    slugs.add(jsonStr(response.getBody(), "$.slug"));
                } else {
                    // A rejected create must be a clean conflict, never a 500.
                    assertThat(status)
                            .as("a losing racer must fail with a conflict, not a server error")
                            .isEqualTo(409);
                }
            }

            assertThat(created)
                    .as("at least one concurrent create must succeed")
                    .isGreaterThanOrEqualTo(1);

            // The unique constraint did its job: every successful create has
            // its own slug.
            assertThat(new HashSet<>(slugs)).hasSameSizeAs(slugs);

            // And the database agrees with what the API reported.
            Integer stored = jdbc.queryForObject(
                    "SELECT count(*) FROM organizations WHERE name = ?", Integer.class, sharedName);
            assertThat(stored).isEqualTo(created);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("every organization has exactly one owner membership (no orphans)")
    void everyOrganizationHasAnOwnerMembership() {
        Integer orphans = jdbc.queryForObject("""
                SELECT count(*) FROM organizations o
                WHERE NOT EXISTS (
                    SELECT 1 FROM organization_members m
                    WHERE m.organization_id = o.id AND m.role = 'BUSINESS_OWNER'
                )
                """, Integer.class);

        assertThat(orphans)
                .as("an organization without an owner membership means a partial create was not rolled back")
                .isZero();

        Integer withoutAnyMember = jdbc.queryForObject("""
                SELECT count(*) FROM organizations o
                WHERE NOT EXISTS (
                    SELECT 1 FROM organization_members m WHERE m.organization_id = o.id
                )
                """, Integer.class);

        assertThat(withoutAnyMember).isZero();
    }

    @Test
    @DisplayName("slugs stay unique even when many organizations share a name")
    void slugsStayUniqueAcrossManyOrganizations() throws Exception {
        int users = 5;
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            sessions.add(register(uniqueEmail(), PASSWORD));
        }

        String sharedName = "Slug Collision PLC " + UUID.randomUUID().toString().substring(0, 8);

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<org.springframework.http.ResponseEntity<String>>> futures = new ArrayList<>();
        try {
            for (Session session : sessions) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return post("/api/v1/organizations", Map.of("name", sharedName), bearerHeaders(session));
                }));
            }
            start.countDown();

            Set<String> slugs = new HashSet<>();
            for (Future<org.springframework.http.ResponseEntity<String>> future : futures) {
                org.springframework.http.ResponseEntity<String> response = future.get(60, TimeUnit.SECONDS);
                if (response.getStatusCode().value() == 201) {
                    slugs.add(jsonStr(response.getBody(), "$.slug"));
                }
            }
            assertThat(slugs).isNotEmpty();

            // The database constraint is the final authority.
            Integer duplicateSlugs = jdbc.queryForObject("""
                    SELECT count(*) FROM (
                        SELECT slug FROM organizations GROUP BY slug HAVING count(*) > 1
                    ) duplicates
                    """, Integer.class);
            assertThat(duplicateSlugs).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
