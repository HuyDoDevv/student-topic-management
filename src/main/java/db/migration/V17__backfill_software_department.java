package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Owner-approved assignment of missing staff/topic departments to Công nghệ phần mềm. */
public final class V17__backfill_software_department extends BaseJavaMigration {
    private static final String FACULTY_NAME = "Khoa Công nghệ Thông tin";
    private static final String DEPARTMENT_NAME = "Công nghệ phần mềm";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        long facultyId = requiredFacultyId(connection);
        long softwareId = findOrCreateSoftwareDepartment(connection, facultyId);
        Map<Long, Long> departmentFaculties = departmentFaculties(connection);
        Map<Long, AccountScope> accounts = accountScopes(connection, facultyId, softwareId,
                departmentFaculties);
        Map<Long, TopicScope> topics = topicScopes(connection, facultyId, softwareId,
                departmentFaculties, accounts);
        validateSupervisors(connection, accounts, topics);
        updateMissingAccounts(connection, accounts, softwareId);
        updateMissingTopics(connection, topics, softwareId);
    }

    private static long requiredFacultyId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM faculties WHERE name = ?")) {
            statement.setString(1, FACULTY_NAME);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("T007 requires the canonical Faculty");
                }
                long id = rows.getLong(1);
                if (rows.next()) {
                    throw new IllegalStateException("T007 found duplicate canonical Faculties");
                }
                return id;
            }
        }
    }

    private static long findOrCreateSoftwareDepartment(Connection connection, long facultyId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = ?")) {
            statement.setLong(1, facultyId);
            statement.setString(2, DEPARTMENT_NAME);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return rows.getLong(1);
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO departments(faculty_id, name) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, facultyId);
            statement.setString(2, DEPARTMENT_NAME);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("T007 could not create the Department");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("T007 Department ID was not returned");
                }
                return keys.getLong(1);
            }
        }
    }

    private static Map<Long, Long> departmentFaculties(Connection connection) throws SQLException {
        Map<Long, Long> faculties = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, faculty_id FROM departments");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                faculties.put(rows.getLong(1), rows.getLong(2));
            }
        }
        return faculties;
    }

    private static Map<Long, AccountScope> accountScopes(Connection connection, long facultyId,
            long softwareId, Map<Long, Long> departmentFaculties) throws SQLException {
        Set<Long> staffIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DISTINCT user_id FROM user_roles
                WHERE role_code IN ('LECTURER', 'DEAN')
                """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                staffIds.add(rows.getLong(1));
            }
        }
        Map<Long, AccountScope> accounts = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, faculty_id, department_id FROM user_accounts");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong(1);
                Long accountFaculty = nullableLong(rows, 2);
                Long currentDepartment = nullableLong(rows, 3);
                if (currentDepartment != null && !Objects.equals(
                        departmentFaculties.get(currentDepartment), accountFaculty)) {
                    throw new IllegalStateException("T007 account has a Department in another Faculty: " + id);
                }
                boolean missingStaffDepartment = staffIds.contains(id) && currentDepartment == null;
                if (missingStaffDepartment && !Objects.equals(accountFaculty, facultyId)) {
                    throw new IllegalStateException("T007 staff needs a separate Faculty mapping: " + id);
                }
                accounts.put(id, new AccountScope(accountFaculty,
                        missingStaffDepartment ? Long.valueOf(softwareId) : currentDepartment,
                        missingStaffDepartment));
            }
        }
        return accounts;
    }

    private static Map<Long, TopicScope> topicScopes(Connection connection, long facultyId,
            long softwareId, Map<Long, Long> departmentFaculties,
            Map<Long, AccountScope> accounts) throws SQLException {
        Map<Long, TopicScope> topics = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT topic.id, topic.department_id, topic.author_id, period.faculty_id
                FROM topics topic JOIN registration_periods period ON period.id = topic.period_id
                """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong(1);
                Long currentDepartment = nullableLong(rows, 2);
                long authorId = rows.getLong(3);
                long periodFaculty = rows.getLong(4);
                boolean missingDepartment = currentDepartment == null;
                if (missingDepartment && periodFaculty != facultyId) {
                    throw new IllegalStateException("T007 topic needs a separate Faculty mapping: " + id);
                }
                long projectedDepartment = missingDepartment ? softwareId : currentDepartment;
                if (!Objects.equals(departmentFaculties.get(projectedDepartment), periodFaculty)) {
                    throw new IllegalStateException("T007 topic Department differs from period Faculty: " + id);
                }
                AccountScope author = accounts.get(authorId);
                if (author == null || !Objects.equals(author.facultyId(), periodFaculty)
                        || !Objects.equals(author.projectedDepartmentId(), projectedDepartment)) {
                    throw new IllegalStateException("T007 topic author has another Department: " + id);
                }
                topics.put(id, new TopicScope(periodFaculty, projectedDepartment,
                        missingDepartment));
            }
        }
        return topics;
    }

    private static void validateSupervisors(Connection connection, Map<Long, AccountScope> accounts,
            Map<Long, TopicScope> topics) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT topic_id, lecturer_id FROM topic_supervisors");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long topicId = rows.getLong(1);
                long lecturerId = rows.getLong(2);
                TopicScope topic = topics.get(topicId);
                AccountScope lecturer = accounts.get(lecturerId);
                if (topic == null || lecturer == null
                        || !Objects.equals(lecturer.facultyId(), topic.facultyId())
                        || !Objects.equals(lecturer.projectedDepartmentId(),
                                topic.projectedDepartmentId())) {
                    throw new IllegalStateException(
                            "T007 supervisor has another Department for topic: " + topicId);
                }
            }
        }
    }

    private static void updateMissingAccounts(Connection connection, Map<Long, AccountScope> accounts,
            long softwareId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE user_accounts SET department_id = ? WHERE id = ? AND department_id IS NULL")) {
            for (Map.Entry<Long, AccountScope> entry : accounts.entrySet()) {
                if (entry.getValue().missingStaffDepartment()) {
                    statement.setLong(1, softwareId);
                    statement.setLong(2, entry.getKey());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("T007 account changed during backfill: "
                                + entry.getKey());
                    }
                }
            }
        }
    }

    private static void updateMissingTopics(Connection connection, Map<Long, TopicScope> topics,
            long softwareId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE topics SET department_id = ? WHERE id = ? AND department_id IS NULL")) {
            for (Map.Entry<Long, TopicScope> entry : topics.entrySet()) {
                if (entry.getValue().missingDepartment()) {
                    statement.setLong(1, softwareId);
                    statement.setLong(2, entry.getKey());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("T007 topic changed during backfill: "
                                + entry.getKey());
                    }
                }
            }
        }
    }

    private static Long nullableLong(ResultSet rows, int column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private record AccountScope(Long facultyId, Long projectedDepartmentId,
            boolean missingStaffDepartment) {
    }

    private record TopicScope(long facultyId, long projectedDepartmentId,
            boolean missingDepartment) {
    }
}
