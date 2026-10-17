package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.repository.FinalResultRepository;

/** T017: group-level paging, filter isolation and allow-listed ordering on MySQL. */
@SpringBootTest
@ActiveProfiles("local")
class ResultListQueryIT {
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @TempDir static Path reportRoot;
    @Autowired JdbcTemplate jdbc;
    @Autowired FinalResultRepository results;
    @Autowired WebApplicationContext context;

    private ReviewFixtureSupport.ReviewFixture fixture;
    private MockMvc mvc;
    private long facultyId;
    private long activePeriodId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        fixture = new ReviewFixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC), reportRoot)
                .seed("not-a-real-test-password");
        facultyId = id("SELECT faculty_id FROM user_accounts WHERE id = ?", fixture.deanId());
        activePeriodId = id("SELECT period_id FROM registrations WHERE id = ?",
                fixture.activeRegistrationOneId());
        insertReadyResults(fixture.activeRegistrationOneId(), fixture.activeCouncilId(),
                NOW.minusSeconds(60));
        insertReadyResults(fixture.activeRegistrationTwoId(), fixture.activeCouncilId(), NOW);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void pagesWholeGroupsInStableOrderAndNeverReturnsAnotherFaculty() {
        var first = page(null, null, null, null, null, null, "recent", 0, 1);
        var second = page(null, null, null, null, null, null, "recent", 1, 1);
        var third = page(null, null, null, null, null, null, "recent", 2, 1);

        assertEquals(3, first.getTotalElements());
        assertEquals(List.of(fixture.activeRegistrationTwoId()), first.getContent());
        assertEquals(List.of(fixture.activeRegistrationOneId()), second.getContent());
        assertEquals(List.of(fixture.publishedRegistrationId()), third.getContent());
        assertEquals(3, results.findPageRowsForFaculty(
                facultyId, first.getContent()).size());
        assertTrue(results.findPageRowsForFaculty(
                facultyId + 1000, first.getContent()).isEmpty());
        assertEquals(0, results.findRegistrationIdsForFaculty(
                facultyId + 1000, null, null, null, null, null, null, null,
                "recent", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void combinesPeriodTopicGroupStatusAndGraderFilters() {
        long groupId = id("SELECT group_id FROM registrations WHERE id = ?",
                fixture.activeRegistrationOneId());
        long graderId = fixture.chairId();
        assertEquals(List.of(fixture.activeRegistrationOneId()),
                page(activePeriodId, null, "KLTN", "quản lý chấm", null,
                        "READY", "topic", 0, 10).getContent());
        assertEquals(List.of(fixture.activeRegistrationOneId()),
                page(null, null, null, "N" + groupId, groupId, null,
                        "recent", 0, 10).getContent());
        assertEquals(2, page(activePeriodId, null, null, null, null,
                "READY", "period", 0, 10).getTotalElements());
        assertEquals(List.of(fixture.publishedRegistrationId(),
                        fixture.activeRegistrationOneId(), fixture.activeRegistrationTwoId()),
                page(null, null, null, null, null, null,
                        "oldest", 0, 10).getContent());
        assertEquals(List.of(fixture.activeRegistrationTwoId(),
                        fixture.activeRegistrationOneId()),
                page(activePeriodId, null, null, null, null, null,
                        "period", 0, 10).getContent());
        assertEquals(List.of(fixture.activeRegistrationOneId(),
                        fixture.activeRegistrationTwoId()),
                page(activePeriodId, null, null, null, null, null,
                        "topic", 0, 10).getContent());
        assertEquals(List.of(fixture.publishedRegistrationId()),
                page(null, null, null, null, null, "PUBLISHED",
                        "recent", 0, 10).getContent());
        assertEquals(3, results.findRegistrationIdsForFaculty(
                facultyId, null, null, null, null, null, graderId, null,
                "recent", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, results.findPeriodOptionsForFaculty(facultyId).size());
        assertTrue(results.findGraderOptionsForFaculty(facultyId).stream()
                .anyMatch(option -> option.getId().equals(graderId)));
    }

    @Test
    void submittedReviewerCanBeUsedAsGraderFilter() {
        long reviewerId = id("SELECT id FROM user_accounts WHERE username = 'lecturer3.demo'");
        jdbc.update("""
                INSERT INTO reviewer_assignments(
                    registration_id, registration_status, lecturer_id,
                    deadline_date, status, assigned_by, assigned_at, submitted_at, version)
                VALUES (?, 'ACCEPTED', ?, '2026-10-20', 'SUBMITTED', ?, ?, ?, 0)
                """, fixture.activeRegistrationOneId(), reviewerId,
                fixture.deanId(), Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.minusSeconds(1200)));

        assertEquals(List.of(fixture.activeRegistrationOneId()),
                results.findRegistrationIdsForFaculty(facultyId, null, null, null,
                        null, null, reviewerId, null, "recent",
                        PageRequest.of(0, 10)).getContent());
        assertTrue(results.findGraderOptionsForFaculty(facultyId).stream()
                .anyMatch(option -> option.getId().equals(reviewerId)));
    }

    @Test
    void renderedListGroupsStudentsAndIgnoresForgedFacultyScope() throws Exception {
        mvc.perform(get("/faculty/results")
                        .param("period", Long.toString(activePeriodId))
                        .param("facultyId", "999")
                        .with(user("dean.demo").roles("DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 nhóm")))
                .andExpect(content().string(containsString("3 sinh viên")))
                .andExpect(content().string(containsString("Xem căn cứ")));

        mvc.perform(get("/faculty/results").param("sort", "id desc")
                        .with(user("dean.demo").roles("DEAN")))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(get("/faculty/results")
                        .with(user("student1.demo").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankOptionalSelectValuesAllowHttpSearchByGroupCode() throws Exception {
        long groupId = id("SELECT group_id FROM registrations WHERE id = ?",
                fixture.activeRegistrationOneId());

        mvc.perform(get("/faculty/results")
                        .param("period", Long.toString(activePeriodId))
                        .param("topicType", "")
                        .param("topicOrGroup", "N" + groupId)
                        .param("grader", "")
                        .param("status", "")
                        .param("sort", "recent")
                        .param("size", "10")
                        .with(user("dean.demo").roles("DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        ReviewFixtureSupport.ACTIVE_TOPIC_ONE)))
                .andExpect(content().string(not(containsString(
                        ReviewFixtureSupport.ACTIVE_TOPIC_TWO))));
    }

    private org.springframework.data.domain.Page<Long> page(
            Long periodId, Long registrationId, String topicType, String search,
            Long groupId, String status, String sort, int page, int size) {
        return results.findRegistrationIdsForFaculty(facultyId, periodId, registrationId,
                topicType, search, groupId, null, status, sort, PageRequest.of(page, size));
    }

    private void insertReadyResults(long registrationId, long councilId, Instant consolidatedAt) {
        for (Long studentId : jdbc.queryForList(
                "SELECT student_id FROM registration_members WHERE registration_id = ? ORDER BY slot",
                Long.class, registrationId)) {
            jdbc.update("""
                    INSERT INTO final_results(
                        registration_id, student_id, registration_status, council_id,
                        consolidated_by, consolidated_at, evaluation_summary, final_score,
                        review_completed_at, grading_completed_at, status, version)
                    VALUES (?, ?, 'ACCEPTED', ?, ?, ?, 'Đã tổng hợp', ?, ?, ?, 'READY', 0)
                    """, registrationId, studentId, councilId, fixture.deanId(),
                    Timestamp.from(consolidatedAt), new BigDecimal("8.50"),
                    Timestamp.from(consolidatedAt), Timestamp.from(consolidatedAt));
        }
    }

    private long id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }
}
