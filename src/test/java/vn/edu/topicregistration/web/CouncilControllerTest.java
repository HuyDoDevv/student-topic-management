package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.CouncilController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.service.CouncilService;
import vn.edu.topicregistration.service.CouncilService.CouncilCreationOptions;
import vn.edu.topicregistration.service.CouncilService.CouncilDetailView;
import vn.edu.topicregistration.service.CouncilService.CouncilPeriodOption;
import vn.edu.topicregistration.service.CouncilService.CouncilSummaryView;
import vn.edu.topicregistration.service.CouncilService.CreationWorkspace;
import vn.edu.topicregistration.service.CouncilService.LecturerOption;
import vn.edu.topicregistration.util.ProjectTime;

class CouncilControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    CouncilService councils;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        councils = mock(CouncilService.class);
        CouncilController controller = new CouncilController(
                councils,
                new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void listsOnlyServiceScopedBoards() throws Exception {
        when(councils.listForCurrentDean()).thenReturn(List.of(summary()));
        CouncilCreationOptions options = new CouncilCreationOptions(
                List.of(new CouncilPeriodOption(7L, "Đợt KLTN", "KLTN")));
        when(councils.creationOptionsForCurrentDean()).thenReturn(options);

        mvc.perform(get("/faculty/boards"))
                .andExpect(status().isOk())
                .andExpect(view().name("councils/list"))
                .andExpect(model().attributeExists("boards"))
                .andExpect(model().attribute("creationOptions", options));
    }

    @Test
    void createFormLoadsScopedLecturerOptions() throws Exception {
        when(councils.creationWorkspace(7L)).thenReturn(workspace());

        mvc.perform(get("/faculty/boards/new")
                        .param("periodId", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("councils/form"))
                .andExpect(model().attribute("lecturers", workspace().lecturers()));
    }

    @Test
    void createWhitelistsCouncilFieldsAndRedirectsWithSeeOther() throws Exception {
        when(councils.create(org.mockito.ArgumentMatchers.any())).thenReturn(summary());

        mvc.perform(post("/faculty/boards")
                        .param("periodId", "7")
                        .param("name", "Hội đồng KLTN")
                        .param("chairId", "11")
                        .param("secretaryId", "12")
                        .param("memberIds", "13")
                        .param("status", "ACTIVE")
                        .param("actorId", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/boards/41"));

        verify(councils).create(argThat(command ->
                command.periodId() == 7L
                        && command.chairId() == 11L
                        && command.secretaryId() == 12L
                        && command.memberIds().equals(List.of(13L))));
    }

    @Test
    void assignmentConvertsVietnamLocalTimeAndIgnoresClientState() throws Exception {
        when(councils.saveAssignment(
                org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.any())).thenReturn(summary());

        mvc.perform(post("/faculty/boards/41/assignments")
                        .param("registrationId", "51")
                        .param("scheduledAt", "2026-09-20T09:00")
                        .param("location", "Phòng A1-01")
                        .param("version", "4")
                        .param("councilId", "999")
                        .param("status", "ACTIVE"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/boards/41"));

        verify(councils).saveAssignment(
                org.mockito.ArgumentMatchers.eq(41L),
                argThat(command -> command.registrationId() == 51L
                        && command.expectedVersion() == 4L
                        && command.scheduledAt().equals(
                                Instant.parse("2026-09-20T02:00:00Z"))));
    }

    @Test
    void detailPublishesNextVietnamMinuteAsScheduleHint() throws Exception {
        when(councils.detailForCurrentDean(41L)).thenReturn(detail());

        mvc.perform(get("/faculty/boards/41"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minScheduleAt", "2026-09-14T15:01"));
    }

    @Test
    void completionActionAppearsOnlyWhenServiceGrantsDeanCapability() throws Exception {
        CouncilSummaryView active = new CouncilSummaryView(
                41L, 7L, "Đợt KLTN", 4L, "Công nghệ phần mềm",
                "Hội đồng KLTN", CouncilStatus.ACTIVE, 4L);
        when(councils.detailForCurrentDean(41L)).thenReturn(new CouncilDetailView(
                active, List.of(), List.of(), List.of(), List.of(),
                false, false, null, null, false));
        mvc.perform(get("/faculty/boards/41"))
                .andExpect(model().attribute("nextAction", org.hamcrest.Matchers.nullValue()));
        when(councils.detailForCurrentDean(41L)).thenReturn(new CouncilDetailView(
                active, List.of(), List.of(), List.of(), List.of(),
                true, true, null, null, false));
        mvc.perform(get("/faculty/boards/41"))
                .andExpect(model().attributeExists("nextAction"));
    }

    @Test
    void detailExposesProgressWithoutAnyScoreValue() throws Exception {
        var progress = new CouncilService.CouncilTopicProgressView(51L,
                "Đề tài thử nghiệm", List.of(new CouncilService.CouncilMemberProgressView(
                        "Giảng viên A", "Chủ tịch", "SUBMITTED", 2, 2, NOW)));
        when(councils.detailForCurrentDean(41L)).thenReturn(new CouncilDetailView(
                summary(), List.of(), List.of(), List.of(), List.of(progress),
                false, true, null, null, false));

        var result = mvc.perform(get("/faculty/boards/41"))
                .andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        var rows = (List<CouncilController.TopicProgressPageView>) result
                .getModelAndView().getModel().get("progress");
        assertEquals("15:00", rows.getFirst().members().getFirst()
                .submittedAt().substring(11));
        assertEquals(2, rows.getFirst().members().getFirst().submittedCount());
    }

    @Test
    void expiredScheduleReturnsToDetailWithScheduledAtFieldError() throws Exception {
        when(councils.detailForCurrentDean(41L)).thenReturn(detail());
        when(councils.saveAssignment(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new CouncilService.InvalidScheduleException());

        mvc.perform(post("/faculty/boards/41/assignments")
                        .param("registrationId", "51")
                        .param("scheduledAt", "2026-09-14T15:00")
                        .param("location", "Phòng A1")
                        .param("version", "4"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("councils/detail"))
                .andExpect(model().attributeHasFieldErrors("assignmentForm", "scheduledAt"));
    }

    @Test
    void memberUpdateUsesVersionAndWhitelistedIds() throws Exception {
        when(councils.updateMembers(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.any())).thenReturn(summary());

        mvc.perform(post("/faculty/boards/41/members")
                        .param("chairId", "11")
                        .param("secretaryId", "12")
                        .param("memberIds", "13", "14")
                        .param("version", "4")
                        .param("status", "ACTIVE")
                        .param("councilId", "99"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/boards/41"));

        verify(councils).updateMembers(org.mockito.ArgumentMatchers.eq(41L),
                argThat(command -> command.chairId() == 11L
                        && command.secretaryId() == 12L
                        && command.memberIds().equals(List.of(13L, 14L))
                        && command.expectedVersion() == 4L));
    }

    @Test
    void memberUpdateRejectsMissingVersionWithoutCallingService() throws Exception {
        when(councils.detailForCurrentDean(41L)).thenReturn(detail());
        mvc.perform(post("/faculty/boards/41/members")
                        .param("chairId", "11")
                        .param("secretaryId", "12")
                        .param("memberIds", "13"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("councils/detail"));
        org.mockito.Mockito.verify(councils, never()).updateMembers(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transitionUsesOnlyTargetRouteAndVersion() throws Exception {
        when(councils.transition(41L, 4L, CouncilStatus.ASSIGNED)).thenReturn(summary());

        mvc.perform(post("/faculty/boards/41/assign")
                        .param("version", "4")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/boards/41"));

        verify(councils).transition(41L, 4L, CouncilStatus.ASSIGNED);
    }

    @Test
    void staleTransitionIsMappedToConflict() throws Exception {
        when(councils.transition(41L, 3L, CouncilStatus.ACTIVE))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mvc.perform(post("/faculty/boards/41/activate").param("version", "3"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("errorCode", "CONFLICT"));
    }

    @Test
    void directCouncilDetailUrlDeniesLecturerWithoutCurrentAssignment() throws Exception {
        when(councils.detailForCurrentDean(41L))
                .thenThrow(new AccessDeniedException("No active council assignment"));

        mvc.perform(get("/faculty/boards/41"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("errorCode", "ACCESS_DENIED"));
    }

    private static CreationWorkspace workspace() {
        return new CreationWorkspace(
                7L,
                "Đợt KLTN",
                4L,
                "Công nghệ phần mềm",
                List.of(
                        new LecturerOption(11L, "Giảng viên A", "lecturer-a"),
                        new LecturerOption(12L, "Giảng viên B", "lecturer-b"),
                        new LecturerOption(13L, "Giảng viên C", "lecturer-c")));
    }

    private static CouncilSummaryView summary() {
        return new CouncilSummaryView(
                41L,
                7L,
                "Đợt KLTN",
                4L,
                "Công nghệ phần mềm",
                "Hội đồng KLTN",
                CouncilStatus.DRAFT,
                4L);
    }

    @SuppressWarnings("unused")
    private static CouncilDetailView detail() {
        return new CouncilDetailView(summary(), List.of(), List.of(), List.of());
    }
}
