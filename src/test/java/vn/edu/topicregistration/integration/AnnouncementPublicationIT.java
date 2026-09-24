package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.integration.FixtureSupport.IdentityFixture;
import vn.edu.topicregistration.integration.FixtureSupport.OrganizationScope;
import vn.edu.topicregistration.service.AnnouncementService;
import vn.edu.topicregistration.service.AnnouncementService.DraftCommand;

@SpringBootTest
@ActiveProfiles("local")
@Import(AnnouncementPublicationIT.FixedClockConfiguration.class)
class AnnouncementPublicationIT {
    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    AnnouncementService announcements;

    @Autowired
    JdbcTemplate jdbc;

    private IdentityFixture admin;
    private IdentityFixture dean;
    private IdentityFixture facultyStudent;
    private IdentityFixture otherFacultyStudent;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        FixtureSupport fixtures = new FixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC));
        OrganizationScope it = fixtures.createOrganization("Khoa Công nghệ thông tin", "Bộ môn CNPM");
        OrganizationScope civil = fixtures.createOrganization("Khoa Xây dựng", "Bộ môn Công trình");
        admin = fixtures.createIdentity(
                "notification.admin", "Quản trị", "NOT_A_LOGIN_HASH", true, null, Set.of(RoleCode.ADMIN));
        dean = fixtures.createIdentity(
                "notification.dean", "Trưởng khoa CNTT", "NOT_A_LOGIN_HASH", true, it,
                Set.of(RoleCode.LECTURER, RoleCode.DEAN));
        facultyStudent = fixtures.createIdentity(
                "notification.student.it", "Sinh viên CNTT", "NOT_A_LOGIN_HASH", true, it,
                Set.of(RoleCode.STUDENT));
        otherFacultyStudent = fixtures.createIdentity(
                "notification.student.civil", "Sinh viên Xây dựng", "NOT_A_LOGIN_HASH", true, civil,
                Set.of(RoleCode.STUDENT));
        fixtures.createIdentity(
                "notification.disabled", "Tài khoản khóa", "NOT_A_LOGIN_HASH", false, it,
                Set.of(RoleCode.STUDENT));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminAndDeanPublishToEnabledScopeExactlyOnce() {
        authenticate(admin);
        var schoolDraft = announcements.create(new DraftCommand("Thông báo trường", "Nội dung trường"));
        var schoolPublication = announcements.publish(schoolDraft.id(), schoolDraft.version());
        assertEquals(3, schoolPublication.recipientCount());
        assertEquals(1, count("SELECT COUNT(*) FROM notification_events WHERE announcement_id = ?", schoolDraft.id()));
        assertEquals(3, count("SELECT COUNT(*) FROM user_notifications notification "
                + "JOIN notification_events event ON event.id = notification.event_id "
                + "WHERE event.announcement_id = ?", schoolDraft.id()));

        authenticate(dean);
        var facultyDraft = announcements.create(new DraftCommand("Thông báo khoa", "Nội dung khoa"));
        var facultyPublication = announcements.publish(facultyDraft.id(), facultyDraft.version());
        assertEquals(1, facultyPublication.recipientCount());
        assertEquals(facultyStudent.accountId(), requiredLong("""
                SELECT notification.recipient_id
                FROM user_notifications notification
                JOIN notification_events event ON event.id = notification.event_id
                WHERE event.announcement_id = ?
                """, facultyDraft.id()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM user_notifications notification
                JOIN notification_events event ON event.id = notification.event_id
                WHERE event.announcement_id = ? AND notification.recipient_id = ?
                """, facultyDraft.id(), otherFacultyStudent.accountId()));

        ApplicationException repeat = assertThrows(ApplicationException.class,
                () -> announcements.publish(facultyDraft.id(), facultyPublication.version()));
        assertEquals(ApplicationErrorCode.CONFLICT, repeat.code());
        assertEquals(1, count("SELECT COUNT(*) FROM notification_events WHERE announcement_id = ?", facultyDraft.id()));
    }

    @Test
    void wrongScopeStaleVersionAndEmptyAudienceLeaveDraftUnpublished() {
        authenticate(admin);
        var schoolDraft = announcements.create(new DraftCommand("Thông báo trường", "Nội dung"));

        authenticate(dean);
        assertThrows(AccessDeniedException.class,
                () -> announcements.update(
                        schoolDraft.id(), schoolDraft.version(), new DraftCommand("Sai phạm vi", "Nội dung")));

        ApplicationException stale = assertThrows(ApplicationException.class,
                () -> announcements.update(
                        createFacultyDraft().id(), 99L, new DraftCommand("Stale", "Nội dung")));
        assertEquals(ApplicationErrorCode.CONFLICT, stale.code());

        jdbc.update("UPDATE user_accounts SET enabled = FALSE WHERE id <> ?", dean.accountId());
        var emptyAudienceDraft = createFacultyDraft();
        ApplicationException emptyAudience = assertThrows(ApplicationException.class,
                () -> announcements.publish(emptyAudienceDraft.id(), emptyAudienceDraft.version()));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, emptyAudience.code());
        assertEquals(0, count("SELECT COUNT(*) FROM announcements WHERE id = ? AND published_at IS NOT NULL",
                emptyAudienceDraft.id()));
        assertEquals(0, count("SELECT COUNT(*) FROM notification_events WHERE announcement_id = ?",
                emptyAudienceDraft.id()));
    }

    private AnnouncementService.AnnouncementView createFacultyDraft() {
        authenticate(dean);
        return announcements.create(new DraftCommand("Thông báo khoa", "Nội dung"));
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private long requiredLong(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Expected database value");
        }
        return value;
    }

    private static void authenticate(IdentityFixture identity) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        identity.username(), "unused", identity.roles().stream()
                                .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + role.name()))
                                .toList()));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock announcementClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
