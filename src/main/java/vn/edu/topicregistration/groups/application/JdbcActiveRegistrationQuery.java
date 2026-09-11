package vn.edu.topicregistration.groups.application;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!preview & !test")
public class JdbcActiveRegistrationQuery implements ActiveRegistrationQuery {
    private final JdbcTemplate jdbc;

    public JdbcActiveRegistrationQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsForGroup(long periodId, long groupId) {
        Boolean result = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM registrations
                    WHERE period_id = ?
                      AND group_id = ?
                      AND status IN ('PENDING', 'ACCEPTED')
                )
                """,
                Boolean.class,
                periodId,
                groupId);
        return Boolean.TRUE.equals(result);
    }
}
