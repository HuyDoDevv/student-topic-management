package vn.edu.topicregistration.repository;

import vn.edu.topicregistration.entity.RegistrationPeriodEntity;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationPeriodRepository
        extends JpaRepository<RegistrationPeriodEntity, Long> {

    @EntityGraph(attributePaths = "faculty")
    List<RegistrationPeriodEntity> findAllByFacultyIdOrderByLecturerStartDesc(long facultyId);

    @EntityGraph(attributePaths = "faculty")
    Optional<RegistrationPeriodEntity> findByIdAndFacultyId(long id, long facultyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "faculty")
    @Query("select period from RegistrationPeriodEntity period where period.id = :id")
    Optional<RegistrationPeriodEntity> findByIdForUpdate(@Param("id") long id);
}
