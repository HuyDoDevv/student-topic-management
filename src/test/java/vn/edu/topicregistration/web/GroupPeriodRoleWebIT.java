package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.MySqlTestSupport;

/** T007: each period card keeps the current student's group role and action isolated. */
@SpringBootTest
@ActiveProfiles("local")
@Import(GroupPeriodRoleWebIT.FixedClockConfiguration.class)
class GroupPeriodRoleWebIT {
    private static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;

    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private FixtureSupport fixtures;
    private FixtureSupport.OrganizationScope faculty;
    private FixtureSupport.IdentityFixture student;
    private FixtureSupport.IdentityFixture otherStudent;
    private FixtureSupport.IdentityFixture lecturer;
    private FixtureSupport.IdentityFixture dean;
    private long departmentId;

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
        faculty = fixtures.createOrganization("Khoa T007");
        student = identity("t007-student", faculty, RoleCode.STUDENT);
        otherStudent = identity("t007-other-student", faculty, RoleCode.STUDENT);
        lecturer = identity("t007-lecturer", faculty, RoleCode.LECTURER);
        dean = identity("t007-dean", faculty, RoleCode.DEAN);
        departmentId = department(faculty.facultyId(), "Bộ môn T007");
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void cardsKeepLeaderMemberAndNoGroupDataWithinTheirOwnPeriod() throws Exception {
        long leaderPeriodId = period(faculty.facultyId(), departmentId, "Đợt Leader T007");
        long memberPeriodId = period(faculty.facultyId(), departmentId, "Đợt Member T007");
        long emptyPeriodId = period(faculty.facultyId(), departmentId, "Đợt Chưa Có Nhóm T007");

        long leaderGroupId = group(leaderPeriodId, student.accountId());
        FixtureSupport.IdentityFixture leaderMemberTwo = identity(
                "t007-leader-member-two", faculty, RoleCode.STUDENT);
        FixtureSupport.IdentityFixture leaderMemberThree = identity(
                "t007-leader-member-three", faculty, RoleCode.STUDENT);
        addMember(leaderGroupId, leaderPeriodId, leaderMemberTwo.accountId(), 2);
        addMember(leaderGroupId, leaderPeriodId, leaderMemberThree.accountId(), 3);
        long memberGroupId = group(memberPeriodId, otherStudent.accountId());
        addMember(memberGroupId, memberPeriodId, student.accountId(), 2);
        long topicId = topic(leaderPeriodId, "Đề tài Leader T007");
        jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status,
                    decided_at, decision_actor_id)
                VALUES (?, ?, ?, 'ACCEPTED', ?, ?)
                """, leaderPeriodId, leaderGroupId, topicId,
                Timestamp.from(NOW.minusSeconds(60)), dean.accountId());

        FixtureSupport.OrganizationScope outside = fixtures.createOrganization("Khoa ngoài T007");
        long outsideDepartmentId = department(outside.facultyId(), "Bộ môn ngoài T007");
        period(outside.facultyId(), outsideDepartmentId, "Đợt Khoa Ngoài T007");

        String html = mvc.perform(get("/groups").with(user(student.username()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String leaderCard = card(html, leaderPeriodId);
        assertTrue(leaderCard.contains("Đợt Leader T007"));
        assertTrue(leaderCard.contains("P" + String.format("%03d", leaderPeriodId)));
        assertTrue(leaderCard.contains("N" + String.format("%03d", leaderGroupId)));
        assertTrue(leaderCard.contains("Trưởng nhóm"));
        assertTrue(leaderCard.contains("Đề tài Leader T007"));
        assertTrue(leaderCard.contains("Đã chấp nhận"));
        assertTrue(leaderCard.contains("/periods/" + leaderPeriodId + "/groups"));
        assertFalse(leaderCard.contains("Thành viên"));

        String memberCard = card(html, memberPeriodId);
        assertTrue(memberCard.contains("Đợt Member T007"));
        assertTrue(memberCard.contains("N" + String.format("%03d", memberGroupId)));
        assertTrue(memberCard.contains("Thành viên"));
        assertTrue(memberCard.contains("Chưa đăng ký đề tài"));
        assertTrue(memberCard.contains("/periods/" + memberPeriodId + "/groups"));
        assertFalse(memberCard.contains("Trưởng nhóm"));
        assertFalse(memberCard.contains("Đề tài Leader T007"));

        String emptyCard = card(html, emptyPeriodId);
        assertTrue(emptyCard.contains("Đợt Chưa Có Nhóm T007"));
        assertTrue(emptyCard.contains("Chưa có nhóm"));
        assertTrue(emptyCard.contains("/periods/" + emptyPeriodId + "/groups/new"));
        assertFalse(emptyCard.contains("N" + String.format("%03d", leaderGroupId)));
        assertFalse(html.contains("Đợt Khoa Ngoài T007"));
    }

    private FixtureSupport.IdentityFixture identity(
            String username,
            FixtureSupport.OrganizationScope scope,
            RoleCode role) {
        return fixtures.createIdentity(
                username, username, "not-a-real-password-hash", true, scope, Set.of(role));
    }

    private long department(long facultyId, String name) {
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, ?)", facultyId, name);
        return jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = ?",
                Long.class, facultyId, name);
    }

    private long period(long facultyId, long targetDepartmentId, String name) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, department_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end)
                VALUES (?, ?, ?, 'COURSE', ?, ?, ?, ?)
                """, facultyId, targetDepartmentId, name,
                Timestamp.from(NOW.minusSeconds(4 * 24 * 60 * 60)),
                Timestamp.from(NOW.minusSeconds(2 * 24 * 60 * 60)),
                Timestamp.from(NOW.minusSeconds(24 * 60 * 60)),
                Timestamp.from(NOW.plusSeconds(24 * 60 * 60)));
        return jdbc.queryForObject(
                "SELECT id FROM registration_periods WHERE faculty_id = ? AND name = ?",
                Long.class, facultyId, name);
    }

    private long group(long periodId, long leaderId) {
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id) VALUES (?, ?)",
                periodId, leaderId);
        return jdbc.queryForObject(
                "SELECT id FROM student_groups WHERE period_id = ? AND leader_student_id = ?",
                Long.class, periodId, leaderId);
    }

    private void addMember(long groupId, long periodId, long studentId, int slot) {
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, ?)
                """, groupId, periodId, studentId, slot);
    }

    private long topic(long periodId, String title) {
        jdbc.update("""
                INSERT INTO topics(
                    period_id, department_id, author_id, title, description,
                    objectives, student_requirements, expected_results, status)
                VALUES (?, ?, ?, ?, 'Mô tả', 'Mục tiêu', 'Yêu cầu', 'Kết quả', 'PUBLISHED')
                """, periodId, departmentId, lecturer.accountId(), title);
        return jdbc.queryForObject(
                "SELECT id FROM topics WHERE period_id = ? AND title = ?",
                Long.class, periodId, title);
    }

    private static String card(String html, long periodId) {
        String marker = "data-period-id=\"" + periodId + "\"";
        int start = html.indexOf(marker);
        assertTrue(start >= 0, "Missing card for period " + periodId);
        int end = html.indexOf("</article>", start);
        assertTrue(end > start, "Unclosed card for period " + periodId);
        return html.substring(start, end);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock groupPeriodRoleClock() {
            return Clock.fixed(NOW, TimeConfig.BUSINESS_ZONE);
        }
    }
}
