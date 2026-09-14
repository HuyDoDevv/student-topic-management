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

public interface CouncilTopicRepository
        extends JpaRepository<CouncilTopicEntity, CouncilTopicId> {
    @EntityGraph(attributePaths = {"council", "registration", "registration.topic"})
    List<CouncilTopicEntity> findAllByCouncilIdOrderByRegistrationId(long councilId);

    @EntityGraph(attributePaths = {"council", "registration", "registration.topic"})
    Optional<CouncilTopicEntity> findByRegistrationId(long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from CouncilTopicEntity assignment "
            + "where assignment.council.id = :councilId order by assignment.registration.id")
    List<CouncilTopicEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    boolean existsByCouncilIdAndRegistrationId(long councilId, long registrationId);
}
