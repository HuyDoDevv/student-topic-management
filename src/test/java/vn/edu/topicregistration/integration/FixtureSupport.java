package vn.edu.topicregistration.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import javax.sql.DataSource;

import vn.edu.topicregistration.identity.domain.RoleCode;

public final class FixtureSupport {
    private final DataSource dataSource;
    private final Clock clock;

    public FixtureSupport(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public OrganizationScope createOrganization(String facultyName, String departmentName) {
        try (Connection connection = dataSource.getConnection()) {
            long facultyId = insertReturningId(connection,
                    "INSERT INTO faculties(name) VALUES (?)", facultyName);
            long departmentId = insertReturningId(connection,
                    "INSERT INTO departments(name, faculty_id) VALUES (?, ?)", departmentName, facultyId);
            return new OrganizationScope(facultyId, departmentId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create organization fixture", exception);
        }
    }

    public IdentityFixture createIdentity(String username, String fullName, String passwordHash,
            boolean enabled, OrganizationScope scope, Set<RoleCode> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("A fixture identity needs at least one role");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long accountId = insertReturningId(connection, """
                        INSERT INTO user_accounts(full_name, enabled, username, password_hash, department_id)
                        VALUES (?, ?, ?, ?, ?)
                        """, fullName, enabled, username, passwordHash,
                        scope == null ? null : scope.departmentId());
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO user_roles(user_id, role_code) VALUES (?, ?)")) {
                    for (RoleCode role : roles) {
                        statement.setLong(1, accountId);
                        statement.setString(2, role.name());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
                return new IdentityFixture(accountId, username, scope, Set.copyOf(roles));
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create identity fixture", exception);
        }
    }

    private static long insertReturningId(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Insert did not return an identifier");
                }
                return keys.getLong(1);
            }
        }
    }

    public record OrganizationScope(long facultyId, long departmentId) {
    }

    public record IdentityFixture(
            long accountId,
            String username,
            OrganizationScope organization,
            Set<RoleCode> roles) {
    }
}
