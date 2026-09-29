package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationPersistenceIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void v12ThroughV15PreserveAnnouncementsAndEnforceNotificationInvariants() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV11 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("11"))
                .cleanDisabled(false)
                .load();
        throughV11.clean();
        throughV11.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa CNTT notification test')");
        long facultyId = requiredLong(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa CNTT notification test'");
        jdbc.update("""
                INSERT INTO departments(name, faculty_id)
                VALUES ('Bộ môn notification test', ?)
                """, facultyId);
        long departmentId = requiredLong(jdbc,
                "SELECT id FROM departments WHERE name = 'Bộ môn notification test'");
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, department_id)
                VALUES ('Publisher', 'notification.publisher', 'NOT_A_LOGIN_HASH', ?)
                """, departmentId);
        long publisherId = requiredLong(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'notification.publisher'");
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, department_id)
                VALUES ('Recipient', 'notification.recipient', 'NOT_A_LOGIN_HASH', ?)
                """, departmentId);
        long recipientId = requiredLong(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'notification.recipient'");
        jdbc.update("""
                INSERT INTO announcements(
                    title, body, source_name, faculty_id, published_by, published_at)
                VALUES ('Thông báo cũ', 'Nội dung cũ', 'Khoa CNTT', ?, ?, CURRENT_TIMESTAMP(6))
                """, facultyId, publisherId);
        long announcementId = requiredLong(jdbc,
                "SELECT id FROM announcements WHERE title = 'Thông báo cũ'");

        Flyway allVersions = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .load();
        assertEquals(4, allVersions.migrate().migrationsExecuted);
        assertDoesNotThrow(allVersions::validate);

        assertEquals(publisherId, requiredLong(jdbc,
                "SELECT created_by FROM announcements WHERE id = ?", announcementId));
        assertNotNull(jdbc.queryForObject(
                "SELECT created_at FROM announcements WHERE id = ?",
                java.sql.Timestamp.class,
                announcementId));
        assertEquals(0, requiredLong(jdbc,
                "SELECT version FROM announcements WHERE id = ?", announcementId));

        String eventKey = "ANNOUNCEMENT:" + announcementId + ":PUBLISHED";
        jdbc.update("""
                INSERT INTO notification_events(
                    event_key, category, event_type, title, body, actor_id,
                    announcement_id, target_type, target_id)
                VALUES (?, 'ANNOUNCEMENT', 'ANNOUNCEMENT_PUBLISHED',
                        'Thông báo cũ', 'Nội dung cũ', ?, ?, 'ANNOUNCEMENT', ?)
                """, eventKey,
                publisherId, announcementId, announcementId);
        long eventId = requiredLong(jdbc,
                "SELECT id FROM notification_events WHERE event_key = ?", eventKey);
        jdbc.update("""
                INSERT INTO user_notifications(event_id, recipient_id)
                VALUES (?, ?)
                """, eventId, recipientId);
        long notificationId = requiredLong(jdbc, """
                SELECT id FROM user_notifications
                WHERE event_id = ? AND recipient_id = ?
                """, eventId, recipientId);

        assertEquals(1, requiredLong(jdbc,
                "SELECT COUNT(*) FROM user_notifications WHERE id = ? AND read_at IS NULL",
                notificationId));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO notification_events(
                    event_key, category, event_type, title, body,
                    announcement_id, target_type, target_id)
                VALUES (?, 'ANNOUNCEMENT', 'ANNOUNCEMENT_PUBLISHED',
                        'Duplicate', 'Duplicate', ?, 'ANNOUNCEMENT', ?)
                """, eventKey,
                announcementId, announcementId));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO user_notifications(event_id, recipient_id)
                VALUES (?, ?)
                """, eventId, recipientId));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO notification_events(
                    event_key, category, event_type, title, body, target_type, target_id)
                VALUES ('INVALID:NONE', 'ACADEMIC_EVENT', 'RESULT_PUBLISHED',
                        'Invalid', 'Invalid', 'NONE', 1)
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                UPDATE user_notifications
                SET read_at = TIMESTAMPADD(SECOND, -1, delivered_at)
                WHERE id = ?
                """, notificationId));
    }

    private static long requiredLong(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Expected a database value");
        }
        return value;
    }
}
