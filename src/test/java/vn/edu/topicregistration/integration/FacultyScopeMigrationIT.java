package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies V13 consolidation and V16's non-destructive department restoration. */
class FacultyScopeMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void v12FixtureUpgradesThroughV18WithoutLosingBusinessRows() {
        verifyUpgradeToV18("12");
    }

    @Test
    void v15FixtureUpgradesThroughV18WithoutLosingBusinessRows() {
        verifyUpgradeToV18("15");
    }

    private static void verifyUpgradeToV18(String startingVersion) {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway starting = Flyway.configure().dataSource(database.dataSource())
                .locations(MIGRATIONS).target(MigrationVersion.fromVersion(startingVersion))
                .cleanDisabled(false).load();
        starting.clean();
        starting.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty;
        if ("12".equals(startingVersion)) {
            jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa legacy T010')");
            faculty = requiredLong(jdbc,
                    "SELECT id FROM faculties WHERE name = 'Khoa legacy T010'");
            jdbc.update("INSERT INTO departments(name, faculty_id) VALUES ('Bộ môn legacy', ?)",
                    faculty);
            long oldDepartment = requiredLong(jdbc,
                    "SELECT id FROM departments WHERE name = 'Bộ môn legacy'");
            jdbc.update("""
                    INSERT INTO user_accounts(full_name, username, password_hash, department_id)
                    VALUES ('T010 author', 't010.author', 'NOT_A_LOGIN_HASH', ?),
                           ('T010 secretary', 't010.secretary', 'NOT_A_LOGIN_HASH', ?)
                    """, oldDepartment, oldDepartment);
        } else {
            faculty = requiredLong(jdbc,
                    "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'");
            jdbc.update("""
                    INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                    VALUES ('T010 author', 't010.author', 'NOT_A_LOGIN_HASH', ?),
                           ('T010 secretary', 't010.secretary', 'NOT_A_LOGIN_HASH', ?)
                    """, faculty, faculty);
        }
        long author = account(jdbc, "t010.author");
        long secretary = account(jdbc, "t010.secretary");
        jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'LECTURER'), (?, 'LECTURER')",
                author, secretary);
        jdbc.update("""
                INSERT INTO registration_periods(faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date)
                VALUES (?, 'Đợt T010', 'KLTN', '2026-01-01', '2026-01-02',
                    '2026-01-03', '2026-01-04', '2026-01-10', '2026-01-15')
                """, faculty);
        long period = requiredLong(jdbc,
                "SELECT id FROM registration_periods WHERE name = 'Đợt T010'");
        if ("12".equals(startingVersion)) {
            long oldDepartment = requiredLong(jdbc,
                    "SELECT id FROM departments WHERE name = 'Bộ môn legacy'");
            jdbc.update("INSERT INTO topics(period_id, department_id, author_id) VALUES (?, ?, ?)",
                    period, oldDepartment, author);
            jdbc.update("""
                    INSERT INTO councils(period_id, department_id, name, chair_id, secretary_id)
                    VALUES (?, ?, 'Hội đồng T010', ?, ?)
                    """, period, oldDepartment, author, secretary);
        } else {
            jdbc.update("INSERT INTO topics(period_id, author_id) VALUES (?, ?)", period, author);
            jdbc.update("""
                    INSERT INTO councils(period_id, name, chair_id, secretary_id)
                    VALUES (?, 'Hội đồng T010', ?, ?)
                    """, period, author, secretary);
        }
        long accountsBefore = count(jdbc, "SELECT COUNT(*) FROM user_accounts");
        long topicsBefore = count(jdbc, "SELECT COUNT(*) FROM topics");
        long councilsBefore = count(jdbc, "SELECT COUNT(*) FROM councils");
        Flyway throughV18 = Flyway.configure().dataSource(database.dataSource())
                .locations(MIGRATIONS).target(MigrationVersion.fromVersion("18")).load();
        throughV18.migrate();
        assertDoesNotThrow(throughV18::validate);
        assertEquals(accountsBefore, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(topicsBefore, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(councilsBefore, count(jdbc, "SELECT COUNT(*) FROM councils"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM topics WHERE department_id IS NULL"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM user_accounts WHERE username LIKE 't010.%' AND department_id IS NULL"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'councils' AND column_name = 'department_id'"));
        assertEquals(0, throughV18.migrate().migrationsExecuted);
    }

    @Test
    void v13ConsolidatesDataAndV16RestoresNullableDepartmentScope() {
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
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date)
                VALUES
                  (?, 'Đợt cũ A', 'KLTN', '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04',
                      '2026-01-10', '2026-01-15'),
                  (?, 'Đợt cũ B', 'COURSE', '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04',
                      NULL, NULL)
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

        Flyway throughV15 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("15"))
                .load();
        assertEquals(3, throughV15.migrate().migrationsExecuted);

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

        Flyway throughV16 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("16"))
                .load();
        assertEquals(1, throughV16.migrate().migrationsExecuted);
        assertDoesNotThrow(throughV16::validate);

        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'departments'
                """));
        assertEquals(2, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('user_accounts', 'topics')
                  AND column_name = 'department_id'
                """));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'councils'
                  AND column_name = 'department_id'
                """));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM departments"),
                "V16 must not invent a department for legacy data");
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM councils"));
        assertEquals(4, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts WHERE username LIKE 'legacy.%'
                """));
        assertEquals(4, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts
                WHERE username LIKE 'legacy.%' AND department_id IS NULL
                """));
        assertEquals(2, count(jdbc, """
                SELECT COUNT(*) FROM topics WHERE department_id IS NULL
                """));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO departments(name, faculty_id) VALUES ('Không hợp lệ', -1)
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO departments(name, faculty_id) VALUES ('   ', ?)
                """, canonicalFaculty));
        jdbc.update("""
                INSERT INTO departments(name, faculty_id) VALUES ('Công nghệ phần mềm', ?)
                """, canonicalFaculty);
        long restoredDepartment = requiredLong(jdbc, """
                SELECT id FROM departments
                WHERE faculty_id = ? AND name = 'Công nghệ phần mềm'
                """, canonicalFaculty);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO departments(name, faculty_id) VALUES ('Công nghệ phần mềm', ?)
                """, canonicalFaculty));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                UPDATE user_accounts SET department_id = -1 WHERE username = 'legacy.a1'
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                UPDATE topics SET department_id = -1 WHERE id = (
                    SELECT id FROM (SELECT id FROM topics ORDER BY id LIMIT 1) existing_topic
                )
                """));
        jdbc.update("""
                UPDATE user_accounts SET department_id = ? WHERE username = 'legacy.a1'
                """, restoredDepartment);
        jdbc.update("""
                UPDATE topics SET department_id = ? ORDER BY id LIMIT 1
                """, restoredDepartment);

        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa mở rộng tương lai')");
        long futureFaculty = requiredLong(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa mở rộng tương lai'");
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                VALUES ('Giảng viên tương lai', 'future.lecturer', 'NOT_A_LOGIN_HASH', ?)
                """, futureFaculty);
        assertEquals(futureFaculty, requiredLong(jdbc,
                "SELECT faculty_id FROM user_accounts WHERE username = 'future.lecturer'"));
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM councils"));
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
