package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.CouncilTopicEntity.CouncilTopicId;
import vn.edu.topicregistration.entity.CouncilStatus;

public interface CouncilTopicRepository
        extends JpaRepository<CouncilTopicEntity, CouncilTopicId> {
    @EntityGraph(attributePaths = {
            "council", "registration", "registration.group", "registration.topic",
            "registration.topic.period", "registration.topic.period.faculty", "registration.topic.supervisors",
            "registration.topic.supervisors.lecturer"
    })
    List<CouncilTopicEntity> findAllByCouncilIdOrderByRegistrationId(long councilId);

    @EntityGraph(attributePaths = {"council", "registration", "registration.topic"})
    Optional<CouncilTopicEntity> findByRegistrationId(long registrationId);

    @EntityGraph(attributePaths = {
            "council", "registration", "registration.group", "registration.topic",
            "registration.topic.supervisors", "registration.topic.supervisors.lecturer"
    })
    @Query("select assignment from CouncilTopicEntity assignment "
            + "where assignment.council.status = :status "
            + "and exists (select member.id from CouncilMemberEntity member "
            + "where member.council.id = assignment.council.id "
            + "and member.lecturer.id = :lecturerId) "
            + "order by assignment.scheduledAt, assignment.council.id, assignment.registration.id")
    List<CouncilTopicEntity> findAllForLecturerByCouncilStatus(
            @Param("lecturerId") long lecturerId,
            @Param("status") CouncilStatus status);

    @EntityGraph(attributePaths = {
            "council", "registration", "registration.group", "registration.topic",
            "registration.topic.supervisors", "registration.topic.supervisors.lecturer"
    })
    Optional<CouncilTopicEntity> findByCouncilIdAndRegistrationId(
            long councilId,
            long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "registration", "registration.group", "registration.topic",
            "registration.topic.period", "registration.topic.period.faculty", "registration.topic.supervisors",
            "registration.topic.supervisors.lecturer"
    })
    @Query("select assignment from CouncilTopicEntity assignment "
            + "where assignment.council.id = :councilId order by assignment.registration.id")
    List<CouncilTopicEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    boolean existsByCouncilIdAndRegistrationId(long councilId, long registrationId);
}
