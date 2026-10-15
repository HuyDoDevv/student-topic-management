package vn.edu.topicregistration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.ReviewerReportEntity;

public interface ReviewerReportRepository extends JpaRepository<ReviewerReportEntity, Long> {
    @EntityGraph(attributePaths = {"assignment", "submittedBy"})
    Optional<ReviewerReportEntity> findByAssignmentId(long assignmentId);

    boolean existsByAssignmentId(long assignmentId);
}
