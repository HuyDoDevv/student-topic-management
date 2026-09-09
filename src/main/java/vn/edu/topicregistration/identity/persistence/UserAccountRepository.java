package vn.edu.topicregistration.identity.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    @EntityGraph(attributePaths = {"department", "department.faculty", "roles", "roles.role"})
    Optional<UserAccountEntity> findByUsername(String username);
}
