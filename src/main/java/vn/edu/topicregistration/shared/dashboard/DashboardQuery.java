package vn.edu.topicregistration.shared.dashboard;

import java.time.Instant;
import java.util.Optional;

/** Read-only, explicitly scoped queries; callers must use the reloaded actor identity. */
public interface DashboardQuery {
    long countFacultyTopics(long facultyId, String status);
    long countFacultyPendingRegistrations(long facultyId);
    long countFacultyPeriods(long facultyId);
    long countLecturerTopics(long lecturerId, long departmentId, long facultyId, String status);
    long countStudentInvitations(long studentId, long facultyId, Instant now);
    long countStudentGroups(long studentId, long facultyId);
    long countStudentRegistrations(long studentId, long facultyId, String status);
    Optional<ActiveRegistration> latestStudentRegistration(long studentId, long facultyId);

    record ActiveRegistration(long id, String title, String status) {}
}
