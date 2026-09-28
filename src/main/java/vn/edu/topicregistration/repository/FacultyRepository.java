package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.FacultyEntity;

public interface FacultyRepository extends JpaRepository<FacultyEntity, Long> {
    List<FacultyEntity> findAllByOrderByNameAsc();

    Optional<FacultyEntity> findByNameIgnoreCase(String name);
}
