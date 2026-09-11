package vn.edu.topicregistration.groups.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.groups.persistence.GroupMembershipEntity.GroupMembershipId;

public interface GroupMembershipRepository
        extends JpaRepository<GroupMembershipEntity, GroupMembershipId> {
    @Query("select membership from GroupMembershipEntity membership "
            + "where membership.group.id = :groupId "
            + "and membership.student.id = :studentId")
    Optional<GroupMembershipEntity> findByGroupIdAndStudentId(
            @Param("groupId") long groupId,
            @Param("studentId") long studentId);
}
