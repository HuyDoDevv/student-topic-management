package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.ScoringController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.dto.ScoreForm;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.EvaluationView;
import vn.edu.topicregistration.service.ScoringService.ScoreWorkspace;
import vn.edu.topicregistration.service.ScoringService.ScoringAssignmentView;
import vn.edu.topicregistration.service.ScoringService.StudentScoreView;
import vn.edu.topicregistration.service.ScoringService.StudentScoreWorkspace;
import vn.edu.topicregistration.util.ProjectTime;

class ScoringControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-20T02:00:00Z");

    ScoringService scoring;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        scoring = mock(ScoringService.class);
        ScoringController controller = new ScoringController(
                scoring, new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void listsOnlyAssignmentsReturnedForCurrentLecturer() throws Exception {
        when(scoring.listForCurrentLecturer()).thenReturn(List.of(assignment()));

        mvc.perform(get("/lecturer/scoring"))
                .andExpect(status().isOk())
                .andExpect(view().name("scoring/list"))
                .andExpect(model().attributeExists("assignments"));
    }

    @Test
    void listFormatsOwnSubmissionProgressInVietnamTime() throws Exception {
        var assignment = new ScoringAssignmentView(41L, "Hội đồng KLTN", 51L, 21L,
                "Đề tài thử nghiệm", NOW, "Phòng A1-01", true, 4L, false,
                "SUBMITTED", 2, 2, NOW);
        when(scoring.listForCurrentLecturer()).thenReturn(List.of(assignment));

        var result = mvc.perform(get("/lecturer/scoring"))
                .andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        var rows = (List<ScoringController.AssignmentPageView>) result
                .getModelAndView().getModel().get("assignments");
        assertEquals("SUBMITTED", rows.getFirst().progressStatus());
        assertEquals("20/09/2026 09:00", rows.getFirst().fullySubmittedAt());
    }

    @Test
    void assignmentPageListsEveryRegistrationStudent() throws Exception {
        when(scoring.workspace(41L, 51L)).thenReturn(workspace());

        mvc.perform(get("/lecturer/scoring/41/51"))
                .andExpect(status().isOk())
                .andExpect(view().name("scoring/form"))
                .andExpect(model().attribute("students", workspace().students()));
    }

    @Test
    void studentScoreFormUsesThatStudentsOwnEvaluation() throws Exception {
        when(scoring.studentWorkspace(41L, 51L, 31L)).thenReturn(studentWorkspace());

        var response = mvc.perform(get("/lecturer/scoring/41/51/students/31"))
                .andExpect(status().isOk())
                .andExpect(view().name("scoring/student-form"))
                .andExpect(model().attribute("student", studentWorkspace().student()))
                .andExpect(model().attributeExists("scoreForm"))
                .andReturn();

        ScoreForm form = (ScoreForm) response.getModelAndView()
                .getModel().get("scoreForm");
        assertEquals("8", form.getScore().toPlainString());
    }

    @Test
    void draftRouteWhitelistsFieldsAndRedirectsWithSeeOther() throws Exception {
        when(scoring.saveDraft(eq(41L), eq(51L), eq(31L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(studentWorkspace().student().evaluation());

        mvc.perform(post("/lecturer/scoring/41/51/students/31/draft")
                        .param("score", "8.50")
                        .param("feedback", "Nhận xét")
                        .param("version", "3")
                        .param("evaluatorId", "999")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/scoring/41/51/students/31"));

        verify(scoring).saveDraft(eq(41L), eq(51L), eq(31L), argThat(command ->
                command.score().equals(new BigDecimal("8.50"))
                        && command.feedback().equals("Nhận xét")
                        && command.expectedVersion() == 3L));
    }

    @Test
    void submitRouteChoosesSubmittedStateOnServer() throws Exception {
        when(scoring.submit(eq(41L), eq(51L), eq(31L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(studentWorkspace().student().evaluation());

        mvc.perform(post("/lecturer/scoring/41/51/students/31/submit")
                        .param("score", "9.00")
                        .param("feedback", "Đạt")
                        .param("version", "3")
                        .param("status", "DRAFT"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/scoring/41/51/students/31"));

        verify(scoring).submit(eq(41L), eq(51L), eq(31L), argThat(command ->
                command.score().equals(new BigDecimal("9.00"))));
    }

    @Test
    void invalidScoreReturnsUnprocessableWithoutWriting() throws Exception {
        when(scoring.studentWorkspace(41L, 51L, 31L)).thenReturn(studentWorkspace());

        mvc.perform(post("/lecturer/scoring/41/51/students/31/submit")
                        .param("score", "8.555")
                        .param("feedback", "Sai precision"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("scoring/student-form"));

        verify(scoring, never()).submit(
                eq(41L), eq(51L), eq(31L), org.mockito.ArgumentMatchers.any());
    }

    private static ScoreWorkspace workspace() {
        return new ScoreWorkspace(
                assignment(),
                List.of(new StudentScoreView(
                        31L,
                        "Sinh viên A",
                        1,
                        true,
                        new EvaluationView(
                        71L,
                        new BigDecimal("8.00"),
                        "Bản cũ",
                        EvaluationStatus.SUBMITTED,
                        NOW,
                        NOW,
                        3L))));
    }

    private static StudentScoreWorkspace studentWorkspace() {
        return new StudentScoreWorkspace(assignment(), workspace().students().getFirst());
    }

    private static ScoringAssignmentView assignment() {
        return new ScoringAssignmentView(
                41L,
                "Hội đồng KLTN",
                51L,
                21L,
                "Đề tài thử nghiệm",
                NOW,
                "Phòng A1-01");
    }
}
