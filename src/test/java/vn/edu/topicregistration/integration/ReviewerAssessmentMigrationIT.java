package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class ReviewerAssessmentMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @AfterEach
    void restoreTestDatabase() {
        MySqlTestSupport.fromEnvironment().resetAndMigrate();
    }

    @Test
    void emptyV1DatabaseMigratesThroughV23AndRerunIsIdempotent() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV1 = flyway(database, "1", true);
        throughV1.clean();
        throughV1.migrate();

        Flyway throughV23 = flyway(database, "23", false);
        assertEquals(22, throughV23.migrate().migrationsExecuted);
        assertDoesNotThrow(throughV23::validate);
        assertEquals(0, throughV23.migrate().migrationsExecuted);
    }

    @Test
    void v21PreservesLegacyAssignmentsAndPublishedResultAndEnforcesNewConstraints() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV21 = flyway(database, "21", true);
        throughV21.clean();
        throughV21.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        Fixture fixture = seedV21Fixture(jdbc);

        List<Long> oldReviewers = jdbc.queryForList(
                "SELECT lecturer_id FROM reviewer_assignments WHERE registration_id = ? "
                        + "ORDER BY lecturer_id", Long.class, fixture.registrationId());
        List<String> oldResult = jdbc.queryForList("""
                SELECT CONCAT(status, ':', final_score, ':', evaluation_summary)
                FROM final_results WHERE registration_id = ? AND student_id = ?
                """, String.class, fixture.registrationId(), fixture.studentId());

        Flyway throughV23 = flyway(database, "23", false);
        assertEquals(2, throughV23.migrate().migrationsExecuted);
        assertDoesNotThrow(throughV23::validate);
        assertEquals(0, throughV23.migrate().migrationsExecuted);

        assertEquals(oldReviewers, jdbc.queryForList(
                "SELECT lecturer_id FROM reviewer_assignments WHERE registration_id = ? "
                        + "ORDER BY lecturer_id", Long.class, fixture.registrationId()));
        assertEquals(2L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM reviewer_assignments
                WHERE registration_id = ? AND status = 'LEGACY_UNVERIFIED'
                  AND assigned_by IS NULL AND submitted_at IS NULL
                """, Long.class, fixture.registrationId()));
        assertEquals(oldResult, jdbc.queryForList("""
                SELECT CONCAT(status, ':', final_score, ':', evaluation_summary)
                FROM final_results WHERE registration_id = ? AND student_id = ?
                """, String.class, fixture.registrationId(), fixture.studentId()));

        jdbc.update("""
                INSERT INTO reviewer_assignments(
                    registration_id, lecturer_id, deadline_date, status,
                    assigned_by, assigned_at)
                VALUES (?, ?, '2027-02-01', 'ASSIGNED', ?, '2027-01-01 00:00:00')
                """, fixture.registrationId(), fixture.reviewerA(), fixture.reviewerB());
        long assignmentId = requiredLong(jdbc, """
                SELECT id FROM reviewer_assignments
                WHERE registration_id = ? AND status = 'ASSIGNED'
                """, fixture.registrationId());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO reviewer_assignments(
                    registration_id, lecturer_id, deadline_date, status,
                    assigned_by, assigned_at)
                VALUES (?, ?, '2027-02-01', 'ASSIGNED', ?, '2027-01-01 00:00:00')
                """, fixture.registrationId(), fixture.reviewerB(), fixture.reviewerA()));

        jdbc.update("""
                INSERT INTO reviewer_evaluations(
                    assignment_id, registration_id, student_id, score)
                VALUES (?, ?, ?, 8.50)
                """, assignmentId, fixture.registrationId(), fixture.studentId());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO reviewer_evaluations(
                    assignment_id, registration_id, student_id, score)
                VALUES (?, ?, ?, 8.00)
                """, assignmentId, fixture.registrationId(), fixture.studentId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO reviewer_evaluations(
                    assignment_id, registration_id, student_id, score)
                VALUES (?, ?, ?, 8.00)
                """, assignmentId, fixture.registrationId(), fixture.reviewerB()));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO reviewer_evaluations(
                    assignment_id, registration_id, student_id, score)
                VALUES (?, ?, ?, 10.50)
                """, assignmentId, fixture.registrationId(), fixture.studentId()));

        jdbc.update("""
                INSERT INTO reviewer_reports(
                    assignment_id, file_name, storage_key, content_type,
                    size_bytes, submitted_by, submitted_at)
                VALUES (?, 'review.pdf', 'review-storage-key', 'application/pdf',
                    1024, ?, '2027-01-02 00:00:00')
                """, assignmentId, fixture.reviewerA());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO reviewer_reports(
                    assignment_id, file_name, storage_key, content_type,
                    size_bytes, submitted_by, submitted_at)
                VALUES (?, 'other.pdf', 'other-key', 'application/pdf',
                    1024, ?, '2027-01-02 00:00:00')
                """, assignmentId, fixture.reviewerA()));
    }

    private static Fixture seedV21Fixture(JdbcTemplate jdbc) {
        long facultyId = requiredLong(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'");
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn T009')",
                facultyId);
        long departmentId = requiredLong(jdbc,
                "SELECT id FROM departments WHERE name = 'Bộ môn T009'");
        for (String username : List.of("t009.author", "t009.reviewer.a",
                "t009.reviewer.b", "t009.student", "t009.student.2",
                "t009.student.3")) {
            jdbc.update("""
                    INSERT INTO user_accounts(
                        full_name, username, password_hash, faculty_id, department_id)
                    VALUES (?, ?, 'NOT_A_LOGIN_HASH', ?, ?)
                    """, username, username, facultyId, departmentId);
        }
        long authorId = account(jdbc, "t009.author");
        long reviewerA = account(jdbc, "t009.reviewer.a");
        long reviewerB = account(jdbc, "t009.reviewer.b");
        long studentId = account(jdbc, "t009.student");
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, department_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date)
                VALUES (?, ?, 'T009 period', 'KLTN',
                    '2027-01-01', '2027-01-02', '2027-01-03', '2027-01-10',
                    '2027-02-01', '2027-02-02')
                """, facultyId, departmentId);
        long periodId = requiredLong(jdbc,
                "SELECT id FROM registration_periods WHERE name = 'T009 period'");
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id, title)
                VALUES (?, ?, ?, 'T009 topic')
                """, periodId, departmentId, authorId);
        long topicId = requiredLong(jdbc, "SELECT id FROM topics WHERE title = 'T009 topic'");
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id) VALUES (?, ?)",
                periodId, studentId);
        long groupId = requiredLong(jdbc,
                "SELECT id FROM student_groups WHERE leader_student_id = ?", studentId);
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, 2), (?, ?, ?, 3)
                """, groupId, periodId, account(jdbc, "t009.student.2"),
                groupId, periodId, account(jdbc, "t009.student.3"));
        jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status, decided_at)
                VALUES (?, ?, ?, 'ACCEPTED', '2027-01-11 00:00:00')
                """, periodId, groupId, topicId);
        long registrationId = requiredLong(jdbc,
                "SELECT id FROM registrations WHERE topic_id = ?", topicId);
        jdbc.update("""
                INSERT INTO reviewer_assignments(registration_id, lecturer_id)
                VALUES (?, ?), (?, ?)
                """, registrationId, reviewerA, registrationId, reviewerB);

        jdbc.update("""
                INSERT INTO councils(period_id, name, chair_id, secretary_id)
                VALUES (?, 'T009 council', ?, ?)
                """, periodId, authorId, reviewerA);
        long councilId = requiredLong(jdbc,
                "SELECT id FROM councils WHERE name = 'T009 council'");
        jdbc.update("""
                INSERT INTO council_topics(council_id, registration_id, period_id)
                VALUES (?, ?, ?)
                """, councilId, registrationId, periodId);
        jdbc.update("""
                INSERT INTO final_results(
                    registration_id, student_id, council_id, consolidated_by,
                    consolidated_at, evaluation_summary, final_score,
                    review_completed_at, grading_completed_at,
                    status, published_by, published_at)
                VALUES (?, ?, ?, ?, '2027-01-12 01:00:00', 'historical result', 8.75,
                    '2027-01-12 00:00:00', '2027-01-12 00:00:00',
                    'PUBLISHED', ?, '2027-01-13 00:00:00')
                """, registrationId, studentId, councilId, authorId, authorId);
        return new Fixture(registrationId, studentId, reviewerA, reviewerB);
    }

    private static long account(JdbcTemplate jdbc, String username) {
        return requiredLong(jdbc,
                "SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private static long requiredLong(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Missing migration fixture value");
        }
        return value;
    }

    private static Flyway flyway(MySqlTestSupport database, String target, boolean cleanEnabled) {
        return Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(!cleanEnabled)
                .load();
    }

    private record Fixture(long registrationId, long studentId, long reviewerA, long reviewerB) {
    }
}
