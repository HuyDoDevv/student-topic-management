package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.concurrency.ConcurrentTestSupport;
import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.shared.time.TimeConfig;

class FoundationDatabaseIT {
    static DataSource dataSource;

    @BeforeAll
    static void migrateIsolatedTestDatabase() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        dataSource = database.dataSource();
    }

    @Test
    void migrationCreatesTheExpectedFoundationSchema() throws Exception {
        assertEquals(20, count("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(6, count("""
                SELECT COUNT(*)
                FROM information_schema.triggers
                WHERE trigger_schema = DATABASE()
                """));
        assertEquals(4, count("SELECT COUNT(*) FROM roles"));
    }

    @Test
    void rollbackLeavesNoPartialFoundationData() throws Exception {
        long countBefore = count("SELECT COUNT(*) FROM faculties");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO faculties(name) VALUES ('Rollback proof')");
            connection.rollback();
        }
        assertEquals(countBefore, count("SELECT COUNT(*) FROM faculties"));
    }

    @Test
    void deterministicFixturesCreateIdentityWithExplicitScope() throws Exception {
        Instant fixedNow = Instant.parse("2026-09-10T01:00:00Z");
        FixtureSupport fixtures = new FixtureSupport(
                dataSource, Clock.fixed(fixedNow, TimeConfig.BUSINESS_ZONE));
        FixtureSupport.OrganizationScope scope = fixtures.createOrganization(
                "Khoa fixture", "Bộ môn fixture");
        FixtureSupport.IdentityFixture identity = fixtures.createIdentity(
                "fixture-lecturer", "Giảng viên fixture", "unusable-fixture-hash",
                true, scope, Set.of(RoleCode.LECTURER));

        assertEquals(fixedNow, fixtures.now());
        assertEquals(scope.departmentId(), count("""
                SELECT department_id FROM user_accounts WHERE id = %d
                """.formatted(identity.accountId())));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM user_roles
                WHERE user_id = %d AND role_code = 'LECTURER'
                """.formatted(identity.accountId())));
    }

    @Test
    void concurrentHarnessUsesSeparateCoordinatedTransactions() throws Exception {
        long before = count("SELECT COUNT(*) FROM faculties");
        ConcurrentTestSupport.runInTransactions(2, dataSource, (worker, connection) -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO faculties(name) VALUES (?)")) {
                statement.setString(1, "Concurrent fixture " + worker);
                return statement.executeUpdate();
            }
        });
        assertEquals(before + 2, count("SELECT COUNT(*) FROM faculties"));
    }

    private static long count(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
