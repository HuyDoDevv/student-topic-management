package vn.edu.topicregistration.repository;

import vn.edu.topicregistration.entity.TopicEntity;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<TopicEntity, Long> {
    @Query("select topic.period.id from TopicEntity topic where topic.id = :id")
    Optional<Long> findPeriodIdByTopicId(@Param("id") long id);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "department", "author",
            "supervisors", "supervisors.lecturer"
    })
    List<TopicEntity> findAllByPeriodIdAndAuthorIdAndHiddenAtIsNullOrderByIdDesc(
            long periodId,
            long authorId);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "department", "author",
            "supervisors", "supervisors.lecturer"
    })
    Optional<TopicEntity> findByIdAndHiddenAtIsNull(long id);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "department", "author",
            "supervisors", "supervisors.lecturer"
    })
    Optional<TopicEntity> findByIdAndAuthorIdAndDepartmentIdAndHiddenAtIsNull(
            long id,
            long authorId,
            long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "period", "period.faculty", "department", "author",
            "supervisors", "supervisors.lecturer"
    })
    @Query("select distinct topic from TopicEntity topic "
            + "left join fetch topic.supervisors "
            + "where topic.id = :id and topic.hiddenAt is null")
    Optional<TopicEntity> findByIdForUpdate(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select topic from TopicEntity topic "
            + "where topic.period.id = :periodId "
            + "and topic.status = 'APPROVED' "
            + "and topic.hiddenAt is null "
            + "order by topic.id")
    List<TopicEntity> findAllApprovedByPeriodIdForUpdate(@Param("periodId") long periodId);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "department", "author",
            "supervisors", "supervisors.lecturer"
    })
    @Query("select distinct topic from TopicEntity topic "
            + "left join topic.supervisors supervisor "
            + "where topic.period.id = :periodId "
            + "and topic.status in :statuses "
            + "and topic.hiddenAt is null "
            + "order by topic.id desc")
    List<TopicEntity> findAllVisibleByPeriodIdAndStatusIn(
            @Param("periodId") long periodId,
            @Param("statuses") List<String> statuses);
}
