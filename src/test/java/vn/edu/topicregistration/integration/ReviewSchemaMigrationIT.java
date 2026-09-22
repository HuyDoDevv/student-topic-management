package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.util.ProjectTime;

/** T008–T010: verifies review migrations against representative V7 rows. */
class ReviewSchemaMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Test
    void v7ReviewRowsAreBackfilledWithoutLosingFilesScoresOrResults() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV7 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("7"))
                .cleanDisabled(false)
                .load();
        throughV7.clean();
        throughV7.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        new LocalDemoSeeder(
                jdbc,
                new BCryptPasswordEncoder(4),
                new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)),
                new DemoSeedProperties(true, "not-a-real-test-password"))
                .run(new DefaultApplicationArguments(new String[0]));

        long registrationId = requiredLong(jdbc, """
                SELECT registration.id
                FROM registrations registration
                JOIN topics topic ON topic.id = registration.topic_id
                WHERE registration.status = 'ACCEPTED'
                  AND topic.title = '[DEMO] Nền tảng quản lý khóa luận'
                """);
        long periodId = requiredLong(jdbc,
                "SELECT period_id FROM registrations WHERE id = ?", registrationId);
        long leaderId = requiredLong(jdbc, """
                SELECT student_id FROM registration_members
                WHERE registration_id = ? AND leader_at_submission = TRUE
                """, registrationId);
        long chairId = account(jdbc, "lecturer1.demo");
        long secretaryId = account(jdbc, "dean.demo");
        long memberId = account(jdbc, "lecturer3.demo");

        jdbc.update("""
                INSERT INTO project_reports(
                    registration_id, registration_status, submitted_by, submitted_at,
                    file_name, storage_key, content_type, size_bytes)
                VALUES (?, 'ACCEPTED', ?, '2026-09-13 08:00:00.000000',
                        'old.pdf', 'legacy-old.pdf', 'application/pdf', 100)
                """, registrationId, leaderId);
        jdbc.update("""
                INSERT INTO project_reports(
                    registration_id, registration_status, submitted_by, submitted_at,
                    file_name, storage_key, content_type, size_bytes)
                VALUES (?, 'ACCEPTED', ?, '2026-09-14 08:00:00.000000',
                        'current.pdf', 'legacy-current.pdf', 'application/pdf', 200)
                """, registrationId, leaderId);

        jdbc.update("""
                INSERT INTO councils(period_id, name, chair_id, secretary_id)
                VALUES (?, 'Legacy review board', ?, ?)
                """, periodId, chairId, secretaryId);
        long councilId = requiredLong(jdbc,
                "SELECT id FROM councils WHERE name = 'Legacy review board'");
        jdbc.update("""
                INSERT INTO council_members(council_id, lecturer_id, slot)
                VALUES (?, ?, 3)
                """, councilId, memberId);
        jdbc.update("""
                INSERT INTO council_topics(
                    council_id, registration_id, period_id, registration_status,
                    scheduled_at, location)
                VALUES (?, ?, ?, 'ACCEPTED', '2026-09-20 02:00:00.000000', 'A1-01')
                """, councilId, registrationId, periodId);
        jdbc.update("""
                INSERT INTO score_components(period_id, name, minimum_score, maximum_score)
                VALUES (?, 'Điểm hội đồng', 0.00, 10.00)
                """, periodId);
        long componentId = requiredLong(jdbc, """
                SELECT id FROM score_components
                WHERE period_id = ? AND name = 'Điểm hội đồng'
                """, periodId);
        for (long evaluatorId : List.of(chairId, secretaryId, memberId)) {
            jdbc.update("""
                    INSERT INTO evaluations(
                        registration_id, period_id, registration_status, evaluator_id,
                        component_id, council_id, score, feedback, evaluated_at)
                    VALUES (?, ?, 'ACCEPTED', ?, ?, ?, 8.50, 'Legacy feedback',
                            '2026-09-21 02:00:00.000000')
                    """, registrationId, periodId, evaluatorId, componentId, councilId);
        }
        jdbc.update("""
                INSERT INTO final_results(
                    registration_id, registration_status, council_id, consolidated_by,
                    consolidated_at, evaluation_summary, final_score,
                    review_completed_at, grading_completed_at)
                VALUES (?, 'ACCEPTED', ?, ?, '2026-09-21 03:00:00.000000',
                        'Legacy summary', 8.50,
                        '2026-09-21 03:00:00.000000', '2026-09-21 03:00:00.000000')
                """, registrationId, councilId, chairId);

        Flyway allVersions = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .load();
        assertEquals(4, allVersions.migrate().migrationsExecuted);
        assertDoesNotThrow(allVersions::validate);

        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM councils WHERE id = ?", String.class, councilId));
        assertEquals("COUNCIL", jdbc.queryForObject(
                "SELECT grading_mode FROM councils WHERE id = ?", String.class, councilId));
        assertEquals(requiredLong(jdbc, """
                SELECT department_id FROM user_accounts WHERE id = ?
                """, chairId), requiredLong(jdbc,
                "SELECT department_id FROM councils WHERE id = ?", councilId));
        assertEquals(9, requiredLong(jdbc, """
                SELECT COUNT(*) FROM evaluations
                WHERE council_id = ? AND status = 'SUBMITTED' AND submitted_at IS NOT NULL
                """, councilId));
        assertEquals(3, requiredLong(jdbc, """
                SELECT COUNT(*) FROM final_results
                WHERE registration_id = ? AND status = 'READY'
                """, registrationId));
        assertEquals(0, requiredLong(jdbc, """
                SELECT COUNT(*) FROM evaluations evaluation
                LEFT JOIN registration_members member
                  ON member.registration_id = evaluation.registration_id
                 AND member.student_id = evaluation.student_id
                WHERE evaluation.council_id = ? AND member.student_id IS NULL
                """, councilId));
        assertEquals(1, requiredLong(jdbc, """
                SELECT COUNT(*) FROM project_reports
                WHERE registration_id = ? AND replaced_at IS NULL
                """, registrationId));
        assertNotNull(jdbc.queryForObject("""
                SELECT replaced_at FROM project_reports
                WHERE registration_id = ? ORDER BY submitted_at LIMIT 1
                """, java.sql.Timestamp.class, registrationId));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO project_reports(
                    registration_id, registration_status, submitted_by, submitted_at,
                    file_name, storage_key, content_type, size_bytes, version)
                VALUES (?, 'ACCEPTED', ?, CURRENT_TIMESTAMP(6),
                        'duplicate.pdf', 'duplicate.pdf', 'application/pdf', 10, 0)
                """, registrationId, leaderId));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE evaluations SET score = 8.123 WHERE council_id = ? LIMIT 1",
                councilId));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE final_results SET final_score = 8.123 WHERE registration_id = ?",
                registrationId));

        long departmentId = requiredLong(jdbc,
                "SELECT department_id FROM councils WHERE id = ?", councilId);
        jdbc.update("""
                INSERT INTO councils(
                    period_id, department_id, name, chair_id, secretary_id,
                    grading_mode, status, created_at, version)
                VALUES (?, ?, 'Direct grading fixture', ?, ?,
                        'DIRECT', 'ACTIVE', CURRENT_TIMESTAMP(6), 0)
                """, periodId, departmentId, memberId, memberId);
        long directId = requiredLong(jdbc,
                "SELECT id FROM councils WHERE name = 'Direct grading fixture'");
        assertEquals(1, requiredLong(jdbc,
                "SELECT COUNT(*) FROM council_members WHERE council_id = ?", directId));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO councils(
                    period_id, department_id, name, chair_id, secretary_id,
                    grading_mode, status, created_at, version)
                VALUES (?, ?, 'Invalid regular council', ?, ?,
                        'COUNCIL', 'DRAFT', CURRENT_TIMESTAMP(6), 0)
                """, periodId, departmentId, memberId, memberId));
    }

    private static long account(JdbcTemplate jdbc, String username) {
        return requiredLong(jdbc,
                "SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private static long requiredLong(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Required migration fixture value is missing");
        }
        return value;
    }
}
