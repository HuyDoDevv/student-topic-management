package vn.edu.topicregistration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.repository.GroupInvitationRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.StudentGroupRepository;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.RegistrationService.ProgressState;
import vn.edu.topicregistration.service.RegistrationService.RegistrationDetailView;

class RegistrationProgressTest {
    @Test
    void nineLabelsAndActionsFollowRealMilestones() {
        assertStage(detail(RegistrationStatus.PENDING, "TLCN", false), false,
                null, 1);
        assertStage(detail(RegistrationStatus.ACCEPTED, "TLCN", false), false,
                progress(false, false, false, false, false), 3);
        assertStage(detail(RegistrationStatus.ACCEPTED, "TLCN", false), true,
                progress(false, false, false, false, false), 5);
        assertStage(detail(RegistrationStatus.ACCEPTED, "TLCN", false), true,
                progress(true, false, false, false, false), 6);
        assertStage(detail(RegistrationStatus.ACCEPTED, "TLCN", false), true,
                progress(true, true, false, false, false), 7);
        assertStage(detail(RegistrationStatus.ACCEPTED, "TLCN", false), true,
                progress(true, true, true, true, false), 8);
        assertStage(detail(RegistrationStatus.ACCEPTED, "TLCN", false), true,
                progress(true, true, true, false, true), 9);

        var waitingReport = RegistrationService.projectProgress(
                detail(RegistrationStatus.ACCEPTED, "TLCN", false), false,
                progress(false, false, false, false, false));
        assertEquals("/reports/51", waitingReport.steps().get(2).actionHref());

        var published = RegistrationService.projectProgress(
                detail(RegistrationStatus.ACCEPTED, "TLCN", false), false,
                progress(false, false, false, false, true));
        assertTrue(published.terminal());
        assertEquals("/student/results/51", published.steps().get(8).actionHref());
        assertEquals(ProgressState.COMPLETED, published.steps().get(3).state());
        assertEquals(List.of("Chờ xét đăng ký", "Đã chấp nhận", "Chờ nộp báo cáo",
                "Đã nộp báo cáo", "Chờ phân công phản biện/chấm",
                "Phản biện đang thực hiện", "Đang chấm điểm",
                "Đã đủ điểm, chờ công bố", "Đã công bố kết quả"),
                published.steps().stream().map(RegistrationService.ProgressStep::label).toList());
    }

    @Test
    void rejectedAndWithdrawnStopWithoutSuggestingFurtherWork() {
        for (RegistrationStatus status : List.of(
                RegistrationStatus.REJECTED, RegistrationStatus.WITHDRAWN)) {
            var projection = RegistrationService.projectProgress(
                    detail(status, "KLTN", false), false, null);
            assertTrue(projection.terminal());
            assertEquals(ProgressState.CURRENT, projection.steps().get(1).state());
            assertTrue(projection.steps().subList(2, 9).stream()
                    .allMatch(step -> step.state() == ProgressState.NOT_APPLICABLE));
        }
    }

    @Test
    void directGradingSkipsReviewerAndDeanGetsNoStudentResultAction() {
        var direct = RegistrationService.projectProgress(
                detail(RegistrationStatus.ACCEPTED, "COURSE", false), true,
                progress(false, false, true, false, false));
        assertEquals(ProgressState.NOT_APPLICABLE, direct.steps().get(5).state());
        assertEquals(ProgressState.CURRENT, direct.steps().get(6).state());

        var dean = RegistrationService.projectProgress(
                detail(RegistrationStatus.ACCEPTED, "COURSE", true), true,
                progress(false, false, true, false, true));
        assertNull(dean.steps().get(8).actionHref());
        assertFalse(dean.steps().isEmpty());
    }

    @Test
    void progressRejectsOtherFacultyBeforeReadingReportOrResult() {
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationMemberRepository members = mock(RegistrationMemberRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        ReportService reports = mock(ReportService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ResultService> results = mock(ObjectProvider.class);
        RegistrationService service = new RegistrationService(
                mock(RegistrationPeriodRepository.class), mock(StudentGroupRepository.class),
                mock(GroupInvitationRepository.class), mock(TopicRepository.class),
                registrations, members, authorization, Clock.systemUTC(), reports, results);
        RegistrationEntity registration = mock(RegistrationEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(registration.getPeriod()).thenReturn(period);
        when(period.getFaculty()).thenReturn(faculty);
        when(faculty.getId()).thenReturn(7L);
        when(registrations.findDetailedById(51L)).thenReturn(Optional.of(registration));
        when(authorization.currentActor()).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.STUDENT), 8L));

        assertThrows(AccessDeniedException.class, () -> service.progressForVisible(51L));
        verifyNoInteractions(reports, results);
    }

    private static void assertStage(RegistrationDetailView detail, boolean report,
            ResultService.RegistrationProgress result, int expected) {
        var projection = RegistrationService.projectProgress(detail, report, result);
        assertEquals(9, projection.steps().size());
        assertEquals(expected, projection.steps().stream()
                .filter(step -> step.state() == ProgressState.CURRENT)
                .findFirst().orElseThrow().number());
    }

    private static ResultService.RegistrationProgress progress(
            boolean reviewerAssigned, boolean reviewerSubmitted,
            boolean gradingStarted, boolean ready, boolean published) {
        return new ResultService.RegistrationProgress(
                reviewerAssigned, reviewerSubmitted, gradingStarted, ready, published);
    }

    private static RegistrationDetailView detail(
            RegistrationStatus status, String type, boolean dean) {
        return new RegistrationDetailView(
                51L, 1L, "Đợt", 21L, 31L, "Đề tài", type,
                "Tác giả", "Khoa", List.of(), List.of(), status,
                null, null, Instant.parse("2026-09-01T00:00:00Z"), null, null,
                0L, false, false, dean);
    }
}
