package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** T001/T002/T012/T013: inventory, backfill, and period Department enforcement. */
class PeriodDepartmentMigrationIT {
    @AfterEach
    void restoreIsolatedTestDatabase() {
        MySqlTestSupport.fromEnvironment().resetAndMigrate();
    }

    @Test
    void classifiesEmptyUniqueMixedAndMismatchedLegacyPeriods() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        resetAndMigrateThrough(database, "19");
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

        Flyway throughV19 = Flyway.configure().dataSource(database.dataSource())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("19")).load();
        assertEquals(1, throughV19.migrate().migrationsExecuted);
        throughV19.validate();
        assertEquals(0, throughV19.migrate().migrationsExecuted);

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

    @Test
    void approvedLegacyPeriodBackfillsAcrossV18V19V20WithoutLosingRows() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        migrateThroughV18(database);
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        long web = department(jdbc, faculty, "Lập Trình Web");
        long chair = account(jdbc, "t012.chair", faculty, web);
        long secretary = account(jdbc, "t012.secretary", faculty, web);
        long student = student(jdbc, faculty, "leader");
        long secondStudent = student(jdbc, faculty, "member2");
        long thirdStudent = student(jdbc, faculty, "member3");
        long legacy = period(jdbc, faculty, "Đồ án môn học");
        assertEquals(1L, legacy);
        topic(jdbc, legacy, chair, web, chair);
        long topicId = id(jdbc, "SELECT id FROM topics WHERE period_id = ?", legacy);
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id) VALUES (?, ?)",
                legacy, student);
        long groupId = id(jdbc, "SELECT id FROM student_groups WHERE period_id = ?", legacy);
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, 2)
                """, groupId, legacy, secondStudent);
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, 3)
                """, groupId, legacy, thirdStudent);
        jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status)
                VALUES (?, ?, ?, 'ACCEPTED')
                """, legacy, groupId, topicId);
        long registrationId = id(jdbc, "SELECT id FROM registrations WHERE period_id = ?", legacy);
        jdbc.update("""
                INSERT INTO councils(period_id, name, chair_id, secretary_id)
                VALUES (?, 'Hội đồng T012', ?, ?)
                """, legacy, chair, secretary);
        long councilId = id(jdbc, "SELECT id FROM councils WHERE period_id = ?", legacy);
        jdbc.update("""
                INSERT INTO council_topics(council_id, registration_id, period_id)
                VALUES (?, ?, ?)
                """, councilId, registrationId, legacy);
        jdbc.update("INSERT INTO score_components(period_id, name) VALUES (?, 'Điểm T012')", legacy);
        long componentId = id(jdbc, "SELECT id FROM score_components WHERE period_id = ?", legacy);
        jdbc.update("""
                INSERT INTO evaluations(registration_id, student_id, period_id,
                    evaluator_id, component_id, council_id, score)
                VALUES (?, ?, ?, ?, ?, ?, 8.00)
                """, registrationId, student, legacy, secretary, componentId, councilId);
        jdbc.update("""
                INSERT INTO final_results(registration_id, student_id, council_id,
                    consolidated_by, consolidated_at, evaluation_summary, final_score,
                    review_completed_at, grading_completed_at)
                VALUES (?, ?, ?, ?, '2026-02-01 00:00:00', 'T012 fixture', 8.00,
                        '2026-02-01 00:00:00', '2026-02-01 00:00:00')
                """, registrationId, student, councilId, chair);
        long[] before = businessRowCounts(jdbc);

        Flyway throughV20 = flywayThrough(database, "20");
        assertEquals(2, throughV20.migrate().migrationsExecuted);
        throughV20.validate();
        assertEquals(0, throughV20.migrate().migrationsExecuted);

        assertEquals(web, id(jdbc,
                "SELECT department_id FROM registration_periods WHERE id = ?", legacy));
        assertArrayEquals(before, businessRowCounts(jdbc));
    }

    @Test
    void unapprovedAdditionalLegacyPeriodStopsBackfillWithoutChangingRows() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        migrateThroughV18(database);
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        department(jdbc, faculty, "Lập Trình Web");
        long approved = period(jdbc, faculty, "Đồ án môn học");
        long unapproved = period(jdbc, faculty, "Đợt chưa duyệt");
        long[] before = businessRowCounts(jdbc);

        assertThrows(FlywayException.class, () -> flywayThrough(database, "20").migrate());

        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, approved));
        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, unapproved));
        assertArrayEquals(before, businessRowCounts(jdbc));
    }

    @Test
    void conflictingTopicDepartmentStopsBackfillWithoutChangingRows() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        migrateThroughV18(database);
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        department(jdbc, faculty, "Lập Trình Web");
        long python = department(jdbc, faculty, "Lập Trình Python");
        long author = account(jdbc, "t012.python", faculty, python);
        long legacy = period(jdbc, faculty, "Đồ án môn học");
        topic(jdbc, legacy, author, python, author);
        long[] before = businessRowCounts(jdbc);

        assertThrows(FlywayException.class, () -> flywayThrough(database, "20").migrate());

        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, legacy));
        assertArrayEquals(before, businessRowCounts(jdbc));
    }

    @Test
    void changedLegacyIdentityStopsBackfillWithoutChangingRows() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        migrateThroughV18(database);
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        department(jdbc, faculty, "Lập Trình Web");
        long changed = period(jdbc, faculty, "Đợt khác");

        assertThrows(FlywayException.class, () -> flywayThrough(database, "20").migrate());

        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, changed));
    }

    @Test
    void conflictingSupervisorDepartmentStopsBackfillWithoutChangingRows() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        migrateThroughV18(database);
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        long web = department(jdbc, faculty, "Lập Trình Web");
        long python = department(jdbc, faculty, "Lập Trình Python");
        long author = account(jdbc, "t012.web", faculty, web);
        long supervisor = account(jdbc, "t012.python.supervisor", faculty, python);
        long legacy = period(jdbc, faculty, "Đồ án môn học");
        topic(jdbc, legacy, author, web, supervisor);

        assertThrows(FlywayException.class, () -> flywayThrough(database, "20").migrate());

        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class, legacy));
    }

    @Test
    void freshDatabaseWithoutHistoricalPeriodMigratesThroughV21() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        resetAndMigrateThrough(database, "21");
        Flyway throughV21 = flywayThrough(database, "21");
        throughV21.validate();
        assertEquals(0, throughV21.migrate().migrationsExecuted);
        assertEquals(0L, id(new JdbcTemplate(database.dataSource()),
                "SELECT COUNT(*) FROM registration_periods"));
        assertEquals(1L, id(new JdbcTemplate(database.dataSource()), """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'registration_periods'
                  AND column_name = 'department_id' AND is_nullable = 'NO'
                """));
    }

    @Test
    void v20DataUpgradesToV21WithoutLossAndEnforcesFacultyScope() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        migrateThroughV18(database);
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        long web = department(jdbc, faculty, "Lập Trình Web");
        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa T013 khác')");
        long otherFaculty = id(jdbc, "SELECT id FROM faculties WHERE name = 'Khoa T013 khác'");
        long otherDepartment = department(jdbc, otherFaculty, "Bộ môn T013 khác");
        long legacy = period(jdbc, faculty, "Đồ án môn học");
        assertEquals(1L, legacy);
        assertEquals(2, flywayThrough(database, "20").migrate().migrationsExecuted);
        long[] before = businessRowCounts(jdbc);

        Flyway throughV21 = flywayThrough(database, "21");
        assertEquals(1, throughV21.migrate().migrationsExecuted);
        throughV21.validate();
        assertEquals(0, throughV21.migrate().migrationsExecuted);
        assertArrayEquals(before, businessRowCounts(jdbc));
        assertEquals(web, id(jdbc,
                "SELECT department_id FROM registration_periods WHERE id = ?", legacy));
        assertEquals(1L, id(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'registration_periods'
                  AND column_name = 'department_id' AND is_nullable = 'NO'
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE registration_periods SET department_id = NULL WHERE id = ?", legacy));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE registration_periods SET department_id = ? WHERE id = ?",
                otherDepartment, legacy));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO registration_periods(faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end)
                VALUES (?, 'T013 missing Department', 'COURSE',
                    '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04')
                """, faculty));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO registration_periods(faculty_id, department_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end)
                VALUES (?, ?, 'T013 cross Faculty', 'COURSE',
                    '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04')
                """, faculty, otherDepartment));
        assertEquals(1L, id(jdbc,
                "SELECT COUNT(*) FROM registration_periods WHERE id = ? AND department_id = ?",
                legacy, web));
    }

    @Test
    void v21RejectsUnassignedPeriodBeforeChangingPermanentSchema() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        resetAndMigrateThrough(database, "20");
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        long unassigned = period(jdbc, faculty, "T013 chưa ánh xạ");
        long[] before = businessRowCounts(jdbc);

        assertThrows(FlywayException.class, () -> migrateThroughV21(database));

        assertEquals(1L, id(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'registration_periods'
                  AND column_name = 'department_id' AND is_nullable = 'YES'
                """));
        assertEquals(0L, id(jdbc, """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'departments'
                  AND index_name = 'uq_departments_id_faculty'
                """));
        assertNull(jdbc.queryForObject(
                "SELECT department_id FROM registration_periods WHERE id = ?", Long.class,
                unassigned));
        assertArrayEquals(before, businessRowCounts(jdbc));
    }

    @Test
    void v21RejectsCrossFacultyPeriodBeforeChangingPermanentSchema() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        resetAndMigrateThrough(database, "20");
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        long faculty = canonicalFaculty(jdbc);
        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa T013 ngoài phạm vi')");
        long otherFaculty = id(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa T013 ngoài phạm vi'");
        long otherDepartment = department(jdbc, otherFaculty, "Bộ môn T013 ngoài phạm vi");
        long period = period(jdbc, faculty, "T013 sai Khoa");
        jdbc.update("UPDATE registration_periods SET department_id = ? WHERE id = ?",
                otherDepartment, period);
        long[] before = businessRowCounts(jdbc);

        assertThrows(FlywayException.class, () -> migrateThroughV21(database));

        assertEquals(1L, id(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'registration_periods'
                  AND column_name = 'department_id' AND is_nullable = 'YES'
                """));
        assertEquals(0L, id(jdbc, """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'departments'
                  AND index_name = 'uq_departments_id_faculty'
                """));
        assertEquals(otherDepartment, id(jdbc,
                "SELECT department_id FROM registration_periods WHERE id = ?", period));
        assertArrayEquals(before, businessRowCounts(jdbc));
    }

    private static void migrateThroughV18(MySqlTestSupport database) {
        resetAndMigrateThrough(database, "18");
    }

    private static void resetAndMigrateThrough(MySqlTestSupport database, String version) {
        Flyway throughVersion = Flyway.configure().dataSource(database.dataSource())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .cleanDisabled(false).load();
        throughVersion.clean();
        throughVersion.migrate();
    }

    private static Flyway flywayThrough(MySqlTestSupport database, String version) {
        return Flyway.configure().dataSource(database.dataSource())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version)).load();
    }

    private static void migrateThroughV21(MySqlTestSupport database) {
        flywayThrough(database, "21").migrate();
    }

    private static long canonicalFaculty(JdbcTemplate jdbc) {
        return id(jdbc, "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'");
    }

    private static long department(JdbcTemplate jdbc, long faculty, String name) {
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, ?)", faculty, name);
        return id(jdbc, "SELECT id FROM departments WHERE faculty_id = ? AND name = ?",
                faculty, name);
    }

    private static long student(JdbcTemplate jdbc, long faculty, String suffix) {
        String username = "t012.student." + suffix;
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                VALUES ('Sinh viên T012', ?, 'NOT_A_LOGIN_HASH', ?)
                """, username, faculty);
        return id(jdbc, "SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private static long[] businessRowCounts(JdbcTemplate jdbc) {
        String[] tables = {"registration_periods", "topics", "topic_supervisors",
                "student_groups", "group_memberships", "registrations", "registration_members",
                "councils", "council_topics", "council_members",
                "score_components", "evaluations", "final_results"};
        long[] counts = new long[tables.length];
        for (int index = 0; index < tables.length; index++) {
            counts[index] = id(jdbc, "SELECT COUNT(*) FROM " + tables[index]);
        }
        return counts;
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
