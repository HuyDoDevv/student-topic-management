package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Timestamp;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.ReviewFixtureSupport.ReviewFixture;
import vn.edu.topicregistration.service.CouncilService;
import vn.edu.topicregistration.service.CouncilService.CreateCouncilCommand;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.DirectGradingService.AssignCommand;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationTargetResolver;
import vn.edu.topicregistration.service.ResultService;

/** T008: assignment and result publication notifications use trusted workflow snapshots. */
@SpringBootTest
@ActiveProfiles("local")
@Import(ReviewResultNotificationIT.TestBeans.class)
class ReviewResultNotificationIT {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final String FIXTURE_PASSWORD = "not-a-real-test-password";
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @TempDir
    static Path reportRoot;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DirectGradingService directGrading;

    @Autowired
    CouncilService councils;

    @Autowired
    ResultService results;

    @Autowired
    NotificationService notifications;

    @Autowired
    RollbackProbe rollbackProbe;

    private ReviewFixture fixture;

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
                .seed(FIXTURE_PASSWORD);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directGraderAssignmentNotifiesOnlyTheAssignedLecturer() {
        long registrationId = requiredLong("""
                SELECT registration.id
                FROM registrations registration
                JOIN topics topic ON topic.id = registration.topic_id
                WHERE topic.title = '[DEMO] Hệ thống quản lý đăng ký đề tài'
                ORDER BY registration.id
                LIMIT 1
                """);
        long submittedBy = requiredLong("""
                SELECT student_id FROM registration_members
                WHERE registration_id = ? AND leader_at_submission = TRUE
                """, registrationId);
        long graderId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'lecturer4.demo'");
        long otherLecturerId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'lecturer5.demo'");
        jdbc.update("""
                UPDATE registrations
                SET status = 'ACCEPTED', decided_at = ?, decision_actor_id = ?, version = version + 1
                WHERE id = ?
                """, Timestamp.from(NOW.minusSeconds(60)), fixture.deanId(), registrationId);
        jdbc.update("""
                INSERT INTO project_reports(
                    registration_id, registration_status, submitted_by, submitted_at,
                    file_name, storage_key, content_type, size_bytes, version)
                VALUES (?, 'ACCEPTED', ?, ?, 'notification-test.pdf',
                        'notification-test.pdf', 'application/pdf', 128, 0)
                """, registrationId, submittedBy, Timestamp.from(NOW.minusSeconds(30)));

        authenticate("dean.demo", RoleCode.DEAN);
        var assignment = directGrading.assign(new AssignCommand(registrationId, graderId));

        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'DIRECT_GRADER_ASSIGNED'
                  AND event.event_key = ?
                  AND event.target_type = 'DIRECT_GRADING'
                  AND event.target_id = ?
                  AND delivery.recipient_id = ?
                """, "DIRECT_GRADING:" + assignment.id() + ":ASSIGNED",
                assignment.id(), graderId));
        assertEquals(0, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'DIRECT_GRADER_ASSIGNED'
                  AND delivery.recipient_id = ?
                """, otherLecturerId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM council_members
                WHERE council_id = ? AND lecturer_id = ? AND slot = 1
                """, assignment.id(), graderId));

        authenticate("lecturer4.demo", RoleCode.LECTURER);
        long notificationId = requiredLong("""
                SELECT delivery.id FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'DIRECT_GRADER_ASSIGNED'
                  AND event.target_id = ? AND delivery.recipient_id = ?
                """, assignment.id(), graderId);
        assertEquals("/lecturer/scoring/" + assignment.id() + "/" + registrationId,
                notifications.openForCurrentActor(notificationId));

        authenticate("dean.demo", RoleCode.DEAN);
        assertThrows(RuntimeException.class,
                () -> directGrading.assign(new AssignCommand(registrationId, otherLecturerId)));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM notification_events
                WHERE event_type = 'DIRECT_GRADER_ASSIGNED'
                """));
    }

    @Test
    void councilCreationNotifiesEveryPersistedMemberOnce() {
        long periodId = requiredLong("""
                SELECT id FROM registration_periods
                WHERE name = '[DEMO] Khóa luận tốt nghiệp - Đang chấm'
                """);
        long chairId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'lecturer1.demo'");
        long secretaryId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'lecturer4.demo'");
        long memberId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'lecturer5.demo'");

        authenticate("dean.demo", RoleCode.DEAN);
        var council = councils.create(new CreateCouncilCommand(
                periodId,
                "[TEST] Hội đồng nhận thông báo",
                chairId,
                secretaryId,
                List.of(memberId)));

        assertEquals(3, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'COUNCIL_MEMBER_ASSIGNED'
                  AND event.event_key = ?
                  AND event.target_type = 'COUNCIL'
                  AND event.target_id = ?
                """, "COUNCIL_MEMBER:" + council.id() + ":ASSIGNED", council.id()));
        assertEquals(0, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'COUNCIL_MEMBER_ASSIGNED'
                  AND delivery.recipient_id = ?
                """, fixture.deanId()));
        assertEquals(3, count("""
                SELECT COUNT(*) FROM council_members WHERE council_id = ?
                """, council.id()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM council_members
                WHERE council_id = ? AND lecturer_id = ? AND slot = 1
                """, council.id(), chairId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM council_members
                WHERE council_id = ? AND lecturer_id = ? AND slot = 2
                """, council.id(), secretaryId));

        authenticate("lecturer1.demo", RoleCode.LECTURER);
        long chairNotificationId = requiredLong("""
                SELECT delivery.id FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'COUNCIL_MEMBER_ASSIGNED'
                  AND event.target_id = ? AND delivery.recipient_id = ?
                """, council.id(), chairId);
        assertEquals(NotificationTargetResolver.UNAVAILABLE,
                notifications.openForCurrentActor(chairNotificationId));
    }

    @Test
    void resultPublicationNotifiesEachSnapshotStudentAndRollsBackAtomically() {
        authenticate("lecturer1.demo", RoleCode.LECTURER);
        var completed = results.complete(fixture.activeCouncilId(), 0L);
        assertEquals(6, completed.resultCount());

        authenticate("dean.demo", RoleCode.DEAN);
        assertThrows(IllegalStateException.class,
                () -> rollbackProbe.publishThenFail(
                        fixture.activeCouncilId(), completed.version()));
        assertEquals("COMPLETED", requiredString(
                "SELECT status FROM councils WHERE id = ?", fixture.activeCouncilId()));
        assertEquals(6, count("""
                SELECT COUNT(*) FROM final_results
                WHERE council_id = ? AND status = 'READY'
                """, fixture.activeCouncilId()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM notification_events
                WHERE event_type = 'RESULT_PUBLISHED'
                """));

        var published = results.publish(
                fixture.activeCouncilId(), completed.version());
        assertEquals(6, published.resultCount());
        assertEquals(6, count("""
                SELECT COUNT(*)
                FROM final_results result
                JOIN notification_events event
                  ON event.event_key = CONCAT('RESULT:', result.registration_id, ':PUBLISHED')
                JOIN user_notifications delivery
                  ON delivery.event_id = event.id
                 AND delivery.recipient_id = result.student_id
                WHERE result.council_id = ?
                  AND result.status = 'PUBLISHED'
                  AND event.event_type = 'RESULT_PUBLISHED'
                  AND event.target_type = 'RESULT'
                  AND event.target_id = result.registration_id
                """, fixture.activeCouncilId()));
        assertEquals(0, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = 'RESULT_PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM registration_members member
                      WHERE member.registration_id = event.target_id
                        AND member.student_id = delivery.recipient_id
                  )
                """));

        assertThrows(RuntimeException.class,
                () -> results.publish(fixture.activeCouncilId(), published.version()));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM notification_events
                WHERE event_type = 'RESULT_PUBLISHED'
                """));
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private long requiredLong(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Expected database identifier");
        }
        return value;
    }

    private String requiredString(String sql, Object... arguments) {
        String value = jdbc.queryForObject(sql, String.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Expected database text");
        }
        return value;
    }

    private static void authenticate(String username, RoleCode... roles) {
        Set<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(java.util.stream.Collectors.toSet());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        username, "unused", authorities));
    }

    static class RollbackProbe {
        private final ResultService results;

        RollbackProbe(ResultService results) {
            this.results = results;
        }

        @Transactional
        public void publishThenFail(long councilId, long expectedVersion) {
            results.publish(councilId, expectedVersion);
            throw new IllegalStateException("Force result and notification rollback");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        @Primary
        Clock reviewNotificationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RollbackProbe reviewNotificationRollbackProbe(ResultService results) {
            return new RollbackProbe(results);
        }
    }
}
