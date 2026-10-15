package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import vn.edu.topicregistration.entity.EvaluationEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.ReviewerEvaluationEntity;
import vn.edu.topicregistration.entity.ReviewerReportEntity;
import java.time.LocalDate;
import vn.edu.topicregistration.entity.ScoreComponentEntity;
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
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.util.ProjectTime;

class ResultServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T02:00:00Z");

    @Test
    void deanCompletesWholeCouncilWithPeriodFirstLocksAndCalculatedMean() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.ACTIVE, 4L);
        when(dependencies.results().saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResultService.BoardResultView completed = dependencies.service().complete(41L, 4L);

        InOrder order = inOrder(
                dependencies.periods(),
                dependencies.authorization(),
                dependencies.councils(),
                dependencies.assignments(),
                dependencies.members(),
                dependencies.evaluations(),
                dependencies.results());
        order.verify(dependencies.periods()).findByIdForUpdate(7L);
        order.verify(dependencies.councils()).findByIdForUpdate(41L);
        order.verify(dependencies.authorization()).requireDeanOfFaculty(2L);
        order.verify(dependencies.assignments()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.members()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.evaluations()).findAllByCouncilIdForUpdate(41L);
        order.verify(dependencies.results()).findAllByCouncilIdForUpdate(41L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FinalResultEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.results()).saveAll(saved.capture());
        List<FinalResultEntity> created = new ArrayList<>();
        saved.getValue().forEach(created::add);
        assertEquals(1, created.size());
        assertEquals(new BigDecimal("8.50"), created.getFirst().getFinalScore());
        assertEquals(31L, created.getFirst().getConsolidatedBy().getId());
        assertEquals(FinalResultStatus.READY, created.getFirst().getStatus());
        verify(fixture.council()).transitionTo(CouncilStatus.COMPLETED);
        assertEquals(CouncilStatus.COMPLETED, completed.status());
        assertEquals(1, completed.resultCount());
    }

    @Test
    void deanCompletesDirectAssignmentFromOneSubmittedScore() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.ACTIVE, 4L);
        when(fixture.council().getMode()).thenReturn(CouncilMode.DIRECT);
        when(fixture.period().getTopicType()).thenReturn("COURSE");
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        UserAccountEntity dean = account(31L, "Trưởng khoa");
        when(dependencies.accounts().findDetailedById(31L))
                .thenReturn(Optional.of(dean));
        CouncilMemberEntity onlyMember = mock(CouncilMemberEntity.class);
        when(onlyMember.getLecturer()).thenReturn(fixture.chair());
        when(dependencies.members().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(onlyMember));
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(fixture.evaluations().getFirst()));
        when(dependencies.results().saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResultService.BoardResultView completed = dependencies.service().complete(41L, 4L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FinalResultEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.results()).saveAll(saved.capture());
        List<FinalResultEntity> created = new ArrayList<>();
        saved.getValue().forEach(created::add);
        assertEquals(1, created.size());
        assertEquals(new BigDecimal("8.50"), created.getFirst().getFinalScore());
        assertEquals(CouncilStatus.COMPLETED, completed.status());
    }

    @Test
    void tlcnDeanConsolidatesExactlyReviewerAndSecondGraderScore() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.ACTIVE, 4L);
        setupTlcnDirect(dependencies, fixture);
        completeReviewer(dependencies, fixture);
        when(dependencies.results().saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        dependencies.service().complete(41L, 4L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FinalResultEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.results()).saveAll(saved.capture());
        List<FinalResultEntity> created = new ArrayList<>();
        saved.getValue().forEach(created::add);
        assertEquals(new BigDecimal("8.38"), created.getFirst().getFinalScore());
        assertEquals("2 điểm đã nộp; trung bình 8.38",
                created.getFirst().getEvaluationSummary());
    }

    @Test
    void tlcnCannotConsolidateWithoutReviewerSource() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.ACTIVE, 4L);
        setupTlcnDirect(dependencies, fixture);
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().complete(41L, 4L));

        assertEquals(ApplicationErrorCode.EVALUATIONS_INCOMPLETE, exception.code());
        verify(dependencies.results(), never()).saveAll(any());
    }

    private static void setupTlcnDirect(Dependencies dependencies, Fixture fixture) {
        when(fixture.period().getTopicType()).thenReturn("TLCN");
        when(fixture.council().getMode()).thenReturn(CouncilMode.DIRECT);
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        UserAccountEntity dean = account(31L, "Trưởng khoa");
        when(dependencies.accounts().findDetailedById(31L)).thenReturn(Optional.of(dean));
        CouncilMemberEntity onlyMember = mock(CouncilMemberEntity.class);
        when(onlyMember.getLecturer()).thenReturn(fixture.chair());
        when(dependencies.members().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(onlyMember));
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(fixture.evaluations().getFirst()));
    }

    private static void completeReviewer(Dependencies dependencies, Fixture fixture) {
        ReviewerAssignmentEntity assignment = mock(ReviewerAssignmentEntity.class);
        when(assignment.getId()).thenReturn(71L);
        when(assignment.getStatus()).thenReturn(ReviewerAssignmentEntity.Status.SUBMITTED);
        when(assignment.getDeadlineDate()).thenReturn(LocalDate.of(2026, 9, 21));
        when(assignment.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.of(assignment));
        ReviewerReportEntity report = mock(ReviewerReportEntity.class);
        when(report.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(dependencies.reviewerReports().findByAssignmentId(71L))
                .thenReturn(Optional.of(report));
        ReviewerEvaluationEntity score = mock(ReviewerEvaluationEntity.class);
        when(score.getRegistration()).thenReturn(fixture.registration());
        when(score.getStudent()).thenReturn(fixture.student());
        when(score.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(score.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(score.getScore()).thenReturn(new BigDecimal("8.25"));
        when(dependencies.reviewerEvaluations().findAllByAssignmentIdForUpdate(71L))
                .thenReturn(List.of(score));
    }

    @Test
    void nonDeanCannotCompleteAndDoesNotCreateResults() {
        Dependencies dependencies = dependencies();
        fixture(dependencies, CouncilStatus.ACTIVE, 4L);
        when(dependencies.authorization().requireDeanOfFaculty(2L))
                .thenThrow(new AccessDeniedException("not dean"));

        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().complete(41L, 4L));

        verify(dependencies.results(), never()).saveAll(any());
    }

    @Test
    void missingOrDraftEvaluationRejectsCompleteWithoutPartialResults() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.ACTIVE, 4L);
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(fixture.evaluations().subList(0, 2));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().complete(41L, 4L));

        assertEquals(ApplicationErrorCode.EVALUATIONS_INCOMPLETE, exception.code());
        verify(dependencies.results(), never()).saveAll(any());
        verify(fixture.council(), never()).transitionTo(any());

        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(fixture.evaluations());
        when(fixture.evaluations().getFirst().getStatus()).thenReturn(EvaluationStatus.DRAFT);
        assertEquals(
                ApplicationErrorCode.EVALUATIONS_INCOMPLETE,
                assertThrows(ApplicationException.class,
                        () -> dependencies.service().complete(41L, 4L)).code());
    }

    @Test
    void staleCouncilVersionCannotComplete() {
        Dependencies dependencies = dependencies();
        fixture(dependencies, CouncilStatus.ACTIVE, 5L);

        assertThrows(OptimisticLockingFailureException.class,
                () -> dependencies.service().complete(41L, 4L));

        verify(dependencies.assignments(), never()).findAllByCouncilIdForUpdate(41L);
        verify(dependencies.results(), never()).saveAll(any());
    }

    @Test
    void scopedDeanPublishesEveryReadyResultAndCouncilTogether() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        FinalResultEntity result = mock(FinalResultEntity.class);
        when(result.getRegistration()).thenReturn(fixture.registration());
        when(result.getStudent()).thenReturn(fixture.student());
        when(result.getCouncil()).thenReturn(fixture.council());
        when(result.getStatus()).thenReturn(FinalResultStatus.READY);
        when(result.getRegistrationId()).thenReturn(51L);
        when(result.getStudentId()).thenReturn(101L);
        when(result.getFinalScore()).thenReturn(new BigDecimal("8.50"));
        when(dependencies.results().findAllByCouncilIdForUpdate(41L)).thenReturn(List.of(result));
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        UserAccountEntity dean = mock(UserAccountEntity.class);
        when(dependencies.accounts().findDetailedById(31L)).thenReturn(Optional.of(dean));

        ResultService.BoardResultView published = dependencies.service().publish(41L, 6L);

        verify(result).publish(dean, NOW);
        verify(fixture.council()).transitionTo(CouncilStatus.PUBLISHED);
        verify(dependencies.results()).flush();
        verify(dependencies.councils()).flush();
        assertEquals(CouncilStatus.PUBLISHED, published.status());
        assertEquals(1, published.resultCount());
    }

    @Test
    void coursePublicationRejectsMissingSubmittedSource() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        when(fixture.council().getMode()).thenReturn(CouncilMode.DIRECT);
        when(fixture.period().getTopicType()).thenReturn("COURSE");
        CouncilMemberEntity onlyMember = mock(CouncilMemberEntity.class);
        when(onlyMember.getLecturer()).thenReturn(fixture.chair());
        when(dependencies.members().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(onlyMember));
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of());
        FinalResultEntity ready = result(fixture, FinalResultStatus.READY);
        when(dependencies.results().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(ready));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().publish(41L, 6L));

        assertEquals(ApplicationErrorCode.RESULT_NOT_READY, rejected.code());
        verify(ready, never()).publish(any(), any());
    }

    @Test
    void kltnPublicationRejectsStoredMeanDifferentFromSources() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        completeReviewer(dependencies, fixture);
        FinalResultEntity ready = result(fixture, FinalResultStatus.READY);
        when(ready.getFinalScore()).thenReturn(new BigDecimal("9.00"));
        when(dependencies.results().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(ready));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().publish(41L, 6L));

        assertEquals(ApplicationErrorCode.RESULT_NOT_READY, rejected.code());
        verify(ready, never()).publish(any(), any());
    }

    @Test
    void tlcnPublishesWhenPersistedDecimalScaleDiffersFromCalculatedScore() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        setupTlcnDirect(dependencies, fixture);
        completeReviewer(dependencies, fixture);
        FinalResultEntity result = mock(FinalResultEntity.class);
        when(result.getRegistration()).thenReturn(fixture.registration());
        when(result.getStudent()).thenReturn(fixture.student());
        when(result.getCouncil()).thenReturn(fixture.council());
        when(result.getStatus()).thenReturn(FinalResultStatus.READY);
        when(result.getRegistrationId()).thenReturn(51L);
        when(result.getStudentId()).thenReturn(101L);
        when(result.getFinalScore()).thenReturn(new BigDecimal("8.3800"));
        when(dependencies.results().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(result));
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(fixture.evaluations().getFirst()));
        ResultService.BoardResultView published = dependencies.service().publish(41L, 6L);

        assertEquals(CouncilStatus.PUBLISHED, published.status());
        verify(result).publish(any(), any());
    }

    @Test
    void deanOutsideFacultyCannotPublish() {
        Dependencies dependencies = dependencies();
        fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        when(dependencies.authorization().requireDeanOfFaculty(2L))
                .thenThrow(new AccessDeniedException("outside faculty"));

        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().publish(41L, 6L));

        verify(dependencies.councils()).findByIdForUpdate(41L);
        verify(dependencies.results(), never()).flush();
    }

    @Test
    void publishRejectsMissingOrAlreadyPublishedResult() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        when(dependencies.results().findAllByCouncilIdForUpdate(41L)).thenReturn(List.of());

        ApplicationException missing = assertThrows(ApplicationException.class,
                () -> dependencies.service().publish(41L, 6L));
        assertEquals(ApplicationErrorCode.RESULT_NOT_READY, missing.code());

        FinalResultEntity published = mock(FinalResultEntity.class);
        when(published.getRegistration()).thenReturn(fixture.registration());
        when(published.getStudent()).thenReturn(fixture.student());
        when(published.getCouncil()).thenReturn(fixture.council());
        when(published.getStatus()).thenReturn(FinalResultStatus.PUBLISHED);
        when(dependencies.results().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(published));
        ApplicationException repeated = assertThrows(ApplicationException.class,
                () -> dependencies.service().publish(41L, 6L));
        assertEquals(ApplicationErrorCode.RESULT_NOT_READY, repeated.code());
        verify(published, never()).publish(any(), any());
    }

    @Test
    void facultyListAndDetailAreAuthorizedFromPersistedFacultyScope() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        when(dependencies.authorization().requireRole(RoleCode.DEAN)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        FinalResultEntity result = result(fixture, FinalResultStatus.READY);
        when(dependencies.results()
                .findAllByCouncilPeriodFacultyIdOrderByConsolidatedAtDescIdRegistrationIdDesc(2L))
                .thenReturn(List.of(result));
        when(dependencies.results().findDetailedByRegistrationIdAndStudentId(51L, 101L))
                .thenReturn(Optional.of(result));
        when(dependencies.evaluations()
                .findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
                        41L, 51L, 101L))
                .thenReturn(fixture.evaluations());

        assertEquals(1, dependencies.service().listForCurrentDean().size());
        ResultService.FacultyResultDetailView detail =
                dependencies.service().detailForCurrentDean(51L, 101L);

        assertEquals(51L, detail.result().registrationId());
        assertEquals(7L, detail.result().periodId());
        assertEquals("Đợt KLTN 2026", detail.result().periodName());
        assertEquals(3, detail.result().lecturers().size());
        assertEquals(3, detail.evaluations().size());
        verify(dependencies.authorization()).requireDeanOfFaculty(2L);
    }

    @Test
    void facultyEvidenceIncludesKltnReviewerButMasksDraftMemberScore() {
        Dependencies dependencies = dependencies();
        Fixture fixture = fixture(dependencies, CouncilStatus.COMPLETED, 6L);
        FinalResultEntity ready = result(fixture, FinalResultStatus.READY);
        when(dependencies.results().findDetailedByRegistrationIdAndStudentId(51L, 101L))
                .thenReturn(Optional.of(ready));
        when(dependencies.evaluations()
                .findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
                        41L, 51L, 101L)).thenReturn(fixture.evaluations());
        when(fixture.evaluations().getFirst().getStatus()).thenReturn(EvaluationStatus.DRAFT);
        ReviewerAssignmentEntity reviewer = mock(ReviewerAssignmentEntity.class);
        when(reviewer.getId()).thenReturn(71L);
        when(reviewer.getStatus()).thenReturn(ReviewerAssignmentEntity.Status.SUBMITTED);
        UserAccountEntity reviewerAccount = account(14L, "Phản biện");
        when(reviewer.getReviewer()).thenReturn(reviewerAccount);
        when(dependencies.reviewerAssignments().findActiveByRegistrationId(
                51L, List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.of(reviewer));
        when(dependencies.reviewerReports().existsByAssignmentId(71L)).thenReturn(true);
        ReviewerEvaluationEntity reviewerScore = mock(ReviewerEvaluationEntity.class);
        when(reviewerScore.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(reviewerScore.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(reviewerScore.getScore()).thenReturn(new BigDecimal("8.50"));
        when(dependencies.reviewerEvaluations().findByAssignmentIdAndStudentId(71L, 101L))
                .thenReturn(Optional.of(reviewerScore));

        var detail = dependencies.service().detailForCurrentDean(51L, 101L);

        assertEquals("GVPB", detail.evidence().sources().getFirst().role());
        assertEquals(new BigDecimal("8.50"), detail.evidence().sources().getFirst().score());
        assertEquals("DRAFT", detail.evidence().sources().get(1).status());
        assertEquals(null, detail.evidence().sources().get(1).score());
        assertEquals(null, detail.evaluations().getFirst().score());
        assertEquals(3, detail.evidence().submittedSourceCount());
    }

    private static FinalResultEntity result(Fixture fixture, FinalResultStatus status) {
        FinalResultEntity result = mock(FinalResultEntity.class);
        when(result.getRegistrationId()).thenReturn(51L);
        when(result.getStudentId()).thenReturn(101L);
        when(result.getRegistration()).thenReturn(fixture.registration());
        when(result.getStudent()).thenReturn(fixture.student());
        when(result.getCouncil()).thenReturn(fixture.council());
        when(result.getStatus()).thenReturn(status);
        when(result.getFinalScore()).thenReturn(new BigDecimal("8.50"));
        when(result.getConsolidatedBy()).thenReturn(fixture.chair());
        when(result.getConsolidatedAt()).thenReturn(NOW);
        when(result.getEvaluationSummary()).thenReturn("3 điểm đã nộp; trung bình 8.50");
        when(result.getVersion()).thenReturn(0L);
        return result;
    }

    private static Fixture fixture(
            Dependencies dependencies,
            CouncilStatus status,
            long version) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(2L);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(7L);
        when(period.getName()).thenReturn("Đợt KLTN 2026");
        when(period.getTopicType()).thenReturn("KLTN");
        when(period.getFaculty()).thenReturn(faculty);

        UserAccountEntity chair = account(11L, "Chủ tịch");
        CouncilEntity council = mock(CouncilEntity.class);
        when(council.getId()).thenReturn(41L);
        when(council.getName()).thenReturn("Hội đồng KLTN");
        when(council.getPeriod()).thenReturn(period);
        when(council.getMode()).thenReturn(CouncilMode.COUNCIL);
        when(council.getChair()).thenReturn(chair);
        when(council.getStatus()).thenReturn(status);
        when(council.getVersion()).thenReturn(version);

        StudentGroupEntity group = mock(StudentGroupEntity.class);
        when(group.getId()).thenReturn(21L);
        TopicEntity topic = mock(TopicEntity.class);
        when(topic.getTitle()).thenReturn("Đề tài thử nghiệm");
        when(topic.getSupervisors()).thenReturn(List.of());
        RegistrationEntity registration = mock(RegistrationEntity.class);
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getGroup()).thenReturn(group);
        when(registration.getTopic()).thenReturn(topic);
        CouncilTopicEntity assignment = mock(CouncilTopicEntity.class);
        when(assignment.getCouncil()).thenReturn(council);
        when(assignment.getRegistration()).thenReturn(registration);

        UserAccountEntity student = account(101L, "Sinh viên A");
        RegistrationMemberEntity snapshotStudent = mock(RegistrationMemberEntity.class);
        when(snapshotStudent.getRegistration()).thenReturn(registration);
        when(snapshotStudent.getStudent()).thenReturn(student);

        List<UserAccountEntity> lecturers = List.of(
                chair,
                account(12L, "Thư ký"),
                account(13L, "Thành viên"));
        when(council.getSecretary()).thenReturn(lecturers.get(1));
        for (UserAccountEntity lecturer : lecturers) {
            when(lecturer.isEnabled()).thenReturn(true);
            when(lecturer.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
            when(lecturer.getFaculty()).thenReturn(faculty);
        }
        List<CouncilMemberEntity> members = lecturers.stream().map(lecturer -> {
            CouncilMemberEntity member = mock(CouncilMemberEntity.class);
            when(member.getLecturer()).thenReturn(lecturer);
            return member;
        }).toList();
        ScoreComponentEntity component = mock(ScoreComponentEntity.class);
        when(component.getName()).thenReturn(ScoreComponentEntity.COUNCIL_SCORE_NAME);
        when(component.getPeriod()).thenReturn(period);
        List<BigDecimal> scores = List.of(
                new BigDecimal("8.50"),
                new BigDecimal("9.00"),
                new BigDecimal("8.00"));
        List<EvaluationEntity> evaluations = new ArrayList<>();
        for (int index = 0; index < lecturers.size(); index++) {
            EvaluationEntity evaluation = mock(EvaluationEntity.class);
            when(evaluation.getRegistration()).thenReturn(registration);
            when(evaluation.getStudent()).thenReturn(student);
            when(evaluation.getEvaluator()).thenReturn(lecturers.get(index));
            when(evaluation.getComponent()).thenReturn(component);
            when(evaluation.getCouncil()).thenReturn(council);
            when(evaluation.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
            when(evaluation.getScore()).thenReturn(scores.get(index));
            when(evaluation.getSubmittedAt()).thenReturn(NOW.minusSeconds(index));
            when(evaluation.getFeedback()).thenReturn("Nhận xét " + index);
            evaluations.add(evaluation);
        }

        when(dependencies.councils().findPeriodIdByCouncilId(41L))
                .thenReturn(Optional.of(7L));
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.authorization().requireRole(RoleCode.LECTURER)).thenReturn(
                new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L));
        when(dependencies.authorization().requireDeanOfFaculty(2L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.DEAN), 2L));
        UserAccountEntity dean = account(31L, "Trưởng khoa");
        when(dependencies.accounts().findDetailedById(31L))
                .thenReturn(Optional.of(dean));
        when(dependencies.councils().findByIdForUpdate(41L)).thenReturn(Optional.of(council));
        when(dependencies.assignments().findAllByCouncilIdForUpdate(41L))
                .thenReturn(List.of(assignment));
        when(dependencies.members().findAllByCouncilIdForUpdate(41L)).thenReturn(members);
        when(dependencies.members().findAllByCouncilIdOrderBySlot(41L)).thenReturn(members);
        when(dependencies.registrationMembers().findAllByRegistrationId(51L))
                .thenReturn(List.of(snapshotStudent));
        when(dependencies.evaluations().findAllByCouncilIdForUpdate(41L))
                .thenReturn(evaluations);
        when(dependencies.results().findAllByCouncilIdForUpdate(41L)).thenReturn(List.of());
        ReviewerAssignmentEntity reviewerAssignment = mock(ReviewerAssignmentEntity.class);
        when(reviewerAssignment.getId()).thenReturn(71L);
        when(reviewerAssignment.getDeadlineDate()).thenReturn(LocalDate.of(2026, 9, 21));
        when(reviewerAssignment.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        UserAccountEntity reviewerAccount = account(14L, "Phản biện");
        when(reviewerAccount.isEnabled()).thenReturn(true);
        when(reviewerAccount.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(reviewerAccount.getFaculty()).thenReturn(faculty);
        when(reviewerAssignment.getReviewer()).thenReturn(reviewerAccount);
        when(dependencies.reviewerAssignments().findActiveByRegistrationIdForUpdate(
                51L, List.of(ReviewerAssignmentEntity.Status.SUBMITTED)))
                .thenReturn(Optional.of(reviewerAssignment));
        ReviewerReportEntity reviewerReport = mock(ReviewerReportEntity.class);
        when(reviewerReport.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(dependencies.reviewerReports().findByAssignmentId(71L))
                .thenReturn(Optional.of(reviewerReport));
        ReviewerEvaluationEntity reviewerScore = mock(ReviewerEvaluationEntity.class);
        when(reviewerScore.getRegistration()).thenReturn(registration);
        when(reviewerScore.getStudent()).thenReturn(student);
        when(reviewerScore.getStatus()).thenReturn(EvaluationStatus.SUBMITTED);
        when(reviewerScore.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(reviewerScore.getScore()).thenReturn(new BigDecimal("8.50"));
        when(dependencies.reviewerEvaluations().findAllByAssignmentIdForUpdate(71L))
                .thenReturn(List.of(reviewerScore));
        return new Fixture(period, council, chair, student, registration, evaluations);
    }

    private static UserAccountEntity account(long id, String name) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        when(account.getFullName()).thenReturn(name);
        when(account.getUsername()).thenReturn("u" + id);
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
                mock(AuthorizationPolicy.class),
                mock(ReviewerAssignmentRepository.class),
                mock(ReviewerEvaluationRepository.class),
                mock(ReviewerReportRepository.class));
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
            AuthorizationPolicy authorization,
            ReviewerAssignmentRepository reviewerAssignments,
            ReviewerEvaluationRepository reviewerEvaluations,
            ReviewerReportRepository reviewerReports) {
        ResultService service() {
            return new ResultService(
                    periods,
                    councils,
                    assignments,
                    members,
                    registrationMembers,
                    evaluations,
                    results,
                    accounts,
                    reviewerAssignments,
                    reviewerEvaluations,
                    reviewerReports,
                    authorization,
                    mock(NotificationService.class),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        }
    }

    private record Fixture(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            UserAccountEntity chair,
            UserAccountEntity student,
            RegistrationEntity registration,
            List<EvaluationEntity> evaluations) {
    }
}
