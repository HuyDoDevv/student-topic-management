package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DeanRoleSeparationMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @AfterEach
    void restoreTestDatabase() {
        MySqlTestSupport.fromEnvironment().resetAndMigrate();
    }

    @Test
    void v22RemovesOnlyLecturerRoleAndPreservesHistoricalReferences() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV21 = flyway(database, "21", true);
        throughV21.clean();
        throughV21.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        Long facultyId = jdbc.queryForObject(
                "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'", Long.class);
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn lịch sử')", facultyId);
        Long departmentId = jdbc.queryForObject(
                "SELECT id FROM departments WHERE name = 'Bộ môn lịch sử'", Long.class);
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id,
                                          department_id, security_version)
                VALUES ('Trưởng khoa cũ', 'legacy.dean', 'NOT_A_LOGIN_HASH', ?, ?, 3)
                """, facultyId, departmentId);
        Long deanId = jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = 'legacy.dean'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'DEAN')", deanId);
        jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'LECTURER')", deanId);
        jdbc.update("""
                INSERT INTO registration_periods
                    (faculty_id, department_id, name, topic_type,
                     lecturer_start, lecturer_end, student_start, student_end)
                VALUES (?, ?, 'Đợt lịch sử', 'COURSE',
                        '2027-01-01 00:00:00', '2027-01-10 00:00:00',
                        '2027-01-11 00:00:00', '2027-01-20 00:00:00')
                """, facultyId, departmentId);
        Long periodId = jdbc.queryForObject(
                "SELECT id FROM registration_periods WHERE name = 'Đợt lịch sử'", Long.class);
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id, title, status)
                VALUES (?, ?, ?, 'Đề tài cũ', 'DRAFT')
                """, periodId, departmentId, deanId);
        Long topicId = jdbc.queryForObject(
                "SELECT id FROM topics WHERE title = 'Đề tài cũ'", Long.class);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)",
                topicId, deanId);

        Flyway throughV22 = flyway(database, "22", false);
        assertEquals(1, throughV22.migrate().migrationsExecuted);
        assertDoesNotThrow(throughV22::validate);
        assertEquals(0, throughV22.migrate().migrationsExecuted);
        assertEquals(4L, jdbc.queryForObject(
                "SELECT security_version FROM user_accounts WHERE id = ?", Long.class, deanId));
        assertEquals("DEAN", jdbc.queryForObject(
                "SELECT role_code FROM user_roles WHERE user_id = ?", String.class, deanId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_accounts WHERE id = ?", Long.class, deanId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM topics WHERE id = ? AND author_id = ?", Long.class,
                topicId, deanId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM topic_supervisors WHERE topic_id = ? AND lecturer_id = ?",
                Long.class, topicId, deanId));
    }

    @Test
    void v22RejectsMultipleEnabledDeansInOneFacultyWithoutChangingRoles() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV21 = flyway(database, "21", true);
        throughV21.clean();
        throughV21.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        Long facultyId = jdbc.queryForObject(
                "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'", Long.class);
        for (int index = 1; index <= 2; index++) {
            String username = "duplicate.dean." + index;
            jdbc.update("""
                    INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                    VALUES ('Trưởng khoa trùng', ?, 'NOT_A_LOGIN_HASH', ?)
                    """, username, facultyId);
            Long id = jdbc.queryForObject(
                    "SELECT id FROM user_accounts WHERE username = ?", Long.class, username);
            jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'DEAN')", id);
            jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'LECTURER')", id);
        }

        assertThrows(FlywayException.class, () -> flyway(database, "22", false).migrate());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE role_code = 'LECTURER'", Long.class));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT SUM(security_version) FROM user_accounts WHERE username LIKE 'duplicate.dean.%'",
                Long.class));
    }

    private static Flyway flyway(MySqlTestSupport database, String target, boolean cleanEnabled) {
        return Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(!cleanEnabled)
                .load();
    }
}
