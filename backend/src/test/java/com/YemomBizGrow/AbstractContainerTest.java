package com.YemomBizGrow;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test: provides the PostgreSQL/Redis
 * containers and wires them into the Spring environment.
 *
 * <p>Why containers instead of a shared dev database: each build gets a
 * pristine schema that only Flyway created (proving migrations alone produce
 * a schema Hibernate accepts), and tests can run in any order or in parallel CI
 * jobs without shared-state flakiness.</p>
 *
 * <p><b>This class deliberately carries no {@code @ActiveProfiles}.</b> The
 * Spring profile belongs to the test that exercises it: the regular
 * integration tests run as {@code test}, while {@link ProductionProfileIT}
 * runs as {@code prod}. {@code @ActiveProfiles} is inherited and
 * <em>merged</em> across the class hierarchy, so a profile declared on a
 * shared base class would silently activate {@code test} together with
 * {@code prod} — which the startup validator rejects on purpose (mixed
 * production/development profiles are a deployment error, not a mode).</p>
 *
 * <p>Container lifetime note: the containers are JVM singletons started lazily
 * by the first context that needs them and reaped by Ryuk when the test JVM
 * exits. They are deliberately NOT {@code @Container}-per-class: Spring caches
 * the application context (keyed by configuration, not by container port), so
 * a class-scoped container would be stopped while a later test class still
 * reuses the context pointing at its dead port.</p>
 */
public abstract class AbstractContainerTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("YemomBizGrow_test")
            .withUsername("YemomBizGrow_test")
            .withPassword("YemomBizGrow_test");

    // No official Redis module exists in Testcontainers; the generic container
    // with an exposed port is the documented approach.
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideDataSourceAndRedis(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
