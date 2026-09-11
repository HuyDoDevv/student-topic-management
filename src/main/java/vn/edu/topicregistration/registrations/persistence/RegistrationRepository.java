package vn.edu.topicregistration.registrations.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<RegistrationEntity, Long> {
    @Query("select registration.period.id from RegistrationEntity registration "
            + "where registration.id = :id")
    Optional<Long> findPeriodIdByRegistrationId(@Param("id") long id);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "group", "group.leader",
            "topic", "topic.department", "topic.author", "decisionActor"
    })
    @Query("select registration from RegistrationEntity registration "
            + "where registration.id = :id")
    Optional<RegistrationEntity> findDetailedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "period", "period.faculty", "group", "group.leader",
            "topic", "topic.department", "topic.author", "decisionActor"
    })
    @Query("select registration from RegistrationEntity registration "
            + "where registration.id = :id")
    Optional<RegistrationEntity> findByIdForUpdate(@Param("id") long id);

    @EntityGraph(attributePaths = {"topic", "topic.department", "decisionActor"})
    List<RegistrationEntity> findAllByGroupIdOrderBySubmittedAtDescIdDesc(long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from RegistrationEntity registration "
            + "where registration.period.id = :periodId "
            + "and registration.topic.id = :topicId "
            + "and registration.status = vn.edu.topicregistration.registrations.persistence.RegistrationStatus.PENDING "
            + "order by registration.id")
    List<RegistrationEntity> findAllPendingByTopicForUpdate(
            @Param("periodId") long periodId,
            @Param("topicId") long topicId);

    boolean existsByPeriodIdAndGroupIdAndStatusIn(
            long periodId,
            long groupId,
            Collection<RegistrationStatus> statuses);

    boolean existsByPeriodIdAndTopicIdAndStatus(
            long periodId,
            long topicId,
            RegistrationStatus status);
}
