package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.CouncilService;
import vn.edu.topicregistration.service.CouncilService.CreateCouncilCommand;
import vn.edu.topicregistration.service.CouncilService.UpdateMembersCommand;
import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.AssignCommand;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReportUpload;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ScoreRow;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionCommand;
import vn.edu.topicregistration.util.ProjectTime;

@SpringBootTest
@ActiveProfiles("local")
@Import(KltnWorkflowIT.FixedClockConfiguration.class)
class KltnWorkflowIT {
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();
    private static final byte[] PDF = "%PDF-1.7\nT012 reviewer report"
            .getBytes(StandardCharsets.US_ASCII);

    @TempDir static Path reportRoot;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProjectTime time;
    @Autowired ReportFileRepository reportFiles;
    @Autowired ReviewerAssignmentService reviewers;
    @Autowired ResultService results;
    @Autowired CouncilService councils;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeed() throws Exception {
        DATABASE.resetAndMigrate();
        new LocalDemoSeeder(jdbc, passwordEncoder, time,
                new DemoSeedProperties(true, "temporary-test-password"), reportFiles)
                .run(new DefaultApplicationArguments());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void multipleTopicsUseTheirOwnReviewerAndAllCouncilScores() {
        long councilId = id("SELECT id FROM councils WHERE name = ?",
                "[DEMO] Hội đồng KLTN - Đang chấm");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ? "
                        + "ORDER BY registration_id", Long.class, councilId);
        assertEquals(2, registrations.size());
        authenticate("lecturer1.demo", "LECTURER");
        long version = id("SELECT version FROM councils WHERE id = ?", councilId);
        assertThrows(ApplicationException.class, () -> results.complete(councilId, version));
        assertEquals(0L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ?", councilId));

        for (Long registrationId : registrations) {
            submitReviewer(registrationId);
        }
        authenticate("lecturer1.demo", "LECTURER");
        results.complete(councilId, version);
        assertEquals(3L, id("SELECT COUNT(*) FROM final_results WHERE registration_id = ? "
                + "AND status = 'READY' AND final_score = 8.13", registrations.getFirst()));
        assertEquals(3L, id("SELECT COUNT(*) FROM final_results WHERE registration_id = ? "
                + "AND status = 'READY' AND final_score = 7.75", registrations.getLast()));
        assertEquals(6L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ?", councilId));

        authenticate("dean.demo", "DEAN");
        long publishedVersion = id("SELECT version FROM councils WHERE id = ?", councilId);
        results.publish(councilId, publishedVersion);
        assertEquals(6L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ? "
                + "AND status = 'PUBLISHED'", councilId));
        assertEquals(6L, id("SELECT COUNT(*) FROM reviewer_evaluations re JOIN "
                + "reviewer_assignments ra ON ra.id = re.assignment_id "
                + "WHERE ra.registration_id IN (?, ?) AND re.score = 7.00",
                registrations.getFirst(), registrations.getLast()));
    }

    @Test
    void fullFiveMemberCouncilCanReplaceChairAndChangeRolesWithoutLosingMembership() {
        authenticate("dean.demo", "DEAN");
        long periodId = id("SELECT id FROM registration_periods WHERE name = ?",
                "[DEMO] Khóa luận tốt nghiệp - Đang chấm");
        long first = lecturer("lecturer1.demo");
        long second = lecturer("lecturer2.demo");
        long third = lecturer("lecturer3.demo");
        long fourth = lecturer("lecturer4.demo");
        long fifth = lecturer("lecturer5.demo");
        jdbc.update("INSERT INTO user_accounts(full_name, enabled, username, password_hash, "
                + "faculty_id, department_id) SELECT 'Giảng viên Demo 6', TRUE, "
                + "'lecturer6.test', password_hash, faculty_id, department_id "
                + "FROM user_accounts WHERE id = ?", fifth);
        long sixth = lecturer("lecturer6.test");
        jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'LECTURER')", sixth);

        var created = councils.create(new CreateCouncilCommand(periodId,
                "[TEST] Hội đồng sửa thành viên", first, second,
                List.of(third, fourth, fifth)));
        var changed = councils.updateMembers(created.id(), new UpdateMembersCommand(
                sixth, fourth, List.of(second, third, fifth), created.version()));

        assertEquals(5L, id("SELECT COUNT(*) FROM council_members WHERE council_id = ?",
                created.id()));
        assertEquals(0L, id("SELECT COUNT(*) FROM council_members "
                + "WHERE council_id = ? AND lecturer_id = ?", created.id(), first));
        assertEquals(sixth, id("SELECT chair_id FROM councils WHERE id = ?", created.id()));
        assertEquals(fourth, id("SELECT secretary_id FROM councils WHERE id = ?", created.id()));
        assertEquals("Chủ tịch", councils.detailForCurrentDean(created.id()).members().stream()
                .filter(member -> member.id().equals(sixth)).findFirst().orElseThrow().role());
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                () -> councils.updateMembers(created.id(), new UpdateMembersCommand(
                        sixth, fourth, List.of(second, third, fifth), created.version())));
        assertEquals(5L, id("SELECT COUNT(*) FROM council_members WHERE council_id = ?",
                created.id()));
        assertEquals(true, changed.version() > created.version());
    }

    @Test
    void oneRegistrationWithoutReviewerScorePreventsWholeCouncilCompletion() {
        long councilId = id("SELECT id FROM councils WHERE name = ?",
                "[DEMO] Hội đồng KLTN - Đang chấm");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ? "
                        + "ORDER BY registration_id", Long.class, councilId);
        submitReviewer(registrations.getFirst());
        authenticate("lecturer1.demo", "LECTURER");
        long version = id("SELECT version FROM councils WHERE id = ?", councilId);

        assertThrows(ApplicationException.class, () -> results.complete(councilId, version));
        assertEquals(0L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ?", councilId));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM councils WHERE id = ?", String.class, councilId));
    }

    @Test
    void reviewerAndThreeMembersScoringSevenProduceSevenExactly() {
        long councilId = id("SELECT id FROM councils WHERE name = ?",
                "[DEMO] Hội đồng KLTN - Đang chấm");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ? "
                        + "ORDER BY registration_id", Long.class, councilId);
        jdbc.update("UPDATE evaluations SET score = 7.00 WHERE council_id = ?", councilId);
        for (Long registrationId : registrations) {
            submitReviewer(registrationId);
        }
        authenticate("lecturer1.demo", "LECTURER");
        long version = id("SELECT version FROM councils WHERE id = ?", councilId);

        results.complete(councilId, version);

        assertEquals(6L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ? "
                + "AND final_score = 7.00 AND status = 'READY'", councilId));
    }

    @Test
    void oneMissingCouncilSubmissionPreventsWholeCouncilCompletion() {
        long councilId = id("SELECT id FROM councils WHERE name = ?",
                "[DEMO] Hội đồng KLTN - Đang chấm");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ? "
                        + "ORDER BY registration_id", Long.class, councilId);
        for (Long registrationId : registrations) {
            submitReviewer(registrationId);
        }
        long evaluationId = id("SELECT id FROM evaluations WHERE council_id = ? "
                + "ORDER BY id LIMIT 1", councilId);
        jdbc.update("UPDATE evaluations SET status = 'DRAFT', submitted_at = NULL WHERE id = ?",
                evaluationId);
        authenticate("lecturer1.demo", "LECTURER");
        long version = id("SELECT version FROM councils WHERE id = ?", councilId);

        assertThrows(ApplicationException.class, () -> results.complete(councilId, version));
        assertEquals(0L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ?", councilId));
    }

    @Test
    void removedReviewerReportPreventsPublicationAfterCompletion() {
        long councilId = id("SELECT id FROM councils WHERE name = ?",
                "[DEMO] Hội đồng KLTN - Đang chấm");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ? "
                        + "ORDER BY registration_id", Long.class, councilId);
        for (Long registrationId : registrations) {
            submitReviewer(registrationId);
        }
        authenticate("lecturer1.demo", "LECTURER");
        long version = id("SELECT version FROM councils WHERE id = ?", councilId);
        results.complete(councilId, version);
        jdbc.update("DELETE FROM reviewer_reports WHERE assignment_id IN "
                + "(SELECT id FROM reviewer_assignments WHERE registration_id = ?)",
                registrations.getFirst());
        authenticate("dean.demo", "DEAN");
        long publishedVersion = id("SELECT version FROM councils WHERE id = ?", councilId);

        assertThrows(ApplicationException.class,
                () -> results.publish(councilId, publishedVersion));
        assertEquals(6L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ? "
                + "AND status = 'READY'", councilId));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM councils WHERE id = ?", String.class, councilId));
    }

    @Test
    void disabledCouncilMemberPreventsPublicationAfterCompletion() {
        long councilId = id("SELECT id FROM councils WHERE name = ?",
                "[DEMO] Hội đồng KLTN - Đang chấm");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ? "
                        + "ORDER BY registration_id", Long.class, councilId);
        for (Long registrationId : registrations) {
            submitReviewer(registrationId);
        }
        authenticate("lecturer1.demo", "LECTURER");
        long version = id("SELECT version FROM councils WHERE id = ?", councilId);
        results.complete(councilId, version);
        jdbc.update("UPDATE user_accounts SET enabled = FALSE WHERE username = 'lecturer5.demo'");
        authenticate("dean.demo", "DEAN");
        long publishedVersion = id("SELECT version FROM councils WHERE id = ?", councilId);

        assertThrows(ApplicationException.class,
                () -> results.publish(councilId, publishedVersion));
        assertEquals(6L, id("SELECT COUNT(*) FROM final_results WHERE council_id = ? "
                + "AND status = 'READY'", councilId));
    }

    private void submitReviewer(long registrationId) {
        long reviewerId = id("SELECT id FROM user_accounts WHERE username = 'lecturer3.demo'");
        authenticate("dean.demo", "DEAN");
        long version = id("SELECT version FROM registrations WHERE id = ?", registrationId);
        long assignmentId = reviewers.assign(new AssignCommand(
                registrationId, reviewerId, version));
        List<Long> students = jdbc.queryForList(
                "SELECT student_id FROM registration_members WHERE registration_id = ? "
                        + "ORDER BY slot", Long.class, registrationId);
        authenticate("lecturer3.demo", "LECTURER");
        reviewers.submit(assignmentId, new SubmissionCommand(0L,
                new ReportUpload("review.pdf", "application/pdf", PDF.length,
                        () -> new ByteArrayInputStream(PDF)),
                students.stream().map(studentId -> new ScoreRow(studentId,
                        new BigDecimal("7.00"), "Đạt yêu cầu")).toList()));
    }

    private long id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private long lecturer(String username) {
        return id("SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private static void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "unused",
                        Set.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary Clock kltnWorkflowClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
