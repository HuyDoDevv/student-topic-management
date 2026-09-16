package vn.edu.topicregistration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.FacultyEntity;

public interface FacultyRepository extends JpaRepository<FacultyEntity, Long> {
    List<FacultyEntity> findAllByOrderByNameAsc();
}
