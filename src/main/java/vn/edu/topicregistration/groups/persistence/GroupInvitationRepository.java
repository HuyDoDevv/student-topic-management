package vn.edu.topicregistration.groups.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitationEntity, Long> {
    @Query("select invitation.period.id from GroupInvitationEntity invitation "
            + "where invitation.id = :id")
    Optional<Long> findPeriodIdByInvitationId(@Param("id") long id);

    @Query("select invitation.group.id from GroupInvitationEntity invitation "
            + "where invitation.id = :id")
    Optional<Long> findGroupIdByInvitationId(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "period", "period.faculty", "group", "group.leader", "student", "invitedBy"
    })
    @Query("select invitation from GroupInvitationEntity invitation "
            + "where invitation.id = :id")
    Optional<GroupInvitationEntity> findByIdForUpdate(@Param("id") long id);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "group", "group.leader", "student", "invitedBy"
    })
    @Query("select invitation from GroupInvitationEntity invitation "
            + "where invitation.student.id = :studentId "
            + "order by invitation.createdAt desc, invitation.id desc")
    List<GroupInvitationEntity> findAllByStudentId(@Param("studentId") long studentId);

    @EntityGraph(attributePaths = {"student", "invitedBy"})
    @Query("select invitation from GroupInvitationEntity invitation "
            + "where invitation.group.id = :groupId "
            + "order by invitation.createdAt desc, invitation.id desc")
    List<GroupInvitationEntity> findAllByGroupId(@Param("groupId") long groupId);

    long countByGroupIdAndStatus(long groupId, GroupInvitationStatus status);

    boolean existsByPeriodIdAndStudentIdAndStatus(
            long periodId,
            long studentId,
            GroupInvitationStatus status);
}
