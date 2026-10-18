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
    @EntityGraph(attributePaths = {
            "registration", "student", "evaluator", "component", "council"
    })
    Optional<EvaluationEntity> findByRegistrationIdAndStudentIdAndEvaluatorIdAndComponentId(
            long registrationId,
            long studentId,
            long evaluatorId,
            long componentId);

    @EntityGraph(attributePaths = {
            "registration", "student", "evaluator", "component", "council"
    })
    Optional<EvaluationEntity> findByCouncilIdAndRegistrationIdAndStudentIdAndEvaluatorId(
            long councilId,
            long registrationId,
            long studentId,
            long evaluatorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "registration", "student", "evaluator", "component", "council"
    })
    @Query("select evaluation from EvaluationEntity evaluation "
            + "where evaluation.council.id = :councilId order by evaluation.id")
    List<EvaluationEntity> findAllByCouncilIdForUpdate(@Param("councilId") long councilId);

    @EntityGraph(attributePaths = {"student", "evaluator", "component"})
    List<EvaluationEntity> findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
            long councilId, long registrationId, long studentId);

    @EntityGraph(attributePaths = {"student", "evaluator", "component"})
    List<EvaluationEntity> findAllByCouncilIdAndRegistrationIdAndEvaluatorIdOrderByStudentId(
            long councilId, long registrationId, long evaluatorId);

    long countByCouncilIdAndStatus(long councilId, EvaluationStatus status);

    boolean existsByCouncilId(long councilId);
}
