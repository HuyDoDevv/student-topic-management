package vn.edu.topicregistration.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.integration.MySqlTestSupport;
import vn.edu.topicregistration.integration.ReviewFixtureSupport;
import vn.edu.topicregistration.integration.ReviewFixtureSupport.ReviewFixture;
import vn.edu.topicregistration.service.ReportService;
import vn.edu.topicregistration.service.ReportService.ReportUpload;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.ScoreCommand;

/** T008: proves report, score, complete and publish races preserve the final invariants. */
@SpringBootTest
@ActiveProfiles("local")
@Import(ReviewWorkflowConcurrencyIT.FixedClockConfiguration.class)
class ReviewWorkflowConcurrencyIT {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final String FIXTURE_PASSWORD = "not-a-real-test-password";
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @TempDir
    static Path reportRoot;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReportService reports;

    @Autowired
    ScoringService scoring;

    @Autowired
    ResultService results;

    private ReviewFixture fixture;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeedReviewFixture() {
        DATABASE.resetAndMigrate();
        fixture = new ReviewFixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC), reportRoot)
                .seed(FIXTURE_PASSWORD);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyOneConcurrentCompleteAndPublishWins() throws Exception {
        List<Outcome> completions = race(
                call("lecturer1.demo", () -> results.complete(fixture.activeCouncilId(), 0L),
                        RoleCode.LECTURER),
                call("lecturer1.demo", () -> results.complete(fixture.activeCouncilId(), 0L),
                        RoleCode.LECTURER));

        assertOneSuccessAndOneStaleFailure(completions);
        assertEquals("COMPLETED", councilStatus());
        assertEquals(6, count("SELECT COUNT(*) FROM final_results WHERE council_id = ?",
                fixture.activeCouncilId()));

        List<Outcome> publications = race(
                call("dean.demo", () -> results.publish(fixture.activeCouncilId(), 1L),
                        RoleCode.DEAN, RoleCode.LECTURER),
                call("dean.demo", () -> results.publish(fixture.activeCouncilId(), 1L),
                        RoleCode.DEAN, RoleCode.LECTURER));

        assertOneSuccessAndOneStaleFailure(publications);
        assertEquals("PUBLISHED", councilStatus());
        assertEquals(6, count("""
                SELECT COUNT(*) FROM final_results
                WHERE council_id = ? AND status = 'PUBLISHED'
                """, fixture.activeCouncilId()));
    }

    @Test
    void scoreRacingCompleteIsEitherIncludedOrRejectedBeforeTheSnapshot() throws Exception {
        authenticate("lecturer4.demo", RoleCode.LECTURER);
        var studentScore = scoring.workspace(
                fixture.activeCouncilId(), fixture.activeRegistrationOneId())
                .students().getFirst();
        long evaluationVersion = studentScore.evaluation().version();
        SecurityContextHolder.clearContext();

        List<Outcome> outcomes = race(
                call("lecturer4.demo", () -> scoring.submit(
                        fixture.activeCouncilId(),
                        fixture.activeRegistrationOneId(),
                        studentScore.studentId(),
                        new ScoreCommand(
                                new BigDecimal("10.00"),
                                "Concurrent update",
                                evaluationVersion)), RoleCode.LECTURER),
                call("lecturer1.demo", () -> results.complete(
                        fixture.activeCouncilId(), 0L), RoleCode.LECTURER));

        Outcome scoreOutcome = outcomes.getFirst();
        Outcome completeOutcome = outcomes.getLast();
        assertTrue(completeOutcome.success(),
                () -> "Completion failed: " + completeOutcome.failure());
        if (!scoreOutcome.success()) {
            ApplicationException rejected = assertInstanceOf(
                    ApplicationException.class, scoreOutcome.failure());
            assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, rejected.code());
        }
        assertEquals("COMPLETED", councilStatus());

        BigDecimal expectedMean = jdbc.queryForObject("""
                SELECT ROUND(AVG(score), 2) FROM evaluations
                WHERE council_id = ? AND registration_id = ? AND student_id = ?
                  AND status = 'SUBMITTED'
                """, BigDecimal.class,
                fixture.activeCouncilId(), fixture.activeRegistrationOneId(),
                studentScore.studentId());
        BigDecimal storedScore = jdbc.queryForObject("""
                SELECT final_score FROM final_results
                WHERE registration_id = ? AND student_id = ?
                """, BigDecimal.class, fixture.activeRegistrationOneId(),
                studentScore.studentId());
        assertTrue(expectedMean != null && storedScore != null);
        assertEquals(0, expectedMean.compareTo(storedScore),
                "Final score must use one consistent submitted-evaluation snapshot");
    }

    @Test
    void concurrentFirstReportUploadsLeaveOneCurrentMetadataRowAndOneFile() throws Exception {
        long filesBefore = reportFileCount();
        byte[] first = "%PDF-1.7\nfirst".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "%PDF-1.7\nsecond".getBytes(StandardCharsets.US_ASCII);

        List<Outcome> uploads = race(
                call("student1.demo", () -> reports.upload(
                        fixture.uploadRegistrationId(), null, upload("first.pdf", first)),
                        RoleCode.STUDENT),
                call("student1.demo", () -> reports.upload(
                        fixture.uploadRegistrationId(), null, upload("second.pdf", second)),
                        RoleCode.STUDENT));

        assertOneSuccessAndOneStaleFailure(uploads);
        assertEquals(1, count("""
                SELECT COUNT(*) FROM project_reports
                WHERE registration_id = ? AND replaced_at IS NULL
                """, fixture.uploadRegistrationId()));
        assertEquals(filesBefore + 1, reportFileCount());
    }

    private static ReportUpload upload(String name, byte[] bytes) {
        return new ReportUpload(
                name,
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private static void assertOneSuccessAndOneStaleFailure(List<Outcome> outcomes) {
        assertEquals(1, outcomes.stream().filter(Outcome::success).count());
        Outcome failure = outcomes.stream().filter(outcome -> !outcome.success())
                .findFirst()
                .orElseThrow();
        assertInstanceOf(OptimisticLockingFailureException.class, failure.failure());
    }

    private String councilStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM councils WHERE id = ?",
                String.class,
                fixture.activeCouncilId());
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private long reportFileCount() throws Exception {
        try (Stream<Path> files = Files.list(reportRoot)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static AuthenticatedCall call(
            String username,
            ThrowingOperation operation,
            RoleCode... roles) {
        return new AuthenticatedCall(username, Set.copyOf(Arrays.asList(roles)), operation);
    }

    private static List<Outcome> race(AuthenticatedCall... calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.length);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (AuthenticatedCall call : calls) {
                futures.add(executor.submit(() -> {
                    authenticate(call.username(), call.roles().toArray(RoleCode[]::new));
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        call.operation().run();
                        return new Outcome(true, null);
                    } catch (Throwable failure) {
                        return new Outcome(false, failure);
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

    private static void authenticate(String username, RoleCode... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "unused", authorities));
    }

    private record AuthenticatedCall(
            String username,
            Set<RoleCode> roles,
            ThrowingOperation operation) {
    }

    private record Outcome(boolean success, Throwable failure) {
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock reviewConcurrencyClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
