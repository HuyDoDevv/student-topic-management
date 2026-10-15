package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
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

import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.ScoreCommand;
import vn.edu.topicregistration.util.ProjectTime;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@ActiveProfiles("local")
@Import(TlcnWorkflowIT.FixedClockConfiguration.class)
class TlcnWorkflowIT {
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @TempDir static Path reportRoot;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProjectTime time;
    @Autowired ReportFileRepository reportFiles;
    @Autowired DirectGradingService directGrading;
    @Autowired ScoringService scoring;
    @Autowired ResultService results;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeed() throws Exception {
        DATABASE.resetAndMigrate();
        seed();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submittedReviewerThenSecondGraderThenDeanPublishesTwoSourceMean() {
        long registrationId = id("SELECT r.id FROM registrations r JOIN topics t "
                + "ON t.id = r.topic_id WHERE t.title = ?",
                "[DEMO] Đối soát điểm tiểu luận chuyên ngành");
        assertEquals(1L, id("SELECT COUNT(*) FROM reviewer_reports rr JOIN "
                + "reviewer_assignments ra ON ra.id = rr.assignment_id "
                + "WHERE ra.registration_id = ?", registrationId));
        List<Long> students = jdbc.queryForList(
                "SELECT student_id FROM registration_members WHERE registration_id = ? ORDER BY slot",
                Long.class, registrationId);
        assertEquals(3, students.size());

        authenticate("dean.demo", "DEAN");
        long graderId = id("SELECT id FROM user_accounts WHERE username = 'lecturer4.demo'");
        long registrationVersion = id("SELECT version FROM registrations WHERE id = ?", registrationId);
        var assignment = directGrading.assign(new DirectGradingService.AssignCommand(
                registrationId, graderId, registrationVersion));
        long councilId = assignment.id();
        assertEquals("DIRECT", jdbc.queryForObject(
                "SELECT grading_mode FROM councils WHERE id = ?", String.class, councilId));

        authenticate("lecturer4.demo", "LECTURER");
        for (Long studentId : students) {
            scoring.submit(councilId, registrationId, studentId,
                    new ScoreCommand(new BigDecimal("8.50"), "Đạt yêu cầu", null));
        }

        authenticate("dean.demo", "DEAN");
        long councilVersion = id("SELECT version FROM councils WHERE id = ?", councilId);
        results.complete(councilId, councilVersion);
        assertEquals(3L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ? "
                + "AND status = 'READY' AND final_score = 8.38", councilId));
        councilVersion = id("SELECT version FROM councils WHERE id = ?", councilId);
        results.publish(councilId, councilVersion);
        assertEquals(3L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ? "
                + "AND status = 'PUBLISHED'", councilId));
        assertEquals(3L, id("SELECT COUNT(*) FROM reviewer_evaluations re JOIN "
                + "reviewer_assignments ra ON ra.id = re.assignment_id "
                + "WHERE ra.registration_id = ? AND re.score = 8.25", registrationId));
    }

    @Test
    void demoFixtureIsIdempotentAndReviewerCannotBeSecondGrader() throws Exception {
        seed();
        long registrationId = id("SELECT r.id FROM registrations r JOIN topics t "
                + "ON t.id = r.topic_id WHERE t.title = ?",
                "[DEMO] Đối soát điểm tiểu luận chuyên ngành");
        assertEquals(1L, id("SELECT COUNT(*) FROM reviewer_assignments "
                + "WHERE registration_id = ?", registrationId));
        long reviewerId = id("SELECT lecturer_id FROM reviewer_assignments "
                + "WHERE registration_id = ?", registrationId);
        authenticate("dean.demo", "DEAN");
        long version = id("SELECT version FROM registrations WHERE id = ?", registrationId);
        assertThrows(RuntimeException.class, () -> directGrading.assign(
                new DirectGradingService.AssignCommand(registrationId, reviewerId, version)));
        assertEquals(0L, id("SELECT COUNT(*) FROM council_topics "
                + "WHERE registration_id = ?", registrationId));
    }

    @Test
    void directScorePostIsRejectedWhenReviewerReportIsMissing() {
        long registrationId = id("SELECT r.id FROM registrations r JOIN topics t "
                + "ON t.id = r.topic_id WHERE t.title = ?",
                "[DEMO] Đối soát điểm tiểu luận chuyên ngành");
        authenticate("dean.demo", "DEAN");
        long graderId = id("SELECT id FROM user_accounts WHERE username = 'lecturer4.demo'");
        long version = id("SELECT version FROM registrations WHERE id = ?", registrationId);
        long councilId = directGrading.assign(new DirectGradingService.AssignCommand(
                registrationId, graderId, version)).id();
        jdbc.update("DELETE FROM reviewer_reports WHERE assignment_id IN "
                + "(SELECT id FROM reviewer_assignments WHERE registration_id = ?)",
                registrationId);
        long studentId = id("SELECT student_id FROM registration_members "
                + "WHERE registration_id = ? ORDER BY slot LIMIT 1", registrationId);
        authenticate("lecturer4.demo", "LECTURER");

        assertThrows(RuntimeException.class, () -> scoring.submit(
                councilId, registrationId, studentId,
                new ScoreCommand(new BigDecimal("8.50"), "Đạt yêu cầu", null)));
        assertEquals(0L, id("SELECT COUNT(*) FROM evaluations WHERE council_id = ?", councilId));
    }

    private void seed() throws Exception {
        new LocalDemoSeeder(jdbc, passwordEncoder, time,
                new DemoSeedProperties(true, "temporary-test-password"), reportFiles)
                .run(new DefaultApplicationArguments());
    }

    private long id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private static void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "unused",
                        Set.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary Clock tlcnWorkflowClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
