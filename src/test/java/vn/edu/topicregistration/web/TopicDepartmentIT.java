package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

/** T005–T006: topic and supervisor Department scope with Faculty-wide workflows. */
@SpringBootTest
@ActiveProfiles("local")
@Import(TopicDepartmentIT.FixedClockConfiguration.class)
class TopicDepartmentIT {
    private static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;

    MockMvc mvc;
    JdbcTemplate jdbc;
    FixtureSupport fixtures;
    FixtureSupport.OrganizationScope faculty;
    FixtureSupport.OrganizationScope otherFaculty;
    FixtureSupport.IdentityFixture lecturer;
    FixtureSupport.IdentityFixture otherLecturer;
    FixtureSupport.IdentityFixture dean;
    FixtureSupport.IdentityFixture outsideDean;
    long departmentId;
    long otherDepartmentId;
    long periodId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        jdbc = new JdbcTemplate(dataSource);
        fixtures = new FixtureSupport(dataSource, Clock.fixed(NOW, TimeConfig.BUSINESS_ZONE));
        faculty = fixtures.createOrganization("Khoa CNTT topic");
        otherFaculty = fixtures.createOrganization("Khoa khác topic");
        departmentId = department(faculty.facultyId(), "Công nghệ phần mềm");
        otherDepartmentId = department(faculty.facultyId(), "Hệ thống thông tin");
        lecturer = identity("topic-dept-author", faculty, Set.of(RoleCode.LECTURER));
        otherLecturer = identity("topic-dept-other", faculty, Set.of(RoleCode.LECTURER));
        dean = identity("topic-dept-dean", faculty, Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        outsideDean = identity("topic-dept-outside-dean", otherFaculty,
                Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        assignDepartment(lecturer, departmentId);
        assignDepartment(otherLecturer, otherDepartmentId);
        assignDepartment(dean, departmentId);
        long outsideDepartmentId = department(otherFaculty.facultyId(), "Bộ môn ngoài Khoa");
        assignDepartment(outsideDean, outsideDepartmentId);
        periodId = period(faculty.facultyId());
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void newTopicUsesAuthenticatedAuthorsDepartmentAndIgnoresForgedDepartmentId() throws Exception {
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài từ Bộ môn")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả")
                        .param("departmentId", Long.toString(otherDepartmentId)))
                .andExpect(status().isSeeOther());

        assertEquals(departmentId, jdbc.queryForObject(
                "SELECT department_id FROM topics WHERE author_id = ?", Long.class,
                lecturer.accountId()));
    }

    @Test
    void missingDepartmentBlocksFormAndDirectPostWithoutPersistingTopic() throws Exception {
        jdbc.update("UPDATE user_accounts SET department_id = NULL WHERE id = ?",
                lecturer.accountId());

        mvc.perform(get("/periods/{periodId}/topics/new", periodId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Bộ môn")));
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Không hợp lệ"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0L, topicCount());
    }

    @Test
    void departmentOutsidePeriodFacultyIsRejectedWithoutPersistingTopic() throws Exception {
        long outsideDepartmentId = department(otherFaculty.facultyId(), "Bộ môn lệch Khoa");
        assignDepartment(lecturer, outsideDepartmentId);

        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Không hợp lệ"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0L, topicCount());
    }

    @Test
    void lecturerFromAnotherFacultyCannotCreateTopicForThisPeriod() throws Exception {
        FixtureSupport.IdentityFixture outsideLecturer = identity(
                "topic-dept-outside-author", otherFaculty, Set.of(RoleCode.LECTURER));
        long outsideDepartmentId = department(otherFaculty.facultyId(), "Bộ môn ngoài");
        assignDepartment(outsideLecturer, outsideDepartmentId);

        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(outsideLecturer, "LECTURER")).with(csrf())
                        .param("title", "Không hợp lệ"))
                .andExpect(status().isNotFound());
        assertEquals(0L, topicCount());
    }

    @Test
    void authorLosingDepartmentCannotSubmitExistingDraft() throws Exception {
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài chờ gửi")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả"))
                .andExpect(status().isSeeOther());
        long topicId = jdbc.queryForObject(
                "SELECT MAX(id) FROM topics WHERE author_id = ?", Long.class, lecturer.accountId());
        jdbc.update("UPDATE user_accounts SET department_id = NULL WHERE id = ?",
                lecturer.accountId());

        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Bộ môn")));
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, topicId));
    }

    @Test
    void deanReviewsAcrossDepartmentsInOwnFacultyButNotAnotherFaculty() throws Exception {
        long first = pendingTopic(lecturer, departmentId, "Đề tài phần mềm");
        long second = pendingTopic(otherLecturer, otherDepartmentId, "Đề tài hệ thống");

        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .with(actor(dean, "DEAN", "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đề tài phần mềm")))
                .andExpect(content().string(containsString("Đề tài hệ thống")));
        mvc.perform(post("/topics/{id}/approve", first)
                        .with(actor(dean, "DEAN", "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/topics/{id}/approve", second)
                        .with(actor(dean, "DEAN", "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isSeeOther());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM topics WHERE status = 'APPROVED'", Long.class));

        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .with(actor(outsideDean, "DEAN", "LECTURER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/topics/{id}/approve", first)
                        .with(actor(outsideDean, "DEAN", "LECTURER")).with(csrf())
                        .param("version", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deanFiltersReviewByDepartmentWithoutChangingFacultyScopeOrSummary() throws Exception {
        pendingTopic(lecturer, departmentId, "Đề tài phần mềm T009");
        pendingTopic(otherLecturer, otherDepartmentId, "Đề tài hệ thống T009");
        long outsideDepartment = department(otherFaculty.facultyId(), "Bộ môn ngoài T009");

        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .param("departmentId", Long.toString(departmentId))
                        .with(actor(dean, "DEAN", "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đề tài phần mềm T009")))
                .andExpect(content().string(not(containsString("Đề tài hệ thống T009"))))
                .andExpect(content().string(containsString("<span>2</span> chờ duyệt")));
        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .param("departmentId", Long.toString(outsideDepartment))
                        .with(actor(dean, "DEAN", "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Đề tài phần mềm T009"))))
                .andExpect(content().string(not(containsString("Đề tài hệ thống T009"))))
                .andExpect(content().string(not(containsString("Bộ môn ngoài T009"))));
        mvc.perform(get("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Công nghệ phần mềm")))
                .andExpect(content().string(not(containsString("Đề tài hệ thống T009"))));
    }

    @Test
    void adminAccountListFiltersStaffByDepartmentAndKeepsStudentListUnchanged() throws Exception {
        FixtureSupport.IdentityFixture admin = identity(
                "topic-dept-admin", null, Set.of(RoleCode.ADMIN));

        mvc.perform(get("/admin/accounts/lecturers")
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .param("departmentId", Long.toString(departmentId))
                        .with(actor(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(lecturer.username())))
                .andExpect(content().string(containsString("Công nghệ phần mềm")))
                .andExpect(content().string(not(containsString(otherLecturer.username()))));
        mvc.perform(get("/admin/accounts/students")
                        .param("departmentId", Long.toString(departmentId))
                        .with(actor(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Lọc theo Bộ môn"))));
    }

    @Test
    void coSupervisorFormShowsOnlyEnabledLecturersInAuthorsDepartment() throws Exception {
        FixtureSupport.IdentityFixture sameDepartment = identity(
                "topic-dept-colleague", faculty, Set.of(RoleCode.LECTURER));
        FixtureSupport.IdentityFixture disabled = identity(
                "topic-dept-disabled", faculty, Set.of(RoleCode.LECTURER));
        FixtureSupport.IdentityFixture student = identity(
                "topic-dept-student-option", faculty, Set.of(RoleCode.STUDENT));
        assignDepartment(sameDepartment, departmentId);
        assignDepartment(disabled, departmentId);
        jdbc.update("UPDATE user_accounts SET enabled = false WHERE id = ?", disabled.accountId());

        mvc.perform(get("/periods/{periodId}/topics/new", periodId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sameDepartment.username())))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString(otherLecturer.username()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString(disabled.username()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString(student.username()))))
                .andExpect(content().string(containsString("cùng Bộ môn")));
    }

    @Test
    void sameDepartmentCoSupervisorIsSavedInSlotTwo() throws Exception {
        FixtureSupport.IdentityFixture colleague = identity(
                "topic-dept-slot-two", faculty, Set.of(RoleCode.LECTURER));
        assignDepartment(colleague, departmentId);

        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài hai GVHD")
                        .param("coSupervisorIds", Long.toString(colleague.accountId())))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();

        assertEquals(lecturer.accountId(), jdbc.queryForObject(
                "SELECT lecturer_id FROM topic_supervisors WHERE topic_id = ? AND slot = 1",
                Long.class, topicId));
        assertEquals(colleague.accountId(), jdbc.queryForObject(
                "SELECT lecturer_id FROM topic_supervisors WHERE topic_id = ? AND slot = 2",
                Long.class, topicId));
    }

    @Test
    void forgedCoSupervisorFromAnotherDepartmentCannotCreateOrEditDraft() throws Exception {
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Sai Bộ môn")
                        .param("coSupervisorIds", Long.toString(otherLecturer.accountId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("không cùng Bộ môn")));
        assertEquals(0L, topicCount());

        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Bản nháp hợp lệ"))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();
        mvc.perform(post("/topics/{id}/draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Bản nháp bị giả mạo")
                        .param("version", "0")
                        .param("coSupervisorIds", Long.toString(otherLecturer.accountId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("không cùng Bộ môn")));
        assertEquals("Bản nháp hợp lệ", jdbc.queryForObject(
                "SELECT title FROM topics WHERE id = ?", String.class, topicId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM topic_supervisors WHERE topic_id = ?", Long.class, topicId));
    }

    @Test
    void submitRechecksStoredCoSupervisorDepartmentWithoutChangingDraft() throws Exception {
        FixtureSupport.IdentityFixture colleague = identity(
                "topic-dept-moved-colleague", faculty, Set.of(RoleCode.LECTURER));
        assignDepartment(colleague, departmentId);
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Kiểm lại GVHD")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả")
                        .param("coSupervisorIds", Long.toString(colleague.accountId())))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();
        assignDepartment(colleague, otherDepartmentId);

        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, topicId));
    }

    @Test
    void studentInFacultySeesPublishedTopicsFromBothDepartments() throws Exception {
        FixtureSupport.IdentityFixture student = identity(
                "topic-dept-student-leader", faculty, Set.of(RoleCode.STUDENT));
        long first = pendingTopic(lecturer, departmentId, "Chọn đề tài phần mềm");
        long second = pendingTopic(otherLecturer, otherDepartmentId, "Chọn đề tài hệ thống");
        jdbc.update("UPDATE topics SET status = 'PUBLISHED' WHERE id IN (?, ?)", first, second);
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id, version) VALUES (?, ?, 0)",
                periodId, student.accountId());
        long groupId = jdbc.queryForObject("SELECT MAX(id) FROM student_groups", Long.class);

        mvc.perform(get("/groups/{groupId}/registrations/new", groupId)
                        .with(actor(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chọn đề tài phần mềm")))
                .andExpect(content().string(containsString("Chọn đề tài hệ thống")));
    }

    private long latestTopicId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM topics WHERE author_id = ?",
                Long.class, lecturer.accountId());
    }

    private FixtureSupport.IdentityFixture identity(String username,
            FixtureSupport.OrganizationScope scope, Set<RoleCode> roles) {
        return fixtures.createIdentity(username, username, "unusable-topic-department-hash",
                true, scope, roles);
    }

    private long department(long facultyId, String name) {
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, ?)", facultyId, name);
        return jdbc.queryForObject("SELECT MAX(id) FROM departments", Long.class);
    }

    private void assignDepartment(FixtureSupport.IdentityFixture identity, long department) {
        jdbc.update("UPDATE user_accounts SET department_id = ? WHERE id = ?",
                department, identity.accountId());
    }

    private long period(long facultyId) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type, lecturer_start, lecturer_end,
                    student_start, student_end, reviewer_score_deadline, council_report_date, version)
                VALUES (?, 'Đợt topic Bộ môn', 'COURSE', ?, ?, ?, ?, NULL, NULL, 0)
                """, facultyId, Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(3600)), Timestamp.from(NOW.plusSeconds(7200)),
                Timestamp.from(NOW.plusSeconds(14400)));
        return jdbc.queryForObject("SELECT MAX(id) FROM registration_periods", Long.class);
    }

    private long pendingTopic(FixtureSupport.IdentityFixture author, long department, String title) {
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id, title, description,
                    objectives, student_requirements, expected_results, status, version)
                VALUES (?, ?, ?, ?, 'Mô tả', 'Mục tiêu', 'Yêu cầu', 'Kết quả', 'PENDING_APPROVAL', 0)
                """, periodId, department, author.accountId(), title);
        return jdbc.queryForObject("SELECT MAX(id) FROM topics", Long.class);
    }

    private long topicCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM topics", Long.class);
    }

    private static UserRequestPostProcessor actor(FixtureSupport.IdentityFixture identity,
            String... roles) {
        return user(identity.username()).roles(roles);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary
        Clock topicDepartmentClock() {
            return Clock.fixed(NOW, TimeConfig.BUSINESS_ZONE);
        }
    }
}
