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
import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.EvaluationEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.ProjectReportEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.CouncilService;
import vn.edu.topicregistration.service.CouncilService.AssignmentCommand;
import vn.edu.topicregistration.service.CouncilService.CreateCouncilCommand;
import vn.edu.topicregistration.service.CouncilService.UpdateMembersCommand;
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
                        7L, "  Hội đồng KLTN  ", 11L, 12L, List.of(13L)));

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
    void councilStillAcceptsLecturersFromDifferentDepartmentsOfSameFaculty() throws Exception {
        Dependencies dependencies = dependencies();
        validCreation(dependencies);
        DepartmentEntity firstDepartment = mock(DepartmentEntity.class);
        DepartmentEntity secondDepartment = mock(DepartmentEntity.class);
        when(dependencies.accounts().findDetailedById(11L).orElseThrow().getDepartment())
                .thenReturn(firstDepartment);
        when(dependencies.accounts().findDetailedById(12L).orElseThrow().getDepartment())
                .thenReturn(secondDepartment);
        when(dependencies.councils().saveAndFlush(any())).thenAnswer(invocation -> {
            CouncilEntity council = invocation.getArgument(0);
            setId(council, 41L);
            return council;
        });

        var created = dependencies.service().create(new CreateCouncilCommand(
                7L, "Hội đồng nhiều Bộ môn", 11L, 12L, List.of(13L)));

        assertEquals(CouncilStatus.DRAFT, created.status());
        verify(dependencies.councils()).saveAndFlush(any());
    }

    @Test
    void rejectsForgedDeanCouncilMemberEvenIfLegacyLecturerRoleIsPresent() {
        Dependencies dependencies = dependencies();
        validCreation(dependencies);
        UserAccountEntity dean = dependencies.accounts().findDetailedById(13L).orElseThrow();
        when(dean.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().create(new CreateCouncilCommand(
                        7L, "Hội đồng", 11L, 12L, List.of(13L))));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS, rejected.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
    }

    @Test
    void creationOptionsExcludePeriodsAfterStudentWindowEnds() {
        Dependencies dependencies = dependencies();
        RegistrationPeriodEntity openPeriod = mockPeriod("KLTN");
        RegistrationPeriodEntity closedPeriod = mockPeriod("KLTN");
        when(closedPeriod.getId()).thenReturn(8L);
        when(closedPeriod.getName()).thenReturn("Đợt KLTN đã kết thúc");
        when(closedPeriod.getStudentEnd()).thenReturn(NOW.minusNanos(1));
        when(dependencies.authorization().requireRole(RoleCode.DEAN)).thenReturn(
                new ActorIdentity(3L, Set.of(RoleCode.DEAN), 2L));
        when(dependencies.periods().findAllByFacultyIdOrderByLecturerStartDesc(2L))
                .thenReturn(List.of(closedPeriod, openPeriod));

        var options = dependencies.service().creationOptionsForCurrentDean();

        assertEquals(List.of(7L), options.periods().stream()
                .map(CouncilService.CouncilPeriodOption::id).toList());
    }

    @Test
    void rejectsCreationWorkspaceAfterStudentWindowEnds() {
        Dependencies dependencies = dependencies();
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        when(period.getStudentEnd()).thenReturn(NOW.minusNanos(1));
        when(dependencies.periods().findById(7L)).thenReturn(Optional.of(period));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().creationWorkspace(7L));

        assertEquals(ApplicationErrorCode.WINDOW_CLOSED, rejected.code());
        verify(dependencies.accounts(), never())
                .findEnabledByFacultyAndRole(2L, RoleCode.LECTURER);
    }

    @Test
    void rejectsCouncilCreationAfterStudentWindowEndsBeforePersistenceOrNotification() {
        Dependencies dependencies = dependencies();
        validCreation(dependencies);
        RegistrationPeriodEntity period = dependencies.periods()
                .findByIdForUpdate(7L).orElseThrow();
        when(period.getStudentEnd()).thenReturn(NOW.minusNanos(1));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().create(new CreateCouncilCommand(
                        7L, "Hội đồng", 11L, 12L, List.of(13L))));

        assertEquals(ApplicationErrorCode.WINDOW_CLOSED, rejected.code());
        verify(dependencies.councils(), never()).saveAndFlush(any());
        verify(dependencies.notifications(), never()).emit(any());
    }

    @Test
    void creationWorkspaceRemainsOpenAtExactStudentEndInstant() {
        Dependencies dependencies = dependencies();
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        when(dependencies.periods().findById(7L)).thenReturn(Optional.of(period));
        when(dependencies.accounts().findEnabledByFacultyAndRole(2L, RoleCode.LECTURER))
                .thenReturn(List.of());

        var workspace = dependencies.service().creationWorkspace(7L);

        assertEquals(7L, workspace.periodId());
    }

    @Test
    void councilWorkspaceOmitsDeanReturnedByLegacyCandidateQuery() {
        Dependencies dependencies = dependencies();
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        when(dependencies.periods().findById(7L)).thenReturn(Optional.of(period));
        UserAccountEntity lecturer = mockLecturer(11L, period.getFaculty());
        UserAccountEntity dean = mockLecturer(12L, period.getFaculty());
        when(dean.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        when(dependencies.accounts().findEnabledByFacultyAndRole(2L, RoleCode.LECTURER))
                .thenReturn(List.of(lecturer, dean));

        var workspace = dependencies.service().creationWorkspace(7L);

        assertEquals(List.of(11L), workspace.lecturers().stream()
                .map(option -> option.id()).toList());
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
                        7L, "Hội đồng", 11L, 12L, List.of(13L))));

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
    void rejectsPastOrEqualScheduleWithoutPersistingAssignment() {
        Dependencies dependencies = dependencies();
        validAssignment(dependencies, CouncilStatus.DRAFT, 4L);

        assertThrows(CouncilService.InvalidScheduleException.class,
                () -> dependencies.service().saveAssignment(41L,
                        new AssignmentCommand(51L, NOW.minusSeconds(1), "Phòng A1", 4L)));
        assertThrows(CouncilService.InvalidScheduleException.class,
                () -> dependencies.service().saveAssignment(41L,
                        new AssignmentCommand(51L, NOW, "Phòng A1", 4L)));
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void acceptsFutureScheduleWhenUpdatingExistingAssignment() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.ASSIGNED, 4L);
        CouncilTopicEntity existing = mock(CouncilTopicEntity.class);
        when(existing.getCouncil()).thenReturn(fixture.council());
        when(existing.getRegistration()).thenReturn(fixture.registration());
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(existing));

        dependencies.service().saveAssignment(41L,
                new AssignmentCommand(51L, NOW.plusSeconds(1), "Phòng B2", 4L));

        verify(existing).schedule(NOW.plusSeconds(1), "Phòng B2");
        verify(dependencies.councilTopics()).saveAndFlush(existing);
    }

    @Test
    void rejectsPastScheduleWhenUpdatingExistingAssignment() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.ASSIGNED, 4L);
        CouncilTopicEntity existing = mock(CouncilTopicEntity.class);
        when(existing.getCouncil()).thenReturn(fixture.council());
        when(existing.getRegistration()).thenReturn(fixture.registration());
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(existing));

        assertThrows(CouncilService.InvalidScheduleException.class,
                () -> dependencies.service().saveAssignment(41L,
                        new AssignmentCommand(51L, NOW.minusSeconds(1), "Phòng B2", 4L)));

        verify(existing, never()).schedule(any(), any());
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void refusesMemberChangesAfterAnyEvaluationEvenInDraft() {
        Dependencies dependencies = dependencies();
        validAssignment(dependencies, CouncilStatus.DRAFT, 4L);
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(mock(EvaluationEntity.class)));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().updateMembers(41L,
                        new UpdateMembersCommand(11L, 12L, List.of(13L), 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, rejected.code());
        verify(dependencies.members(), never()).delete(any());
    }

    @Test
    void refusesMemberChangesAfterCouncilBecomesActive() {
        Dependencies dependencies = dependencies();
        validAssignment(dependencies, CouncilStatus.ACTIVE, 4L);

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().updateMembers(41L,
                        new UpdateMembersCommand(11L, 12L, List.of(13L), 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, rejected.code());
        verify(dependencies.members(), never()).delete(any());
    }

    @Test
    void refusesSupervisorAsMemberAcrossAssignedTopics() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.ASSIGNED, 4L);
        CouncilTopicEntity assignment = mock(CouncilTopicEntity.class);
        when(assignment.getRegistration()).thenReturn(fixture.registration());
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(assignment));
        for (CouncilMemberEntity member : fixture.members()) {
            UserAccountEntity lecturer = member.getLecturer();
            when(dependencies.accounts().findDetailedById(lecturer.getId()))
                    .thenReturn(Optional.of(lecturer));
        }
        TopicSupervisorEntity supervisor = mock(TopicSupervisorEntity.class);
        UserAccountEntity supervisedLecturer = fixture.members().getLast().getLecturer();
        when(supervisor.getLecturer()).thenReturn(supervisedLecturer);
        when(fixture.topic().getSupervisors()).thenReturn(List.of(supervisor));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().updateMembers(41L,
                        new UpdateMembersCommand(11L, 12L, List.of(13L), 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS, rejected.code());
        verify(dependencies.members(), never()).delete(any());
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
    void assignmentRejectsReviewerWhoIsAlsoCouncilMember() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.DRAFT, 4L);
        ReviewerAssignmentEntity reviewerAssignment = dependencies.reviewerAssignments()
                .findActiveByRegistrationIdForUpdate(51L, List.of(
                        ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)).orElseThrow();
        UserAccountEntity member = fixture.members().getLast().getLecturer();
        when(reviewerAssignment.getReviewer()).thenReturn(member);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().saveAssignment(41L, new AssignmentCommand(
                        51L, REVIEW_AT, "Phòng A1-01", 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void assignmentRejectsRegistrationWithoutActiveReviewer() {
        Dependencies dependencies = dependencies();
        validAssignment(dependencies, CouncilStatus.DRAFT, 4L);
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().saveAssignment(41L, new AssignmentCommand(
                        51L, REVIEW_AT, "Phòng A1-01", 4L)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.councilTopics(), never()).saveAndFlush(any());
    }

    @Test
    void assignmentRejectsTopicFromAnotherPeriodInSameFaculty() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(dependencies, CouncilStatus.DRAFT, 4L);
        RegistrationPeriodEntity otherPeriod = mockPeriod("TLCN");
        when(otherPeriod.getId()).thenReturn(8L);
        when(fixture.topic().getPeriod()).thenReturn(otherPeriod);

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

    @Test
    void activationRechecksReviewerAgainstEveryCouncilMember() {
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
        ReviewerAssignmentEntity reviewerAssignment = dependencies.reviewerAssignments()
                .findActiveByRegistrationIdForUpdate(51L, List.of(
                        ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)).orElseThrow();
        UserAccountEntity member = fixture.members().getFirst().getLecturer();
        when(reviewerAssignment.getReviewer()).thenReturn(member);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().transition(41L, 4L, CouncilStatus.ACTIVE));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, exception.code());
        verify(fixture.council(), never()).transitionTo(any());
    }

    @Test
    void activeChairInFacultyWithCurrentMembershipCanViewOnlyAssignedCouncil() {
        Dependencies dependencies = dependencies();
        CouncilEntity council = detailCouncil(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L));
        when(dependencies.members().existsByCouncilIdAndLecturerId(41L, 11L))
                .thenReturn(true);

        var detail = dependencies.service().detailForCurrentDean(41L);

        assertEquals(41L, detail.council().id());
        assertEquals(false, detail.canComplete());
        verify(dependencies.members()).existsByCouncilIdAndLecturerId(41L, 11L);
    }

    @Test
    void chairCannotViewCouncilAfterTransferMembershipLossOrCompletion() {
        Dependencies dependencies = dependencies();
        CouncilEntity council = detailCouncil(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 99L));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().detailForCurrentDean(41L));

        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().detailForCurrentDean(41L));

        when(dependencies.members().existsByCouncilIdAndLecturerId(41L, 11L))
                .thenReturn(true);
        when(council.getStatus()).thenReturn(CouncilStatus.COMPLETED);
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().detailForCurrentDean(41L));
    }

    @Test
    void deanKeepsFacultyCouncilDetailAccessAfterCompletion() {
        Dependencies dependencies = dependencies();
        detailCouncil(dependencies, CouncilStatus.COMPLETED);
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));

        var detail = dependencies.service().detailForCurrentDean(41L);

        assertEquals(41L, detail.council().id());
        assertEquals(true, detail.canPublish());
    }

    @Test
    void onlyDeanReceivesActiveCouncilCompletionCapability() {
        Dependencies dependencies = dependencies();
        detailCouncil(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));

        var detail = dependencies.service().detailForCurrentDean(41L);

        assertEquals(true, detail.canComplete());
    }

    @Test
    void deanSeesOnlySubmissionProgressWithoutDraftScores() {
        Dependencies dependencies = dependencies();
        CouncilEntity council = detailCouncil(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        CouncilMemberEntity member = mock(CouncilMemberEntity.class);
        UserAccountEntity chair = council.getChair();
        when(member.getLecturer()).thenReturn(chair);
        CouncilMemberEntity secretaryMember = mock(CouncilMemberEntity.class);
        UserAccountEntity secretary = council.getSecretary();
        when(secretaryMember.getLecturer()).thenReturn(secretary);
        when(dependencies.members().findAllByCouncilIdOrderBySlot(41L))
                .thenReturn(List.of(member, secretaryMember));
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        when(group.getId()).thenReturn(21L);
        when(registration.getGroup()).thenReturn(group);
        TopicEntity registrationTopic = mock(TopicEntity.class);
        when(registrationTopic.getTitle()).thenReturn("Đề tài thử nghiệm");
        when(registration.getTopic()).thenReturn(registrationTopic);
        CouncilTopicEntity topic = mock(CouncilTopicEntity.class);
        when(topic.getRegistration()).thenReturn(registration);
        when(dependencies.councilTopics().findAllByCouncilIdOrderByRegistrationId(41L))
                .thenReturn(List.of(topic));
        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getId()).thenReturn(101L);
        RegistrationMemberEntity snapshot = mock(RegistrationMemberEntity.class);
        when(snapshot.getStudent()).thenReturn(student);
        when(dependencies.registrationMembers().findAllByRegistrationId(51L))
                .thenReturn(List.of(snapshot));
        EvaluationEntity draft = mock(EvaluationEntity.class);
        when(draft.getStudent()).thenReturn(student);
        when(draft.getStatus()).thenReturn(EvaluationStatus.DRAFT);
        when(dependencies.evaluations()
                .findAllByCouncilIdAndRegistrationIdAndEvaluatorIdOrderByStudentId(
                        41L, 51L, 11L)).thenReturn(List.of(draft));

        var membersProgress = dependencies.service().detailForCurrentDean(41L)
                .progress().getFirst().members();
        var progress = membersProgress.getFirst();

        assertEquals("DRAFT", progress.status());
        assertEquals(0, progress.submittedCount());
        assertEquals(1, progress.totalCount());
        assertEquals(null, progress.submittedAt());
        assertEquals("NOT_STARTED", membersProgress.get(1).status());
    }

    private static CouncilEntity detailCouncil(
            Dependencies dependencies, CouncilStatus status) {
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        UserAccountEntity chair = mockLecturer(11L, period.getFaculty());
        UserAccountEntity secretary = mockLecturer(12L, period.getFaculty());
        CouncilEntity council = mock(CouncilEntity.class);
        when(council.getId()).thenReturn(41L);
        when(council.getPeriod()).thenReturn(period);
        when(council.getChair()).thenReturn(chair);
        when(council.getSecretary()).thenReturn(secretary);
        when(council.getMode()).thenReturn(CouncilMode.COUNCIL);
        when(council.getStatus()).thenReturn(status);
        when(dependencies.councils().findDetailedById(41L)).thenReturn(Optional.of(council));
        return council;
    }

    @Test
    void activationRejectsAssignedTopicMovedToAnotherPeriodInSameFaculty() {
        Dependencies dependencies = dependencies();
        AssignmentFixture fixture = validAssignment(
                dependencies, CouncilStatus.SCHEDULED, 4L);
        RegistrationPeriodEntity otherPeriod = mockPeriod("TLCN");
        when(otherPeriod.getId()).thenReturn(8L);
        when(fixture.topic().getPeriod()).thenReturn(otherPeriod);
        CouncilTopicEntity assignment = mock(CouncilTopicEntity.class);
        when(assignment.getRegistration()).thenReturn(fixture.registration());
        when(assignment.getScheduledAt()).thenReturn(REVIEW_AT);
        when(assignment.getLocation()).thenReturn("Phòng A1-01");
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(assignment));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(51L))
                .thenReturn(Optional.of(mock(ProjectReportEntity.class)));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().transition(
                        41L, 4L, CouncilStatus.ACTIVE));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, exception.code());
        verify(fixture.council(), never()).transitionTo(any());
    }

    private static CreationFixture validCreation(Dependencies dependencies) {
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        FacultyEntity faculty = period.getFaculty();
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(3L, Set.of(RoleCode.DEAN), 2L));
        for (long id = 11; id <= 13; id++) {
            UserAccountEntity lecturer = mockLecturer(id, faculty);
            when(dependencies.accounts().findDetailedById(id)).thenReturn(Optional.of(lecturer));
        }
        return new CreationFixture(faculty);
    }

    private static AssignmentFixture validAssignment(
            Dependencies dependencies,
            CouncilStatus status,
            long version) {
        RegistrationPeriodEntity period = mockPeriod("KLTN");
        FacultyEntity faculty = period.getFaculty();
        CouncilEntity council = mock(CouncilEntity.class);
        when(council.getId()).thenReturn(41L);
        when(council.getPeriod()).thenReturn(period);
        when(council.getMode()).thenReturn(CouncilMode.COUNCIL);
        when(council.getStatus()).thenReturn(status);
        when(council.getVersion()).thenReturn(version);

        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getSupervisors()).thenReturn(List.of());
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);

        UserAccountEntity first = mockLecturer(11L, faculty);
        UserAccountEntity second = mockLecturer(12L, faculty);
        UserAccountEntity third = mockLecturer(13L, faculty);
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
                new ActorIdentity(3L, Set.of(RoleCode.DEAN), 2L));
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
        ReviewerAssignmentEntity reviewerAssignment = mock(ReviewerAssignmentEntity.class);
        UserAccountEntity reviewer = mockLecturer(14L, faculty);
        when(reviewerAssignment.getReviewer()).thenReturn(reviewer);
        when(reviewerAssignment.getRegistration()).thenReturn(registration);
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.of(reviewerAssignment));
        return new AssignmentFixture(council, registration, topic, members);
    }

    private static RegistrationPeriodEntity mockPeriod(String type) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(2L);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(7L);
        when(period.getName()).thenReturn("Đợt KLTN");
        when(period.getTopicType()).thenReturn(type);
        when(period.getStudentEnd()).thenReturn(NOW);
        when(period.getFaculty()).thenReturn(faculty);
        return period;
    }

    private static UserAccountEntity mockLecturer(long id, FacultyEntity faculty) {
        UserAccountEntity lecturer = mock(UserAccountEntity.class);
        when(lecturer.getId()).thenReturn(id);
        when(lecturer.getFullName()).thenReturn("Giảng viên " + id);
        when(lecturer.getUsername()).thenReturn("lecturer" + id);
        when(lecturer.isEnabled()).thenReturn(true);
        when(lecturer.getFaculty()).thenReturn(faculty);
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
                mock(UserAccountRepository.class),
                mock(CouncilRepository.class),
                mock(CouncilMemberRepository.class),
                mock(CouncilTopicRepository.class),
                mock(RegistrationRepository.class),
                mock(RegistrationMemberRepository.class),
                mock(ProjectReportRepository.class),
                mock(ReviewerAssignmentRepository.class),
                mock(EvaluationRepository.class),
                mock(AuthorizationPolicy.class),
                mock(EntityManager.class),
                mock(NotificationService.class));
    }

    private record Dependencies(
            RegistrationPeriodRepository periods,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilMemberRepository members,
            CouncilTopicRepository councilTopics,
            RegistrationRepository registrations,
            RegistrationMemberRepository registrationMembers,
            ProjectReportRepository reports,
            ReviewerAssignmentRepository reviewerAssignments,
            EvaluationRepository evaluations,
            AuthorizationPolicy authorization,
            EntityManager entityManager,
            NotificationService notifications) {
        CouncilService service() {
            return new CouncilService(
                    periods,
                    accounts,
                    councils,
                    members,
                    councilTopics,
                    registrations,
                    registrationMembers,
                    reports,
                    reviewerAssignments,
                    evaluations,
                    authorization,
                    notifications,
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
