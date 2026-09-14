package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.EvaluationEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;

public interface EvaluationRepository extends JpaRepository<EvaluationEntity, Long> {
    @EntityGraph(attributePaths = {"registration", "evaluator", "component", "council"})
    Optional<EvaluationEntity> findByRegistrationIdAndEvaluatorIdAndComponentId(
            long registrationId,
            long evaluatorId,
            long componentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"registration", "evaluator", "component", "council"})
    @Query("select evaluation from EvaluationEntity evaluation "
            + "where evaluation.council.id = :councilId order by evaluation.id")
    List<EvaluationEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    List<EvaluationEntity> findAllByCouncilIdAndRegistrationIdOrderByEvaluatorId(
            long councilId,
            long registrationId);

    long countByCouncilIdAndStatus(long councilId, EvaluationStatus status);
}
