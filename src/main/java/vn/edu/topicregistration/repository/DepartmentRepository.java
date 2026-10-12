package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.DepartmentEntity;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
    @EntityGraph(attributePaths = "faculty")
    List<DepartmentEntity> findAllByFacultyIdOrderByNameAsc(long facultyId);

    @EntityGraph(attributePaths = "faculty")
    Optional<DepartmentEntity> findByIdAndFacultyId(long id, long facultyId);

    boolean existsByFacultyIdAndNameIgnoreCase(long facultyId, String name);
}
