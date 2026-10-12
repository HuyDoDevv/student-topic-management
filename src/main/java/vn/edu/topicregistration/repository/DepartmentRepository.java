package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.DepartmentEntity;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
    @Query("""
            select department
            from DepartmentEntity department
            join fetch department.faculty faculty
            order by faculty.name asc, department.name asc
            """)
    List<DepartmentEntity> findAllDetailed();

    @Query("""
            select department
            from DepartmentEntity department
            join fetch department.faculty
            where department.id = :id
            """)
    Optional<DepartmentEntity> findDetailedById(@Param("id") long id);

    @EntityGraph(attributePaths = "faculty")
    List<DepartmentEntity> findAllByFacultyIdOrderByNameAsc(long facultyId);

    @EntityGraph(attributePaths = "faculty")
    Optional<DepartmentEntity> findByIdAndFacultyId(long id, long facultyId);

    boolean existsByFacultyIdAndNameIgnoreCase(long facultyId, String name);

    boolean existsByFacultyIdAndNameIgnoreCaseAndIdNot(
            long facultyId, String name, long id);
}
