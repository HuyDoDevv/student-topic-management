package vn.edu.topicregistration.groups.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentGroupRepository extends JpaRepository<StudentGroupEntity, Long> {
    @Query("select studentGroup.period.id from StudentGroupEntity studentGroup "
            + "where studentGroup.id = :id")
    Optional<Long> findPeriodIdByGroupId(@Param("id") long id);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "leader", "members", "members.student"
    })
    @Query("select distinct studentGroup from StudentGroupEntity studentGroup "
            + "join studentGroup.members membership "
            + "where studentGroup.period.id = :periodId "
            + "and membership.student.id = :studentId")
    Optional<StudentGroupEntity> findByPeriodIdAndMemberStudentId(
            @Param("periodId") long periodId,
            @Param("studentId") long studentId);

    @EntityGraph(attributePaths = {
            "period", "period.faculty", "leader", "members", "members.student"
    })
    @Query("select distinct studentGroup from StudentGroupEntity studentGroup "
            + "join studentGroup.members membership "
            + "where studentGroup.id = :id "
            + "and membership.student.id = :studentId")
    Optional<StudentGroupEntity> findByIdVisibleToStudent(
            @Param("id") long id,
            @Param("studentId") long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "period", "period.faculty", "leader", "members", "members.student"
    })
    @Query("select distinct studentGroup from StudentGroupEntity studentGroup "
            + "left join studentGroup.members membership "
            + "where studentGroup.id = :id")
    Optional<StudentGroupEntity> findByIdForUpdate(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentGroupEntity studentGroup "
            + "set studentGroup.version = studentGroup.version + 1 "
            + "where studentGroup.id = :id and studentGroup.version = :expectedVersion")
    int incrementVersion(
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion);
}
