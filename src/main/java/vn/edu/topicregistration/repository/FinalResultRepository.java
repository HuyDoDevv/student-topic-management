package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultEntity.FinalResultId;
import vn.edu.topicregistration.entity.FinalResultStatus;

public interface FinalResultRepository extends JpaRepository<FinalResultEntity, FinalResultId> {
    interface FilterOption {
        Long getId();
        String getName();
    }

    @Query(value = """
            SELECT fr.registration_id
            FROM final_results fr
            JOIN registrations r ON r.id = fr.registration_id
            JOIN registration_periods p ON p.id = r.period_id
            JOIN topics t ON t.id = r.topic_id
            WHERE p.faculty_id = :facultyId
              AND (:periodId IS NULL OR p.id = :periodId)
              AND (:registrationId IS NULL OR r.id = :registrationId)
              AND (:topicType IS NULL OR p.topic_type = :topicType)
              AND (:search IS NULL OR LOCATE(LOWER(:search), LOWER(t.title)) > 0
                   OR (:groupId IS NOT NULL AND r.group_id = :groupId))
              AND (:status IS NULL OR fr.status = :status)
              AND (:graderId IS NULL OR EXISTS (
                   SELECT 1 FROM evaluations e
                   WHERE e.registration_id = r.id AND e.evaluator_id = :graderId
                     AND e.status = 'SUBMITTED') OR EXISTS (
                   SELECT 1 FROM reviewer_assignments ra
                   WHERE ra.registration_id = r.id AND ra.lecturer_id = :graderId
                     AND ra.status = 'SUBMITTED'))
            GROUP BY fr.registration_id
            ORDER BY
              CASE WHEN :sort = 'recent' THEN MAX(fr.consolidated_at) END DESC,
              CASE WHEN :sort = 'oldest' THEN MIN(fr.consolidated_at) END ASC,
              CASE WHEN :sort = 'period' THEN MAX(p.name) END ASC,
              CASE WHEN :sort = 'topic' THEN MAX(t.title) END ASC,
              fr.registration_id DESC
            """, countQuery = """
            SELECT COUNT(DISTINCT fr.registration_id)
            FROM final_results fr
            JOIN registrations r ON r.id = fr.registration_id
            JOIN registration_periods p ON p.id = r.period_id
            JOIN topics t ON t.id = r.topic_id
            WHERE p.faculty_id = :facultyId
              AND (:periodId IS NULL OR p.id = :periodId)
              AND (:registrationId IS NULL OR r.id = :registrationId)
              AND (:topicType IS NULL OR p.topic_type = :topicType)
              AND (:search IS NULL OR LOCATE(LOWER(:search), LOWER(t.title)) > 0
                   OR (:groupId IS NOT NULL AND r.group_id = :groupId))
              AND (:status IS NULL OR fr.status = :status)
              AND (:graderId IS NULL OR EXISTS (
                   SELECT 1 FROM evaluations e
                   WHERE e.registration_id = r.id AND e.evaluator_id = :graderId
                     AND e.status = 'SUBMITTED') OR EXISTS (
                   SELECT 1 FROM reviewer_assignments ra
                   WHERE ra.registration_id = r.id AND ra.lecturer_id = :graderId
                     AND ra.status = 'SUBMITTED'))
            """, nativeQuery = true)
    Page<Long> findRegistrationIdsForFaculty(
            @Param("facultyId") long facultyId,
            @Param("periodId") Long periodId,
            @Param("registrationId") Long registrationId,
            @Param("topicType") String topicType,
            @Param("search") String search,
            @Param("groupId") Long groupId,
            @Param("graderId") Long graderId,
            @Param("status") String status,
            @Param("sort") String sort,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.period", "registration.topic",
            "student", "council", "council.chair", "council.secretary"
    })
    @Query("select result from FinalResultEntity result "
            + "where result.council.period.faculty.id = :facultyId "
            + "and result.registration.id in :registrationIds "
            + "order by result.registration.id, result.student.id")
    List<FinalResultEntity> findPageRowsForFaculty(
            @Param("facultyId") long facultyId,
            @Param("registrationIds") List<Long> registrationIds);

    @Query(value = """
            SELECT DISTINCT p.id AS id, p.name AS name
            FROM final_results fr
            JOIN registrations r ON r.id = fr.registration_id
            JOIN registration_periods p ON p.id = r.period_id
            WHERE p.faculty_id = :facultyId
            ORDER BY name, id
            """, nativeQuery = true)
    List<FilterOption> findPeriodOptionsForFaculty(@Param("facultyId") long facultyId);

    @Query(value = """
            SELECT u.id AS id, COALESCE(NULLIF(u.full_name, ''), u.username) AS name
            FROM user_accounts u
            JOIN (
                SELECT e.evaluator_id AS actor_id
                FROM evaluations e
                JOIN final_results fr ON fr.registration_id = e.registration_id
                JOIN councils c ON c.id = fr.council_id
                JOIN registration_periods p ON p.id = c.period_id
                WHERE p.faculty_id = :facultyId AND e.status = 'SUBMITTED'
                UNION
                SELECT ra.lecturer_id AS actor_id
                FROM reviewer_assignments ra
                JOIN final_results fr ON fr.registration_id = ra.registration_id
                JOIN councils c ON c.id = fr.council_id
                JOIN registration_periods p ON p.id = c.period_id
                WHERE p.faculty_id = :facultyId AND ra.status = 'SUBMITTED'
            ) graders ON graders.actor_id = u.id
            ORDER BY name, id
            """, nativeQuery = true)
    List<FilterOption> findGraderOptionsForFaculty(@Param("facultyId") long facultyId);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.period", "registration.topic",
            "student", "council", "council.chair", "council.secretary",
            "council.period", "council.period.faculty", "consolidatedBy", "publishedBy"
    })
    @Query("select result from FinalResultEntity result "
            + "where result.registration.id = :registrationId "
            + "and result.student.id = :studentId")
    Optional<FinalResultEntity> findDetailedByRegistrationIdAndStudentId(
            @Param("registrationId") long registrationId,
            @Param("studentId") long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from FinalResultEntity result "
            + "where result.council.id = :councilId "
            + "order by result.registration.id, result.student.id")
    List<FinalResultEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.period", "registration.topic",
            "student", "council", "council.chair", "council.secretary"
    })
    List<FinalResultEntity>
            findAllByCouncilPeriodFacultyIdOrderByConsolidatedAtDescIdRegistrationIdDesc(
                    long facultyId);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "student", "council"
    })
    List<FinalResultEntity> findAllByCouncilPeriodFacultyIdAndStatusOrderByPublishedAtDesc(
            long facultyId,
            FinalResultStatus status);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "student", "council"
    })
    @Query("select result from FinalResultEntity result "
            + "where result.status = :status "
            + "and result.student.id = :studentId "
            + "order by result.publishedAt desc, result.registration.id desc")
    List<FinalResultEntity> findAllVisibleToStudent(
            @Param("studentId") long studentId,
            @Param("status") FinalResultStatus status);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "council"
    })
    @Query("select result from FinalResultEntity result "
            + "where result.registration.id = :registrationId "
            + "and result.status = :status "
            + "and result.student.id = :studentId")
    Optional<FinalResultEntity> findVisibleToStudent(
            @Param("registrationId") long registrationId,
            @Param("studentId") long studentId,
            @Param("status") FinalResultStatus status);
}
