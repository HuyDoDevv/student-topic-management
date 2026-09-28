package vn.edu.topicregistration.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class JdbcDashboardQuery implements DashboardQuery {
    private final JdbcTemplate jdbc;

    public JdbcDashboardQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminAccountStatistics countAdminAccounts() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                  COALESCE(SUM(CASE WHEN u.enabled THEN 1 ELSE 0 END), 0) AS enabled,
                  COALESCE(SUM(CASE WHEN EXISTS (
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.code = ur.role_code
                    WHERE ur.user_id = u.id AND r.code = 'STUDENT'
                  ) THEN 1 ELSE 0 END), 0) AS students,
                  COALESCE(SUM(CASE WHEN EXISTS (
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.code = ur.role_code
                    WHERE ur.user_id = u.id AND r.code = 'LECTURER'
                  ) AND NOT EXISTS (
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.code = ur.role_code
                    WHERE ur.user_id = u.id AND r.code = 'DEAN'
                  ) THEN 1 ELSE 0 END), 0) AS lecturers,
                  COALESCE(SUM(CASE WHEN EXISTS (
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.code = ur.role_code
                    WHERE ur.user_id = u.id AND r.code = 'DEAN'
                  ) THEN 1 ELSE 0 END), 0) AS deans
                FROM user_accounts u
                """, (rs, rowNum) -> new AdminAccountStatistics(
                rs.getLong("total"),
                rs.getLong("enabled"),
                rs.getLong("students"),
                rs.getLong("lecturers"),
                rs.getLong("deans")));
    }

    @Override
    public long countFacultyTopics(long facultyId, String status, Instant now) {
        return count("""
                SELECT COUNT(*) FROM topics t
                JOIN registration_periods p ON p.id = t.period_id
                JOIN departments d ON d.id = t.department_id
                WHERE p.faculty_id = ? AND d.faculty_id = ?
                  AND p.student_end >= ?
                  AND t.hidden_at IS NULL AND t.status = ?
                """, facultyId, facultyId, Timestamp.from(now), status);
    }

    @Override
    public long countFacultyPendingRegistrations(long facultyId, Instant now) {
        return count("""
                SELECT COUNT(*) FROM registrations r
                JOIN registration_periods p ON p.id = r.period_id
                WHERE p.faculty_id = ? AND p.student_end >= ? AND r.status = 'PENDING'
                """, facultyId, Timestamp.from(now));
    }

    @Override
    public long countFacultyActivePeriods(long facultyId, Instant now) {
        return count("""
                SELECT COUNT(*) FROM registration_periods
                WHERE faculty_id = ? AND student_end >= ?
                """, facultyId, Timestamp.from(now));
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
