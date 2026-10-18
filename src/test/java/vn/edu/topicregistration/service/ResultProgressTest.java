package vn.edu.topicregistration.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.FinalResultRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.util.ProjectTime;

class ResultProgressTest {
    @Test
    void allStudentsMustHavePublishedSnapshotsForTerminalProgress() {
        RegistrationMemberRepository members = mock(RegistrationMemberRepository.class);
        FinalResultRepository results = mock(FinalResultRepository.class);
        ReviewerAssignmentRepository reviewers = mock(ReviewerAssignmentRepository.class);
        RegistrationMemberEntity first = member(31L);
        RegistrationMemberEntity second = member(32L);
        when(members.findAllByRegistrationId(51L)).thenReturn(List.of(first, second));
        when(reviewers.findActiveByRegistrationId(51L,
                List.of(vn.edu.topicregistration.entity.ReviewerAssignmentEntity.Status.ASSIGNED,
                        vn.edu.topicregistration.entity.ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.empty());
        FinalResultEntity published = result(FinalResultStatus.PUBLISHED);
        when(results.findDetailedByRegistrationIdAndStudentId(51L, 31L))
                .thenReturn(Optional.of(published));
        when(results.findDetailedByRegistrationIdAndStudentId(51L, 32L))
                .thenReturn(Optional.empty());
        ResultService service = service(members, results, reviewers);

        assertFalse(service.progressForVisibleRegistration(51L).published());

        FinalResultEntity ready = result(FinalResultStatus.READY);
        when(results.findDetailedByRegistrationIdAndStudentId(51L, 32L))
                .thenReturn(Optional.of(ready));
        assertFalse(service.progressForVisibleRegistration(51L).published());
        assertFalse(service.progressForVisibleRegistration(51L).ready());

        when(results.findDetailedByRegistrationIdAndStudentId(51L, 32L))
                .thenReturn(Optional.of(published));
        assertTrue(service.progressForVisibleRegistration(51L).published());
    }

    private static RegistrationMemberEntity member(long studentId) {
        RegistrationMemberEntity member = mock(RegistrationMemberEntity.class);
        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getId()).thenReturn(studentId);
        when(member.getStudent()).thenReturn(student);
        return member;
    }

    private static FinalResultEntity result(FinalResultStatus status) {
        FinalResultEntity result = mock(FinalResultEntity.class);
        when(result.getStatus()).thenReturn(status);
        return result;
    }

    private static ResultService service(
            RegistrationMemberRepository members, FinalResultRepository results,
            ReviewerAssignmentRepository reviewers) {
        Clock clock = Clock.systemUTC();
        return new ResultService(
                mock(RegistrationPeriodRepository.class), mock(CouncilRepository.class),
                mock(CouncilTopicRepository.class), mock(CouncilMemberRepository.class),
                members, mock(EvaluationRepository.class), results,
                mock(UserAccountRepository.class), reviewers,
                mock(ReviewerEvaluationRepository.class), mock(ReviewerReportRepository.class),
                mock(AuthorizationPolicy.class), mock(NotificationService.class),
                clock, new ProjectTime(clock));
    }
}
