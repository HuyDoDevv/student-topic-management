package vn.edu.topicregistration.shared.dashboard;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!preview & !test")
public class JdbcDashboardQuery implements DashboardQuery {
    private final JdbcTemplate jdbc;

    public JdbcDashboardQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countFacultyTopics(long facultyId, String status) {
        return count("""
                SELECT COUNT(*) FROM topics t
                JOIN registration_periods p ON p.id = t.period_id
                JOIN departments d ON d.id = t.department_id
                WHERE p.faculty_id = ? AND d.faculty_id = ?
                  AND t.hidden_at IS NULL AND t.status = ?
                """, facultyId, facultyId, status);
    }

    @Override
    public long countFacultyPendingRegistrations(long facultyId) {
        return count("""
                SELECT COUNT(*) FROM registrations r
                JOIN registration_periods p ON p.id = r.period_id
                WHERE p.faculty_id = ? AND r.status = 'PENDING'
                """, facultyId);
    }

    @Override
    public long countFacultyPeriods(long facultyId) {
        return count("SELECT COUNT(*) FROM registration_periods WHERE faculty_id = ?", facultyId);
    }

    @Override
    public long countLecturerTopics(
            long lecturerId, long departmentId, long facultyId, String status) {
        return count("""
                SELECT COUNT(*) FROM topics t
                JOIN registration_periods p ON p.id = t.period_id
                WHERE t.author_id = ? AND t.department_id = ? AND p.faculty_id = ?
                  AND t.hidden_at IS NULL AND t.status = ?
                """, lecturerId, departmentId, facultyId, status);
    }

    @Override
    public long countStudentInvitations(long studentId, long facultyId, Instant now) {
        return count("""
                SELECT COUNT(*) FROM group_invitations i
                JOIN student_groups g ON g.id = i.group_id
                JOIN registration_periods p ON p.id = i.period_id
                WHERE i.student_id = ? AND p.faculty_id = ?
                  AND g.dissolved_at IS NULL AND i.status = 'PENDING'
                  AND p.student_start <= ? AND p.student_end >= ?
                """, studentId, facultyId, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public long countStudentGroups(long studentId, long facultyId) {
        return count("""
                SELECT COUNT(*) FROM group_memberships m
                JOIN student_groups g ON g.id = m.group_id
                JOIN registration_periods p ON p.id = g.period_id
                WHERE m.student_id = ? AND p.faculty_id = ? AND g.dissolved_at IS NULL
                """, studentId, facultyId);
    }

    @Override
    public long countStudentRegistrations(long studentId, long facultyId, String status) {
        return count("""
                SELECT COUNT(*) FROM registration_members m
                JOIN registrations r ON r.id = m.registration_id
                JOIN registration_periods p ON p.id = r.period_id
                WHERE m.student_id = ? AND p.faculty_id = ? AND r.status = ?
                """, studentId, facultyId, status);
    }

    @Override
    public Optional<ActiveRegistration> latestStudentRegistration(long studentId, long facultyId) {
        return jdbc.query("""
                SELECT r.id, t.title, r.status FROM registration_members m
                JOIN registrations r ON r.id = m.registration_id
                JOIN registration_periods p ON p.id = r.period_id
                JOIN topics t ON t.id = r.topic_id
                WHERE m.student_id = ? AND p.faculty_id = ?
                  AND r.status IN ('PENDING', 'ACCEPTED')
                ORDER BY r.submitted_at DESC, r.id DESC LIMIT 1
                """, (rs, rowNum) -> new ActiveRegistration(
                rs.getLong("id"), rs.getString("title"), rs.getString("status")),
                studentId, facultyId).stream().findFirst();
    }

    private long count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }
}
