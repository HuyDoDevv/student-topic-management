package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.MySqlTestSupport;

/** Topic and supervisor Department scope with Faculty-wide student workflows. */
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
        dean = identity("topic-dept-dean", faculty, Set.of(RoleCode.DEAN));
        outsideDean = identity("topic-dept-outside-dean", otherFaculty,
                Set.of(RoleCode.DEAN));
        assignDepartment(lecturer, departmentId);
        assignDepartment(otherLecturer, otherDepartmentId);
        assignDepartment(dean, departmentId);
        long outsideDepartmentId = department(otherFaculty.facultyId(), "Bộ môn ngoài Khoa");
        assignDepartment(outsideDean, outsideDepartmentId);
        periodId = period(faculty.facultyId(), departmentId, "Đợt phần mềm");
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void lecturerPeriodListContainsOnlyOwnDepartmentAndDeanKeepsFacultyScope()
            throws Exception {
        long otherPeriodId = period(faculty.facultyId(), otherDepartmentId, "Đợt hệ thống");
        period(faculty.facultyId(), null, "Đợt legacy");

        mvc.perform(get("/topics").with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đợt phần mềm")))
                .andExpect(content().string(not(containsString("Đợt hệ thống"))))
                .andExpect(content().string(not(containsString("Đợt legacy"))));
        mvc.perform(get("/topics").with(actor(otherLecturer, "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đợt hệ thống")))
                .andExpect(content().string(not(containsString("Đợt phần mềm"))));
        mvc.perform(get("/periods").with(actor(dean, "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đợt phần mềm")))
                .andExpect(content().string(containsString("Đợt hệ thống")));
        assertEquals(otherDepartmentId, jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?",
                Long.class, otherPeriodId));
    }

    @Test
    void directOtherDepartmentOrLegacyPeriodUrlIsNotFoundAndDoesNotCreateTopic()
            throws Exception {
        long otherPeriodId = period(faculty.facultyId(), otherDepartmentId, "Đợt hệ thống");
        long legacyPeriodId = period(faculty.facultyId(), null, "Đợt legacy");

        for (long blockedPeriodId : new long[] {otherPeriodId, legacyPeriodId}) {
            mvc.perform(get("/periods/{periodId}/topics", blockedPeriodId)
                            .with(actor(lecturer, "LECTURER")))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/periods/{periodId}/topics/new", blockedPeriodId)
                            .with(actor(lecturer, "LECTURER")))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/periods/{periodId}/topics", blockedPeriodId)
                            .with(actor(lecturer, "LECTURER")).with(csrf())
                            .param("title", "Đề tài không được tạo"))
                    .andExpect(status().isNotFound());
        }
        assertEquals(0L, topicCount());
    }

    @Test
    void authorMovedToOtherDepartmentCannotOpenOrSubmitOwnTopic() throws Exception {
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài trước khi chuyển Bộ môn"))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();
        assignDepartment(lecturer, otherDepartmentId);

        mvc.perform(get("/topics/{id}", topicId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isNotFound());
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, topicId));
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
        long otherPeriodId = period(faculty.facultyId(), otherDepartmentId, "Đợt hệ thống");
        long first = pendingTopic(lecturer, departmentId, "Đề tài phần mềm");
        long second = pendingTopic(otherPeriodId, otherLecturer, otherDepartmentId,
                "Đề tài hệ thống");

        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .with(actor(dean, "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đề tài phần mềm")))
                .andExpect(content().string(not(containsString("Đề tài hệ thống"))));
        mvc.perform(get("/periods/{periodId}/topics/review", otherPeriodId)
                        .with(actor(dean, "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đề tài hệ thống")))
                .andExpect(content().string(not(containsString("Đề tài phần mềm"))));
        mvc.perform(post("/topics/{id}/approve", first)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/topics/{id}/approve", second)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isSeeOther());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM topics WHERE status = 'APPROVED'", Long.class));

        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .with(actor(outsideDean, "DEAN")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/topics/{id}/approve", first)
                        .with(actor(outsideDean, "DEAN")).with(csrf())
                        .param("version", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorReturnsApprovedTopicThenEditsAndResubmitsForReview() throws Exception {
        long topicId = approvedTopic("Đề tài cần sửa");
        long approvedVersion = topicVersion(topicId);

        mvc.perform(get("/topics/{id}", topicId).with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trả về bản nháp để sửa")));
        mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(approvedVersion)))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/topics/" + topicId + "/edit"))
                .andExpect(flash().attribute("returnToDraftNotice",
                        "Đề tài đã trở về bản nháp. Sau khi sửa, bạn cần gửi duyệt lại."));
        assertEquals("DRAFT", topicStatus(topicId));
        MvcResult editPage = mvc.perform(get("/topics/{id}/edit", topicId)
                        .with(actor(lecturer, "LECTURER"))
                        .flashAttr("returnToDraftNotice", "Sau khi sửa, bạn cần gửi duyệt lại."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sau khi sửa, bạn cần gửi duyệt lại.")))
                .andReturn();
        long renderedVersion = renderedTopicVersion(editPage);
        assertEquals(topicVersion(topicId), renderedVersion);
        mvc.perform(post("/topics/{id}/draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(renderedVersion))
                        .param("title", "Đề tài đã sửa")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả"))
                .andExpect(status().isSeeOther());
        long savedVersion = topicVersion(topicId);
        assertEquals(renderedVersion + 1, savedVersion);
        mvc.perform(post("/topics/{id}/draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(renderedVersion))
                        .param("title", "Nội dung từ tab cũ")
                        .param("description", "Mô tả cũ")
                        .param("objectives", "Mục tiêu cũ")
                        .param("studentRequirements", "Yêu cầu cũ")
                        .param("expectedResults", "Kết quả cũ"))
                .andExpect(status().isConflict());
        assertEquals(savedVersion, topicVersion(topicId));
        assertEquals("Đề tài đã sửa", jdbc.queryForObject(
                "SELECT title FROM topics WHERE id = ?", String.class, topicId));
        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(savedVersion)))
                .andExpect(status().isSeeOther());
        assertEquals("PENDING_APPROVAL", topicStatus(topicId));
        assertEquals("Đề tài đã sửa", jdbc.queryForObject(
                "SELECT title FROM topics WHERE id = ?", String.class, topicId));
    }

    @Test
    void returnedTopicKeepsChangesAndRemovesCoSupervisorBeforeResubmission()
            throws Exception {
        FixtureSupport.IdentityFixture firstColleague = identity(
                "topic-return-co-first", faculty, Set.of(RoleCode.LECTURER));
        FixtureSupport.IdentityFixture secondColleague = identity(
                "topic-return-co-second", faculty, Set.of(RoleCode.LECTURER));
        assignDepartment(firstColleague, departmentId);
        assignDepartment(secondColleague, departmentId);

        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài có hai GVHD")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả")
                        .param("coSupervisorIds", Long.toString(firstColleague.accountId())))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();
        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId))))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/topics/{id}/approve", topicId)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId))))
                .andExpect(status().isSeeOther());

        Long[] coSupervisors = {firstColleague.accountId(), secondColleague.accountId(), null};
        for (int index = 0; index < coSupervisors.length; index++) {
            mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                            .with(actor(lecturer, "LECTURER")).with(csrf())
                            .param("version", Long.toString(topicVersion(topicId))))
                    .andExpect(status().isSeeOther());
            MvcResult editPage = mvc.perform(get("/topics/{id}/edit", topicId)
                            .with(actor(lecturer, "LECTURER")))
                    .andExpect(status().isOk())
                    .andReturn();
            long renderedVersion = renderedTopicVersion(editPage);
            assertEquals(topicVersion(topicId), renderedVersion);
            var saveRequest = post("/topics/{id}/draft", topicId)
                    .with(actor(lecturer, "LECTURER")).with(csrf())
                    .param("version", Long.toString(renderedVersion))
                    .param("title", "Đề tài sửa lần " + index)
                    .param("description", "Mô tả")
                    .param("objectives", "Mục tiêu")
                    .param("studentRequirements", "Yêu cầu")
                    .param("expectedResults", "Kết quả");
            if (coSupervisors[index] != null) {
                saveRequest.param("coSupervisorIds", Long.toString(coSupervisors[index]));
            }
            mvc.perform(saveRequest).andExpect(status().isSeeOther());
            assertEquals(lecturer.accountId(), jdbc.queryForObject(
                    "SELECT lecturer_id FROM topic_supervisors WHERE topic_id = ? AND slot = 1",
                    Long.class, topicId));
            assertEquals(coSupervisors[index] == null ? 1L : 2L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM topic_supervisors WHERE topic_id = ?",
                    Long.class, topicId));
            if (coSupervisors[index] != null) {
                assertEquals(coSupervisors[index], jdbc.queryForObject(
                        "SELECT lecturer_id FROM topic_supervisors WHERE topic_id = ? AND slot = 2",
                        Long.class, topicId));
            }
            mvc.perform(post("/topics/{id}/submit", topicId)
                            .with(actor(lecturer, "LECTURER")).with(csrf())
                            .param("version", Long.toString(topicVersion(topicId))))
                    .andExpect(status().isSeeOther());
            assertEquals("PENDING_APPROVAL", topicStatus(topicId));
            if (index < coSupervisors.length - 1) {
                mvc.perform(post("/topics/{id}/approve", topicId)
                                .with(actor(dean, "DEAN")).with(csrf())
                                .param("version", Long.toString(topicVersion(topicId))))
                        .andExpect(status().isSeeOther());
            }
        }
    }

    @Test
    void authorCanSaveReturnedTopicCreatedThroughAuthorFormWithPrimarySupervisor()
            throws Exception {
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài tạo từ form")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả"))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();
        assertEquals(lecturer.accountId(), jdbc.queryForObject(
                "SELECT lecturer_id FROM topic_supervisors WHERE topic_id = ? AND slot = 1",
                Long.class, topicId));

        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId))))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/topics/{id}/approve", topicId)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId))))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId))))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/topics/{id}/draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId)))
                        .param("title", "Đề tài đã sửa")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả"))
                .andExpect(status().isSeeOther());
        assertEquals("Đề tài đã sửa", jdbc.queryForObject(
                "SELECT title FROM topics WHERE id = ?", String.class, topicId));
        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(topicVersion(topicId))))
                .andExpect(status().isSeeOther());
        assertEquals("PENDING_APPROVAL", topicStatus(topicId));
    }

    @Test
    void returnToDraftRejectsPublishedExpiredStaleAndUnauthorizedRequests() throws Exception {
        long topicId = approvedTopic("Đề tài không được sửa");
        long version = topicVersion(topicId);
        for (FixtureSupport.IdentityFixture denied : new FixtureSupport.IdentityFixture[] {
                otherLecturer, dean, outsideDean}) {
            mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                            .with(actor(denied,
                                    denied == otherLecturer ? "LECTURER" : "DEAN"))
                            .with(csrf()).param("version", Long.toString(version)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(version - 1)))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE registration_periods SET lecturer_end = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(1)), periodId);
        mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("APPROVED", topicStatus(topicId));

        jdbc.update("UPDATE topics SET status = 'PUBLISHED' WHERE id = ?", topicId);
        mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("PUBLISHED", topicStatus(topicId));
    }

    @Test
    void anyRegistrationStatusBlocksReturningApprovedTopic() throws Exception {
        for (String status : new String[] {"PENDING", "ACCEPTED", "REJECTED", "WITHDRAWN"}) {
            long topicId = approvedTopic("Đề tài có đăng ký " + status);
            FixtureSupport.IdentityFixture student = identity(
                    "topic-return-student-" + status, faculty, Set.of(RoleCode.STUDENT));
            FixtureSupport.IdentityFixture memberTwo = identity(
                    "topic-return-member-two-" + status, faculty, Set.of(RoleCode.STUDENT));
            FixtureSupport.IdentityFixture memberThree = identity(
                    "topic-return-member-three-" + status, faculty, Set.of(RoleCode.STUDENT));
            jdbc.update("INSERT INTO student_groups(period_id, leader_student_id, version) VALUES (?, ?, 0)",
                    periodId, student.accountId());
            long groupId = jdbc.queryForObject("SELECT MAX(id) FROM student_groups", Long.class);
            jdbc.update("INSERT INTO group_memberships(group_id, period_id, student_id, slot) "
                    + "VALUES (?, ?, ?, 2)", groupId, periodId, memberTwo.accountId());
            jdbc.update("INSERT INTO group_memberships(group_id, period_id, student_id, slot) "
                    + "VALUES (?, ?, ?, 3)", groupId, periodId, memberThree.accountId());
            jdbc.update("INSERT INTO registrations(period_id, group_id, topic_id, status, "
                    + "rejection_kind, rejection_reason, version) VALUES (?, ?, ?, ?, ?, ?, 0)",
                    periodId, groupId, topicId, status,
                    "REJECTED".equals(status) ? "MANUAL" : null,
                    "REJECTED".equals(status) ? "Không phù hợp" : null);

            mvc.perform(post("/topics/{id}/return-to-draft", topicId)
                            .with(actor(lecturer, "LECTURER")).with(csrf())
                            .param("version", Long.toString(topicVersion(topicId))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(content().string(containsString("đã có đăng ký")));
            assertEquals("APPROVED", topicStatus(topicId));
        }
    }

    @Test
    void deanReviewRejectsForgedDepartmentAndExcludesMismatchedTopics() throws Exception {
        long valid = pendingTopic(lecturer, departmentId, "Đề tài đúng Bộ môn");
        long mismatched = pendingTopic(otherLecturer, otherDepartmentId,
                "Đề tài lệch Bộ môn");

        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .with(actor(dean, "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đề tài đúng Bộ môn")))
                .andExpect(content().string(not(containsString("Đề tài lệch Bộ môn"))))
                .andExpect(content().string(not(containsString("Tất cả Bộ môn trong Khoa"))))
                .andExpect(content().string(containsString("<span>1</span> chờ duyệt")));
        mvc.perform(get("/periods/{periodId}/topics/review", periodId)
                        .param("departmentId", Long.toString(otherDepartmentId))
                        .with(actor(dean, "DEAN")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/topics/{id}/approve", valid)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", "0")
                        .param("departmentId", Long.toString(otherDepartmentId)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/topics/{id}/reject", valid)
                        .queryParam("departmentId", Long.toString(otherDepartmentId))
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/periods/{periodId}/topics/publish", periodId)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("departmentId", Long.toString(otherDepartmentId)))
                .andExpect(status().isBadRequest());
        for (long topicId : new long[] {valid, mismatched}) {
            assertEquals("PENDING_APPROVAL", jdbc.queryForObject(
                    "SELECT status FROM topics WHERE id = ?", String.class, topicId));
        }
        mvc.perform(post("/topics/{id}/approve", mismatched)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/topics/{id}/approve", valid)
                        .with(actor(dean, "DEAN")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isSeeOther());
        jdbc.update("UPDATE topics SET status = 'APPROVED' WHERE id = ?", mismatched);
        mvc.perform(post("/periods/{periodId}/topics/publish", periodId)
                        .with(actor(dean, "DEAN")).with(csrf()))
                .andExpect(status().isSeeOther());
        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, valid));
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, mismatched));
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
    void topicDepartmentMismatchBlocksEditAndSubmitWithoutChangingDraft() throws Exception {
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài gốc")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả"))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();
        jdbc.update("UPDATE topics SET department_id = ? WHERE id = ?",
                otherDepartmentId, topicId);

        mvc.perform(post("/topics/{id}/draft", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Không được lưu")
                        .param("version", "0"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("Đề tài gốc", jdbc.queryForObject(
                "SELECT title FROM topics WHERE id = ?", String.class, topicId));
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, topicId));
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
        mvc.perform(get("/topics/{id}/edit", topicId)
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isOk());
        assignDepartment(colleague, otherDepartmentId);

        mvc.perform(post("/topics/{id}/submit", topicId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("version", "0"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, topicId));
    }

    @Test
    void concurrentSupervisorDepartmentChangeBlocksSubmit() throws Exception {
        FixtureSupport.IdentityFixture colleague = identity(
                "topic-dept-concurrent-colleague", faculty, Set.of(RoleCode.LECTURER));
        assignDepartment(colleague, departmentId);
        mvc.perform(post("/periods/{periodId}/topics", periodId)
                        .with(actor(lecturer, "LECTURER")).with(csrf())
                        .param("title", "Đề tài kiểm tra đồng thời")
                        .param("description", "Mô tả")
                        .param("objectives", "Mục tiêu")
                        .param("studentRequirements", "Yêu cầu")
                        .param("expectedResults", "Kết quả")
                        .param("coSupervisorIds", Long.toString(colleague.accountId())))
                .andExpect(status().isSeeOther());
        long topicId = latestTopicId();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE user_accounts SET department_id = ? WHERE id = ?")) {
                update.setLong(1, otherDepartmentId);
                update.setLong(2, colleague.accountId());
                update.executeUpdate();
            }
            CountDownLatch requestStarted = new CountDownLatch(1);
            Future<Integer> pending = executor.submit(() -> {
                requestStarted.countDown();
                return mvc.perform(post("/topics/{id}/submit", topicId)
                                .with(actor(lecturer, "LECTURER")).with(csrf())
                                .param("version", "0"))
                        .andReturn().getResponse().getStatus();
            });
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,
                    () -> pending.get(200, TimeUnit.MILLISECONDS));
            connection.commit();
            assertEquals(422, pending.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM topics WHERE id = ?", String.class, topicId));
    }

    @Test
    void studentSeesDepartmentPeriodsInFacultyIncludingLegacyButNotAnotherFaculty()
            throws Exception {
        FixtureSupport.IdentityFixture student = identity(
                "topic-dept-student-leader", faculty, Set.of(RoleCode.STUDENT));
        period(faculty.facultyId(), otherDepartmentId, "Đợt hệ thống");
        period(faculty.facultyId(), null, "Đợt legacy");
        period(otherFaculty.facultyId(), null, "Đợt Khoa khác");

        mvc.perform(get("/groups").with(actor(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đợt phần mềm")))
                .andExpect(content().string(containsString("Đợt hệ thống")))
                .andExpect(content().string(containsString("Công nghệ phần mềm")))
                .andExpect(content().string(containsString("Hệ thống thông tin")))
                .andExpect(content().string(containsString("Chưa xác định Bộ môn")))
                .andExpect(content().string(not(containsString("Đợt Khoa khác"))));
    }

    @Test
    void studentSeesOnlyPublishedTopicsWithinEachDepartmentPeriod() throws Exception {
        FixtureSupport.IdentityFixture student = identity(
                "topic-dept-student-leader", faculty, Set.of(RoleCode.STUDENT));
        long secondPeriodId = period(faculty.facultyId(), otherDepartmentId, "Đợt hệ thống");
        long first = pendingTopic(lecturer, departmentId, "Chọn đề tài phần mềm");
        long second = pendingTopic(secondPeriodId, otherLecturer, otherDepartmentId,
                "Chọn đề tài hệ thống");
        pendingTopic(lecturer, departmentId, "Đề tài chưa công bố");
        jdbc.update("UPDATE topics SET status = 'PUBLISHED' WHERE id IN (?, ?)", first, second);
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id, version) VALUES (?, ?, 0)",
                periodId, student.accountId());
        long firstGroupId = jdbc.queryForObject("SELECT MAX(id) FROM student_groups", Long.class);
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id, version) VALUES (?, ?, 0)",
                secondPeriodId, student.accountId());
        long secondGroupId = jdbc.queryForObject("SELECT MAX(id) FROM student_groups", Long.class);

        mvc.perform(get("/groups/{groupId}/registrations/new", firstGroupId)
                        .with(actor(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chọn đề tài phần mềm")))
                .andExpect(content().string(not(containsString("Chọn đề tài hệ thống"))))
                .andExpect(content().string(not(containsString("Đề tài chưa công bố"))));
        mvc.perform(get("/groups/{groupId}/registrations/new", secondGroupId)
                        .with(actor(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chọn đề tài hệ thống")))
                .andExpect(content().string(not(containsString("Chọn đề tài phần mềm"))));
    }

    @Test
    void directRegistrationRejectsUnpublishedAndOtherPeriodTopics() throws Exception {
        FixtureSupport.IdentityFixture leader = identity(
                "topic-dept-submit-leader", faculty, Set.of(RoleCode.STUDENT));
        FixtureSupport.IdentityFixture memberTwo = identity(
                "topic-dept-submit-member-two", faculty, Set.of(RoleCode.STUDENT));
        FixtureSupport.IdentityFixture memberThree = identity(
                "topic-dept-submit-member-three", faculty, Set.of(RoleCode.STUDENT));
        long secondPeriodId = period(faculty.facultyId(), otherDepartmentId, "Đợt hệ thống");
        long unpublished = pendingTopic(lecturer, departmentId, "Chưa công bố trực tiếp");
        long eligible = pendingTopic(lecturer, departmentId, "Đề tài hợp lệ");
        long otherPeriodTopic = pendingTopic(secondPeriodId, otherLecturer,
                otherDepartmentId, "Đề tài đợt khác");
        jdbc.update("UPDATE topics SET status = 'PUBLISHED' WHERE id IN (?, ?)",
                eligible, otherPeriodTopic);
        jdbc.update("""
                UPDATE registration_periods
                SET lecturer_start = ?, lecturer_end = ?, student_start = ?, student_end = ?
                WHERE id = ?
                """, Timestamp.from(NOW.minusSeconds(14400)),
                Timestamp.from(NOW.minusSeconds(10800)),
                Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(3600)), periodId);
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id, version) VALUES (?, ?, 0)",
                periodId, leader.accountId());
        long groupId = jdbc.queryForObject("SELECT MAX(id) FROM student_groups", Long.class);
        jdbc.update("INSERT INTO group_memberships(group_id, period_id, student_id, slot) VALUES (?, ?, ?, 2)",
                groupId, periodId, memberTwo.accountId());
        jdbc.update("INSERT INTO group_memberships(group_id, period_id, student_id, slot) VALUES (?, ?, ?, 3)",
                groupId, periodId, memberThree.accountId());

        for (long blockedTopic : new long[] {unpublished, otherPeriodTopic}) {
            mvc.perform(post("/groups/{groupId}/registrations", groupId)
                            .with(actor(leader, "STUDENT")).with(csrf())
                            .param("topicId", Long.toString(blockedTopic))
                            .param("version", "0"))
                    .andExpect(status().isUnprocessableEntity());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM registrations", Long.class));
        mvc.perform(post("/groups/{groupId}/registrations", groupId)
                        .with(actor(leader, "STUDENT")).with(csrf())
                        .param("topicId", Long.toString(eligible))
                        .param("version", "0"))
                .andExpect(status().isSeeOther());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM registrations", Long.class));
    }

    private long latestTopicId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM topics WHERE author_id = ?",
                Long.class, lecturer.accountId());
    }

    private long approvedTopic(String title) throws Exception {
        long topicId = pendingTopic(lecturer, departmentId, title);
        mvc.perform(post("/topics/{id}/approve", topicId)
                        .with(actor(dean, "DEAN")).with(csrf()).param("version", "0"))
                .andExpect(status().isSeeOther());
        return topicId;
    }

    private long topicVersion(long topicId) {
        return jdbc.queryForObject("SELECT version FROM topics WHERE id = ?", Long.class, topicId);
    }

    private static long renderedTopicVersion(MvcResult result) throws Exception {
        String html = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile(
                        "<input(?=[^>]*\\bname=\"version\")(?=[^>]*\\bvalue=\"(\\d+)\")[^>]*>")
                .matcher(html);
        assertTrue(matcher.find(), "Edit form must render the persisted topic version");
        return Long.parseLong(matcher.group(1));
    }

    private String topicStatus(long topicId) {
        return jdbc.queryForObject("SELECT status FROM topics WHERE id = ?", String.class, topicId);
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

    private long period(long facultyId, Long periodDepartmentId, String name) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, department_id, name, topic_type, lecturer_start, lecturer_end,
                    student_start, student_end, reviewer_score_deadline, council_report_date, version)
                VALUES (?, ?, ?, 'COURSE', ?, ?, ?, ?, NULL, NULL, 0)
                """, facultyId, periodDepartmentId, name, Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(3600)), Timestamp.from(NOW.plusSeconds(7200)),
                Timestamp.from(NOW.plusSeconds(14400)));
        return jdbc.queryForObject("SELECT MAX(id) FROM registration_periods", Long.class);
    }

    private long pendingTopic(FixtureSupport.IdentityFixture author, long department, String title) {
        return pendingTopic(periodId, author, department, title);
    }

    private long pendingTopic(long targetPeriodId, FixtureSupport.IdentityFixture author,
            long department, String title) {
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id, title, description,
                    objectives, student_requirements, expected_results, status, version)
                VALUES (?, ?, ?, ?, 'Mô tả', 'Mục tiêu', 'Yêu cầu', 'Kết quả', 'PENDING_APPROVAL', 0)
                """, targetPeriodId, department, author.accountId(), title);
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
