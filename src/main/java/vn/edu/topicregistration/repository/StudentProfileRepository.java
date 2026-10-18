package vn.edu.topicregistration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.StudentProfileEntity;

public interface StudentProfileRepository extends JpaRepository<StudentProfileEntity, Long> {
    boolean existsByStudentCodeIgnoreCase(String studentCode);
}
