package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies V13 consolidation from legacy department scope to direct faculty scope. */
class FacultyScopeMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void v13ConsolidatesExistingAcademicDataWithoutLosingBusinessRows() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV12 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("12"))
                .cleanDisabled(false)
                .load();
        throughV12.clean();
        throughV12.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa cũ A'), ('Khoa cũ B')");
        long facultyA = requiredLong(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa cũ A'");
        long facultyB = requiredLong(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa cũ B'");
        jdbc.update("""
                INSERT INTO departments(name, faculty_id)
                VALUES ('Bộ môn cũ A', ?), ('Bộ môn cũ B', ?)
                """, facultyA, facultyB);
        long departmentA = requiredLong(jdbc,
                "SELECT id FROM departments WHERE name = 'Bộ môn cũ A'");
        long departmentB = requiredLong(jdbc,
                "SELECT id FROM departments WHERE name = 'Bộ môn cũ B'");
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, department_id)
                VALUES
                  ('Giảng viên A1', 'legacy.a1', 'NOT_A_LOGIN_HASH', ?),
                  ('Giảng viên A2', 'legacy.a2', 'NOT_A_LOGIN_HASH', ?),
                  ('Giảng viên B', 'legacy.b', 'NOT_A_LOGIN_HASH', ?),
                  ('Quản trị', 'legacy.admin', 'NOT_A_LOGIN_HASH', NULL)
                """, departmentA, departmentA, departmentB);
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_code)
                SELECT id, 'LECTURER' FROM user_accounts WHERE username LIKE 'legacy.%' AND username <> 'legacy.admin'
                """);
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_code)
                SELECT id, 'ADMIN' FROM user_accounts WHERE username = 'legacy.admin'
                """);
        long lecturerA1 = account(jdbc, "legacy.a1");
        long lecturerA2 = account(jdbc, "legacy.a2");
        long lecturerB = account(jdbc, "legacy.b");
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end)
                VALUES
                  (?, 'Đợt cũ A', 'KLTN', '2026-01-01', '2026-01-02', '2026-01-02', '2026-01-03'),
                  (?, 'Đợt cũ B', 'COURSE', '2026-01-01', '2026-01-02', '2026-01-02', '2026-01-03')
                """, facultyA, facultyB);
        long periodA = requiredLong(jdbc,
                "SELECT id FROM registration_periods WHERE name = 'Đợt cũ A'");
        long periodB = requiredLong(jdbc,
                "SELECT id FROM registration_periods WHERE name = 'Đợt cũ B'");
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id)
                VALUES (?, ?, ?), (?, ?, ?)
                """, periodA, departmentA, lecturerA1, periodB, departmentB, lecturerB);
        jdbc.update("""
                INSERT INTO councils(period_id, department_id, name, chair_id, secretary_id)
                VALUES (?, ?, 'Hội đồng cũ A', ?, ?)
                """, periodA, departmentA, lecturerA1, lecturerA2);

        Flyway allVersions = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .load();
        assertEquals(1, allVersions.migrate().migrationsExecuted);
        assertDoesNotThrow(allVersions::validate);

        long canonicalFaculty = requiredLong(jdbc, """
                SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'
                """);
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM faculties"));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'departments'
                """));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('user_accounts', 'topics', 'councils')
                  AND column_name = 'department_id'
                """));
        assertEquals(3, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts
                WHERE faculty_id = %d
                """.formatted(canonicalFaculty)));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts
                WHERE username = 'legacy.admin' AND faculty_id IS NULL
                """));
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM registration_periods"));
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM councils"));

        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa mở rộng tương lai')");
        long futureFaculty = requiredLong(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa mở rộng tương lai'");
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                VALUES ('Giảng viên tương lai', 'future.lecturer', 'NOT_A_LOGIN_HASH', ?)
                """, futureFaculty);
        assertEquals(futureFaculty, requiredLong(jdbc,
                "SELECT faculty_id FROM user_accounts WHERE username = 'future.lecturer'"));
    }

    private static long account(JdbcTemplate jdbc, String username) {
        return requiredLong(jdbc,
                "SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return requiredLong(jdbc, sql);
    }

    private static long requiredLong(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Required migration fixture value is missing");
        }
        return value;
    }
}
