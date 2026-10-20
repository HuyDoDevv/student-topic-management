package vn.edu.topicregistration.integration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.integration.ReviewFixtureSupport.ReviewFixture;
import vn.edu.topicregistration.security.AccountDetailsService;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.AssignCommand;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReportUpload;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ScoreRow;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionCommand;

/** Exercises the dean's actual HTTP flow, including security, advice and rendered redirects. */
@SpringBootTest
@ActiveProfiles("local")
@Import(CouncilCompletionHttpIT.FixedClockConfiguration.class)
class CouncilCompletionHttpIT {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();
    private static final Pattern VERSION_INPUT = Pattern.compile(
            "<input[^>]*name=\"version\"[^>]*value=\"(\\d+)\"");

    @TempDir
    static Path reportRoot;

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountDetailsService accounts;
    @Autowired ReviewerAssignmentService reviewers;

    private MockMvc mvc;
    private ReviewFixture fixture;
    private UserDetails dean;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndPrepareCompleteScoreMatrix() {
        DATABASE.resetAndMigrate();
        fixture = new ReviewFixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC), reportRoot)
                .seed("not-a-real-test-password");
        List<Long> registrations = jdbc.queryForList(
                "SELECT registration_id FROM council_topics WHERE council_id = ?",
                Long.class, fixture.activeCouncilId());
        for (long registrationId : registrations) {
            submitReviewer(registrationId);
        }
        SecurityContextHolder.clearContext();
        dean = accounts.loadUserByUsername("dean.demo");
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        assertEquals("ACTIVE", councilStatus());
        assertEquals(0L, resultCount());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void firstCompletionRedirectsDeanToCompletedDetailAndRefreshDoesNotRepeatPost() throws Exception {
        long version = detailVersion();

        var completion = mvc.perform(post(detailPath() + "/complete")
                        .with(user(dean)).with(csrf())
                        .param("version", Long.toString(version)))
                .andReturn();

        assertEquals("COMPLETED", councilStatus());
        assertEquals(6L, resultCount());
        assertEquals(version + 1, councilVersion());
        assertNull(completion.getResolvedException(),
                () -> "POST failed after completion: " + completion.getResolvedException());
        assertEquals(303, completion.getResponse().getStatus());
        assertEquals(detailPath(), completion.getResponse().getRedirectedUrl());

        for (int refresh = 0; refresh < 2; refresh++) {
            mvc.perform(get(completion.getResponse().getRedirectedUrl()).with(user(dean)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Đã hoàn tất")))
                    .andExpect(content().string(containsString("Công bố kết quả")));
            assertEquals(6L, resultCount());
            assertEquals(version + 1, councilVersion());
        }
        assertEquals(6L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM final_results
                WHERE council_id = ? AND status = 'READY'
                  AND created_at IS NOT NULL AND updated_at IS NOT NULL
                """, Long.class, fixture.activeCouncilId()));
        assertEquals(3L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM final_results
                WHERE council_id = ? AND registration_id = ? AND final_score = 8.13
                """, Long.class, fixture.activeCouncilId(), fixture.activeRegistrationOneId()));
        assertEquals(3L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM final_results
                WHERE council_id = ? AND registration_id = ? AND final_score = 7.75
                """, Long.class, fixture.activeCouncilId(), fixture.activeRegistrationTwoId()));
    }

    @Test
    void replayingOldPostReturnsLatestDetailWithoutCreatingDuplicateResults() throws Exception {
        long version = detailVersion();
        mvc.perform(post(detailPath() + "/complete").with(user(dean)).with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(detailPath()));

        var replay = mvc.perform(post(detailPath() + "/complete")
                        .with(user(dean)).with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(detailPath()))
                .andExpect(flash().attributeExists("completionConflictMessage"))
                .andReturn();

        mvc.perform(get(detailPath()).with(user(dean))
                        .flashAttrs(replay.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dữ liệu hội đồng đã thay đổi")));
        assertEquals("COMPLETED", councilStatus());
        assertEquals(version + 1, councilVersion());
        assertEquals(6L, resultCount());
    }

    @Test
    void manuallyReactivatedCouncilWithResultsStillRejectsDuplicateCompletion() throws Exception {
        long version = detailVersion();
        mvc.perform(post(detailPath() + "/complete").with(user(dean)).with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isSeeOther());

        // Reproduce the reported manual edit only in the isolated test database.
        jdbc.update("UPDATE councils SET status = 'ACTIVE' WHERE id = ?",
                fixture.activeCouncilId());
        var rejected = mvc.perform(post(detailPath() + "/complete")
                        .with(user(dean)).with(csrf())
                        .param("version", Long.toString(councilVersion())))
                .andExpect(status().isConflict())
                .andReturn();

        assertNotNull(rejected.getResolvedException());
        assertEquals(ApplicationErrorCode.CONFLICT,
                ((ApplicationException) rejected.getResolvedException()).code());
        assertEquals("ACTIVE", councilStatus());
        assertEquals(version + 1, councilVersion());
        assertEquals(6L, resultCount());
    }

    private long detailVersion() throws Exception {
        var detail = mvc.perform(get(detailPath()).with(user(dean)))
                .andExpect(status().isOk())
                .andReturn();
        var input = VERSION_INPUT.matcher(detail.getResponse().getContentAsString());
        if (!input.find()) {
            throw new AssertionError("Council detail did not render the version input");
        }
        return Long.parseLong(input.group(1));
    }

    private String detailPath() {
        return "/faculty/boards/" + fixture.activeCouncilId();
    }

    private String councilStatus() {
        return jdbc.queryForObject("SELECT status FROM councils WHERE id = ?",
                String.class, fixture.activeCouncilId());
    }

    private long councilVersion() {
        return jdbc.queryForObject("SELECT version FROM councils WHERE id = ?",
                Long.class, fixture.activeCouncilId());
    }

    private long resultCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM final_results WHERE council_id = ?",
                Long.class, fixture.activeCouncilId());
    }

    private void submitReviewer(long registrationId) {
        authenticate("dean.demo");
        long reviewerId = jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = 'lecturer3.demo'", Long.class);
        long version = jdbc.queryForObject(
                "SELECT version FROM registrations WHERE id = ?", Long.class, registrationId);
        long assignmentId = reviewers.assign(new AssignCommand(registrationId, reviewerId, version));
        List<Long> students = jdbc.queryForList(
                "SELECT student_id FROM registration_members WHERE registration_id = ? ORDER BY slot",
                Long.class, registrationId);
        byte[] pdf = "%PDF-1.7\nCouncil completion HTTP regression".getBytes(StandardCharsets.US_ASCII);
        authenticate("lecturer3.demo");
        reviewers.submit(assignmentId, new SubmissionCommand(0L,
                new ReportUpload("review.pdf", "application/pdf", pdf.length,
                        () -> new ByteArrayInputStream(pdf)),
                students.stream().map(studentId -> new ScoreRow(
                        studentId, new BigDecimal("7.00"), "Đạt yêu cầu")).toList()));
    }

    private void authenticate(String username) {
        UserDetails principal = accounts.loadUserByUsername(username);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, "unused", principal.getAuthorities()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock councilCompletionClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
