package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** T007: the approved single-department backfill must preserve existing assignments. */
class DepartmentBackfillMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void backfillsOnlyMissingStaffAndTopicsIntoSoftwareDepartment() {
        JdbcTemplate jdbc = resetToV16();
        long faculty = canonicalFaculty(jdbc);
        long otherDepartment = department(jdbc, faculty, "Mạng máy tính");
        long author = account(jdbc, "legacy.author", faculty, null, "LECTURER");
        long dean = account(jdbc, "legacy.dean", faculty, null, "DEAN");
        long colleague = account(jdbc, "legacy.colleague", faculty, null, "LECTURER");
        long other = account(jdbc, "legacy.other", faculty, otherDepartment, "LECTURER");
        long student = account(jdbc, "legacy.student", faculty, null, "STUDENT");
        long admin = account(jdbc, "legacy.admin", null, null, "ADMIN");
        long period = period(jdbc, faculty);
        long missingTopic = topic(jdbc, period, author, null);
        long existingTopic = topic(jdbc, period, other, otherDepartment);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)",
                missingTopic, author);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 2)",
                missingTopic, colleague);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)",
                existingTopic, other);
        long accountCount = count(jdbc, "SELECT COUNT(*) FROM user_accounts");
        long topicCount = count(jdbc, "SELECT COUNT(*) FROM topics");

        Flyway flyway = latest();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();

        Long software = jdbc.queryForObject("""
                SELECT department.id FROM departments department
                WHERE department.faculty_id = ? AND department.name = 'Công nghệ phần mềm'
                """, Long.class, faculty);
        assertNotNull(software);
        for (long id : new long[] {author, dean, colleague}) {
            assertEquals(software, accountDepartment(jdbc, id));
        }
        assertEquals(otherDepartment, accountDepartment(jdbc, other));
        assertNull(accountDepartment(jdbc, student));
        assertNull(accountDepartment(jdbc, admin));
        assertEquals(software, topicDepartment(jdbc, missingTopic));
        assertEquals(otherDepartment, topicDepartment(jdbc, existingTopic));
        assertEquals(accountCount, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(topicCount, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(0, latest().migrate().migrationsExecuted);
    }

    @Test
    void conflictingExistingAuthorDepartmentStopsWithoutPartialBackfill() {
        JdbcTemplate jdbc = resetToV16();
        long faculty = canonicalFaculty(jdbc);
        long otherDepartment = department(jdbc, faculty, "Mạng máy tính");
        long author = account(jdbc, "legacy.conflict", faculty, otherDepartment, "LECTURER");
        long period = period(jdbc, faculty);
        long missingTopic = topic(jdbc, period, author, null);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)",
                missingTopic, author);

        assertThrows(RuntimeException.class, () -> latest().migrate());

        assertNull(topicDepartment(jdbc, missingTopic));
        assertEquals(otherDepartment, accountDepartment(jdbc, author));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM departments WHERE name = 'Công nghệ phần mềm'"));
    }

    @Test
    void existingSoftwareDepartmentIsReusedWithoutChangingItsId() {
        JdbcTemplate jdbc = resetToV16();
        long faculty = canonicalFaculty(jdbc);
        long software = department(jdbc, faculty, "Công nghệ phần mềm");
        long lecturer = account(jdbc, "legacy.reuse", faculty, null, "LECTURER");
        long missingTopic = topic(jdbc, period(jdbc, faculty), lecturer, null);

        assertEquals(1, latest().migrate().migrationsExecuted);

        assertEquals(software, accountDepartment(jdbc, lecturer));
        assertEquals(software, topicDepartment(jdbc, missingTopic));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM departments WHERE name = 'Công nghệ phần mềm'"));
    }

    @Test
    void conflictingSupervisorDepartmentStopsWithoutPartialBackfill() {
        JdbcTemplate jdbc = resetToV16();
        long faculty = canonicalFaculty(jdbc);
        long otherDepartment = department(jdbc, faculty, "Mạng máy tính");
        long author = account(jdbc, "legacy.supervised", faculty, null, "LECTURER");
        long colleague = account(jdbc, "legacy.wrong-supervisor", faculty,
                otherDepartment, "LECTURER");
        long missingTopic = topic(jdbc, period(jdbc, faculty), author, null);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 1)",
                missingTopic, author);
        jdbc.update("INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, 2)",
                missingTopic, colleague);

        assertThrows(RuntimeException.class, () -> latest().migrate());

        assertNull(accountDepartment(jdbc, author));
        assertNull(topicDepartment(jdbc, missingTopic));
        assertEquals(otherDepartment, accountDepartment(jdbc, colleague));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM departments WHERE name = 'Công nghệ phần mềm'"));
    }

    @Test
    void missingStaffDepartmentInAnotherFacultyStopsWithoutPartialBackfill() {
        JdbcTemplate jdbc = resetToV16();
        long faculty = canonicalFaculty(jdbc);
        long author = account(jdbc, "legacy.canonical", faculty, null, "LECTURER");
        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa ngoài phạm vi T007')");
        Long outsideFaculty = jdbc.queryForObject(
                "SELECT id FROM faculties WHERE name = 'Khoa ngoài phạm vi T007'", Long.class);
        account(jdbc, "legacy.outside", outsideFaculty, null, "LECTURER");

        assertThrows(RuntimeException.class, () -> latest().migrate());

        assertNull(accountDepartment(jdbc, author));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM departments WHERE name = 'Công nghệ phần mềm'"));
    }

    private static JdbcTemplate resetToV16() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV16 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("16"))
                .cleanDisabled(false)
                .load();
        throughV16.clean();
        throughV16.migrate();
        return new JdbcTemplate(database.dataSource());
    }

    private static Flyway latest() {
        return Flyway.configure()
                .dataSource(MySqlTestSupport.fromEnvironment().dataSource())
                .locations(MIGRATIONS)
                .load();
    }

    private static long canonicalFaculty(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'", Long.class);
    }

    private static long department(JdbcTemplate jdbc, long faculty, String name) {
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, ?)", faculty, name);
        return jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = ?", Long.class,
                faculty, name);
    }

    private static long account(JdbcTemplate jdbc, String username, Long faculty,
            Long department, String role) {
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id, department_id)
                VALUES (?, ?, 'NOT_A_LOGIN_HASH', ?, ?)
                """, username, username, faculty, department);
        long id = jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, ?)", id, role);
        return id;
    }

    private static long period(JdbcTemplate jdbc, long faculty) {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type, lecturer_start, lecturer_end,
                    student_start, student_end)
                VALUES (?, 'Đợt T007', 'COURSE', '2026-01-01', '2026-01-02',
                    '2026-01-03', '2026-01-04')
                """, faculty);
        return jdbc.queryForObject(
                "SELECT id FROM registration_periods WHERE name = 'Đợt T007'", Long.class);
    }

    private static long topic(JdbcTemplate jdbc, long period, long author, Long department) {
        jdbc.update("INSERT INTO topics(period_id, author_id, department_id) VALUES (?, ?, ?)",
                period, author, department);
        return jdbc.queryForObject("SELECT MAX(id) FROM topics", Long.class);
    }

    private static Long accountDepartment(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject(
                "SELECT department_id FROM user_accounts WHERE id = ?", Long.class, id);
    }

    private static Long topicDepartment(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT department_id FROM topics WHERE id = ?", Long.class, id);
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
