package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.DepartmentEntity;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
    @EntityGraph(attributePaths = "faculty")
    List<DepartmentEntity> findAllByOrderByFacultyNameAscNameAsc();

    @EntityGraph(attributePaths = "faculty")
    Optional<DepartmentEntity> findByIdAndFacultyId(long id, long facultyId);

    @EntityGraph(attributePaths = "faculty")
    List<DepartmentEntity> findAllByFacultyIdOrderByName(long facultyId);
}
