package vn.edu.topicregistration.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport.IdentityFixture;
import vn.edu.topicregistration.integration.FixtureSupport.OrganizationScope;
import vn.edu.topicregistration.integration.MySqlTestSupport;
import vn.edu.topicregistration.integration.ReviewFixtureSupport;
import vn.edu.topicregistration.service.RegistrationDecisionService;

@SpringBootTest
@ActiveProfiles("local")
@Import(RegistrationCapacityConcurrencyIT.TestBeans.class)
class RegistrationCapacityConcurrencyIT {
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String FIXTURE_PASSWORD = "not-a-real-test-password";
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @TempDir
    static Path reportRoot;

    @Autowired
    RegistrationDecisionService decisions;

    @Autowired
    JdbcTemplate jdbc;

    private IdentityFixture dean;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        new ReviewFixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC), reportRoot)
                .seed(FIXTURE_PASSWORD);
        long deanId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'dean.demo'");
        long facultyId = requiredLong(
                "SELECT faculty_id FROM user_accounts WHERE id = ?", deanId);
        dean = new IdentityFixture(
                deanId, "dean.demo", new OrganizationScope(facultyId), Set.of(RoleCode.DEAN));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentApprovalsNeverExceedConfiguredCapacity() throws Exception {
        List<Long> pendingIds = jdbc.queryForList("""
                SELECT registration.id
                FROM registrations registration
                JOIN topics topic ON topic.id = registration.topic_id
                WHERE topic.title = '[DEMO] Hệ thống quản lý đăng ký đề tài'
                  AND registration.status = 'PENDING'
                ORDER BY registration.id
                """, Long.class);
        assertEquals(2, pendingIds.size());
        long topicId = requiredLong(
                "SELECT topic_id FROM registrations WHERE id = ?", pendingIds.getFirst());
        jdbc.update("UPDATE topics SET max_group_count = 1 WHERE id = ?", topicId);

        List<Outcome> outcomes = race(
                () -> decisions.accept(pendingIds.get(0), 0L),
                () -> decisions.accept(pendingIds.get(1), 0L));

        assertEquals(1, outcomes.stream().filter(Outcome::success).count());
        assertEquals(1, count("""
                SELECT COUNT(*) FROM registrations
                WHERE topic_id = ? AND status = 'ACCEPTED'
                """, topicId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM registrations
                WHERE topic_id = ? AND status = 'REJECTED'
                """, topicId));
    }

    private List<Outcome> race(ThrowingSupplier... operations) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(operations.length);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (ThrowingSupplier operation : operations) {
                futures.add(executor.submit(() -> {
                    authenticate(dean);
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        return new Outcome(true, operation.get(), null);
                    } catch (Throwable failure) {
                        return new Outcome(false, null, failure);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            return List.copyOf(outcomes);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void authenticate(IdentityFixture identity) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        identity.username(), "unused", identity.roles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                                .toList()));
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private long requiredLong(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Expected database identifier");
        }
        return value;
    }

    private record Outcome(boolean success, Object value, Throwable failure) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        @Primary
        Clock registrationCapacityClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
