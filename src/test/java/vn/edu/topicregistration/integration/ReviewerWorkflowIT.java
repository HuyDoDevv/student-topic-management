package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.AssignCommand;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReportUpload;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ScoreRow;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionCommand;

@SpringBootTest
@ActiveProfiles("local")
@Import(ReviewerWorkflowIT.FixedClockConfiguration.class)
class ReviewerWorkflowIT {
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();
    private static final byte[] PDF = "%PDF-1.7\nT010 integration"
            .getBytes(StandardCharsets.US_ASCII);

    @TempDir static Path reportRoot;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewerAssignmentService reviewers;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    private long registrationId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        seed();
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deanAssignsAndReviewerSubmitsPdfWithEveryStudentScore() {
        long reviewerId = account("lecturer2.demo");
        long version = jdbc.queryForObject(
                "SELECT version FROM registrations WHERE id = ?", Long.class, registrationId);
        authenticate("dean.demo", RoleCode.DEAN);
        long assignmentId = reviewers.assign(new AssignCommand(registrationId, reviewerId, version));
        assertEquals("ASSIGNED", assignmentStatus(assignmentId));

        List<Long> students = jdbc.queryForList(
                "SELECT student_id FROM registration_members WHERE registration_id = ? ORDER BY slot",
                Long.class, registrationId);
        authenticate("lecturer1.demo", RoleCode.LECTURER);
        assertThrows(AccessDeniedException.class, () -> reviewers.submit(
                assignmentId, submission(0L, students)));
        assertEquals(0L, count("SELECT COUNT(*) FROM reviewer_reports WHERE assignment_id = ?",
                assignmentId));

        authenticate("lecturer2.demo", RoleCode.LECTURER);
        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> reviewers.submit(assignmentId,
                        new SubmissionCommand(0L, pdf(), List.of())));
        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> reviewers.submit(assignmentId, new SubmissionCommand(0L,
                        new ReportUpload("wrong.pdf", "application/pdf", 5,
                                () -> new ByteArrayInputStream("WRONG".getBytes(StandardCharsets.US_ASCII))),
                        students.stream().map(id -> new ScoreRow(id,
                                new BigDecimal("8.25"), "Đạt yêu cầu")).toList())));
        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> reviewers.submit(assignmentId, new SubmissionCommand(0L, pdf(),
                        List.of(new ScoreRow(students.getFirst(), new BigDecimal("8.25"), "A"),
                                new ScoreRow(students.getFirst(), new BigDecimal("8.25"), "B"),
                                new ScoreRow(students.getLast(), new BigDecimal("8.25"), "C")))));
        assertEquals(0L, count("SELECT COUNT(*) FROM reviewer_evaluations WHERE assignment_id = ?",
                assignmentId));

        reviewers.submit(assignmentId, submission(0L, students));
        assertEquals("SUBMITTED", assignmentStatus(assignmentId));
        assertEquals(1L, count("SELECT COUNT(*) FROM reviewer_reports WHERE assignment_id = ?",
                assignmentId));
        assertEquals(students.size(), count(
                "SELECT COUNT(*) FROM reviewer_evaluations WHERE assignment_id = ? AND status = 'SUBMITTED'",
                assignmentId));
        authenticate("dean.demo", RoleCode.DEAN);
        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> reviewers.replace(assignmentId,
                        new AssignCommand(registrationId, account("lecturer1.demo"), 1L)));
    }

    @Test
    void replacingBeforeSubmissionKeepsOldAssignmentAsAuditRow() {
        authenticate("dean.demo", RoleCode.DEAN);
        long oldId = reviewers.assign(new AssignCommand(registrationId,
                account("lecturer2.demo"), 0L));
        long newId = reviewers.replace(oldId, new AssignCommand(registrationId,
                account("lecturer3.demo"), 0L));

        assertEquals("REPLACED", assignmentStatus(oldId));
        assertEquals("ASSIGNED", assignmentStatus(newId));
        assertEquals(account("dean.demo"), id(
                "SELECT replaced_by FROM reviewer_assignments WHERE id = ?", oldId));
        assertEquals(2L, count(
                "SELECT COUNT(*) FROM reviewer_assignments WHERE registration_id = ?",
                registrationId));
        authenticate("lecturer2.demo", RoleCode.LECTURER);
        assertThrows(AccessDeniedException.class,
                () -> reviewers.submissionWorkspace(oldId));
    }

    @Test
    void reviewerPagesRenderThroughMvcAndThymeleaf() throws Exception {
        mvc.perform(get("/faculty/reviewers").with(user("dean.demo").roles("DEAN")))
                .andExpect(status().isOk());
        authenticate("dean.demo", RoleCode.DEAN);
        long assignmentId = reviewers.assign(new AssignCommand(registrationId,
                account("lecturer2.demo"), 0L));
        mvc.perform(get("/lecturer/reviewers").with(user("lecturer2.demo").roles("LECTURER")))
                .andExpect(status().isOk());
        mvc.perform(get("/lecturer/reviewers/{id}", assignmentId)
                        .with(user("lecturer2.demo").roles("LECTURER")))
                .andExpect(status().isOk());
    }

    @Test
    void expiredDeadlineAndInvalidScoreLeaveNoReportOrEvaluation() {
        authenticate("dean.demo", RoleCode.DEAN);
        long assignmentId = reviewers.assign(new AssignCommand(registrationId,
                account("lecturer2.demo"), 0L));
        List<Long> students = jdbc.queryForList(
                "SELECT student_id FROM registration_members WHERE registration_id = ? ORDER BY slot",
                Long.class, registrationId);
        authenticate("lecturer2.demo", RoleCode.LECTURER);
        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> reviewers.submit(assignmentId, new SubmissionCommand(0L, pdf(),
                        students.stream().map(id -> new ScoreRow(id,
                                new BigDecimal("8.30"), "Đạt yêu cầu")).toList())));
        jdbc.update("UPDATE reviewer_assignments SET deadline_date = '2026-10-14' WHERE id = ?",
                assignmentId);
        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> reviewers.submit(assignmentId, submission(0L, students)));
        assertEquals(0L, count("SELECT COUNT(*) FROM reviewer_reports WHERE assignment_id = ?",
                assignmentId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM reviewer_evaluations WHERE assignment_id = ?",
                assignmentId));
    }

    private static SubmissionCommand submission(long version, List<Long> students) {
        return new SubmissionCommand(version, pdf(), students.stream()
                .map(id -> new ScoreRow(id, new BigDecimal("8.25"), "Đạt yêu cầu"))
                .toList());
    }

    private static ReportUpload pdf() {
        return new ReportUpload("review.pdf", "application/pdf", PDF.length,
                () -> new ByteArrayInputStream(PDF));
    }

    private String assignmentStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM reviewer_assignments WHERE id = ?",
                String.class, id);
    }

    private long account(String username) {
        return jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = ?",
                Long.class, username);
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private void seed() {
        jdbc.update("INSERT INTO faculties(name) VALUES ('T010 faculty')");
        long faculty = id("SELECT id FROM faculties WHERE name = 'T010 faculty'");
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'T010 department')",
                faculty);
        long department = id("SELECT id FROM departments WHERE name = 'T010 department'");
        for (String username : List.of("dean.demo", "lecturer1.demo", "lecturer2.demo",
                "lecturer3.demo", "student1.demo", "student2.demo", "student3.demo")) {
            jdbc.update("""
                    INSERT INTO user_accounts(
                        full_name, username, password_hash, faculty_id, department_id)
                    VALUES (?, ?, 'NOT_A_LOGIN_HASH', ?, ?)
                    """, username, username, faculty, department);
            String role = username.startsWith("student") ? "STUDENT"
                    : username.startsWith("dean") ? "DEAN" : "LECTURER";
            jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, ?)",
                    account(username), role);
        }
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, department_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date)
                VALUES (?, ?, 'T010 period', 'KLTN',
                    '2026-08-01', '2026-08-02', '2026-08-03', '2026-08-10',
                    '2026-10-15', '2026-10-20')
                """, faculty, department);
        long period = id("SELECT id FROM registration_periods WHERE name = 'T010 period'");
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id, title,
                    description, objectives, student_requirements, expected_results, status)
                VALUES (?, ?, ?, 'T010 topic', 'Description', 'Objectives',
                    'Requirements', 'Expected', 'PUBLISHED')
                """, period, department, account("lecturer1.demo"));
        long topic = id("SELECT id FROM topics WHERE title = 'T010 topic'");
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)",
                topic, account("lecturer1.demo"));
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id) VALUES (?, ?)",
                period, account("student1.demo"));
        long group = id("SELECT id FROM student_groups WHERE period_id = ?", period);
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, 2), (?, ?, ?, 3)
                """, group, period, account("student2.demo"),
                group, period, account("student3.demo"));
        jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status,
                    decision_actor_id, decided_at)
                VALUES (?, ?, ?, 'ACCEPTED', ?, '2026-08-11 00:00:00')
                """, period, group, topic, account("dean.demo"));
        registrationId = id("SELECT id FROM registrations WHERE topic_id = ?", topic);
    }

    private long id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private static void authenticate(String username, RoleCode... roles) {
        Set<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "unused", authorities));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary Clock reviewerWorkflowClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
