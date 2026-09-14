package vn.edu.topicregistration.repository;

import vn.edu.topicregistration.entity.RegistrationMemberEntity;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.RegistrationMemberEntity.RegistrationMemberId;

public interface RegistrationMemberRepository
        extends Repository<RegistrationMemberEntity, RegistrationMemberId> {
    @EntityGraph(attributePaths = "student")
    @Query("select member from RegistrationMemberEntity member "
            + "where member.registration.id = :registrationId order by member.slot")
    List<RegistrationMemberEntity> findAllByRegistrationId(
            @Param("registrationId") long registrationId);

    @EntityGraph(attributePaths = "student")
    @Query("select member from RegistrationMemberEntity member "
            + "where member.registration.period.id = :periodId "
            + "order by member.registration.id, member.slot")
    List<RegistrationMemberEntity> findAllByPeriodId(@Param("periodId") long periodId);

    @Query("select case when count(member) > 0 then true else false end "
            + "from RegistrationMemberEntity member "
            + "where member.registration.id = :registrationId "
            + "and member.student.id = :studentId")
    boolean existsByRegistrationIdAndStudentId(
            @Param("registrationId") long registrationId,
            @Param("studentId") long studentId);
}
