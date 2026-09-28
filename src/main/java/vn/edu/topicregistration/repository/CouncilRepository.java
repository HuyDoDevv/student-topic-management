package vn.edu.topicregistration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.CouncilEntity;
import vn.edu.topicregistration.entity.CouncilMode;

public interface CouncilRepository extends JpaRepository<CouncilEntity, Long> {
    @Query("select council.period.id from CouncilEntity council where council.id = :id")
    Optional<Long> findPeriodIdByCouncilId(@Param("id") long id);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "chair", "chair.faculty", "secretary", "secretary.faculty"
    })
    Optional<CouncilEntity> findDetailedById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "period", "period.faculty", "chair", "chair.faculty", "secretary", "secretary.faculty"
    })
    @Query("select council from CouncilEntity council where council.id = :id")
    Optional<CouncilEntity> findByIdForUpdate(@Param("id") long id);

    @EntityGraph(attributePaths = {"period", "period.faculty", "chair", "secretary"})
    List<CouncilEntity> findAllByPeriodFacultyIdAndModeOrderByCreatedAtDescIdDesc(
            long facultyId,
            CouncilMode mode);
}
