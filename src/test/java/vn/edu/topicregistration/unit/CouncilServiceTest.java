package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.entity.CouncilEntity;
import vn.edu.topicregistration.entity.CouncilMemberEntity;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.ProjectReportEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.CouncilService;
import vn.edu.topicregistration.service.CouncilService.AssignmentCommand;
import vn.edu.topicregistration.service.CouncilService.CreateCouncilCommand;
import vn.edu.topicregistration.service.NotificationService;

class CouncilServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final Instant REVIEW_AT = Instant.parse("2026-09-20T02:00:00Z");

    @Test
    void createsDraftAfterPeriodLockAndPersistsOnlyAdditionalTriggerMembers() throws Exception {
        Dependencies dependencies = dependencies();
        CreationFixture fixture = validCreation(dependencies);
        when(dependencies.councils().saveAndFlush(any())).thenAnswer(invocation -> {
            CouncilEntity council = invocation.getArgument(0);
            setId(council, 41L);
            return council;
        });

        CouncilService.CouncilSummaryView created = dependencies.service().create(
                new CreateCouncilCommand(
                        7L, 4L, "  Hội đồng KLTN  ", 11L, 12L, List.of(13L)));

        InOrder order = inOrder(
                dependencies.periods(),
                dependencies.authorization(),
                dependencies.councils(),
                dependencies.members());
        order.verify(dependencies.periods()).findByIdForUpdate(7L);
        order.verify(dependencies.authorization()).requireDeanOfFaculty(2L);
        order.verify(dependencies.councils()).saveAndFlush(any());
        order.verify(dependencies.members()).saveAll(any());

        ArgumentCaptor<Iterable<CouncilMemberEntity>> savedMembers =
                ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.members()).saveAll(savedMembers.capture());
        List<CouncilMemberEntity> additional = ((List<CouncilMemberEntity>) savedMembers
                .getValue());
        assertEquals(1, additional.size());
        assertEquals(13L, additional.getFirst().getLecturer().getId());
        assertEquals(3, additional.getFirst().getSlot());
        assertEquals("Hội đồng KLTN", created.name());
        assertEquals(CouncilStatus.DRAFT, created.status());
        assertEquals(2L, fixture.faculty().getId());
    }

    @Test
    void rejectsDeanOutsideFacultyBeforeCouncilPersistence() {
        Dependencies dependencies = dependencies();
        RegistrationPeriodEntity period = mockPeriod("TLCN");
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.authorization().requireDeanOfFaculty(2L))
                .thenThrow(new AccessDeniedException("outside faculty"));

        assertThrows(AccessDeniedException.class, () -> dependencies.service().create(
                new CreateCouncilCommand(
                        7L, 4L, "Hội đồng", 11L, 12L, List.of(13L))));

        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void locksPeriodThenCouncilAndChildrenBeforeSavingAnAssignment() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.DRAFT, 4L);

        dependencies.service().saveAssignment(41L, new AssignmentCommand(
                51L, REVIEW_AT, "Phòng A1-01", 4L));

        InOrder order = inOrder(
                dependencies.periods(),
                dependencies.councils(),
                dependencies.councilTopics(),
                dependencies.members(),
                dependencies.registrations());
        order.verify(dependencies.periods()).findByIdForUpdate(7L);
        order.verify(dependencies.councils()).findByIdForUpdate(41L);
        order.verify(dependencies.councilTopics()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.members()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.registrations()).findByIdForUpdate(51L);
        verify(dependencies.councilTopics()).saveAndFlush(any(CouncilTopicEntity.class));
        assertEquals(51L, fixture.registration().getId());
    }

    @Test
    void assignmentRejectsSupervisorWithoutChangingCouncilTopics() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.DRAFT, 4L);
        TopicSupervisorEntity supervisor = mock(TopicSupervisorEntity.class);
        UserAccountEntity councilMember = fixture.members().getFirst().getLecturer();
        when(supervisor.getLecturer()).thenReturn(councilMember);
        when(fixture.topic().getSupervisors()).thenReturn(List.of(supervisor));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().saveAssignment(41L, new AssignmentCommand(
                        51L, REVIEW_AT, "Phòng A1-01", 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void assignmentRevalidatesCurrentLecturerEligibility() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.DRAFT, 4L);
        when(fixture.members().getLast().getLecturer().isEnabled()).thenReturn(false);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().saveAssignment(41L, new AssignmentCommand(
                        51L, REVIEW_AT, "Phòng A1-01", 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS, exception.code());
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void staleVersionFailsBeforeChildMutation() {
        Dependencies dependencies = dependencies();
        validAssignment(dependencies, CouncilStatus.DRAFT, 5L);

        assertThrows(OptimisticLockingFailureException.class,
                () -> dependencies.service().saveAssignment(41L, new AssignmentCommand(
                        51L, REVIEW_AT, "Phòng A1-01", 4L)));

        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void activationRequiresEveryCurrentReportAndLeavesStateUnchangedWhenMissing() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(
                dependencies, CouncilStatus.SCHEDULED, 4L);
        CouncilTopicEntity assignment = mock(CouncilTopicEntity.class);
        when(assignment.getRegistration()).thenReturn(fixture.registration());
        when(assignment.getScheduledAt()).thenReturn(REVIEW_AT);
        when(assignment.getLocation()).thenReturn("Phòng A1-01");
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(assignment));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().transition(
                        41L, 4L, CouncilStatus.ACTIVE));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, exception.code());
        verify(fixture.council(), never()).transitionTo(any());
    }

    @Test
    void validScheduledCouncilActivatesAfterRevalidatingReports() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(
                dependencies, CouncilStatus.SCHEDULED, 4L);
        CouncilTopicEntity assignment = mock(CouncilTopicEntity.class);
        when(assignment.getRegistration()).thenReturn(fixture.registration());
        when(assignment.getScheduledAt()).thenReturn(REVIEW_AT);
        when(assignment.getLocation()).thenReturn("Phòng A1-01");
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(assignment));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));

        dependencies.service().transition(41L, 4L, CouncilStatus.ACTIVE);

        verify(fixture.council()).transitionTo(CouncilStatus.ACTIVE);
        verify(dependencies.councils()).flush();
    }

    private static CreationFixture validCreation(Dependencies dependencies) {
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        FacultyEntity faculty = period.getFaculty();
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(4L);
        when(department.getName()).thenReturn("Công nghệ phần mềm");
        when(department.getFaculty()).thenReturn(faculty);
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.departments().findByIdAndFacultyId(4L, 2L))
                .thenReturn(Optional.of(department));
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(3L, Set.of(RoleCode.LECTURER, RoleCode.DEAN), 4L, 2L));
        for (long id = 11; id <= 13; id++) {
            UserAccountEntity lecturer = mockLecturer(id, department);
            when(dependencies.accounts().findDetailedById(id)).thenReturn(Optional.of(lecturer));
        }
        return new CreationFixture(faculty);
    }

    private static AssignmentFixture validAssignment(
            Dependencies dependencies,
            CouncilStatus status,
            long version) {
        RegistrationPeriodEntity period = mockPeriod("TLCN");
        FacultyEntity faculty = period.getFaculty();
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(4L);
        when(department.getFaculty()).thenReturn(faculty);
        CouncilEntity council = mock(CouncilEntity.class);
        when(council.getId()).thenReturn(41L);
        when(council.getPeriod()).thenReturn(period);
        when(council.getDepartment()).thenReturn(department);
        when(council.getStatus()).thenReturn(status);
        when(council.getVersion()).thenReturn(version);

        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getDepartment()).thenReturn(department);
        when(topic.getSupervisors()).thenReturn(List.of());
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);

        UserAccountEntity first = mockLecturer(11L, department);
        UserAccountEntity second = mockLecturer(12L, department);
        UserAccountEntity third = mockLecturer(13L, department);
        when(council.getChair()).thenReturn(first);
        when(council.getSecretary()).thenReturn(second);
        List<CouncilMemberEntity> members = List.of(
                mockMember(council, first, 1),
                mockMember(council, second, 2),
                mockMember(council, third, 3));

        when(dependencies.councils().findPeriodIdByCouncilId(41L))
                .thenReturn(Optional.of(7L));
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(3L, Set.of(RoleCode.LECTURER, RoleCode.DEAN), 4L, 2L));
        when(dependencies.councils().findByIdForUpdate(41L)).thenReturn(Optional.of(council));
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of());
        when(dependencies.members().findAllByCouncilIdForUpdate(41L)).thenReturn(members);
        when(dependencies.registrations().findByIdForUpdate(51L))
                .thenReturn(Optional.of(registration));
        when(dependencies.councilTopics().findByRegistrationId(51L))
                .thenReturn(Optional.empty());
        when(dependencies.councilTopics().saveAndFlush(any())).thenAnswer(
                invocation -> invocation.getArgument(0));
        return new AssignmentFixture(council, registration, topic, members);
    }

    private static RegistrationPeriodEntity mockPeriod(String type) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(2L);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(7L);
        when(period.getName()).thenReturn("Đợt KLTN");
        when(period.getTopicType()).thenReturn(type);
        when(period.getFaculty()).thenReturn(faculty);
        return period;
    }

    private static UserAccountEntity mockLecturer(long id, DepartmentEntity department) {
        UserAccountEntity lecturer = mock(UserAccountEntity.class);
        when(lecturer.getId()).thenReturn(id);
        when(lecturer.getFullName()).thenReturn("Giảng viên " + id);
        when(lecturer.getUsername()).thenReturn("lecturer" + id);
        when(lecturer.isEnabled()).thenReturn(true);
        when(lecturer.getDepartment()).thenReturn(department);
        when(lecturer.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        return lecturer;
    }

    private static CouncilMemberEntity mockMember(
            CouncilEntity council,
            UserAccountEntity lecturer,
            int slot) {
        CouncilMemberEntity member = mock(CouncilMemberEntity.class);
        when(member.getCouncil()).thenReturn(council);
        when(member.getLecturer()).thenReturn(lecturer);
        when(member.getSlot()).thenReturn(slot);
        return member;
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static Dependencies dependencies() {
        return new Dependencies(
                mock(RegistrationPeriodRepository.class),
                mock(DepartmentRepository.class),
                mock(UserAccountRepository.class),
                mock(CouncilRepository.class),
                mock(CouncilMemberRepository.class),
                mock(CouncilTopicRepository.class),
                mock(RegistrationRepository.class),
                mock(ProjectReportRepository.class),
                mock(AuthorizationPolicy.class),
                mock(EntityManager.class));
    }

    private record Dependencies(
            RegistrationPeriodRepository periods,
            DepartmentRepository departments,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilMemberRepository members,
            CouncilTopicRepository councilTopics,
            RegistrationRepository registrations,
            ProjectReportRepository reports,
            AuthorizationPolicy authorization,
            EntityManager entityManager) {
        CouncilService service() {
            return new CouncilService(
                    periods,
                    departments,
                    accounts,
                    councils,
                    members,
                    councilTopics,
                    registrations,
                    reports,
                    authorization,
                    mock(NotificationService.class),
                    entityManager,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    private record CreationFixture(FacultyEntity faculty) {
    }

    private record AssignmentFixture(
            CouncilEntity council,
            RegistrationEntity registration,
            TopicEntity topic,
            List<CouncilMemberEntity> members) {
    }
}
