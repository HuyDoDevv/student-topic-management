package vn.edu.topicregistration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, RoleCode> {
}
