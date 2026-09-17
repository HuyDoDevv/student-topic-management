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
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.util.ProjectTime;

/** T060/T008: migrate only an explicitly configured, isolated MySQL test database. */
class SchemaSeedMigrationIT {
    private static final Instant DEMO_NOW = Instant.parse("2026-09-13T03:00:00Z");
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Test
    void emptyDatabaseMigratesThroughV9WithEveryTableConstraintAndTrigger() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());

        assertEquals(Set.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
                Set.copyOf(jdbc.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success = 1",
                        String.class)));
        assertDoesNotThrow(() -> flyway(database).validate());
        assertEquals(22, count(jdbc, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(57, constraintCount(jdbc, "CHECK"));
        assertEquals(50, constraintCount(jdbc, "FOREIGN KEY"));
        assertEquals(21, constraintCount(jdbc, "UNIQUE"));
        assertEquals(23, constraintCount(jdbc, "PRIMARY KEY"));
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
        assertConstraint(jdbc, "uq_memberships_period_student", "UNIQUE");
        assertConstraint(jdbc, "fk_memberships_group_period", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_one_active_registration_per_group", "UNIQUE");
        assertConstraint(jdbc, "uq_one_accepted_group_per_topic", "UNIQUE");
        assertConstraint(jdbc, "uq_group_invitations_pending_student", "UNIQUE");
        assertConstraint(jdbc, "uq_group_invitations_pending_slot", "UNIQUE");
        assertConstraint(jdbc, "fk_registration_members_registration", "FOREIGN KEY");
        assertConstraint(jdbc, "uq_registration_members_slot", "UNIQUE");
        assertConstraint(jdbc, "uq_registration_members_leader", "UNIQUE");
        assertConstraint(jdbc, "chk_registration_members_slot", "CHECK");
        assertConstraint(jdbc, "uq_project_reports_current_registration", "UNIQUE");
        assertConstraint(jdbc, "chk_project_reports_content_type", "CHECK");
        assertConstraint(jdbc, "fk_councils_department", "FOREIGN KEY");
        assertConstraint(jdbc, "chk_councils_grading_mode_officers", "CHECK");
        assertConstraint(jdbc, "uq_council_topics_registration", "UNIQUE");
        assertConstraint(jdbc, "chk_evaluations_lifecycle", "CHECK");
        assertConstraint(jdbc, "chk_final_results_lifecycle", "CHECK");
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
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM departments"));
        assertEquals(12, count(jdbc, "SELECT COUNT(*) FROM user_accounts"));
        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM registration_periods"));
        assertEquals(6, count(jdbc, "SELECT COUNT(*) FROM topics"));
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
        assertEquals(0, count(jdbc, """
                SELECT COUNT(*) FROM user_accounts
                WHERE username = 'admin.demo' AND department_id IS NOT NULL
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
    void v6WithExistingFixtureUpgradesThroughV9WithoutLosingHistory() {
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
        demoFixture(jdbc).seeder().run(new DefaultApplicationArguments(new String[0]));
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

        Flyway allVersions = flyway(database);
        allVersions.migrate();
        assertDoesNotThrow(allVersions::validate);
        assertEquals(oldChecksums, appliedChecksums(jdbc).entrySet().stream()
                .filter(entry -> Integer.parseInt(entry.getKey()) <= 6)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(9, count(jdbc,
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
        assertEquals(0, allVersions.migrate().migrationsExecuted);
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

    private record DemoFixture(
            String password,
            PasswordEncoder encoder,
            LocalDemoSeeder seeder) {
    }
}
