package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Verifies the clean-slate local baseline against the guarded MySQL test database. */
class SchemaSeedMigrationIT {
    private static final String ADMIN_PASSWORD = "123123123";

    @Test
    void baselineCreatesFinalSchemaAndOnlyTheLocalAdminBootstrapData() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        assertEquals(Set.of("1"), Set.copyOf(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1",
                String.class)));
        assertEquals(1, count(jdbc,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertDoesNotThrow(() -> flyway(database).validate());

        assertEquals(27, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(82, constraintCount(jdbc, "CHECK"));
        assertEquals(65, constraintCount(jdbc, "FOREIGN KEY"));
        assertEquals(32, constraintCount(jdbc, "UNIQUE"));
        assertEquals(28, constraintCount(jdbc, "PRIMARY KEY"));
        assertEquals(27, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND column_name = 'created_at'
                  AND is_nullable = 'NO'
                  AND UPPER(column_default) = 'CURRENT_TIMESTAMP(6)'
                """));
        assertEquals(27, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND column_name = 'updated_at'
                  AND is_nullable = 'NO'
                  AND UPPER(column_default) = 'CURRENT_TIMESTAMP(6)'
                  AND LOWER(extra) LIKE '%on update current_timestamp(6)%'
                """));
        assertEquals(Set.of(
                "trg_councils_add_officers",
                "trg_councils_validate_officers",
                "trg_council_members_keep_officers",
                "trg_group_memberships_keep_leader",
                "trg_registrations_snapshot_members",
                "trg_registration_members_immutable_delete",
                "trg_registration_members_immutable_update",
                "trg_student_groups_add_leader",
                "trg_student_groups_validate_leader"),
                Set.copyOf(jdbc.queryForList("""
                        SELECT trigger_name FROM information_schema.triggers
                        WHERE trigger_schema = DATABASE()
                        """, String.class)));

        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM roles"));
        assertEquals(Set.of("ADMIN", "STUDENT", "LECTURER", "DEAN"),
                Set.copyOf(jdbc.queryForList("SELECT code FROM roles", String.class)));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM faculties"));
        assertEquals("Khoa Công nghệ Thông tin", jdbc.queryForObject(
                "SELECT name FROM faculties", String.class));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM departments"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM user_roles"));

        AdminRow admin = jdbc.queryForObject("""
                SELECT account.full_name, account.enabled, account.username,
                       account.password_hash, account.must_change_password,
                       account.security_version, account.faculty_id, account.department_id,
                       assignment.role_code
                FROM user_accounts account
                JOIN user_roles assignment ON assignment.user_id = account.id
                """, (rows, rowNumber) -> new AdminRow(
                        rows.getString("full_name"),
                        rows.getBoolean("enabled"),
                        rows.getString("username"),
                        rows.getString("password_hash"),
                        rows.getBoolean("must_change_password"),
                        rows.getLong("security_version"),
                        rows.getObject("faculty_id", Long.class),
                        rows.getObject("department_id", Long.class),
                        rows.getString("role_code")));

        assertEquals("Quản trị viên", admin.fullName());
        assertTrue(admin.enabled());
        assertEquals("admin", admin.username());
        assertTrue(new BCryptPasswordEncoder().matches(ADMIN_PASSWORD, admin.passwordHash()));
        assertFalse(admin.mustChangePassword());
        assertEquals(0L, admin.securityVersion());
        assertNull(admin.facultyId());
        assertNull(admin.departmentId());
        assertEquals("ADMIN", admin.roleCode());
    }

    private static Flyway flyway(MySqlTestSupport database) {
        return Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MySqlTestSupport.MIGRATION_LOCATION)
                .load();
    }

    private static long constraintCount(JdbcTemplate jdbc, String type) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND constraint_type = ?
                """, Long.class, type);
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private record AdminRow(
            String fullName,
            boolean enabled,
            String username,
            String passwordHash,
            boolean mustChangePassword,
            long securityVersion,
            Long facultyId,
            Long departmentId,
            String roleCode) {
    }
}
