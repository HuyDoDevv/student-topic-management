package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import vn.edu.topicregistration.integration.FixtureSupport.IdentityFixture;
import vn.edu.topicregistration.integration.FixtureSupport.OrganizationScope;
import vn.edu.topicregistration.service.GroupService;
import vn.edu.topicregistration.service.RegistrationDecisionService;
import vn.edu.topicregistration.service.TopicReviewService;

/** T007: notifications emitted atomically by group, topic and registration transitions. */
@SpringBootTest
@ActiveProfiles("local")
@Import(RegistrationNotificationIT.TestBeans.class)
class RegistrationNotificationIT {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final String FIXTURE_PASSWORD = "not-a-real-test-password";
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @TempDir
    static Path reportRoot;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GroupService groups;

    @Autowired
    TopicReviewService topicReviews;

    @Autowired
    RegistrationDecisionService registrationDecisions;

    @Autowired
    RollbackProbe rollbackProbe;

    private FixtureSupport fixtures;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("app.report-storage.root", () -> reportRoot.toString());
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        new ReviewFixtureSupport(DATABASE.dataSource(), clock, reportRoot)
                .seed(FIXTURE_PASSWORD);
        fixtures = new FixtureSupport(DATABASE.dataSource(), clock);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void groupInvitationLifecycleNotifiesInviteeAndLeaderOnce() {
        long facultyId = requiredLong("""
                SELECT account.faculty_id
                FROM user_accounts account
                WHERE account.username = 'student6.demo'
                """);
        OrganizationScope scope = new OrganizationScope(facultyId);
        IdentityFixture rejectingStudent = fixtures.createIdentity(
                "notification.rejecting.student",
                "Sinh viên từ chối lời mời",
                "NOT_A_LOGIN_HASH",
                true,
                scope,
                Set.of(RoleCode.STUDENT));
        IdentityFixture acceptingStudent = fixtures.createIdentity(
                "notification.accepting.student",
                "Sinh viên chấp nhận lời mời",
                "NOT_A_LOGIN_HASH",
                true,
                scope,
                Set.of(RoleCode.STUDENT));
        long leaderId = requiredLong(
                "SELECT id FROM user_accounts WHERE username = 'student6.demo'");
        long groupId = requiredLong(
                "SELECT id FROM student_groups WHERE leader_student_id = ?", leaderId);

        authenticate("student6.demo", RoleCode.STUDENT);
        groups.inviteMember(groupId, groupVersion(groupId), rejectingStudent.accountId());
        long rejectedInvitationId = pendingInvitationId(rejectingStudent.accountId());
        assertDelivery(
                "GROUP_INVITATION_CREATED", rejectedInvitationId,
                rejectingStudent.accountId(), "GROUP_INVITATION", rejectedInvitationId);

        authenticate(rejectingStudent.username(), RoleCode.STUDENT);
        groups.rejectInvitation(rejectedInvitationId, 0L);
        assertDelivery(
                "GROUP_INVITATION_REJECTED", rejectedInvitationId,
                leaderId, "GROUP", groupId);

        authenticate("student6.demo", RoleCode.STUDENT);
        groups.inviteMember(groupId, groupVersion(groupId), acceptingStudent.accountId());
        long acceptedInvitationId = pendingInvitationId(acceptingStudent.accountId());
        assertDelivery(
                "GROUP_INVITATION_CREATED", acceptedInvitationId,
                acceptingStudent.accountId(), "GROUP_INVITATION", acceptedInvitationId);

        authenticate(acceptingStudent.username(), RoleCode.STUDENT);
        groups.acceptInvitation(acceptedInvitationId, 0L);
        assertDelivery(
                "GROUP_INVITATION_ACCEPTED", acceptedInvitationId,
                leaderId, "GROUP", groupId);

        assertEquals(4, count("SELECT COUNT(*) FROM notification_events"));
        assertEquals(4, count("SELECT COUNT(*) FROM user_notifications"));
    }

    @Test
    void topicDecisionsNotifyAuthorAndRollbackWithTheSourceTransition() {
        long approvedTopicId = requiredLong(
                "SELECT id FROM topics WHERE title = '[DEMO] Trợ lý tra cứu quy chế'");
        long authorId = requiredLong(
                "SELECT author_id FROM topics WHERE id = ?", approvedTopicId);

        authenticate("dean.demo", RoleCode.DEAN, RoleCode.LECTURER);
        assertThrows(IllegalStateException.class,
                () -> rollbackProbe.approveThenFail(approvedTopicId, 0L));
        assertEquals("PENDING_APPROVAL", requiredString(
                "SELECT status FROM topics WHERE id = ?", approvedTopicId));
        assertEquals(0, count("SELECT COUNT(*) FROM notification_events"));

        topicReviews.approve(approvedTopicId, 0L);
        assertEquals("APPROVED", requiredString(
                "SELECT status FROM topics WHERE id = ?", approvedTopicId));
        assertDelivery(
                "TOPIC_PROPOSAL_APPROVED", approvedTopicId,
                authorId, "TOPIC", approvedTopicId);
        assertThrows(RuntimeException.class,
                () -> topicReviews.approve(approvedTopicId, 1L));
        assertEquals(1, eventCount("TOPIC_PROPOSAL_APPROVED", approvedTopicId));

        jdbc.update("""
                INSERT INTO topics(
                    period_id, author_id, department_id, title, description,
                    objectives, student_requirements, expected_results, status, version)
                SELECT period_id, author_id, department_id,
                       '[TEST] Đề tài bị từ chối', description, objectives,
                       student_requirements, expected_results, 'PENDING_APPROVAL', 0
                FROM topics WHERE id = ?
                """, approvedTopicId);
        long rejectedTopicId = requiredLong(
                "SELECT id FROM topics WHERE title = '[TEST] Đề tài bị từ chối'");
        topicReviews.reject(rejectedTopicId, 0L);
        assertDelivery(
                "TOPIC_PROPOSAL_REJECTED", rejectedTopicId,
                authorId, "TOPIC", rejectedTopicId);
    }

    @Test
    void acceptingRegistrationNotifiesSelectedAndAutoRejectedSnapshots() {
        authenticate("dean.demo", RoleCode.DEAN, RoleCode.LECTURER);
        long selectedId = firstPendingRegistrationId();
        long otherId = requiredLong("""
                SELECT registration.id
                FROM registrations registration
                WHERE registration.topic_id = (
                    SELECT topic_id FROM registrations WHERE id = ?)
                  AND registration.status = 'PENDING'
                  AND registration.id <> ?
                """, selectedId, selectedId);

        registrationDecisions.accept(selectedId, 0L);

        assertEquals("ACCEPTED", registrationStatus(selectedId));
        assertEquals("REJECTED", registrationStatus(otherId));
        assertEquals(3, deliveryCount("REGISTRATION_ACCEPTED", selectedId));
        assertEquals(3, deliveryCount("REGISTRATION_AUTO_REJECTED", otherId));
        assertEquals(0, outOfSnapshotDeliveryCount("REGISTRATION_ACCEPTED", selectedId));
        assertEquals(0, outOfSnapshotDeliveryCount("REGISTRATION_AUTO_REJECTED", otherId));

        assertThrows(RuntimeException.class,
                () -> registrationDecisions.accept(selectedId, 1L));
        assertEquals(1, eventCount("REGISTRATION_ACCEPTED", selectedId));
        assertEquals(1, eventCount("REGISTRATION_AUTO_REJECTED", otherId));
    }

    @Test
    void manualRegistrationRejectionNotifiesEverySnapshotStudent() {
        authenticate("dean.demo", RoleCode.DEAN, RoleCode.LECTURER);
        long registrationId = firstPendingRegistrationId();

        registrationDecisions.reject(registrationId, 0L, "Nội dung đăng ký chưa phù hợp");

        assertEquals("REJECTED", registrationStatus(registrationId));
        assertEquals(3, deliveryCount("REGISTRATION_REJECTED", registrationId));
        assertEquals(0, outOfSnapshotDeliveryCount("REGISTRATION_REJECTED", registrationId));
    }

    private long firstPendingRegistrationId() {
        return requiredLong("""
                SELECT registration.id
                FROM registrations registration
                JOIN topics topic ON topic.id = registration.topic_id
                WHERE topic.title = '[DEMO] Hệ thống quản lý đăng ký đề tài'
                  AND registration.status = 'PENDING'
                ORDER BY registration.id
                LIMIT 1
                """);
    }

    private long pendingInvitationId(long studentId) {
        return requiredLong("""
                SELECT id FROM group_invitations
                WHERE student_id = ? AND status = 'PENDING'
                """, studentId);
    }

    private long groupVersion(long groupId) {
        return requiredLong("SELECT version FROM student_groups WHERE id = ?", groupId);
    }

    private String registrationStatus(long registrationId) {
        return requiredString(
                "SELECT status FROM registrations WHERE id = ?", registrationId);
    }

    private int eventCount(String eventType, long targetId) {
        return count("""
                SELECT COUNT(*) FROM notification_events
                WHERE event_type = ? AND target_id = ?
                """, eventType, targetId);
    }

    private int deliveryCount(String eventType, long registrationId) {
        return count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = ? AND event.target_id = ?
                """, eventType, registrationId);
    }

    private int outOfSnapshotDeliveryCount(String eventType, long registrationId) {
        return count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = ? AND event.target_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM registration_members member
                      WHERE member.registration_id = ?
                        AND member.student_id = delivery.recipient_id)
                """, eventType, registrationId, registrationId);
    }

    private void assertDelivery(
            String eventType,
            long sourceId,
            long recipientId,
            String targetType,
            long targetId) {
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_type = ?
                  AND event.target_id = ?
                  AND event.target_type = ?
                  AND delivery.recipient_id = ?
                """, eventType, targetId, targetType, recipientId));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM notification_events WHERE event_key = ?",
                eventKey(eventType, sourceId)));
    }

    private static String eventKey(String eventType, long sourceId) {
        return switch (eventType) {
            case "GROUP_INVITATION_CREATED" -> "GROUP_INVITATION:" + sourceId + ":CREATED";
            case "GROUP_INVITATION_ACCEPTED" -> "GROUP_INVITATION:" + sourceId + ":ACCEPTED";
            case "GROUP_INVITATION_REJECTED" -> "GROUP_INVITATION:" + sourceId + ":REJECTED";
            case "TOPIC_PROPOSAL_APPROVED" -> "TOPIC:" + sourceId + ":APPROVED";
            case "TOPIC_PROPOSAL_REJECTED" -> "TOPIC:" + sourceId + ":REJECTED";
            default -> throw new IllegalArgumentException("Unsupported assertion event type");
        };
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
        private final TopicReviewService topics;

        RollbackProbe(TopicReviewService topics) {
            this.topics = topics;
        }

        @Transactional
        public void approveThenFail(long topicId, long expectedVersion) {
            topics.approve(topicId, expectedVersion);
            throw new IllegalStateException("Force source and notification rollback");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        @Primary
        Clock notificationIntegrationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RollbackProbe notificationRollbackProbe(TopicReviewService topics) {
            return new RollbackProbe(topics);
        }
    }
}
