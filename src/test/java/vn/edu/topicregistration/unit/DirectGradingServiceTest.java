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
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.DirectGradingService.AssignCommand;
import vn.edu.topicregistration.service.NotificationService;

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
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        DirectGradingService service = new DirectGradingService(
                periods,
                registrations,
                accounts,
                councils,
                councilTopics,
                reports,
                authorization,
                mock(NotificationService.class),
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Dependencies(
                periods, registrations, accounts, councils, councilTopics,
                reports, authorization, service);
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
            AuthorizationPolicy authorization,
            DirectGradingService service) {
    }
}
