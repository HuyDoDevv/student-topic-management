package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import jakarta.persistence.EntityManager;
import vn.edu.topicregistration.entity.CouncilEntity;
import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.ProjectReportEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.ReviewerEvaluationEntity;
import vn.edu.topicregistration.entity.ReviewerReportEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;
import java.time.LocalDate;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.DirectGradingService.AssignCommand;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.util.ProjectTime;

class DirectGradingServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T02:00:00Z");

    @Test
    void deanAssignsOneIndependentGraderAndImmediatelyLocksTheReport() throws Exception {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "COURSE", List.of());
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());
        when(dependencies.councils().saveAndFlush(any())).thenAnswer(invocation -> {
            CouncilEntity council = invocation.getArgument(0);
            setId(council, 41L);
            return council;
        });
        when(dependencies.councilTopics().saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DirectGradingService.DirectAssignmentView assigned = dependencies.service()
                .assign(new AssignCommand(51L, 11L));

        InOrder order = inOrder(
                dependencies.periods(),
                dependencies.authorization(),
                dependencies.registrations(),
                dependencies.reports(),
                dependencies.councilTopics(),
                dependencies.accounts(),
                dependencies.councils());
        order.verify(dependencies.periods()).findByIdForUpdate(7L);
        order.verify(dependencies.authorization()).requireDeanOfFaculty(2L);
        order.verify(dependencies.registrations()).findByIdForUpdate(51L);
        order.verify(dependencies.reports()).findCurrentByRegistrationIdForUpdate(51L);
        order.verify(dependencies.councilTopics()).findByRegistrationId(51L);
        order.verify(dependencies.accounts()).findByIdForUpdate(11L);
        order.verify(dependencies.councils()).saveAndFlush(any());

        ArgumentCaptor<CouncilEntity> councilCaptor = ArgumentCaptor.forClass(CouncilEntity.class);
        verify(dependencies.councils()).saveAndFlush(councilCaptor.capture());
        CouncilEntity council = councilCaptor.getValue();
        assertEquals(CouncilMode.DIRECT, council.getMode());
        assertSame(fixture.grader(), council.getChair());
        assertSame(fixture.grader(), council.getSecretary());
        assertEquals(CouncilStatus.ACTIVE, council.getStatus());

        ArgumentCaptor<CouncilTopicEntity> assignmentCaptor =
                ArgumentCaptor.forClass(CouncilTopicEntity.class);
        verify(dependencies.councilTopics()).saveAndFlush(assignmentCaptor.capture());
        assertEquals(NOW, assignmentCaptor.getValue().getScheduledAt());
        assertEquals("Chấm độc lập", assignmentCaptor.getValue().getLocation());
        assertEquals(7L, assigned.periodId());
        assertEquals(CouncilStatus.ACTIVE, assigned.status());
    }

    @Test
    void directGraderMayBeFromAnotherDepartmentInTheSameFaculty() throws Exception {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "COURSE", List.of());
        when(fixture.topic().getDepartment()).thenReturn(mock(DepartmentEntity.class));
        when(fixture.grader().getDepartment()).thenReturn(mock(DepartmentEntity.class));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());
        when(dependencies.councils().saveAndFlush(any())).thenAnswer(invocation -> {
            CouncilEntity council = invocation.getArgument(0);
            setId(council, 41L);
            return council;
        });
        when(dependencies.councilTopics().saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var assigned = dependencies.service().assign(new AssignCommand(51L, 11L));

        assertEquals(CouncilStatus.ACTIVE, assigned.status());
        verify(dependencies.councils()).saveAndFlush(any());
    }

    @Test
    void rejectsSupervisorAndDoesNotCreateDirectAssignment() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "NCKH", List.of());
        TopicSupervisorEntity supervisor = mock(TopicSupervisorEntity.class);
        when(supervisor.getLecturer()).thenReturn(fixture.grader());
        when(fixture.topic().getSupervisors()).thenReturn(List.of(supervisor));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void rejectsForgedDeanGraderEvenIfLegacyLecturerRoleIsPresent() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "COURSE", List.of());
        when(fixture.grader().getRoleCodes())
                .thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, rejected.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void graderWorkspaceOmitsDeanReturnedByLegacyCandidateQuery() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "COURSE", List.of());
        UserAccountEntity dean = mock(UserAccountEntity.class);
        when(dean.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        when(dependencies.authorization().requireRole(RoleCode.DEAN)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        when(dependencies.periods().findAllByFacultyIdOrderByLecturerStartDesc(2L))
                .thenReturn(List.of(fixture.period()));
        when(dependencies.registrations().findAllByPeriodIdOrderBySubmittedAtDescIdDesc(7L))
                .thenReturn(List.of(fixture.registration()));
        when(dependencies.reports().findByRegistrationIdAndReplacedAtIsNull(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));
        when(dependencies.councilTopics().findByRegistrationId(51L)).thenReturn(Optional.empty());
        when(dependencies.accounts().findEnabledByFacultyAndRole(2L, RoleCode.LECTURER))
                .thenReturn(List.of(fixture.grader(), dean));

        var workspace = dependencies.service().workspaceForCurrentDean();

        assertEquals(List.of(11L), workspace.availableRegistrations().getFirst()
                .eligibleGraders().stream().map(option -> option.id()).toList());
    }

    @Test
    void rejectsTopicFromAnotherPeriodInSameFacultyWithoutCreatingAssignment() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "COURSE", List.of());
        RegistrationPeriodEntity otherPeriod = mock(RegistrationPeriodEntity.class);
        when(otherPeriod.getId()).thenReturn(8L);
        FacultyEntity faculty = fixture.period().getFaculty();
        when(otherPeriod.getFaculty()).thenReturn(faculty);
        when(fixture.topic().getPeriod()).thenReturn(otherPeriod);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void requiresCurrentReportBeforeAssignment() {
        Dependencies dependencies = dependencies();
        fixture(dependencies, "COURSE", List.of());
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.empty());
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void thesisRegistrationCannotUseDirectAssignmentPath() {
        Dependencies dependencies = dependencies();
        fixture(dependencies, "KLTN", List.of());
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, exception.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void tlcnWaitsForCompleteReviewerSubmission() {
        Dependencies dependencies = dependencies();
        fixture(dependencies, "TLCN", List.of());
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void tlcnAssignsDifferentSecondGraderAfterReviewerSubmittedAllScores() throws Exception {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "TLCN", List.of());
        completeReviewer(dependencies, fixture, 12L);
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());
        when(dependencies.councils().saveAndFlush(any())).thenAnswer(invocation -> {
            CouncilEntity council = invocation.getArgument(0);
            setId(council, 41L);
            return council;
        });
        when(dependencies.councilTopics().saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = dependencies.service().assign(new AssignCommand(51L, 11L));

        assertEquals(CouncilStatus.ACTIVE, result.status());
        verify(dependencies.councils()).saveAndFlush(any());
    }

    @Test
    void tlcnRejectsReviewerAsSecondGrader() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, "TLCN", List.of());
        completeReviewer(dependencies, fixture, 11L);
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().assign(new AssignCommand(51L, 11L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    private static void completeReviewer(
            Dependencies dependencies, Fixture fixture, long reviewerId) {
        ReviewerAssignmentEntity assignment = mock(ReviewerAssignmentEntity.class);
        UserAccountEntity reviewer = mock(UserAccountEntity.class);
        when(reviewer.getId()).thenReturn(reviewerId);
        when(assignment.getId()).thenReturn(71L);
        when(assignment.getReviewer()).thenReturn(reviewer);
        when(assignment.getStatus()).thenReturn(ReviewerAssignmentEntity.Status.SUBMITTED);
        when(assignment.getDeadlineDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(assignment.getSubmittedAt()).thenReturn(NOW.minusSeconds(3600));
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.of(assignment));
        ReviewerReportEntity report = mock(ReviewerReportEntity.class);
        when(report.getSubmittedAt()).thenReturn(NOW.minusSeconds(3600));
        when(dependencies.reviewerReports().findByAssignmentId(71L))
                .thenReturn(Optional.of(report));
        RegistrationMemberEntity member = mock(RegistrationMemberEntity.class);
        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getId()).thenReturn(101L);
        when(member.getStudent()).thenReturn(student);
        when(dependencies.registrationMembers().findAllByRegistrationId(51L))
                .thenReturn(List.of(member));
        ReviewerEvaluationEntity evaluation = mock(ReviewerEvaluationEntity.class);
        when(evaluation.getRegistration()).thenReturn(fixture.registration());
        when(evaluation.getStudent()).thenReturn(student);
        when(evaluation.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(evaluation.getSubmittedAt()).thenReturn(NOW.minusSeconds(3600));
        when(dependencies.reviewerEvaluations().findAllByAssignmentIdForUpdate(71L))
                .thenReturn(List.of(evaluation));
    }

    private static Fixture fixture(
            Dependencies dependencies,
            String topicType,
            List<TopicSupervisorEntity> supervisors) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(2L);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(7L);
        when(period.getName()).thenReturn("Đợt " + topicType);
        when(period.getTopicType()).thenReturn(topicType);
        when(period.getFaculty()).thenReturn(faculty);

        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getTitle()).thenReturn("Đề tài thử nghiệm");
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getSupervisors()).thenReturn(supervisors);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        when(group.getId()).thenReturn(21L);
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getGroup()).thenReturn(group);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);

        UserAccountEntity grader = mock(UserAccountEntity.class);
        when(grader.getId()).thenReturn(11L);
        when(grader.getFullName()).thenReturn("Giảng viên B");
        when(grader.getUsername()).thenReturn("lecturer-b");
        when(grader.isEnabled()).thenReturn(true);
        when(grader.getFaculty()).thenReturn(faculty);
        when(grader.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));

        when(dependencies.registrations().findPeriodIdByRegistrationId(51L))
                .thenReturn(Optional.of(7L));
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        when(dependencies.registrations().findByIdForUpdate(51L))
                .thenReturn(Optional.of(registration));
        when(dependencies.accounts().findByIdForUpdate(11L)).thenReturn(Optional.of(grader));
        return new Fixture(period, topic, registration, grader);
    }

    private static Dependencies dependencies() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        CouncilRepository councils = mock(CouncilRepository.class);
        CouncilTopicRepository councilTopics = mock(CouncilTopicRepository.class);
        ProjectReportRepository reports = mock(ProjectReportRepository.class);
        RegistrationMemberRepository registrationMembers = mock(RegistrationMemberRepository.class);
        ReviewerAssignmentRepository reviewerAssignments = mock(ReviewerAssignmentRepository.class);
        ReviewerEvaluationRepository reviewerEvaluations = mock(ReviewerEvaluationRepository.class);
        ReviewerReportRepository reviewerReports = mock(ReviewerReportRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        DirectGradingService service = new DirectGradingService(
                periods,
                registrations,
                accounts,
                councils,
                councilTopics,
                reports,
                registrationMembers,
                reviewerAssignments,
                reviewerEvaluations,
                reviewerReports,
                authorization,
                mock(NotificationService.class),
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        return new Dependencies(
                periods, registrations, accounts, councils, councilTopics,
                reports, registrationMembers, reviewerAssignments,
                reviewerEvaluations, reviewerReports, authorization, service);
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private record Fixture(
            RegistrationPeriodEntity period,
            TopicEntity topic,
            RegistrationEntity registration,
            UserAccountEntity grader) {
    }

    private record Dependencies(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilTopicRepository councilTopics,
            ProjectReportRepository reports,
            RegistrationMemberRepository registrationMembers,
            ReviewerAssignmentRepository reviewerAssignments,
            ReviewerEvaluationRepository reviewerEvaluations,
            ReviewerReportRepository reviewerReports,
            AuthorizationPolicy authorization,
            DirectGradingService service) {
    }
}
