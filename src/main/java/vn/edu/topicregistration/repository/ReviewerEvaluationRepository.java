package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.ReviewerEvaluationEntity;

public interface ReviewerEvaluationRepository extends JpaRepository<ReviewerEvaluationEntity, Long> {
    @EntityGraph(attributePaths = {"assignment", "student"})
    Optional<ReviewerEvaluationEntity> findByAssignmentIdAndStudentId(
            long assignmentId, long studentId);

    boolean existsByAssignmentId(long assignmentId);

    @EntityGraph(attributePaths = {"assignment", "student"})
    List<ReviewerEvaluationEntity> findAllByAssignmentIdOrderByStudentId(long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"assignment", "student"})
    @Query("select evaluation from ReviewerEvaluationEntity evaluation "
            + "where evaluation.assignment.id = :assignmentId order by evaluation.student.id")
    List<ReviewerEvaluationEntity> findAllByAssignmentIdForUpdate(
            @Param("assignmentId") long assignmentId);
}
