package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.AssignCommand;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReportUpload;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ScoreRow;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionCommand;
import vn.edu.topicregistration.util.ProjectTime;

class ReviewerAssignmentServiceTest {
    private final RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final RegistrationMemberRepository members = mock(RegistrationMemberRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final CouncilTopicRepository councilTopics = mock(CouncilTopicRepository.class);
    private final CouncilMemberRepository councilMembers = mock(CouncilMemberRepository.class);
    private final ReviewerAssignmentRepository assignments = mock(ReviewerAssignmentRepository.class);
    private final ReviewerReportRepository reports = mock(ReviewerReportRepository.class);
    private final ReviewerEvaluationRepository evaluations = mock(ReviewerEvaluationRepository.class);
    private final ReportFileRepository files = mock(ReportFileRepository.class);
    private final AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
    private final ReviewerAssignmentService service = new ReviewerAssignmentService(
            periods, registrations, members, accounts, councilTopics, councilMembers,
            assignments, reports, evaluations, files, authorization,
            new ProjectTime(Clock.fixed(Instant.parse("2026-10-15T16:59:59Z"), ZoneOffset.UTC)));

    @Test
    void validAssignmentRequiresScopedDeanAndEligibleReviewer() {
        Fixture fixture = fixture();
        when(authorization.requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(9L, Set.of(RoleCode.DEAN), 2L));
        when(assignments.findActiveByRegistrationIdForUpdate(51L,
                List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED))).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(11L)).thenReturn(Optional.of(fixture.reviewer));
        when(accounts.findById(9L)).thenReturn(Optional.of(mock(UserAccountEntity.class)));
        when(assignments.saveAndFlush(any())).thenAnswer(invocation -> {
            ReviewerAssignmentEntity saved = invocation.getArgument(0);
            java.lang.reflect.Field id = ReviewerAssignmentEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(saved, 71L);
            return saved;
        });

        assertEquals(71L, service.assign(new AssignCommand(51L, 11L, 0L)));
        verify(assignments).saveAndFlush(any());
    }

    @Test
    void refusesDeanAsReviewerEvenWhenLecturerRoleExists() {
        Fixture fixture = fixture();
        when(authorization.requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(9L, Set.of(RoleCode.DEAN), 2L));
        when(accounts.findByIdForUpdate(11L)).thenReturn(Optional.of(fixture.reviewer));
        when(fixture.reviewer.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));

        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> service.assign(new AssignCommand(51L, 11L, 0L)));
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test
    void incompleteStudentScoresDoNotStoreFileOrWriteRows() throws Exception {
        Fixture fixture = fixture();
        ReviewerAssignmentEntity assignment = assignment(fixture);
        when(assignments.findDetailedById(71L)).thenReturn(Optional.of(assignment));
        when(assignments.findByIdForUpdate(71L)).thenReturn(Optional.of(assignment));
        when(authorization.requireLecturerOfFaculty(2L)).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L));
        RegistrationMemberEntity student = mock(RegistrationMemberEntity.class);
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(101L);
        when(student.getStudent()).thenReturn(account);
        when(members.findAllByRegistrationId(51L)).thenReturn(List.of(student));

        assertThrows(ReviewerAssignmentService.ReviewerValidationException.class,
                () -> service.submit(71L, new SubmissionCommand(0L, pdf(), List.of())));
        verify(files, never()).store(any(), anyLong());
        verify(evaluations, never()).save(any());
    }

    @Test
    void staleVersionRejectsBeforeFileWrite() throws Exception {
        Fixture fixture = fixture();
        ReviewerAssignmentEntity assignment = assignment(fixture);
        when(assignments.findDetailedById(71L)).thenReturn(Optional.of(assignment));
        when(assignments.findByIdForUpdate(71L)).thenReturn(Optional.of(assignment));
        when(authorization.requireLecturerOfFaculty(2L)).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L));

        assertThrows(ApplicationException.class,
                () -> service.submit(71L, new SubmissionCommand(99L, pdf(),
                        List.of(new ScoreRow(101L, new BigDecimal("8.25"), "Tốt")))));
        verify(files, never()).store(any(), anyLong());
    }

    private Fixture fixture() {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(2L);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(7L);
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getTopicType()).thenReturn("KLTN");
        when(period.getReviewerScoreDeadline()).thenReturn(LocalDate.of(2026, 10, 15));
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);
        when(registration.getVersion()).thenReturn(0L);
        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getSupervisors()).thenReturn(List.of());
        when(registration.getTopic()).thenReturn(topic);
        when(registrations.findPeriodIdByRegistrationId(51L)).thenReturn(Optional.of(7L));
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(registrations.findByIdForUpdate(51L)).thenReturn(Optional.of(registration));
        UserAccountEntity reviewer = mock(UserAccountEntity.class);
        when(reviewer.getId()).thenReturn(11L);
        when(reviewer.isEnabled()).thenReturn(true);
        when(reviewer.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(reviewer.getFaculty()).thenReturn(faculty);
        return new Fixture(registration, reviewer);
    }

    private static ReviewerAssignmentEntity assignment(Fixture fixture) {
        ReviewerAssignmentEntity assignment = new ReviewerAssignmentEntity(
                fixture.registration, fixture.reviewer, LocalDate.of(2026, 10, 15),
                mock(UserAccountEntity.class), Instant.parse("2026-10-14T00:00:00Z"));
        try {
            java.lang.reflect.Field id = ReviewerAssignmentEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(assignment, 71L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return assignment;
    }

    private static ReportUpload pdf() {
        byte[] bytes = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return new ReportUpload("review.pdf", "application/pdf", bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private record Fixture(RegistrationEntity registration, UserAccountEntity reviewer) { }
}
