package vn.edu.topicregistration.repository;

import vn.edu.topicregistration.entity.GroupMembershipEntity;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.GroupMembershipEntity.GroupMembershipId;

public interface GroupMembershipRepository
        extends JpaRepository<GroupMembershipEntity, GroupMembershipId> {
    @Query("select membership from GroupMembershipEntity membership "
            + "where membership.group.id = :groupId "
            + "and membership.student.id = :studentId")
    Optional<GroupMembershipEntity> findByGroupIdAndStudentId(
            @Param("groupId") long groupId,
            @Param("studentId") long studentId);

    @EntityGraph(attributePaths = "student")
    @Query("select membership from GroupMembershipEntity membership "
            + "where membership.group.id = :groupId "
            + "order by membership.slot")
    List<GroupMembershipEntity> findAllByGroupIdOrderBySlot(@Param("groupId") long groupId);
}
