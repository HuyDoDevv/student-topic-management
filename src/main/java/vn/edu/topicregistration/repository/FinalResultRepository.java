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
import vn.edu.topicregistration.entity.FinalResultStatus;

public interface FinalResultRepository extends JpaRepository<FinalResultEntity, Long> {
    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "council",
            "council.period", "council.period.faculty", "consolidatedBy", "publishedBy"
    })
    Optional<FinalResultEntity> findDetailedByRegistrationId(long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from FinalResultEntity result "
            + "where result.council.id = :councilId order by result.registrationId")
    List<FinalResultEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "council"
    })
    List<FinalResultEntity>
            findAllByCouncilPeriodFacultyIdOrderByConsolidatedAtDescRegistrationIdDesc(
                    long facultyId);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "council"
    })
    List<FinalResultEntity> findAllByCouncilPeriodFacultyIdAndStatusOrderByPublishedAtDesc(
            long facultyId,
            FinalResultStatus status);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "council"
    })
    @Query("select result from FinalResultEntity result "
            + "where result.status = :status "
            + "and exists (select member.id from RegistrationMemberEntity member "
            + "where member.registration.id = result.registrationId "
            + "and member.student.id = :studentId) "
            + "order by result.publishedAt desc, result.registrationId desc")
    List<FinalResultEntity> findAllVisibleToStudent(
            @Param("studentId") long studentId,
            @Param("status") FinalResultStatus status);

    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic", "council"
    })
    @Query("select result from FinalResultEntity result "
            + "where result.registrationId = :registrationId "
            + "and result.status = :status "
            + "and exists (select member.id from RegistrationMemberEntity member "
            + "where member.registration.id = result.registrationId "
            + "and member.student.id = :studentId)")
    Optional<FinalResultEntity> findVisibleToStudent(
            @Param("registrationId") long registrationId,
            @Param("studentId") long studentId,
            @Param("status") FinalResultStatus status);
}
