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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.MySqlTestSupport;
import vn.edu.topicregistration.shared.time.TimeConfig;

@SpringBootTest
@ActiveProfiles("local")
@Import(PeriodContractTest.ContractClockConfiguration.class)
class PeriodContractTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T17:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-11T16:59:59.999999Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-11T17:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-13T16:59:59.999999Z");
    private static final String UNUSED_HASH = "unusable-contract-test-hash";
    private static final MySqlTestSupport DATABASE = preparedDatabase();

    @Autowired
    WebApplicationContext context;

    @Autowired
    DataSource dataSource;

    MockMvc mvc;
    JdbcTemplate jdbc;
    FixtureSupport fixtures;
    FixtureSupport.OrganizationScope deanScope;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetDatabaseAndIdentity() {
        DATABASE.resetAndMigrate();
        jdbc = new JdbcTemplate(dataSource);
        fixtures = new FixtureSupport(
                dataSource, Clock.fixed(FIXED_NOW, TimeConfig.BUSINESS_ZONE));
        deanScope = fixtures.createOrganization("Khoa CNTT", "Công nghệ phần mềm");
        fixtures.createIdentity(
                "dean", "Trưởng khoa", UNUSED_HASH, true,
                deanScope, Set.of(RoleCode.LECTURER, RoleCode.DEAN));
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void deanCreatesAValidPeriodAndReadsPersistedValues() throws Exception {
        mvc.perform(validCreateRequest("COURSE")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrlPattern("/periods/*"));

        assertEquals(1L, periodCount());
        PeriodRow saved = loadOnlyPeriod();
        assertEquals(deanScope.facultyId(), saved.facultyId());
        assertEquals("Đợt HK1", saved.name());
        assertEquals("COURSE", saved.topicType());
        assertEquals(LECTURER_START, saved.lecturerStart());
        assertEquals(STUDENT_END, saved.studentEnd());

        mvc.perform(get("/periods/{id}", saved.id())
                        .with(user("dean").roles("LECTURER", "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đợt HK1")));
    }

    @Test
    void formUsesCalendarDatesAndRejectsPastOrTimeValues() throws Exception {
        mvc.perform(get("/periods/new")
                        .with(user("dean").roles("LECTURER", "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("type=\"date\"")))
                .andExpect(content().string(containsString("min=\"2026-09-10\"")))
                .andExpect(content().string(not(containsString("datetime-local"))));

        mvc.perform(baseCreateRequest(
                        "COURSE", "2026-09-09", "2026-09-11", "2026-09-12", "2026-09-13")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0L, periodCount());

        mvc.perform(baseCreateRequest(
                        "COURSE", "2026-09-10T08:00", "2026-09-11", "2026-09-12", "2026-09-13")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0L, periodCount());
    }

    @Test
    void conditionalDatesAndScheduleValidationRejectTheWholeCreate() throws Exception {
        mvc.perform(baseCreateRequest("KLTN")
                        .param("reviewerScoreDeadline", "2026-09-20")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0L, periodCount());

        mvc.perform(baseCreateRequest("COURSE", "2026-09-11")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0L, periodCount());

        mvc.perform(validCreateRequest("COURSE")
                        .param("reviewerScoreDeadline", "2026-09-20")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0L, periodCount());
    }

    @Test
    void createRequiresCsrfAndPersistedDeanRole() throws Exception {
        mvc.perform(validCreateRequest("COURSE")
                        .with(user("dean").roles("LECTURER", "DEAN")))
                .andExpect(status().isForbidden());
        assertEquals(0L, periodCount());

        fixtures.createIdentity(
                "lecturer", "Giảng viên", UNUSED_HASH, true,
                deanScope, Set.of(RoleCode.LECTURER));
        mvc.perform(validCreateRequest("COURSE")
                        .param("role", "DEAN")
                        .with(user("lecturer").roles("DEAN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        assertEquals(0L, periodCount());
    }

    @Test
    void listAndUpdateStayInsideThePersistedFacultyScope() throws Exception {
        long ownPeriod = insertPeriod(deanScope.facultyId(), "Đợt khoa CNTT", 0L);
        FixtureSupport.OrganizationScope otherScope = fixtures.createOrganization(
                "Khoa Điện", "Điện công nghiệp");
        long otherPeriod = insertPeriod(otherScope.facultyId(), "Đợt khoa Điện", 0L);

        mvc.perform(get("/periods")
                        .with(user("dean").roles("LECTURER", "DEAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đợt khoa CNTT")))
                .andExpect(content().string(not(containsString("Đợt khoa Điện"))));

        mvc.perform(validUpdateRequest(otherPeriod, 0L)
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        assertEquals("Đợt khoa Điện", periodName(otherPeriod));
        assertEquals("Đợt khoa CNTT", periodName(ownPeriod));
    }

    @Test
    void validUpdatePersistsAtomicallyAndStaleVersionReturnsConflict() throws Exception {
        long periodId = insertPeriod(deanScope.facultyId(), "Tên cũ", 3L);

        mvc.perform(validUpdateRequest(periodId, 3L)
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrlPattern("/periods/*"));

        assertEquals("Tên đã sửa", periodName(periodId));
        assertEquals(4L, periodVersion(periodId));

        mvc.perform(validUpdateRequest(periodId, 3L)
                        .param("name", "Ghi đè bằng version cũ")
                        .with(user("dean").roles("LECTURER", "DEAN"))
                        .with(csrf()))
                .andExpect(status().isConflict());
        assertEquals("Tên đã sửa", periodName(periodId));
        assertEquals(4L, periodVersion(periodId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validCreateRequest(String topicType) {
        var request = baseCreateRequest(topicType);
        if ("TLCN".equals(topicType) || "KLTN".equals(topicType)) {
            request.param("reviewerScoreDeadline", "2026-09-20");
        }
        if ("KLTN".equals(topicType)) {
            request.param("councilReportDate", "2026-09-25");
        }
        return request;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            baseCreateRequest(String topicType) {
        return baseCreateRequest(topicType, "2026-09-12");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            baseCreateRequest(String topicType, String studentStart) {
        return baseCreateRequest(
                topicType, "2026-09-11", "2026-09-11", studentStart, "2026-09-13");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            baseCreateRequest(
                    String topicType,
                    String lecturerStart,
                    String lecturerEnd,
                    String studentStart,
                    String studentEnd) {
        return post("/periods")
                .param("name", "Đợt HK1")
                .param("topicType", topicType)
                .param("lecturerStart", lecturerStart)
                .param("lecturerEnd", lecturerEnd)
                .param("studentStart", studentStart)
                .param("studentEnd", studentEnd);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validUpdateRequest(long periodId, long version) {
        return post("/periods/{id}", periodId)
                .param("name", "Tên đã sửa")
                .param("topicType", "COURSE")
                .param("lecturerStart", "2026-09-11")
                .param("lecturerEnd", "2026-09-11")
                .param("studentStart", "2026-09-12")
                .param("studentEnd", "2026-09-13")
                .param("version", Long.toString(version));
    }

    private long insertPeriod(long facultyId, String name, long version) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date, version)
                VALUES (?, ?, 'COURSE', ?, ?, ?, ?, NULL, NULL, ?)
                """,
                facultyId, name,
                Timestamp.from(LECTURER_START), Timestamp.from(LECTURER_END),
                Timestamp.from(STUDENT_START), Timestamp.from(STUDENT_END), version);
        return jdbc.queryForObject("SELECT MAX(id) FROM registration_periods", Long.class);
    }

    private long periodCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registration_periods", Long.class);
    }

    private String periodName(long id) {
        return jdbc.queryForObject(
                "SELECT name FROM registration_periods WHERE id = ?", String.class, id);
    }

    private long periodVersion(long id) {
        return jdbc.queryForObject(
                "SELECT version FROM registration_periods WHERE id = ?", Long.class, id);
    }

    private PeriodRow loadOnlyPeriod() {
        return jdbc.queryForObject("""
                SELECT id, faculty_id, name, topic_type, lecturer_start, student_end,
                       reviewer_score_deadline, council_report_date, version
                FROM registration_periods
                """, (result, row) -> new PeriodRow(
                        result.getLong("id"),
                        result.getLong("faculty_id"),
                        result.getString("name"),
                        result.getString("topic_type"),
                        result.getTimestamp("lecturer_start").toInstant(),
                        result.getTimestamp("student_end").toInstant(),
                        localDate(result.getDate("reviewer_score_deadline")),
                        localDate(result.getDate("council_report_date")),
                        result.getLong("version")));
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static MySqlTestSupport preparedDatabase() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        return database;
    }

    private record PeriodRow(
            long id,
            long facultyId,
            String name,
            String topicType,
            Instant lecturerStart,
            Instant studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate,
            long version) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContractClockConfiguration {
        @Bean
        @Primary
        Clock periodContractClock() {
            return Clock.fixed(FIXED_NOW, TimeConfig.BUSINESS_ZONE);
        }
    }
}
