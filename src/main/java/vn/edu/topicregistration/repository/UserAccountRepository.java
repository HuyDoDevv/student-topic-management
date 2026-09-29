package vn.edu.topicregistration.repository;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select account from UserAccountEntity account "
            + "where account.enabled = true order by account.id")
    List<UserAccountEntity> findAllEnabled();

    @Query("select account from UserAccountEntity account "
            + "where account.enabled = true "
            + "and account.faculty.id = :facultyId "
            + "order by account.id")
    List<UserAccountEntity> findAllEnabledByFacultyId(@Param("facultyId") long facultyId);

    @EntityGraph(attributePaths = {"faculty", "roles", "roles.role", "studentProfile"})
    @Query("select distinct account from UserAccountEntity account order by account.username")
    List<UserAccountEntity> findAllDetailed();

    @EntityGraph(attributePaths = {"faculty", "roles", "roles.role"})
    Optional<UserAccountEntity> findByUsername(String username);

    @EntityGraph(attributePaths = {"faculty", "roles", "roles.role"})
    @Query("select distinct account from UserAccountEntity account where account.id = :id")
    Optional<UserAccountEntity> findDetailedById(@Param("id") long id);

    @EntityGraph(attributePaths = {"faculty", "roles", "roles.role"})
    @Query("select distinct account from UserAccountEntity account "
            + "join account.roles assignment "
            + "where account.enabled = true "
            + "and account.faculty.id = :facultyId "
            + "and assignment.role.code = :role "
            + "order by account.fullName, account.username")
    List<UserAccountEntity> findEnabledByFacultyAndRole(
            @Param("facultyId") long facultyId,
            @Param("role") RoleCode role);

    @EntityGraph(attributePaths = {"faculty", "roles", "roles.role"})
    @Query("select distinct account from UserAccountEntity account "
            + "join account.roles assignment "
            + "where account.enabled = true "
            + "and account.faculty.id = :facultyId "
            + "and assignment.role.code = :role "
            + "and not exists ("
            + "select membership.id from GroupMembershipEntity membership "
            + "where membership.periodId = :periodId "
            + "and membership.student.id = account.id) "
            + "and not exists ("
            + "select invitation.id from GroupInvitationEntity invitation "
            + "where invitation.period.id = :periodId "
            + "and invitation.student.id = account.id "
            + "and invitation.status = :invitationStatus) "
            + "order by account.fullName, account.username")
    List<UserAccountEntity> findEligibleGroupMembers(
            @Param("facultyId") long facultyId,
            @Param("periodId") long periodId,
            @Param("role") RoleCode role,
            @Param("invitationStatus")
            vn.edu.topicregistration.entity.GroupInvitationStatus invitationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"faculty", "roles", "roles.role"})
    @Query("select distinct account from UserAccountEntity account "
            + "left join account.roles assignment "
            + "where account.id = :id")
    Optional<UserAccountEntity> findByIdForUpdate(@Param("id") long id);
}
