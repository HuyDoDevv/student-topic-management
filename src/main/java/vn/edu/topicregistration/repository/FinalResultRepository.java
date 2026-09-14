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
            "registration", "registration.topic", "council", "consolidatedBy", "publishedBy"
    })
    Optional<FinalResultEntity> findDetailedByRegistrationId(long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from FinalResultEntity result "
            + "where result.council.id = :councilId order by result.registrationId")
    List<FinalResultEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    @EntityGraph(attributePaths = {"registration", "registration.topic", "council"})
    List<FinalResultEntity> findAllByCouncilPeriodFacultyIdAndStatusOrderByPublishedAtDesc(
            long facultyId,
            FinalResultStatus status);
}
