package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the V14 account email and student-profile upgrade contract. */
class AccountProfileMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void v14PreservesAccountsAndEnforcesStudentIdentityConstraints() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV13 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("13"))
                .cleanDisabled(false)
                .load();
        throughV13.clean();
        throughV13.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                SELECT 'Sinh viên cũ', 'legacy.student', 'NOT_A_LOGIN_HASH', id
                FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'
                """);
        long legacyId = accountId(jdbc, "legacy.student");

        Flyway allVersions = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("14"))
                .load();
        assertEquals(1, allVersions.migrate().migrationsExecuted);
        assertDoesNotThrow(allVersions::validate);

        assertEquals(legacyId, accountId(jdbc, "legacy.student"));
        assertNull(jdbc.queryForObject(
                "SELECT email FROM user_accounts WHERE id = ?", String.class, legacyId));

        long firstStudent = insertAccount(jdbc, "23110001");
        long secondStudent = insertAccount(jdbc, "23110002");
        assertEquals(3L, count(jdbc,
                "SELECT COUNT(*) FROM user_accounts WHERE email IS NULL"));
        jdbc.update("UPDATE user_accounts SET email = ? WHERE id = ?",
                "23110001@student.hcmute.edu.vn", firstStudent);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE user_accounts SET email = ? WHERE id = ?",
                "23110001@student.hcmute.edu.vn", secondStudent));

        jdbc.update("""
                INSERT INTO student_profiles(user_id, student_code, class_name, cohort_code)
                VALUES (?, '23110001', '23110CL1', 'K2023')
                """, firstStudent);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO student_profiles(user_id, student_code, class_name, cohort_code)
                VALUES (?, '23110001', '23110CL2', 'K2024')
                """, secondStudent));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO student_profiles(user_id, student_code, class_name, cohort_code)
                VALUES (?, '23110002', '23110CL2', '2024')
                """, secondStudent));

        jdbc.update("DELETE FROM user_accounts WHERE id = ?", firstStudent);
        assertEquals(0L, count(jdbc,
                "SELECT COUNT(*) FROM student_profiles WHERE user_id = ?", firstStudent));
    }

    private static long insertAccount(JdbcTemplate jdbc, String username) {
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                SELECT ?, ?, 'NOT_A_LOGIN_HASH', id
                FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'
                """, "Sinh viên " + username, username);
        return accountId(jdbc, username);
    }

    private static long accountId(JdbcTemplate jdbc, String username) {
        Long value = jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = ?", Long.class, username);
        if (value == null) {
            throw new IllegalStateException("Expected account fixture");
        }
        return value;
    }

    private static long count(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }
}
