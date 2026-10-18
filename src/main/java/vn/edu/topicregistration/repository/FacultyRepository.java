package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.FacultyEntity;

public interface FacultyRepository extends JpaRepository<FacultyEntity, Long> {
    List<FacultyEntity> findAllByOrderByNameAsc();

    Optional<FacultyEntity> findByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select faculty from FacultyEntity faculty where faculty.id = :id")
    Optional<FacultyEntity> findByIdForUpdate(@Param("id") long id);
}
