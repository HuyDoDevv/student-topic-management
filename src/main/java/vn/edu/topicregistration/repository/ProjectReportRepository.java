package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.ProjectReportEntity;

public interface ProjectReportRepository extends JpaRepository<ProjectReportEntity, Long> {
    @EntityGraph(attributePaths = {"registration", "submittedBy"})
    Optional<ProjectReportEntity> findByRegistrationIdAndReplacedAtIsNull(long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"registration", "submittedBy"})
    @Query("select report from ProjectReportEntity report "
            + "where report.registration.id = :registrationId and report.replacedAt is null")
    Optional<ProjectReportEntity> findCurrentByRegistrationIdForUpdate(
            @Param("registrationId") long registrationId);

    List<ProjectReportEntity> findAllByRegistrationIdOrderBySubmittedAtDescIdDesc(
            long registrationId);
}
