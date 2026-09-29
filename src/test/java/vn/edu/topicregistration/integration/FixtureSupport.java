package vn.edu.topicregistration.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import javax.sql.DataSource;

import vn.edu.topicregistration.entity.RoleCode;

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

    public DataSource dataSource() {
        return dataSource;
    }

    public OrganizationScope createOrganization(String facultyName) {
        try (Connection connection = dataSource.getConnection()) {
            long facultyId = insertReturningId(connection,
                    "INSERT INTO faculties(name) VALUES (?)", facultyName);
            return new OrganizationScope(facultyId);
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
                        INSERT INTO user_accounts(full_name, enabled, username, password_hash, faculty_id)
                        VALUES (?, ?, ?, ?, ?)
                        """, fullName, enabled, username, passwordHash,
                        scope == null ? null : scope.facultyId());
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

    public IdentityFixture createStudentIdentity(
            String studentCode,
            String fullName,
            String className,
            String cohortCode,
            String passwordHash,
            boolean enabled,
            OrganizationScope scope) {
        IdentityFixture identity = createIdentity(
                studentCode, fullName, passwordHash, enabled, scope, Set.of(RoleCode.STUDENT));
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement account = connection.prepareStatement(
                    "UPDATE user_accounts SET email = ? WHERE id = ?")) {
                account.setString(1, studentCode.toLowerCase(java.util.Locale.ROOT)
                        + "@student.hcmute.edu.vn");
                account.setLong(2, identity.accountId());
                account.executeUpdate();
            }
            try (PreparedStatement profile = connection.prepareStatement("""
                    INSERT INTO student_profiles(user_id, student_code, class_name, cohort_code)
                    VALUES (?, ?, ?, ?)
                    """)) {
                profile.setLong(1, identity.accountId());
                profile.setString(2, studentCode);
                profile.setString(3, className);
                profile.setString(4, cohortCode);
                profile.executeUpdate();
            }
            return identity;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create student profile fixture", exception);
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

    public record OrganizationScope(long facultyId) {
    }

    public record IdentityFixture(
            long accountId,
            String username,
            OrganizationScope organization,
            Set<RoleCode> roles) {
    }
}
