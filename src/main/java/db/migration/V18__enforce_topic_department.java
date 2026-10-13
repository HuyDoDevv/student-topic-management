package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Rejects inconsistent V17 rows before making every Topic's Department mandatory. */
public final class V18__enforce_topic_department extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        requireNoRows(connection,
                "SELECT COUNT(*) FROM topics WHERE department_id IS NULL",
                "T008 found Topics without Department");
        requireNoRows(connection, """
                SELECT COUNT(*)
                FROM topics topic
                JOIN departments department ON department.id = topic.department_id
                JOIN registration_periods period ON period.id = topic.period_id
                JOIN user_accounts author ON author.id = topic.author_id
                WHERE department.faculty_id <> period.faculty_id
                   OR author.faculty_id IS NULL
                   OR author.faculty_id <> period.faculty_id
                   OR author.department_id IS NULL
                   OR author.department_id <> topic.department_id
                """, "T008 found Topics whose author or Faculty has another Department");
        requireNoRows(connection, """
                SELECT COUNT(*)
                FROM topic_supervisors supervisor
                JOIN topics topic ON topic.id = supervisor.topic_id
                JOIN user_accounts lecturer ON lecturer.id = supervisor.lecturer_id
                JOIN registration_periods period ON period.id = topic.period_id
                WHERE lecturer.faculty_id IS NULL
                   OR lecturer.faculty_id <> period.faculty_id
                   OR lecturer.department_id IS NULL
                   OR lecturer.department_id <> topic.department_id
                """, "T008 found supervisors in another Department");

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE topics
                        DROP FOREIGN KEY fk_topics_department,
                        MODIFY COLUMN department_id BIGINT NOT NULL,
                        ADD CONSTRAINT fk_topics_department_required
                            FOREIGN KEY (department_id) REFERENCES departments(id)
                    """);
        }
    }

    private static void requireNoRows(Connection connection, String query, String message)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query);
                ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new IllegalStateException("T008 precheck did not return a count");
            }
            if (rows.getLong(1) != 0L) {
                throw new IllegalStateException(message);
            }
        }
    }
}
