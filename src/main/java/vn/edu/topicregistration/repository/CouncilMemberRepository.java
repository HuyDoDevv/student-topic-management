package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.CouncilMemberEntity;
import vn.edu.topicregistration.entity.CouncilMemberEntity.CouncilMemberId;

public interface CouncilMemberRepository
        extends JpaRepository<CouncilMemberEntity, CouncilMemberId> {
    @EntityGraph(attributePaths = {"council", "lecturer"})
    List<CouncilMemberEntity> findAllByCouncilIdOrderBySlot(long councilId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "council", "lecturer", "lecturer.department", "lecturer.roles", "lecturer.roles.role"
    })
    @Query("select member from CouncilMemberEntity member "
            + "where member.council.id = :councilId order by member.slot")
    List<CouncilMemberEntity> findAllByCouncilIdForUpdate(
            @Param("councilId") long councilId);

    @EntityGraph(attributePaths = {"council", "lecturer"})
    Optional<CouncilMemberEntity> findByCouncilIdAndLecturerId(
            long councilId,
            long lecturerId);

    long countByCouncilId(long councilId);

    boolean existsByCouncilIdAndLecturerId(long councilId, long lecturerId);
}
