package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultEntity.FinalResultId;
import vn.edu.topicregistration.entity.FinalResultStatus;

public interface FinalResultRepository extends JpaRepository<FinalResultEntity, FinalResultId> {
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
