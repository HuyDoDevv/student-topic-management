package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.CouncilMemberEntity;
import vn.edu.topicregistration.entity.CouncilMemberEntity.CouncilMemberId;

public interface CouncilMemberRepository
        extends JpaRepository<CouncilMemberEntity, CouncilMemberId> {
    @EntityGraph(attributePaths = {"council", "lecturer"})
    List<CouncilMemberEntity> findAllByCouncilIdOrderBySlot(long councilId);

    @EntityGraph(attributePaths = {"council", "lecturer"})
    Optional<CouncilMemberEntity> findByCouncilIdAndLecturerId(
            long councilId,
            long lecturerId);

    long countByCouncilId(long councilId);

    boolean existsByCouncilIdAndLecturerId(long councilId, long lecturerId);
}
