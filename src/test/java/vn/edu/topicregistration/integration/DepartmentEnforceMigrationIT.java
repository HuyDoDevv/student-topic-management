package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** T008: V18 enforces non-null topic departments only after validating V17 data. */
class DepartmentEnforceMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void validV17DataUpgradesAndRerunsWithoutChangingBusinessRows() {
        JdbcTemplate jdbc = resetToV17();
        long faculty = canonicalFaculty(jdbc);
        long software = softwareDepartment(jdbc);
        long author = account(jdbc, "t008.valid", faculty, software);
        long period = period(jdbc, faculty);
        long topic = topic(jdbc, period, author, software);
        long accountsBefore = count(jdbc, "SELECT COUNT(*) FROM user_accounts");
        long topicsBefore = count(jdbc, "SELECT COUNT(*) FROM topics");

        Flyway flyway = latest();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();

        assertEquals(software, jdbc.queryForObject(
                "SELECT department_id FROM topics WHERE id = ?", Long.class, topic));
        assertFalse(topicDepartmentNullable(jdbc));
        assertEquals(accountsBefore, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(topicsBefore, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertThrows(DataAccessException.class,
                () -> topic(jdbc, period, author, null));
    }

    @Test
    void nullTopicAfterV17BlocksEnforcementWithoutChangingSchema() {
        JdbcTemplate jdbc = resetToV17();
        long faculty = canonicalFaculty(jdbc);
        long author = account(jdbc, "t008.null", faculty, softwareDepartment(jdbc));
        long topic = topic(jdbc, period(jdbc, faculty), author, null);

        assertThrows(RuntimeException.class, () -> latest().migrate());

        assertEquals(0, count(jdbc,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '18' AND success = 1"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM topics WHERE id = ? AND department_id IS NULL",
                Long.class, topic));
        assertEquals(true, topicDepartmentNullable(jdbc));
    }

    @Test
    void authorDepartmentMismatchAfterV17BlocksEnforcement() {
        JdbcTemplate jdbc = resetToV17();
        long faculty = canonicalFaculty(jdbc);
        long software = softwareDepartment(jdbc);
        long author = account(jdbc, "t008.mismatch", faculty, software);
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn khác T008')", faculty);
        long other = jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = 'Bộ môn khác T008'",
                Long.class, faculty);
        topic(jdbc, period(jdbc, faculty), author, other);

        assertThrows(RuntimeException.class, () -> latest().migrate());

        assertEquals(true, topicDepartmentNullable(jdbc));
        assertEquals(0, count(jdbc,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '18' AND success = 1"));
    }

    private static JdbcTemplate resetToV17() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway flyway = Flyway.configure().dataSource(database.dataSource())
                .locations(MIGRATIONS).target(MigrationVersion.fromVersion("17"))
                .cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        return new JdbcTemplate(database.dataSource());
    }

    private static Flyway latest() {
        return Flyway.configure().dataSource(MySqlTestSupport.fromEnvironment().dataSource())
                .locations(MIGRATIONS).load();
    }

    private static long canonicalFaculty(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'", Long.class);
    }

    private static long softwareDepartment(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT id FROM departments WHERE name = 'Công nghệ phần mềm'", Long.class);
    }

    private static long account(JdbcTemplate jdbc, String username, long faculty, long department) {
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id, department_id)
                VALUES (?, ?, 'NOT_A_LOGIN_HASH', ?, ?)
                """, username, username, faculty, department);
        return jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = ?", Long.class, username);
    }

    private static long period(JdbcTemplate jdbc, long faculty) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type, lecturer_start, lecturer_end,
                    student_start, student_end)
                VALUES (?, 'Đợt T008', 'COURSE', '2026-01-01', '2026-01-02',
                    '2026-01-03', '2026-01-04')
                """, faculty);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM registration_periods", Long.class);
    }

    private static long topic(JdbcTemplate jdbc, long period, long author, Long department) {
        jdbc.update("INSERT INTO topics(period_id, author_id, department_id) VALUES (?, ?, ?)",
                period, author, department);
        return jdbc.queryForObject("SELECT MAX(id) FROM topics", Long.class);
    }

    private static boolean topicDepartmentNullable(JdbcTemplate jdbc) {
        return "YES".equals(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'topics'
                  AND column_name = 'department_id'
                """, String.class));
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
