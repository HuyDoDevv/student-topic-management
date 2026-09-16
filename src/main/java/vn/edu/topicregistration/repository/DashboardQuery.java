package vn.edu.topicregistration.repository;

import java.time.Instant;
import java.util.Optional;

/** Read-only, explicitly scoped queries; callers must use the reloaded actor identity. */
public interface DashboardQuery {
    AdminAccountStatistics countAdminAccounts();
    long countFacultyTopics(long facultyId, String status, Instant now);
    long countFacultyPendingRegistrations(long facultyId, Instant now);
    long countFacultyActivePeriods(long facultyId, Instant now);
    long countLecturerTopics(long lecturerId, long departmentId, long facultyId, String status);
    long countStudentInvitations(long studentId, long facultyId, Instant now);
    long countStudentGroups(long studentId, long facultyId);
    long countStudentRegistrations(long studentId, long facultyId, String status);
    Optional<ActiveRegistration> latestStudentRegistration(long studentId, long facultyId);

    record AdminAccountStatistics(
            long total, long enabled, long students, long lecturers, long deans) {}

    record ActiveRegistration(long id, String title, String status) {}
}
