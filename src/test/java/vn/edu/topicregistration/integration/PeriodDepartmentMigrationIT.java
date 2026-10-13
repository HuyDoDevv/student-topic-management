package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** T001/T002: inventory evidence and non-destructive period Department expansion. */
class PeriodDepartmentMigrationIT {
    @AfterEach
    void restoreIsolatedTestDatabase() {
        MySqlTestSupport.fromEnvironment().resetAndMigrate();
    }

    @Test
    void classifiesEmptyUniqueMixedAndMismatchedLegacyPeriods() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa T001 inventory')");
        long faculty = id(jdbc, "SELECT id FROM faculties WHERE name = 'Khoa T001 inventory'");
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn T001 A')", faculty);
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn T001 B')", faculty);
        long departmentA = id(jdbc, "SELECT id FROM departments WHERE name = 'Bộ môn T001 A'");
        long departmentB = id(jdbc, "SELECT id FROM departments WHERE name = 'Bộ môn T001 B'");
        long authorA = account(jdbc, "t001.author.a", faculty, departmentA);
        long authorB = account(jdbc, "t001.author.b", faculty, departmentB);

        long empty = period(jdbc, faculty, "T001 empty");
        long unique = period(jdbc, faculty, "T001 unique");
        long mixed = period(jdbc, faculty, "T001 mixed");
        long mismatch = period(jdbc, faculty, "T001 mismatch");
        long supervisorMismatch = period(jdbc, faculty, "T001 supervisor mismatch");

        topic(jdbc, unique, authorA, departmentA, authorA);
        topic(jdbc, mixed, authorA, departmentA, authorA);
        topic(jdbc, mixed, authorB, departmentB, authorB);
        topic(jdbc, mismatch, authorB, departmentA, authorB);
        topic(jdbc, supervisorMismatch, authorA, departmentA, authorB);

        assertEquals("NO_EVIDENCE", classify(inventory(jdbc, empty)));
        assertEquals("SINGLE_CANDIDATE_REVIEW", classify(inventory(jdbc, unique)));
        assertEquals(List.of(departmentA), departmentIds(jdbc, unique));
        assertEquals("STOP_CONFLICT", classify(inventory(jdbc, mixed)));
        assertEquals(List.of(departmentA, departmentB), departmentIds(jdbc, mixed));
        assertEquals("STOP_CONFLICT", classify(inventory(jdbc, mismatch)));
        assertEquals("STOP_CONFLICT", classify(inventory(jdbc, supervisorMismatch)));
    }

    @Test
    void v18BusinessRowsUpgradeToNullableV19WithoutBackfill() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV18 = Flyway.configure().dataSource(database.dataSource())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("18"))
                .cleanDisabled(false).load();
        throughV18.clean();
        throughV18.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa T002 migration')");
        long faculty = id(jdbc, "SELECT id FROM faculties WHERE name = 'Khoa T002 migration'");
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn T002')", faculty);
        long department = id(jdbc, "SELECT id FROM departments WHERE name = 'Bộ môn T002'");
        long chair = account(jdbc, "t002.chair", faculty, department);
        long secretary = account(jdbc, "t002.secretary", faculty, department);
        long period = period(jdbc, faculty, "T002 legacy period");
        topic(jdbc, period, chair, department, chair);
        jdbc.update("""
                INSERT INTO councils(period_id, name, chair_id, secretary_id)
                VALUES (?, 'T002 legacy council', ?, ?)
                """, period, chair, secretary);
        long periodsBefore = id(jdbc, "SELECT COUNT(*) FROM registration_periods");
        long topicsBefore = id(jdbc, "SELECT COUNT(*) FROM topics");
        long councilsBefore = id(jdbc, "SELECT COUNT(*) FROM councils");

        Flyway latest = Flyway.configure().dataSource(database.dataSource())
                .locations("classpath:db/migration").load();
        assertEquals(1, latest.migrate().migrationsExecuted);
        latest.validate();
        assertEquals(0, latest.migrate().migrationsExecuted);

        assertEquals(periodsBefore, id(jdbc, "SELECT COUNT(*) FROM registration_periods"));
        assertEquals(topicsBefore, id(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(councilsBefore, id(jdbc, "SELECT COUNT(*) FROM councils"));
        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, period));
        assertEquals(1, id(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'registration_periods'
                  AND column_name = 'department_id' AND is_nullable = 'YES'
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE registration_periods SET department_id = -1 WHERE id = ?", period));
        jdbc.update("UPDATE registration_periods SET department_id = ? WHERE id = ?",
                department, period);
        assertEquals(department, jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, period));
    }

    private static Inventory inventory(JdbcTemplate jdbc, long periodId) {
        Inventory base = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT topic.id) AS topic_count,
                       COUNT(DISTINCT topic.department_id) AS department_count,
                       COUNT(DISTINCT CASE WHEN department.faculty_id IS NULL
                           OR department.faculty_id <> period.faculty_id
                           OR author.faculty_id IS NULL
                           OR author.faculty_id <> period.faculty_id
                           OR author.department_id IS NULL
                           OR author.department_id <> topic.department_id
                           THEN topic.id END) AS inconsistent_topics
                FROM registration_periods period
                LEFT JOIN topics topic ON topic.period_id = period.id
                LEFT JOIN departments department ON department.id = topic.department_id
                LEFT JOIN user_accounts author ON author.id = topic.author_id
                WHERE period.id = ?
                """, (rs, row) -> new Inventory(rs.getLong("topic_count"),
                        rs.getLong("department_count"),
                        rs.getLong("inconsistent_topics"), 0), periodId);
        long inconsistentSupervisors = id(jdbc, """
                SELECT COUNT(*) FROM topic_supervisors supervisor
                JOIN topics topic ON topic.id = supervisor.topic_id
                JOIN registration_periods period ON period.id = topic.period_id
                JOIN user_accounts lecturer ON lecturer.id = supervisor.lecturer_id
                WHERE period.id = ? AND (lecturer.faculty_id IS NULL
                    OR lecturer.faculty_id <> period.faculty_id
                    OR lecturer.department_id IS NULL
                    OR lecturer.department_id <> topic.department_id)
                """, periodId);
        return new Inventory(base.topicCount(), base.departmentCount(),
                base.inconsistentTopics(), inconsistentSupervisors);
    }

    private static String classify(Inventory inventory) {
        if (inventory.topicCount() == 0) {
            return "NO_EVIDENCE";
        }
        if (inventory.departmentCount() != 1 || inventory.inconsistentTopics() != 0
                || inventory.inconsistentSupervisors() != 0) {
            return "STOP_CONFLICT";
        }
        return "SINGLE_CANDIDATE_REVIEW";
    }

    private static List<Long> departmentIds(JdbcTemplate jdbc, long periodId) {
        return jdbc.queryForList("""
                SELECT DISTINCT topic.department_id FROM topics topic
                WHERE topic.period_id = ? ORDER BY topic.department_id
                """, Long.class, periodId);
    }

    private static long account(JdbcTemplate jdbc, String username, long faculty, long department) {
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id, department_id)
                VALUES ('T001 fixture', ?, 'NOT_A_LOGIN_HASH', ?, ?)
                """, username, faculty, department);
        return id(jdbc, "SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private static long period(JdbcTemplate jdbc, long faculty, String name) {
        jdbc.update("""
                INSERT INTO registration_periods(faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end)
                VALUES (?, ?, 'COURSE', '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04')
                """, faculty, name);
        return id(jdbc, "SELECT id FROM registration_periods WHERE name = ?", name);
    }

    private static void topic(JdbcTemplate jdbc, long period, long author, long department,
            long supervisor) {
        jdbc.update("INSERT INTO topics(period_id, department_id, author_id) VALUES (?, ?, ?)",
                period, department, author);
        long topic = id(jdbc, "SELECT MAX(id) FROM topics WHERE period_id = ?", period);
        jdbc.update("""
                INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)
                """, topic, supervisor);
    }

    private static long id(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private record Inventory(long topicCount, long departmentCount, long inconsistentTopics,
            long inconsistentSupervisors) {
    }
}
