package vn.edu.topicregistration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.entity.UserRoleId;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {
}
