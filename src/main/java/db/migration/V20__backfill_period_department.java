package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Applies the owner-confirmed Department mapping for the legacy period only. */
public final class V20__backfill_period_department extends BaseJavaMigration {
    private static final long LEGACY_PERIOD_ID = 1L;
    private static final String LEGACY_PERIOD_NAME = "Đồ án môn học";
    private static final String FACULTY_NAME = "Khoa Công nghệ Thông tin";
    private static final String DEPARTMENT_NAME = "Lập Trình Web";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<PeriodScope> periods = periodsForUpdate(connection);
        PeriodScope legacy = null;
        for (PeriodScope period : periods) {
            if (period.id() == LEGACY_PERIOD_ID) {
                legacy = period;
            } else if (period.departmentId() == null) {
                throw new IllegalStateException(
                        "T012 has no approved Department mapping for period " + period.id());
            }
        }
        requireNoRows(connection, """
                SELECT COUNT(*) FROM registration_periods period
                LEFT JOIN departments department ON department.id = period.department_id
                WHERE period.department_id IS NOT NULL
                  AND (department.id IS NULL OR department.faculty_id <> period.faculty_id)
                """, "T012 found a period with a Department outside its Faculty");
        if (legacy == null) {
            return; // A fresh database has no historical period to backfill.
        }
        if (!LEGACY_PERIOD_NAME.equals(legacy.name())
                || !"COURSE".equals(legacy.topicType())
                || !FACULTY_NAME.equals(legacy.facultyName())) {
            throw new IllegalStateException("T012 legacy period identity changed");
        }
        long departmentId = requiredDepartmentId(connection, legacy.facultyId());
        if (legacy.departmentId() != null && legacy.departmentId() != departmentId) {
            throw new IllegalStateException("T012 legacy period already belongs to another Department");
        }
        requireNoRows(connection, """
                SELECT COUNT(*) FROM topics topic
                JOIN user_accounts author ON author.id = topic.author_id
                WHERE topic.period_id = 1
                  AND (topic.department_id IS NULL OR topic.department_id <> %d
                       OR author.faculty_id IS NULL OR author.faculty_id <> %d
                       OR author.department_id IS NULL OR author.department_id <> %d)
                """.formatted(departmentId, legacy.facultyId(), departmentId),
                "T012 legacy topics or authors do not belong to Lập Trình Web");
        requireNoRows(connection, """
                SELECT COUNT(*) FROM topic_supervisors supervisor
                JOIN topics topic ON topic.id = supervisor.topic_id
                JOIN user_accounts lecturer ON lecturer.id = supervisor.lecturer_id
                WHERE topic.period_id = 1
                  AND (lecturer.faculty_id IS NULL OR lecturer.faculty_id <> %d
                       OR lecturer.department_id IS NULL OR lecturer.department_id <> %d)
                """.formatted(legacy.facultyId(), departmentId),
                "T012 legacy supervisors do not belong to Lập Trình Web");
        if (legacy.departmentId() == null) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE registration_periods SET department_id = ?
                    WHERE id = ? AND department_id IS NULL
                    """)) {
                update.setLong(1, departmentId);
                update.setLong(2, LEGACY_PERIOD_ID);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("T012 legacy period changed during backfill");
                }
            }
        }
    }

    private static List<PeriodScope> periodsForUpdate(Connection connection) throws SQLException {
        List<PeriodScope> periods = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT period.id, period.faculty_id, period.department_id,
                       period.name, period.topic_type, faculty.name
                FROM registration_periods period
                JOIN faculties faculty ON faculty.id = period.faculty_id
                ORDER BY period.id FOR UPDATE
                """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long assigned = rows.getLong(3);
                boolean unassigned = rows.wasNull();
                periods.add(new PeriodScope(rows.getLong(1), rows.getLong(2),
                        unassigned ? null : assigned,
                        rows.getString(4), rows.getString(5), rows.getString(6)));
            }
        }
        return periods;
    }

    private static long requiredDepartmentId(Connection connection, long facultyId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM departments WHERE faculty_id = ? AND name = ?
                """)) {
            statement.setLong(1, facultyId);
            statement.setString(2, DEPARTMENT_NAME);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("T012 approved Department is missing");
                }
                long id = rows.getLong(1);
                if (rows.next()) {
                    throw new IllegalStateException("T012 approved Department is ambiguous");
                }
                return id;
            }
        }
    }

    private static void requireNoRows(Connection connection, String query, String message)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query);
                ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || rows.getLong(1) != 0L) {
                throw new IllegalStateException(message);
            }
        }
    }

    private record PeriodScope(long id, long facultyId, Long departmentId,
            String name, String topicType, String facultyName) {
    }
}
