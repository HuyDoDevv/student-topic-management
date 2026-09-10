package vn.edu.topicregistration.identity.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.identity.domain.RoleCode;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    @EntityGraph(attributePaths = {"department", "department.faculty", "roles", "roles.role"})
    Optional<UserAccountEntity> findByUsername(String username);

    @EntityGraph(attributePaths = {"department", "roles", "roles.role"})
    @Query("select distinct account from UserAccountEntity account "
            + "join account.roles assignment "
            + "where account.enabled = true "
            + "and account.department.id = :departmentId "
            + "and assignment.role.code = :role "
            + "order by account.fullName, account.username")
    List<UserAccountEntity> findEnabledByDepartmentAndRole(
            @Param("departmentId") long departmentId,
            @Param("role") RoleCode role);
}
