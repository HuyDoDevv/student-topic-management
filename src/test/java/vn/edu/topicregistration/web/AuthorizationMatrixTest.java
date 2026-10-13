package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.MySqlTestSupport;

/** T061: direct GET/POST authorization matrix backed by persisted roles and scope. */
@SpringBootTest
@ActiveProfiles("local")
@Import(AuthorizationMatrixTest.FixedClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthorizationMatrixTest {
    private static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");
    private static final String UNUSED_HASH = "unusable-authorization-matrix-hash";
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    WebApplicationContext context;

    @Autowired
    DataSource dataSource;

    MockMvc mvc;
    JdbcTemplate jdbc;
    FixtureSupport fixtures;
    FixtureSupport.OrganizationScope ownScope;
    FixtureSupport.OrganizationScope otherScope;
    FixtureSupport.IdentityFixture dean;
    FixtureSupport.IdentityFixture lecturer;
    FixtureSupport.IdentityFixture leader;
    FixtureSupport.IdentityFixture member;
    FixtureSupport.IdentityFixture outsider;
    FixtureSupport.IdentityFixture unrelated;
    FixtureSupport.IdentityFixture admin;
    long periodId;
    long otherPeriodId;
    long topicId;
    long groupId;
    long registrationId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetAndSeedAuthorizationFixture() {
        DATABASE.resetAndMigrate();
        jdbc = new JdbcTemplate(dataSource);
        fixtures = new FixtureSupport(
                dataSource, Clock.fixed(NOW, TimeConfig.BUSINESS_ZONE));
        ownScope = fixtures.createOrganization("Khoa CNTT auth");
        otherScope = fixtures.createOrganization("Khoa Xây dựng auth");
        dean = identity(
                "auth-dean", "Trưởng khoa auth", ownScope,
                Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        lecturer = identity(
                "auth-lecturer", "Giảng viên auth", ownScope,
                Set.of(RoleCode.LECTURER));
        leader = identity(
                "auth-student-leader", "Sinh viên trưởng nhóm", ownScope,
                Set.of(RoleCode.STUDENT));
        member = identity(
                "auth-student-member", "Sinh viên thành viên", ownScope,
                Set.of(RoleCode.STUDENT));
        outsider = identity(
                "auth-student-third", "Sinh viên thứ ba", ownScope,
                Set.of(RoleCode.STUDENT));
        unrelated = identity(
                "auth-student-outsider", "Sinh viên ngoài nhóm", ownScope,
                Set.of(RoleCode.STUDENT));
        admin = identity(
                "auth-admin", "Quản trị auth", null, Set.of(RoleCode.ADMIN));
        identity(
                "auth-other-dean", "Trưởng khoa khác", otherScope,
                Set.of(RoleCode.DEAN, RoleCode.LECTURER));

        periodId = period(ownScope.facultyId(), "Đợt khoa CNTT auth");
        otherPeriodId = period(otherScope.facultyId(), "Đợt khoa Xây dựng auth");
        topicId = topic(periodId, lecturer.accountId());
        groupId = completeGroup(periodId);
        registrationId = registration(periodId, groupId, topicId);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticatedAndMissingCsrfRequestsAreRejectedBeforeMutation() throws Exception {
        mvc.perform(get("/periods"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/registrations/{id}/withdraw", registrationId)
                        .param("version", "0")
                        .with(actor(leader, "STUDENT")))
                .andExpect(status().isForbidden());
        assertTrue("PENDING".equals(statusOfRegistration()));
    }

    @Test
    void adminCanProvisionAndModerateButCannotPerformAcademicWork() throws Exception {
        UserRequestPostProcessor actor = actor(admin, "ADMIN");
        mvc.perform(get("/admin/accounts/students").with(actor))
                .andExpect(status().isOk());
        mvc.perform(get("/dashboard").with(actor))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Đợt khoa CNTT auth"))))
                .andExpect(content().string(not(containsString("Đề tài phân quyền"))));

        for (String path : new String[] {
                "/periods", "/topics", "/groups", "/group-invitations",
                "/faculty/boards", "/faculty/results", "/lecturer/scoring",
                "/student/results", "/faculty/direct-grading"
        }) {
            mvc.perform(get(path).with(actor(admin, "ADMIN")))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(post("/periods")
                        .param("name", "Không được tạo")
                        .param("topicType", "COURSE")
                        .param("lecturerStart", "2026-09-15")
                        .param("lecturerEnd", "2026-09-16")
                        .param("studentStart", "2026-09-17")
                        .param("studentEnd", "2026-09-18")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/periods/{id}/groups", periodId)
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/topics/{id}/approve", topicId)
                        .param("version", "0")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/registrations/{id}/accept", registrationId)
                        .param("version", "0")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        assertTrue("PENDING".equals(statusOfRegistration()));

        mvc.perform(post("/admin/moderation/topics/{id}/hide", topicId)
                        .param("reason", "Nội dung vi phạm")
                        .param("version", "0")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isSeeOther());
        assertNotNull(jdbc.queryForObject(
                "SELECT hidden_at FROM topics WHERE id = ?", Timestamp.class, topicId));
        assertFalse(jdbc.queryForObject(
                "SELECT enabled FROM user_accounts WHERE id = ?",
                Boolean.class,
                lecturer.accountId()));
    }

    @Test
    void rolesCannotCrossAdminDeanLecturerAndStudentBoundaries() throws Exception {
        assertForbiddenGet(dean, new String[] {"LECTURER", "DEAN"},
                "/topics", "/groups", "/group-invitations", "/admin/accounts/students");
        assertForbiddenGet(lecturer, new String[] {"LECTURER"},
                "/periods", "/groups", "/group-invitations", "/admin/accounts/students");
        assertForbiddenGet(leader, new String[] {"STUDENT"},
                "/periods", "/topics", "/faculty/boards", "/faculty/results",
                "/lecturer/scoring", "/faculty/direct-grading", "/admin/accounts/students");

        mvc.perform(get("/periods").with(actor(dean, "LECTURER", "DEAN")))
                .andExpect(status().isOk());
        mvc.perform(get("/topics").with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk());
        mvc.perform(get("/groups/{id}", groupId).with(actor(leader, "STUDENT")))
                .andExpect(status().isOk());

        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "23119991")
                        .param("fullName", "Không được tạo")
                        .param("className", "23110CL9")
                        .param("cohortCode", "K2023")
                        .param("password", "temporary-password")
                        .with(actor(dean, "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        assertTrue(count("""
                SELECT COUNT(*) FROM user_accounts WHERE username = '23119991'
                """) == 0L);
    }

    @Test
    void persistedScopeAndRelationshipsAreRecheckedAfterTheyChange() throws Exception {
        mvc.perform(get("/periods/{id}", otherPeriodId)
                        .with(actor(dean, "LECTURER", "DEAN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/topics/{id}", topicId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk());

        jdbc.update(
                "UPDATE user_accounts SET faculty_id = ? WHERE id = ?",
                otherScope.facultyId(),
                lecturer.accountId());
        mvc.perform(get("/topics/{id}", topicId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isNotFound());

        jdbc.update(
                "UPDATE student_groups SET leader_student_id = ?, version = version + 1 WHERE id = ?",
                member.accountId(),
                groupId);
        mvc.perform(post("/groups/{id}/registrations", groupId)
                        .param("topicId", Long.toString(topicId))
                        .param("version", "1")
                        .with(actor(leader, "STUDENT")).with(csrf()))
                .andExpect(status().isForbidden());

        jdbc.update(
                "UPDATE user_accounts SET faculty_id = ? WHERE id = ?",
                otherScope.facultyId(),
                member.accountId());
        mvc.perform(get("/groups/{id}", groupId)
                        .with(actor(member, "STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/registrations/{id}", registrationId)
                        .with(actor(unrelated, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordsDoNotAppearInResponsesOrCapturedLogs(CapturedOutput output) throws Exception {
        String secret = "T061-secret-must-never-be-logged";
        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "23119992")
                        .param("fullName", "Sinh viên được tạo")
                        .param("className", "23110CL9")
                        .param("cohortCode", "K2023")
                        .param("password", secret)
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(content().string(not(containsString(secret))));

        String storedHash = jdbc.queryForObject("""
                SELECT password_hash FROM user_accounts WHERE username = '23119992'
                """, String.class);
        assertNotNull(storedHash);
        assertFalse(storedHash.contains(secret));
        assertFalse(output.getAll().contains(secret));
        assertFalse(output.getAll().contains(storedHash));
    }

    private void assertForbiddenGet(
            FixtureSupport.IdentityFixture identity,
            String[] roles,
            String... paths) throws Exception {
        for (String path : paths) {
            mvc.perform(get(path).with(actor(identity, roles)))
                    .andExpect(status().isForbidden());
        }
    }

    private FixtureSupport.IdentityFixture identity(
            String username,
            String fullName,
            FixtureSupport.OrganizationScope scope,
            Set<RoleCode> roles) {
        return fixtures.createIdentity(username, fullName, UNUSED_HASH, true, scope, roles);
    }

    private long period(long facultyId, String name) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date, version)
                VALUES (?, ?, 'COURSE', ?, ?, ?, ?, NULL, NULL, 0)
                """, facultyId, name,
                Timestamp.from(NOW.minusSeconds(14400)),
                Timestamp.from(NOW.minusSeconds(10800)),
                Timestamp.from(NOW.minusSeconds(7200)),
                Timestamp.from(NOW.plusSeconds(14400)));
        return jdbc.queryForObject("SELECT MAX(id) FROM registration_periods", Long.class);
    }

    private long topic(long targetPeriodId, long authorId) {
        long facultyId = jdbc.queryForObject(
                "SELECT faculty_id FROM registration_periods WHERE id = ?",
                Long.class, targetPeriodId);
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, ?)",
                facultyId, "Bộ môn phân quyền " + targetPeriodId);
        long departmentId = jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = ?",
                Long.class, facultyId, "Bộ môn phân quyền " + targetPeriodId);
        jdbc.update("UPDATE registration_periods SET department_id = ? WHERE id = ?",
                departmentId, targetPeriodId);
        jdbc.update("UPDATE user_accounts SET department_id = ? WHERE id = ?",
                departmentId, authorId);
        jdbc.update("""
                INSERT INTO topics(
                    period_id, author_id, department_id, title, description, objectives,
                    student_requirements, expected_results, status, version)
                VALUES (?, ?, ?, 'Đề tài phân quyền', 'Mô tả', 'Mục tiêu',
                        'Yêu cầu', 'Kết quả', 'PUBLISHED', 0)
                """, targetPeriodId, authorId, departmentId);
        return jdbc.queryForObject("SELECT MAX(id) FROM topics", Long.class);
    }

    private long completeGroup(long targetPeriodId) {
        jdbc.update("""
                INSERT INTO student_groups(period_id, leader_student_id, version)
                VALUES (?, ?, 0)
                """, targetPeriodId, leader.accountId());
        long result = jdbc.queryForObject("SELECT MAX(id) FROM student_groups", Long.class);
        addMember(result, targetPeriodId, member.accountId(), 2);
        addMember(result, targetPeriodId, outsider.accountId(), 3);
        return result;
    }

    private void addMember(long targetGroupId, long targetPeriodId, long studentId, int slot) {
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, ?)
                """, targetGroupId, targetPeriodId, studentId, slot);
    }

    private long registration(long targetPeriodId, long targetGroupId, long targetTopicId) {
        jdbc.update("""
                INSERT INTO registrations(
                    period_id, group_id, topic_id, status, submitted_at, version)
                VALUES (?, ?, ?, 'PENDING', ?, 0)
                """, targetPeriodId, targetGroupId, targetTopicId, Timestamp.from(NOW));
        return jdbc.queryForObject("SELECT MAX(id) FROM registrations", Long.class);
    }

    private String statusOfRegistration() {
        return jdbc.queryForObject(
                "SELECT status FROM registrations WHERE id = ?",
                String.class,
                registrationId);
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments);
        return result == null ? 0L : result;
    }

    private static UserRequestPostProcessor actor(
            FixtureSupport.IdentityFixture identity,
            String... roles) {
        return user(identity.username()).roles(roles);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock authorizationMatrixClock() {
            return Clock.fixed(NOW, TimeConfig.BUSINESS_ZONE);
        }
    }
}
