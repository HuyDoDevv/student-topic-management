package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.util.ProjectTime;

/** Migrate only an explicitly configured, isolated MySQL test database. */
class SchemaSeedMigrationIT {
    private static final Instant DEMO_NOW = Instant.parse("2026-09-13T03:00:00Z");
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @AfterEach
    void restoreCleanTestDatabase() {
        MySqlTestSupport.fromEnvironment().resetAndMigrate();
    }

    @Test
    void emptyDatabaseMigratesThroughV26WithEveryTableConstraintAndTrigger() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        assertEquals(Set.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26"),
                Set.copyOf(jdbc.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success = 1",
                        String.class)));
        assertDoesNotThrow(() -> flyway(database).validate());
        assertEquals(27, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(82, constraintCount(jdbc, "CHECK"));
        assertEquals(65, constraintCount(jdbc, "FOREIGN KEY"));
        assertEquals(32, constraintCount(jdbc, "UNIQUE"));
        assertEquals(28, constraintCount(jdbc, "PRIMARY KEY"));
        assertEquals(Set.of(
                "trg_councils_add_officers",
                "trg_councils_validate_officers",
                "trg_council_members_keep_officers",
                "trg_group_memberships_keep_leader",
                "trg_registrations_snapshot_members",
                "trg_registration_members_immutable_delete",
                "trg_registration_members_immutable_update",
                "trg_student_groups_add_leader",
                "trg_student_groups_validate_leader"),
                Set.copyOf(jdbc.queryForList("""
                        SELECT trigger_name FROM information_schema.triggers
                        WHERE trigger_schema = DATABASE()
                        """, String.class)));

        assertConstraint(jdbc, "chk_periods_windows", "CHECK");
        assertConstraint(jdbc, "chk_topics_submitted_content", "CHECK");
        assertConstraint(jdbc, "chk_topics_max_group_count", "CHECK");
        assertConstraint(jdbc, "uq_memberships_period_student", "UNIQUE");
        assertConstraint(jdbc, "fk_memberships_group_period", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_one_active_registration_per_group", "UNIQUE");
        assertConstraintAbsent(jdbc, "uq_one_accepted_group_per_topic");
        assertConstraint(jdbc, "uq_group_invitations_pending_student", "UNIQUE");
        assertConstraint(jdbc, "uq_group_invitations_pending_slot", "UNIQUE");
        assertConstraint(jdbc, "fk_registration_members_registration", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_registration_members_slot", "UNIQUE");
        assertConstraint(jdbc, "uq_registration_members_leader", "UNIQUE");
        assertConstraint(jdbc, "chk_registration_members_slot", "CHECK");
        assertConstraint(jdbc, "uq_project_reports_current_registration", "UNIQUE");
        assertConstraint(jdbc, "chk_project_reports_content_type", "CHECK");
        assertConstraint(jdbc, "fk_accounts_faculty", "FOREIGN KEY");
        assertConstraint(jdbc, "fk_departments_faculty", "FOREIGN KEY");
        assertConstraint(jdbc, "fk_periods_department_faculty", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_departments_id_faculty", "UNIQUE");
        assertConstraint(jdbc, "fk_accounts_department", "FOREIGN KEY");
        assertConstraint(jdbc, "fk_topics_department_required", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_departments_faculty_name", "UNIQUE");
        assertConstraint(jdbc, "uq_user_accounts_email", "UNIQUE");
        assertConstraint(jdbc, "uq_student_profiles_student_code", "UNIQUE");
        assertConstraint(jdbc, "fk_student_profiles_user", "FOREIGN KEY");
        assertConstraint(jdbc, "chk_user_accounts_security_version", "CHECK");
        assertConstraint(jdbc, "chk_councils_grading_mode_officers", "CHECK");
        assertConstraint(jdbc, "uq_council_topics_registration", "UNIQUE");
        assertConstraint(jdbc, "chk_evaluations_lifecycle", "CHECK");
        assertConstraint(jdbc, "uq_evaluations_student_actor_component", "UNIQUE");
        assertConstraint(jdbc, "fk_evaluations_registration_student", "FOREIGN KEY");
        assertConstraint(jdbc, "chk_final_results_lifecycle", "CHECK");
        assertConstraint(jdbc, "fk_final_results_registration_student", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_notification_events_key", "UNIQUE");
        assertConstraint(jdbc, "uq_notification_events_announcement", "UNIQUE");
        assertConstraint(jdbc, "uq_user_notifications_event_recipient", "UNIQUE");
        assertConstraint(jdbc, "fk_notification_events_actor", "FOREIGN KEY");
        assertConstraint(jdbc, "fk_user_notifications_recipient", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_reviewers_one_active_registration", "UNIQUE");
        assertConstraint(jdbc, "chk_reviewers_status", "CHECK");
        assertConstraint(jdbc, "fk_reviewers_assigned_by", "FOREIGN KEY");
        assertConstraint(jdbc, "fk_reviewers_replaced_by", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_reviewer_reports_assignment", "UNIQUE");
        assertConstraint(jdbc, "chk_reviewer_reports_content_type", "CHECK");
        assertConstraint(jdbc, "uq_reviewer_evaluations_assignment_student", "UNIQUE");
        assertConstraint(jdbc, "fk_reviewer_evaluations_member", "FOREIGN KEY");
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
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'topics'
                  AND column_name = 'department_id' AND is_nullable = 'NO'
                """));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'topics'
                  AND column_name = 'max_group_count' AND is_nullable = 'NO'
                  AND column_default = '1'
                """));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'registration_periods'
                  AND column_name = 'department_id' AND is_nullable = 'NO'
                """));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'councils'
                  AND column_name = 'department_id'
                """));
        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM roles"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM user_accounts"),
                "Flyway must not enable the optional demo seed");
    }

    @Test
    void explicitDemoSeedIsIdempotentAndV7SnapshotsAreImmutable() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        LocalDemoSeeder missingSecret = new LocalDemoSeeder(
                jdbc,
                new BCryptPasswordEncoder(4),
                new ProjectTime(Clock.fixed(DEMO_NOW, ZoneOffset.UTC)),
                new DemoSeedProperties(true, null));
        assertThrows(IllegalStateException.class,
                () -> missingSecret.run(new DefaultApplicationArguments(new String[0])));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));

        DemoFixture fixture = demoFixture(jdbc);
        fixture.seeder().run(new DefaultApplicationArguments(new String[0]));

        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM faculties"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM departments"));
        assertEquals(12, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(7, count(jdbc, "SELECT COUNT(*) FROM student_profiles"));
        assertEquals(7, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts
                WHERE email LIKE '%@student.hcmute.edu.vn'
                """));
        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM registration_periods"));
        assertEquals(6, count(jdbc, "SELECT COUNT(*) FROM topics"));
        assertEquals(6, count(jdbc,
                "SELECT COUNT(*) FROM topics WHERE max_group_count = 1"));
        assertEquals(0, count(jdbc,
                "SELECT COUNT(*) FROM topics WHERE department_id IS NULL"));
        assertEquals(8, count(jdbc,
                "SELECT COUNT(*) FROM user_accounts WHERE department_id IS NULL"));
        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM student_groups"));
        assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM registrations"));
        assertEquals(9, count(jdbc, "SELECT COUNT(*) FROM registration_members"));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM (
                    SELECT registration_id FROM registration_members
                    GROUP BY registration_id
                    HAVING COUNT(*) <> 3 OR SUM(leader_at_submission) <> 1
                ) invalid_snapshot
                """));
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE username = 'dean.demo'",
                String.class);
        assertTrue(fixture.encoder().matches(fixture.password(), hash));
        String adminHash = jdbc.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE username = 'admin.demo'",
                String.class);
        assertTrue(fixture.encoder().matches(fixture.password(), adminHash));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM user_roles role_assignment
                JOIN user_accounts account ON account.id = role_assignment.user_id
                WHERE account.username = 'admin.demo' AND role_assignment.role_code = 'ADMIN'
                """));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM user_roles role_assignment
                JOIN user_accounts account ON account.id = role_assignment.user_id
                WHERE account.username = 'admin.demo'
                """));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM user_roles role_assignment
                JOIN user_accounts account ON account.id = role_assignment.user_id
                WHERE account.username = 'dean.demo' AND role_assignment.role_code = 'DEAN'
                """));
        assertEquals(1, count(jdbc, """
                SELECT COUNT(*) FROM user_roles role_assignment
                JOIN user_accounts account ON account.id = role_assignment.user_id
                WHERE account.username = 'dean.demo'
                """));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts
                WHERE username = 'admin.demo' AND faculty_id IS NOT NULL
                """));

        Long pendingId = jdbc.queryForObject(
                "SELECT id FROM registrations WHERE status = 'PENDING' ORDER BY id LIMIT 1",
                Long.class);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE registration_members SET slot = slot WHERE registration_id = ? LIMIT 1",
                pendingId));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "DELETE FROM registration_members WHERE registration_id = ? LIMIT 1",
                pendingId));

        Long singleMemberGroup = jdbc.queryForObject("""
                SELECT student_group.id FROM student_groups student_group
                JOIN group_memberships membership ON membership.group_id = student_group.id
                GROUP BY student_group.id
                HAVING COUNT(*) = 1
                LIMIT 1
                """, Long.class);
        Long periodId = jdbc.queryForObject(
                "SELECT period_id FROM student_groups WHERE id = ?", Long.class,
                singleMemberGroup);
        Long topicId = jdbc.queryForObject("""
                SELECT id FROM topics WHERE period_id = ? AND status = 'PUBLISHED'
                ORDER BY id LIMIT 1
                """, Long.class, periodId);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status, version)
                VALUES (?, ?, ?, 'PENDING', 0)
                """, periodId, singleMemberGroup, topicId));
        assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM registrations"));

        jdbc.update("""
                UPDATE registrations SET status = 'WITHDRAWN',
                    withdrawn_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, pendingId);
        Long seededPeriodId = jdbc.queryForObject(
                "SELECT period_id FROM registrations WHERE id = ?", Long.class, pendingId);
        jdbc.update("""
                UPDATE registration_periods
                SET student_end = DATE_ADD(student_end, INTERVAL 1 DAY)
                WHERE id = ?
                """, seededPeriodId);
        Timestamp extendedEnd = jdbc.queryForObject(
                "SELECT student_end FROM registration_periods WHERE id = ?",
                Timestamp.class, seededPeriodId);
        fixture.seeder().run(new DefaultApplicationArguments(new String[0]));
        assertEquals("WITHDRAWN", jdbc.queryForObject(
                "SELECT status FROM registrations WHERE id = ?", String.class, pendingId));
        assertEquals(extendedEnd, jdbc.queryForObject(
                "SELECT student_end FROM registration_periods WHERE id = ?",
                Timestamp.class, seededPeriodId));
        assertEquals(12, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM registrations"));
        assertEquals(9, count(jdbc, "SELECT COUNT(*) FROM registration_members"));
    }

    @Test
    void v6WithExistingFixtureUpgradesThroughV16WithoutLosingHistory() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV6 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATION_LOCATION)
                .target(MigrationVersion.fromVersion("6"))
                .cleanDisabled(false)
                .load();
        throughV6.clean();
        throughV6.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        seedLegacyV6Fixture(jdbc);
        assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM registrations"));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'registration_members'
                """));

        Long acceptedId = jdbc.queryForObject(
                "SELECT id FROM registrations WHERE status = 'ACCEPTED'", Long.class);
        Long historicalGroupId = jdbc.queryForObject(
                "SELECT group_id FROM registrations WHERE id = ?", Long.class, acceptedId);
        Long historicalLeader = jdbc.queryForObject(
                "SELECT leader_student_id FROM student_groups WHERE id = ?",
                Long.class, historicalGroupId);
        jdbc.update("UPDATE student_groups SET dissolved_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                historicalGroupId);
        jdbc.update("DELETE FROM group_memberships WHERE group_id = ?", historicalGroupId);
        Map<String, Integer> oldChecksums = appliedChecksums(jdbc);

        Flyway throughV16 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATION_LOCATION)
                .target(MigrationVersion.fromVersion("16"))
                .load();
        throughV16.migrate();
        assertDoesNotThrow(throughV16::validate);
        assertEquals(oldChecksums, appliedChecksums(jdbc).entrySet().stream()
                .filter(entry -> Integer.parseInt(entry.getKey()) <= 6)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(16, count(jdbc,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM registrations"));
        assertEquals(7, count(jdbc, "SELECT COUNT(*) FROM registration_members"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM registration_members
                WHERE registration_id = ? AND student_id = ?
                  AND slot = 1 AND leader_at_submission = TRUE
                """, Integer.class, acceptedId, historicalLeader));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration_members WHERE registration_id = ?",
                Integer.class, acceptedId));
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM (
                    SELECT registration_id FROM registration_members
                    GROUP BY registration_id
                    HAVING SUM(leader_at_submission) <> 1
                ) invalid_leader
                """));
        assertEquals(0, throughV16.migrate().migrationsExecuted);
    }

    private static void seedLegacyV6Fixture(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa fixture V6')");
        long facultyId = requiredId(jdbc,
                "SELECT id FROM faculties WHERE name = 'Khoa fixture V6'");
        jdbc.update("INSERT INTO departments(name, faculty_id) VALUES ('Bộ môn fixture V6', ?)",
                facultyId);
        long departmentId = requiredId(jdbc,
                "SELECT id FROM departments WHERE name = 'Bộ môn fixture V6'");

        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, department_id)
                VALUES
                  ('Giảng viên fixture', 'legacy.lecturer', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 1', 'legacy.student1', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 2', 'legacy.student2', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 3', 'legacy.student3', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 4', 'legacy.student4', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 5', 'legacy.student5', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 6', 'legacy.student6', 'NOT_A_LOGIN_HASH', ?),
                  ('Sinh viên fixture 7', 'legacy.student7', 'NOT_A_LOGIN_HASH', ?)
                """, departmentId, departmentId, departmentId, departmentId,
                departmentId, departmentId, departmentId, departmentId);
        long lecturerId = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.lecturer'");
        long student1 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student1'");
        long student2 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student2'");
        long student3 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student3'");
        long student4 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student4'");
        long student5 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student5'");
        long student6 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student6'");
        long student7 = requiredId(jdbc,
                "SELECT id FROM user_accounts WHERE username = 'legacy.student7'");

        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end)
                VALUES
                  (?, 'Đợt fixture V6 A', 'COURSE',
                      '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04'),
                  (?, 'Đợt fixture V6 B', 'COURSE',
                      '2026-02-01', '2026-02-02', '2026-02-03', '2026-02-04')
                """, facultyId, facultyId);
        long periodA = requiredId(jdbc,
                "SELECT id FROM registration_periods WHERE name = 'Đợt fixture V6 A'");
        long periodB = requiredId(jdbc,
                "SELECT id FROM registration_periods WHERE name = 'Đợt fixture V6 B'");
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id)
                VALUES (?, ?, ?), (?, ?, ?)
                """, periodA, departmentId, lecturerId,
                periodB, departmentId, lecturerId);
        long topicA = requiredId(jdbc,
                "SELECT id FROM topics WHERE period_id = ?", periodA);
        long topicB = requiredId(jdbc,
                "SELECT id FROM topics WHERE period_id = ?", periodB);

        jdbc.update("""
                INSERT INTO student_groups(period_id, leader_student_id)
                VALUES (?, ?), (?, ?), (?, ?)
                """, periodA, student1, periodA, student4, periodB, student6);
        long groupA1 = requiredId(jdbc,
                "SELECT id FROM student_groups WHERE period_id = ? AND leader_student_id = ?",
                periodA, student1);
        long groupA2 = requiredId(jdbc,
                "SELECT id FROM student_groups WHERE period_id = ? AND leader_student_id = ?",
                periodA, student4);
        long groupB = requiredId(jdbc,
                "SELECT id FROM student_groups WHERE period_id = ? AND leader_student_id = ?",
                periodB, student6);
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES
                  (?, ?, ?, 2), (?, ?, ?, 3),
                  (?, ?, ?, 2), (?, ?, ?, 3),
                  (?, ?, ?, 2), (?, ?, ?, 3)
                """,
                groupA1, periodA, student2, groupA1, periodA, student3,
                groupA2, periodA, student5, groupA2, periodA, student7,
                groupB, periodB, student1, groupB, periodB, student2);
        jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status)
                VALUES (?, ?, ?, 'PENDING'), (?, ?, ?, 'PENDING'), (?, ?, ?, 'ACCEPTED')
                """,
                periodA, groupA1, topicA,
                periodA, groupA2, topicA,
                periodB, groupB, topicB);
    }

    private static Flyway flyway(MySqlTestSupport database) {
        return Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATION_LOCATION)
                .load();
    }

    private static DemoFixture demoFixture(JdbcTemplate jdbc) {
        String password = UUID.randomUUID().toString();
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        LocalDemoSeeder seeder = new LocalDemoSeeder(
                jdbc,
                encoder,
                new ProjectTime(Clock.fixed(DEMO_NOW, ZoneOffset.UTC)),
                new DemoSeedProperties(true, password));
        return new DemoFixture(password, encoder, seeder);
    }

    private static Map<String, Integer> appliedChecksums(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT version, checksum FROM flyway_schema_history
                WHERE success = 1 ORDER BY installed_rank
                """, resultSet -> {
            Map<String, Integer> checksums = new HashMap<>();
            while (resultSet.next()) {
                checksums.put(resultSet.getString("version"), resultSet.getInt("checksum"));
            }
            return checksums;
        });
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static long requiredId(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Required migration fixture value is missing");
        }
        return value;
    }

    private static int constraintCount(JdbcTemplate jdbc, String type) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND constraint_type = ?
                """, Integer.class, type);
    }

    private static void assertConstraint(JdbcTemplate jdbc, String name, String type) {
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND constraint_name = ? AND constraint_type = ?
                """, Integer.class, name, type), name);
    }

    private static void assertConstraintAbsent(JdbcTemplate jdbc, String name) {
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND constraint_name = ?
                """, Integer.class, name), name);
    }

    private record DemoFixture(
            String password,
            PasswordEncoder encoder,
            LocalDemoSeeder seeder) {
    }
}
