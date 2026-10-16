package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.ScoreComponentEntity;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ScoreComponentRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.ScoringService.GroupScoreCommand;
import vn.edu.topicregistration.service.ScoringService.ScoreRowCommand;

class ScoringServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T02:00:00Z");

    @Test
    void kltnMemberCannotSubmitWhenReviewerOverlapsCouncil() {
        Dependencies dependencies = dependencies();
        Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
        when(fixture.period().getTopicType()).thenReturn("KLTN");
        when(fixture.council().getMode()).thenReturn(CouncilMode.COUNCIL);
        ReviewerAssignmentEntity reviewer = mock(ReviewerAssignmentEntity.class);
        when(reviewer.getReviewer()).thenReturn(fixture.evaluator());
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.of(reviewer));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        command(101L, "8.00", "Đạt", null)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT, exception.code());
        verify(dependencies.evaluations(), never()).save(any());
    }

    @Test
    void createsDraftUnderPeriodCouncilAndChildLocks() {
        Dependencies dependencies = dependencies();
        Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.evaluations().save(any())).thenAnswer(
                invocation -> invocation.getArgument(0));

        ScoringService.EvaluationView result = dependencies.service().saveGroupDraft(
                41L,
                51L,
                command(101L, "8.50", "Nhận xét", null)).getFirst();

        InOrder order = inOrder(
                dependencies.periods(),
                dependencies.authorization(),
                dependencies.councils(),
                dependencies.councilTopics(),
                dependencies.members(),
                dependencies.registrationMembers(),
                dependencies.evaluations());
        order.verify(dependencies.periods()).findByIdForUpdate(7L);
        order.verify(dependencies.authorization()).requireRole(RoleCode.LECTURER);
        order.verify(dependencies.councils()).findByIdForUpdate(41L);
        order.verify(dependencies.councilTopics()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.members()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.registrationMembers()).findAllByRegistrationId(51L);
        order.verify(dependencies.evaluations()).findAllByCouncilIdForUpdate(41L);

        ArgumentCaptor<EvaluationEntity> saved = ArgumentCaptor.forClass(EvaluationEntity.class);
        verify(dependencies.evaluations()).save(saved.capture());
        assertEquals(fixture.evaluator(), saved.getValue().getEvaluator());
        assertEquals(new BigDecimal("8.50"), saved.getValue().getScore());
        assertEquals(EvaluationStatus.DRAFT, result.status());
    }

    @Test
    void submitCreatesSystemComponentWhenPeriodHasNone() {
        Dependencies dependencies = dependencies();
        validFixture(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.components().findByPeriodIdAndName(
                7L, ScoreComponentEntity.COUNCIL_SCORE_NAME)).thenReturn(Optional.empty());
        when(dependencies.components().saveAndFlush(any())).thenAnswer(
                invocation -> invocation.getArgument(0));
        when(dependencies.evaluations().save(any())).thenAnswer(
                invocation -> invocation.getArgument(0));

        ScoringService.EvaluationView result = dependencies.service().submitGroup(
                41L,
                51L,
                command(101L, "9.00", "Đạt", null)).getFirst();

        verify(dependencies.components()).saveAndFlush(any(ScoreComponentEntity.class));
        assertEquals(EvaluationStatus.SUBMITTED, result.status());
        assertEquals(NOW, result.submittedAt());
    }

    @Test
    void submittedEvaluationCanBeChangedAndSubmittedAgainWhileActive() {
        Dependencies dependencies = dependencies();
        validFixture(dependencies, CouncilStatus.ACTIVE);
        EvaluationEntity existing = mock(EvaluationEntity.class);
        RegistrationEntity registration = registration(dependencies);
        UserAccountEntity evaluator = evaluator(dependencies);
        ScoreComponentEntity component = component(dependencies);
        UserAccountEntity student = student(dependencies);
        when(existing.getRegistration()).thenReturn(registration);
        when(existing.getEvaluator()).thenReturn(evaluator);
        when(existing.getStudent()).thenReturn(student);
        when(existing.getComponent()).thenReturn(component);
        when(existing.getVersion()).thenReturn(3L);
        when(existing.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(existing.getScore()).thenReturn(new BigDecimal("8.00"));
        when(existing.getFeedback()).thenReturn("Cũ");
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(existing));

        dependencies.service().submitGroup(
                41L,
                51L,
                command(101L, "9.25", "Đã sửa", 3L));

        verify(existing).submit(new BigDecimal("9.25"), "Đã sửa", NOW);
        verify(dependencies.evaluations()).flush();
    }

    @Test
    void staleEvaluationVersionCannotOverwriteExistingScore() {
        Dependencies dependencies = dependencies();
        validFixture(dependencies, CouncilStatus.ACTIVE);
        EvaluationEntity existing = mock(EvaluationEntity.class);
        RegistrationEntity registration = registration(dependencies);
        UserAccountEntity evaluator = evaluator(dependencies);
        ScoreComponentEntity component = component(dependencies);
        UserAccountEntity student = student(dependencies);
        when(existing.getRegistration()).thenReturn(registration);
        when(existing.getEvaluator()).thenReturn(evaluator);
        when(existing.getStudent()).thenReturn(student);
        when(existing.getComponent()).thenReturn(component);
        when(existing.getVersion()).thenReturn(4L);
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(existing));

        assertThrows(OptimisticLockingFailureException.class,
                () -> dependencies.service().saveGroupDraft(
                        41L,
                        51L,
                        command(101L, "7.50", null, 3L)));

        verify(existing, never()).saveDraft(any(), any(), any());
        verify(dependencies.evaluations(), never()).flush();
    }

    @Test
    void acceptsCompleteGroupsFromOneToThreeStudents() {
        for (int size = 1; size <= 3; size++) {
            Dependencies dependencies = dependencies();
            Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
            List<RegistrationMemberEntity> snapshots = snapshots(dependencies, fixture, size);
            when(dependencies.evaluations().save(any())).thenAnswer(
                    invocation -> invocation.getArgument(0));

            List<ScoringService.EvaluationView> saved = dependencies.service().saveGroupDraft(
                    41L, 51L, new GroupScoreCommand(0L, snapshots.stream()
                            .map(snapshot -> new ScoreRowCommand(
                                    snapshot.getStudent().getId(),
                                    new BigDecimal("8.00"), "Nhận xét", null))
                            .toList()));

            assertEquals(size, saved.size());
            verify(dependencies.evaluations(), times(size)).save(any());
            verify(dependencies.evaluations()).flush();
        }
    }

    @Test
    void rejectsMissingExtraAndDuplicateRowsBeforeWriting() {
        Dependencies dependencies = dependencies();
        Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
        snapshots(dependencies, fixture, 2);

        assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        new GroupScoreCommand(0L, List.of(
                                row(101L, "8.00", null)))));
        assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        new GroupScoreCommand(0L, List.of(
                                row(101L, "8.00", null),
                                row(102L, "8.00", null),
                                row(999L, "8.00", null)))));
        assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        new GroupScoreCommand(0L, List.of(
                                row(101L, "8.00", null),
                                row(101L, "8.00", null)))));

        verify(dependencies.evaluations(), never()).save(any());
        verify(dependencies.evaluations(), never()).flush();
    }

    @Test
    void invalidScoreInOneRowRejectsWholeGroupBeforeWriting() {
        Dependencies dependencies = dependencies();
        Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
        snapshots(dependencies, fixture, 3);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        new GroupScoreCommand(0L, List.of(
                                row(101L, "8.00", null),
                                row(102L, "8.25", null),
                                row(103L, "8.10", null)))));

        assertEquals(ApplicationErrorCode.INVALID_SCORE, exception.code());
        verify(dependencies.evaluations(), never()).save(any());
        verify(dependencies.evaluations(), never()).flush();
    }

    @Test
    void staleRowRejectsWholeGroupBeforeAnyExistingEvaluationChanges() {
        Dependencies dependencies = dependencies();
        Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
        List<RegistrationMemberEntity> snapshots = snapshots(dependencies, fixture, 2);
        EvaluationEntity first = evaluation(
                dependencies, snapshots.get(0).getStudent(), 2L);
        EvaluationEntity second = evaluation(
                dependencies, snapshots.get(1).getStudent(), 5L);
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(first, second));

        assertThrows(OptimisticLockingFailureException.class,
                () -> dependencies.service().saveGroupDraft(41L, 51L,
                        new GroupScoreCommand(0L, List.of(
                                row(101L, "9.00", 2L),
                                row(102L, "9.00", 4L)))));

        verify(first, never()).saveDraft(any(), any(), any());
        verify(second, never()).saveDraft(any(), any(), any());
        verify(dependencies.evaluations(), never()).flush();
    }

    @Test
    void staleCouncilVersionRejectsGroupBeforeReadingRows() {
        Dependencies dependencies = dependencies();
        Fixture fixture = validFixture(dependencies, CouncilStatus.ACTIVE);
        when(fixture.council().getVersion()).thenReturn(4L);

        assertThrows(OptimisticLockingFailureException.class,
                () -> dependencies.service().saveGroupDraft(41L, 51L,
                        new GroupScoreCommand(3L, List.of(
                                row(101L, "8.00", null)))));

        verify(dependencies.registrationMembers(), never()).findAllByRegistrationId(51L);
        verify(dependencies.evaluations(), never()).save(any());
    }

    @Test
    void outsiderAndSupervisorCannotWriteAnEvaluation() {
        Dependencies outsiderDependencies = dependencies();
        validFixture(outsiderDependencies, CouncilStatus.ACTIVE);
        when(outsiderDependencies.authorization().requireRole(RoleCode.LECTURER)).thenReturn(
                new ActorIdentity(99L, Set.of(RoleCode.LECTURER), 2L));

        assertThrows(AccessDeniedException.class,
                () -> outsiderDependencies.service().submitGroup(
                        41L, 51L, command(101L, "8.00", null, null)));
        verify(outsiderDependencies.evaluations(), never()).save(any());

        Dependencies supervisorDependencies = dependencies();
        Fixture fixture = validFixture(supervisorDependencies, CouncilStatus.ACTIVE);
        TopicSupervisorEntity supervisor = mock(TopicSupervisorEntity.class);
        when(supervisor.getLecturer()).thenReturn(fixture.evaluator());
        when(fixture.topic().getSupervisors()).thenReturn(List.of(supervisor));

        assertThrows(AccessDeniedException.class,
                () -> supervisorDependencies.service().submitGroup(
                        41L, 51L, command(101L, "8.00", null, null)));
        verify(supervisorDependencies.evaluations(), never()).save(any());
    }

    @Test
    void completedCouncilRejectsChangesToSubmittedScore() {
        Dependencies dependencies = dependencies();
        validFixture(dependencies, CouncilStatus.COMPLETED);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().saveGroupDraft(
                        41L,
                        51L,
                        command(101L, "8.00", null, null)));

        assertEquals(ApplicationErrorCode.INVALID_COUNCIL_STATE, exception.code());
        verify(dependencies.evaluations(), never()).save(any());
    }

    @Test
    void activeAssignmentIsVisibleOnlyToCurrentLecturerInFaculty() {
        Dependencies dependencies = dependencies();
        Fixture fixture = readFixture(dependencies, CouncilStatus.ACTIVE);
        when(fixture.evaluator().getDepartment()).thenReturn(mock(DepartmentEntity.class));
        when(fixture.topic().getDepartment()).thenReturn(mock(DepartmentEntity.class));

        assertEquals(1, dependencies.service().listForCurrentLecturer().size());
        assertEquals(51L, dependencies.service().workspace(41L, 51L)
                .assignment().registrationId());
    }

    @Test
    void ownAssignmentCountsOnlySubmittedSnapshotStudents() {
        Dependencies dependencies = dependencies();
        readFixture(dependencies, CouncilStatus.ACTIVE);
        UserAccountEntity secondStudent = mock(UserAccountEntity.class);
        when(secondStudent.getId()).thenReturn(102L);
        UserAccountEntity thirdStudent = mock(UserAccountEntity.class);
        when(thirdStudent.getId()).thenReturn(103L);
        RegistrationMemberEntity secondSnapshot = mock(RegistrationMemberEntity.class);
        when(secondSnapshot.getStudent()).thenReturn(secondStudent);
        RegistrationMemberEntity thirdSnapshot = mock(RegistrationMemberEntity.class);
        when(thirdSnapshot.getStudent()).thenReturn(thirdStudent);
        var firstSnapshot = dependencies.registrationMembers()
                .findAllByRegistrationId(51L).getFirst();
        when(dependencies.registrationMembers().findAllByRegistrationId(51L))
                .thenReturn(List.of(firstSnapshot, secondSnapshot, thirdSnapshot));
        EvaluationEntity submitted = mock(EvaluationEntity.class);
        UserAccountEntity firstStudent = firstSnapshot.getStudent();
        when(submitted.getStudent()).thenReturn(firstStudent);
        when(submitted.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(submitted.getSubmittedAt()).thenReturn(NOW);
        EvaluationEntity draft = mock(EvaluationEntity.class);
        when(draft.getStudent()).thenReturn(secondStudent);
        when(draft.getStatus()).thenReturn(EvaluationStatus.DRAFT);
        EvaluationEntity thirdSubmitted = mock(EvaluationEntity.class);
        when(thirdSubmitted.getStudent()).thenReturn(thirdStudent);
        when(thirdSubmitted.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(thirdSubmitted.getSubmittedAt()).thenReturn(NOW);
        when(dependencies.evaluations()
                .findAllByCouncilIdAndRegistrationIdAndEvaluatorIdOrderByStudentId(
                        41L, 51L, 11L)).thenReturn(List.of(submitted, draft, thirdSubmitted));

        var partial = dependencies.service().listForCurrentLecturer().getFirst();
        assertEquals("DRAFT", partial.progressStatus());
        assertEquals(2, partial.submittedCount());
        assertEquals(3, partial.totalCount());
        assertEquals(null, partial.fullySubmittedAt());

        when(draft.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(draft.getSubmittedAt()).thenReturn(NOW.plusSeconds(60));
        var complete = dependencies.service().listForCurrentLecturer().getFirst();
        assertEquals("SUBMITTED", complete.progressStatus());
        assertEquals(NOW.plusSeconds(60), complete.fullySubmittedAt());
    }

    @Test
    void transferredLecturerCannotListOpenOrWriteFormerAssignment() {
        Dependencies dependencies = dependencies();
        readFixture(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.authorization().requireRole(RoleCode.LECTURER)).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 99L));

        assertEquals(0, dependencies.service().listForCurrentLecturer().size());
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().workspace(41L, 51L));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().saveGroupDraft(41L, 51L,
                        command(101L, "8.00", null, null)));
        verify(dependencies.evaluations(), never()).save(any());
    }

    @Test
    void missingMembershipOrAssignmentRevokesDirectUrlAndWriteAccess() {
        Dependencies dependencies = dependencies();
        readFixture(dependencies, CouncilStatus.ACTIVE);
        when(dependencies.councilTopics().findAllForLecturerByCouncilStatus(
                11L, CouncilStatus.ACTIVE)).thenReturn(List.of());
        when(dependencies.members().findByCouncilIdAndLecturerId(41L, 11L))
                .thenReturn(Optional.empty());
        when(dependencies.members().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of());

        assertEquals(0, dependencies.service().listForCurrentLecturer().size());
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().workspace(41L, 51L));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        command(101L, "8.00", null, null)));
        verify(dependencies.evaluations(), never()).save(any());

        when(dependencies.councilTopics().findByCouncilIdAndRegistrationId(41L, 51L))
                .thenReturn(Optional.empty());
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of());
        assertThrows(ApplicationException.class,
                () -> dependencies.service().workspace(41L, 51L));
        assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        command(101L, "8.00", null, null)));
        verify(dependencies.evaluations(), never()).save(any());
    }

    @Test
    void completedCouncilNoLongerExposesAssignmentOrAllowsWrite() {
        Dependencies dependencies = dependencies();
        readFixture(dependencies, CouncilStatus.COMPLETED);

        assertEquals(0, dependencies.service().listForCurrentLecturer().size());
        assertThrows(ApplicationException.class,
                () -> dependencies.service().workspace(41L, 51L));
        assertThrows(ApplicationException.class,
                () -> dependencies.service().submitGroup(41L, 51L,
                        command(101L, "8.00", null, null)));
        verify(dependencies.evaluations(), never()).save(any());
    }

    @Test
    void inconsistentTopicPeriodDoesNotGrantScoringAccess() {
        Dependencies dependencies = dependencies();
        Fixture fixture = readFixture(dependencies, CouncilStatus.ACTIVE);
        RegistrationPeriodEntity otherPeriod = mock(RegistrationPeriodEntity.class);
        when(otherPeriod.getId()).thenReturn(8L);
        when(fixture.topic().getPeriod()).thenReturn(otherPeriod);

        assertEquals(0, dependencies.service().listForCurrentLecturer().size());
        assertThrows(ApplicationException.class,
                () -> dependencies.service().workspace(41L, 51L));
        assertThrows(ApplicationException.class,
                () -> dependencies.service().saveGroupDraft(41L, 51L,
                        command(101L, "8.00", null, null)));
        verify(dependencies.evaluations(), never()).save(any());
    }

    private static Fixture readFixture(Dependencies dependencies, CouncilStatus status) {
        Fixture fixture = validFixture(dependencies, status);
        CouncilTopicEntity assignment = dependencies.councilTopics()
                .findAllByCouncilIdForUpdate(41L).getFirst();
        CouncilMemberEntity member = dependencies.members()
                .findAllByCouncilIdForUpdate(41L).getFirst();
        when(dependencies.councilTopics().findAllForLecturerByCouncilStatus(
                11L, CouncilStatus.ACTIVE)).thenReturn(List.of(assignment));
        when(dependencies.councilTopics().findByCouncilIdAndRegistrationId(41L, 51L))
                .thenReturn(Optional.of(assignment));
        when(dependencies.members().findByCouncilIdAndLecturerId(41L, 11L))
                .thenReturn(Optional.of(member));
        return fixture;
    }

    private static GroupScoreCommand command(
            long studentId, String score, String feedback, Long version) {
        return new GroupScoreCommand(0L, List.of(
                new ScoreRowCommand(studentId, new BigDecimal(score), feedback, version)));
    }

    private static ScoreRowCommand row(long studentId, String score, Long version) {
        return new ScoreRowCommand(studentId, new BigDecimal(score), null, version);
    }

    private static List<RegistrationMemberEntity> snapshots(
            Dependencies dependencies, Fixture fixture, int size) {
        List<RegistrationMemberEntity> rows = new java.util.ArrayList<>(
                dependencies.registrationMembers().findAllByRegistrationId(51L));
        for (int index = 2; index <= size; index++) {
            UserAccountEntity student = mock(UserAccountEntity.class);
            when(student.getId()).thenReturn(100L + index);
            when(student.getFullName()).thenReturn("Sinh viên " + index);
            RegistrationMemberEntity snapshot = mock(RegistrationMemberEntity.class);
            when(snapshot.getRegistration()).thenReturn(fixture.registration());
            when(snapshot.getStudent()).thenReturn(student);
            when(snapshot.getSlot()).thenReturn(index);
            rows.add(snapshot);
        }
        when(dependencies.registrationMembers().findAllByRegistrationId(51L))
                .thenReturn(rows);
        return rows;
    }

    private static EvaluationEntity evaluation(
            Dependencies dependencies, UserAccountEntity student, long version) {
        EvaluationEntity evaluation = mock(EvaluationEntity.class);
        RegistrationEntity registration = registration(dependencies);
        UserAccountEntity evaluator = evaluator(dependencies);
        ScoreComponentEntity component = component(dependencies);
        when(evaluation.getRegistration()).thenReturn(registration);
        when(evaluation.getEvaluator()).thenReturn(evaluator);
        when(evaluation.getStudent()).thenReturn(student);
        when(evaluation.getComponent()).thenReturn(component);
        when(evaluation.getVersion()).thenReturn(version);
        return evaluation;
    }

    private static Fixture validFixture(Dependencies dependencies, CouncilStatus status) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(2L);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(7L);
        when(period.getFaculty()).thenReturn(faculty);

        CouncilEntity council = mock(CouncilEntity.class);
        when(council.getId()).thenReturn(41L);
        when(council.getPeriod()).thenReturn(period);
        when(council.getName()).thenReturn("Hội đồng KLTN");
        when(council.getStatus()).thenReturn(status);

        UserAccountEntity evaluator = mock(UserAccountEntity.class);
        when(evaluator.getId()).thenReturn(11L);
        when(evaluator.getFullName()).thenReturn("Giảng viên A");
        when(council.getChair()).thenReturn(evaluator);
        CouncilMemberEntity member = mock(CouncilMemberEntity.class);
        when(member.getLecturer()).thenReturn(evaluator);

        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getTitle()).thenReturn("Đề tài thử nghiệm");
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getSupervisors()).thenReturn(List.of());
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        when(group.getId()).thenReturn(21L);
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getGroup()).thenReturn(group);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);
        CouncilTopicEntity assignment = mock(CouncilTopicEntity.class);
        when(assignment.getCouncil()).thenReturn(council);
        when(assignment.getRegistration()).thenReturn(registration);

        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getId()).thenReturn(101L);
        when(student.getFullName()).thenReturn("Sinh viên A");
        RegistrationMemberEntity snapshotStudent = mock(RegistrationMemberEntity.class);
        when(snapshotStudent.getRegistration()).thenReturn(registration);
        when(snapshotStudent.getStudent()).thenReturn(student);
        when(snapshotStudent.getSlot()).thenReturn(1);
        when(snapshotStudent.isLeaderAtSubmission()).thenReturn(true);

        ScoreComponentEntity component = mock(ScoreComponentEntity.class);
        when(component.getId()).thenReturn(61L);
        when(component.getPeriod()).thenReturn(period);

        when(dependencies.councils().findPeriodIdByCouncilId(41L))
                .thenReturn(Optional.of(7L));
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.authorization().requireRole(RoleCode.LECTURER)).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L));
        when(dependencies.councils().findByIdForUpdate(41L)).thenReturn(Optional.of(council));
        when(dependencies.councilTopics().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(assignment));
        when(dependencies.members().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(member));
        when(dependencies.registrationMembers().findAllByRegistrationId(51L))
                .thenReturn(List.of(snapshotStudent));
        when(dependencies.components().findByPeriodIdAndName(
                7L, ScoreComponentEntity.COUNCIL_SCORE_NAME))
                .thenReturn(Optional.of(component));
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of());

        return new Fixture(period, council, evaluator, topic, registration, component);
    }

    private static RegistrationEntity registration(Dependencies dependencies) {
        return dependencies.councilTopics().findAllByCouncilIdForUpdate(41L)
                .getFirst().getRegistration();
    }

    private static UserAccountEntity evaluator(Dependencies dependencies) {
        return dependencies.members().findAllByCouncilIdForUpdate(41L)
                .getFirst().getLecturer();
    }

    private static UserAccountEntity student(Dependencies dependencies) {
        return dependencies.registrationMembers().findAllByRegistrationId(51L)
                .getFirst().getStudent();
    }

    private static ScoreComponentEntity component(Dependencies dependencies) {
        return dependencies.components().findByPeriodIdAndName(
                7L, ScoreComponentEntity.COUNCIL_SCORE_NAME).orElseThrow();
    }

    private static Dependencies dependencies() {
        return new Dependencies(
                mock(RegistrationPeriodRepository.class),
                mock(CouncilRepository.class),
                mock(CouncilTopicRepository.class),
                mock(CouncilMemberRepository.class),
                mock(RegistrationMemberRepository.class),
                mock(ScoreComponentRepository.class),
                mock(EvaluationRepository.class),
                mock(ReviewerAssignmentRepository.class),
                mock(AuthorizationPolicy.class));
    }

    private record Dependencies(
            RegistrationPeriodRepository periods,
            CouncilRepository councils,
            CouncilTopicRepository councilTopics,
            CouncilMemberRepository members,
            RegistrationMemberRepository registrationMembers,
            ScoreComponentRepository components,
            EvaluationRepository evaluations,
            ReviewerAssignmentRepository reviewerAssignments,
            AuthorizationPolicy authorization) {
        ScoringService service() {
            return new ScoringService(
                    periods,
                    councils,
                    councilTopics,
                    members,
                    registrationMembers,
                    components,
                    evaluations,
                    reviewerAssignments,
                    mock(DirectGradingService.class),
                    authorization,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    private record Fixture(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            UserAccountEntity evaluator,
            TopicEntity topic,
            RegistrationEntity registration,
            ScoreComponentEntity component) {
    }
}
