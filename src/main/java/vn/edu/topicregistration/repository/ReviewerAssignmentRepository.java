package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;

public interface ReviewerAssignmentRepository extends JpaRepository<ReviewerAssignmentEntity, Long> {
    @EntityGraph(attributePaths = {"registration", "reviewer", "assignedBy"})
    @Query("select assignment from ReviewerAssignmentEntity assignment "
            + "where assignment.registration.id = :registrationId "
            + "and assignment.status in :statuses")
    Optional<ReviewerAssignmentEntity> findActiveByRegistrationId(
            @Param("registrationId") long registrationId,
            @Param("statuses") List<ReviewerAssignmentEntity.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"registration", "reviewer", "assignedBy"})
    @Query("select assignment from ReviewerAssignmentEntity assignment "
            + "where assignment.registration.id = :registrationId "
            + "and assignment.status in :statuses")
    Optional<ReviewerAssignmentEntity> findActiveByRegistrationIdForUpdate(
            @Param("registrationId") long registrationId,
            @Param("statuses") List<ReviewerAssignmentEntity.Status> statuses);

    @EntityGraph(attributePaths = {"registration", "reviewer", "assignedBy"})
    List<ReviewerAssignmentEntity> findAllByRegistrationIdOrderByIdDesc(long registrationId);
}
