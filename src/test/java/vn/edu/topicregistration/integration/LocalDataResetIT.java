package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Verifies the local reset SQL only against the guarded disposable test database. */
class LocalDataResetIT {
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();
    private static final List<String> EMPTY_TABLES = List.of(
            "announcements", "council_members", "council_topics", "councils",
            "departments", "evaluations", "final_results", "group_invitations",
            "group_memberships", "notification_events", "project_reports",
            "registration_members", "registration_periods", "registrations",
            "reviewer_assignments", "reviewer_evaluations", "reviewer_reports",
            "score_components", "student_groups", "student_profiles",
            "topic_supervisors", "topics", "user_notifications");
    private static final List<String> GUARDED_TRIGGERS = List.of(
            "trg_council_members_keep_officers",
            "trg_group_memberships_keep_leader",
            "trg_registration_members_immutable_delete",
            "trg_registration_members_immutable_update");

    @TempDir
    Path reportRoot;

    @Test
    void keepsOnlyAdminFacultyAndSystemMetadataAfterReset() throws Exception {
        DATABASE.resetAndMigrate();
        new ReviewFixtureSupport(
                DATABASE.dataSource(),
                Clock.fixed(Instant.parse("2026-09-14T08:00:00Z"), ZoneOffset.UTC),
                reportRoot)
                .seed("local-reset-test-only");
        JdbcTemplate jdbc = new JdbcTemplate(DATABASE.dataSource());
        long adminId = count(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'admin'");
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE id = ?",
                String.class, adminId);
        long facultyId = count(jdbc,
                "SELECT id FROM faculties WHERE BINARY name = BINARY 'Khoa Công nghệ Thông tin'");
        assertTrue(count(jdbc, "SELECT COUNT(*) FROM registrations") > 0);
        assertTrue(count(jdbc, "SELECT COUNT(*) FROM reviewer_reports") > 0);

        Map<String, String> triggerDefinitions = triggerDefinitions(jdbc);
        assertEquals(GUARDED_TRIGGERS, List.copyOf(triggerDefinitions.keySet()));
        try {
            for (String trigger : GUARDED_TRIGGERS) {
                jdbc.execute("DROP TRIGGER " + trigger);
            }
            try (var connection = DATABASE.dataSource().getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new FileSystemResource(Path.of("scripts/clear-all-data.sql")));
            }
        } finally {
            for (String trigger : GUARDED_TRIGGERS) {
                jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
            for (String definition : triggerDefinitions.values()) {
                jdbc.execute(definition);
            }
        }
        assertEquals(triggerDefinitions, triggerDefinitions(jdbc));

        for (String table : EMPTY_TABLES) {
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM " + table), table);
        }
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(adminId, count(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'admin'"));
        assertEquals(passwordHash, jdbc.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE id = ?",
                String.class, adminId));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM user_roles"));
        assertEquals("ADMIN", jdbc.queryForObject(
                "SELECT role_code FROM user_roles WHERE user_id = ?",
                String.class, adminId));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM faculties"));
        assertEquals(facultyId, count(jdbc,
                "SELECT id FROM faculties WHERE BINARY name = BINARY 'Khoa Công nghệ Thông tin'"));
        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM roles"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static Map<String, String> triggerDefinitions(JdbcTemplate jdbc) {
        Map<String, String> definitions = new LinkedHashMap<>();
        for (String trigger : GUARDED_TRIGGERS) {
            String definition = jdbc.queryForObject("SHOW CREATE TRIGGER " + trigger,
                    (result, row) -> result.getString("SQL Original Statement"));
            definitions.put(trigger, definition);
        }
        return definitions;
    }
}
