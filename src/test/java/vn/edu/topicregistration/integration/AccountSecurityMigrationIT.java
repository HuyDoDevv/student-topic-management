package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the V15 account security-state upgrade contract. */
class AccountSecurityMigrationIT {
    private static final String MIGRATIONS = "classpath:db/migration";

    @Test
    void v15PreservesExistingAccountsWithSafeDefaults() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        Flyway throughV14 = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("14"))
                .cleanDisabled(false)
                .load();
        throughV14.clean();
        throughV14.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash)
                VALUES ('Tài khoản cũ', 'legacy.security', 'NOT_A_LOGIN_HASH')
                """);

        Flyway allVersions = Flyway.configure()
                .dataSource(database.dataSource())
                .locations(MIGRATIONS)
                .target(MigrationVersion.fromVersion("15"))
                .load();
        assertEquals(1, allVersions.migrate().migrationsExecuted);
        assertDoesNotThrow(allVersions::validate);

        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_accounts WHERE username = ?",
                Boolean.class, "legacy.security");
        Long securityVersion = jdbc.queryForObject(
                "SELECT security_version FROM user_accounts WHERE username = ?",
                Long.class, "legacy.security");
        assertFalse(Boolean.TRUE.equals(mustChange));
        assertEquals(0L, securityVersion);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE user_accounts SET security_version = -1 WHERE username = ?",
                "legacy.security"));
    }
}
