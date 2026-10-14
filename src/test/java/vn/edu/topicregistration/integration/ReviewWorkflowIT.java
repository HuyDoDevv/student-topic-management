package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.integration.ReviewFixtureSupport.ReviewFixture;
import vn.edu.topicregistration.service.ReportService;
import vn.edu.topicregistration.service.ReportService.ReportUpload;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.ScoreCommand;

/** T008: real JPA/MySQL journey for reports, scoring, publication and snapshot privacy. */
@SpringBootTest
@ActiveProfiles("local")
@Import(ReviewWorkflowIT.FixedClockConfiguration.class)
class ReviewWorkflowIT {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final byte[] PDF = "%PDF-1.7\nT008 integration report"
            .getBytes(StandardCharsets.US_ASCII);
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

    private ReviewFixtureSupport fixtureSupport;
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
        fixtureSupport = new ReviewFixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC), reportRoot);
        fixture = fixtureSupport.seed(FIXTURE_PASSWORD);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void demoSeedAndServicesCompleteTheReviewJourneyWithoutLeakingOtherResults()
            throws Exception {
        assertEquals(14, count("SELECT COUNT(*) FROM user_accounts"));
        assertEquals(6, count("SELECT COUNT(*) FROM registration_periods"));
        assertEquals(2, count("SELECT COUNT(*) FROM councils"));
        assertEquals(3, count("SELECT COUNT(*) FROM project_reports"));
        assertEquals(27, count("SELECT COUNT(*) FROM evaluations"));
        assertEquals(3, count("SELECT COUNT(*) FROM final_results"));
        long filesBeforeSecondSeed = reportFileCount();

        fixtureSupport.seed(FIXTURE_PASSWORD);

        assertEquals(14, count("SELECT COUNT(*) FROM user_accounts"));
        assertEquals(2, count("SELECT COUNT(*) FROM councils"));
        assertEquals(3, count("SELECT COUNT(*) FROM project_reports"));
        assertEquals(27, count("SELECT COUNT(*) FROM evaluations"));
        assertEquals(filesBeforeSecondSeed, reportFileCount(),
                "Idempotent seed must not create orphan report files");

        authenticate("student1.demo", RoleCode.STUDENT);
        assertEquals(List.of(fixture.publishedRegistrationId()),
                results.listForCurrentStudent().stream()
                        .map(ResultService.StudentResultSummaryView::registrationId)
                        .toList());
        assertTrue(reports.workspace(fixture.uploadRegistrationId()).canUpload());
        reports.upload(
                fixture.uploadRegistrationId(),
                null,
                new ReportUpload(
                        "../integration-report.pdf",
                        "application/pdf",
                        PDF.length,
                        () -> new ByteArrayInputStream(PDF)));
        try (var download = reports.download(fixture.uploadRegistrationId()).content()) {
            assertArrayEquals(PDF, download.readAllBytes());
        }
        assertEquals("integration-report.pdf", jdbc.queryForObject("""
                SELECT file_name FROM project_reports
                WHERE registration_id = ? AND replaced_at IS NULL
                """, String.class, fixture.uploadRegistrationId()));

        authenticate("student4.demo", RoleCode.STUDENT);
        ApplicationException hidden = assertThrows(ApplicationException.class,
                () -> results.detailForCurrentStudent(fixture.publishedRegistrationId()));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, hidden.code());

        authenticate("lecturer4.demo", RoleCode.LECTURER);
        ScoringService.ScoreWorkspace scoreWorkspace = scoring.workspace(
                fixture.activeCouncilId(), fixture.activeRegistrationOneId());
        var studentScore = scoreWorkspace.students().getFirst();
        scoring.submit(
                fixture.activeCouncilId(),
                fixture.activeRegistrationOneId(),
                studentScore.studentId(),
                new ScoreCommand(
                        new BigDecimal("9.25"),
                        "Integration score update",
                        studentScore.evaluation().version()));

        authenticate("lecturer1.demo", RoleCode.LECTURER);
        ResultService.BoardResultView completed = results.complete(
                fixture.activeCouncilId(), 0L);
        assertEquals(CouncilStatus.COMPLETED, completed.status());
        assertEquals(6, completed.resultCount());

        authenticate("dean.demo", RoleCode.DEAN);
        ResultService.BoardResultView published = results.publish(
                fixture.activeCouncilId(), completed.version());
        assertEquals(CouncilStatus.PUBLISHED, published.status());
        assertEquals(6, count("""
                SELECT COUNT(*) FROM final_results
                WHERE council_id = ? AND status = 'PUBLISHED'
                """, fixture.activeCouncilId()));

        authenticate("student1.demo", RoleCode.STUDENT);
        ResultService.StudentResultSummaryView activeResult = results
                .listForCurrentStudent()
                .stream()
                .filter(item -> item.registrationId() == fixture.activeRegistrationOneId())
                .findFirst()
                .orElseThrow();
        assertEquals(0, activeResult.finalScore().compareTo(new BigDecimal("8.75")));
        assertEquals(3, results.detailForCurrentStudent(
                fixture.activeRegistrationOneId()).evaluations().size());

        authenticate("student2.demo", RoleCode.STUDENT);
        ResultService.StudentResultDetailView sameGroupResult = results
                .detailForCurrentStudent(fixture.activeRegistrationOneId());
        assertEquals(0, sameGroupResult.result().finalScore()
                .compareTo(new BigDecimal("8.50")));
        assertTrue(sameGroupResult.evaluations().stream()
                .noneMatch(item -> "Integration score update".equals(item.feedback())),
                "A snapshot member must not receive a teammate's individual feedback");

        authenticate("student4.demo", RoleCode.STUDENT);
        assertThrows(ApplicationException.class,
                () -> results.detailForCurrentStudent(fixture.activeRegistrationOneId()));
    }

    private long reportFileCount() throws Exception {
        try (Stream<Path> files = Files.list(reportRoot)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static void authenticate(String username, RoleCode... roles) {
        Set<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(java.util.stream.Collectors.toSet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "unused", authorities));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock reviewWorkflowClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
