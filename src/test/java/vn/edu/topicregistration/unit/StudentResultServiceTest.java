package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.entity.CouncilEntity;
import vn.edu.topicregistration.entity.EvaluationEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.FinalResultRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.ResultService;

class StudentResultServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T03:00:00Z");

    @Test
    void listUsesPublishedSnapshotQueryForCurrentStudent() {
        Dependencies dependencies = dependencies();
        FinalResultEntity result = publishedResult();
        when(dependencies.authorization().requireRole(RoleCode.STUDENT)).thenReturn(
                student());
        when(dependencies.results().findAllVisibleToStudent(
                31L, FinalResultStatus.PUBLISHED)).thenReturn(List.of(result));

        var visible = dependencies.service().listForCurrentStudent();

        assertEquals(1, visible.size());
        assertEquals(51L, visible.getFirst().registrationId());
        assertEquals(new BigDecimal("8.50"), visible.getFirst().finalScore());
    }

    @Test
    void snapshotStudentReadsPublishedDetailAndSubmittedFeedback() {
        Dependencies dependencies = dependencies();
        FinalResultEntity result = publishedResult();
        when(dependencies.authorization().requireRole(RoleCode.STUDENT)).thenReturn(student());
        when(dependencies.results().findVisibleToStudent(
                51L, 31L, FinalResultStatus.PUBLISHED)).thenReturn(Optional.of(result));
        EvaluationEntity evaluation = mock(EvaluationEntity.class);
        UserAccountEntity evaluator = account(11L, "Giảng viên A");
        when(evaluation.getEvaluator()).thenReturn(evaluator);
        when(evaluation.getScore()).thenReturn(new BigDecimal("8.50"));
        when(evaluation.getFeedback()).thenReturn("Đạt");
        when(evaluation.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(evaluation.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(dependencies.evaluations()
                .findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
                        41L, 51L, 31L))
                .thenReturn(List.of(evaluation));

        var detail = dependencies.service().detailForCurrentStudent(51L);

        assertEquals("Đạt", detail.evaluations().getFirst().feedback());
        assertEquals("3 điểm đã nộp; trung bình 8.50", detail.evaluationSummary());
    }

    @Test
    void readyOrOtherGroupResultIsHiddenWithoutEvaluationLeak() {
        Dependencies dependencies = dependencies();
        when(dependencies.authorization().requireRole(RoleCode.STUDENT)).thenReturn(student());
        when(dependencies.results().findVisibleToStudent(
                51L, 31L, FinalResultStatus.PUBLISHED)).thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().detailForCurrentStudent(51L));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(dependencies.evaluations(), never())
                .findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
                        41L, 51L, 31L);
    }

    @Test
    void adminCannotUseStudentResultQuery() {
        Dependencies dependencies = dependencies();
        when(dependencies.authorization().requireRole(RoleCode.STUDENT))
                .thenThrow(new AccessDeniedException("not student"));

        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().listForCurrentStudent());
        verify(dependencies.results(), never()).findAllVisibleToStudent(
                99L, FinalResultStatus.PUBLISHED);
    }

    private static ActorIdentity student() {
        return new ActorIdentity(31L, Set.of(RoleCode.STUDENT), 4L, 2L);
    }

    private static FinalResultEntity publishedResult() {
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        when(group.getId()).thenReturn(21L);
        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getTitle()).thenReturn("Đề tài thử nghiệm");
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getGroup()).thenReturn(group);
        when(registration.getTopic()).thenReturn(topic);
        CouncilEntity council = mock(CouncilEntity.class);
        when(council.getId()).thenReturn(41L);
        when(council.getName()).thenReturn("Hội đồng KLTN");
        FinalResultEntity result = mock(FinalResultEntity.class);
        when(result.getRegistrationId()).thenReturn(51L);
        when(result.getRegistration()).thenReturn(registration);
        when(result.getCouncil()).thenReturn(council);
        when(result.getStatus()).thenReturn(FinalResultStatus.PUBLISHED);
        when(result.getFinalScore()).thenReturn(new BigDecimal("8.50"));
        when(result.getEvaluationSummary()).thenReturn("3 điểm đã nộp; trung bình 8.50");
        when(result.getPublishedAt()).thenReturn(NOW);
        return result;
    }

    private static UserAccountEntity account(long id, String fullName) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        when(account.getFullName()).thenReturn(fullName);
        return account;
    }

    private static Dependencies dependencies() {
        return new Dependencies(
                mock(RegistrationPeriodRepository.class),
                mock(CouncilRepository.class),
                mock(CouncilTopicRepository.class),
                mock(CouncilMemberRepository.class),
                mock(RegistrationMemberRepository.class),
                mock(EvaluationRepository.class),
                mock(FinalResultRepository.class),
                mock(UserAccountRepository.class),
                mock(AuthorizationPolicy.class));
    }

    private record Dependencies(
            RegistrationPeriodRepository periods,
            CouncilRepository councils,
            CouncilTopicRepository assignments,
            CouncilMemberRepository members,
            RegistrationMemberRepository registrationMembers,
            EvaluationRepository evaluations,
            FinalResultRepository results,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization) {
        ResultService service() {
            return new ResultService(
                    periods, councils, assignments, members, registrationMembers,
                    evaluations, results,
                    accounts, authorization, Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
