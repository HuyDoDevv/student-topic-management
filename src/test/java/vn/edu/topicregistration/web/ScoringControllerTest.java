package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import vn.edu.topicregistration.controller.ScoringController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.dto.GroupScoreForm;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.EvaluationView;
import vn.edu.topicregistration.service.ScoringService.ScoreWorkspace;
import vn.edu.topicregistration.service.ScoringService.ScoringAssignmentView;
import vn.edu.topicregistration.service.ScoringService.StudentScoreView;
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
                .setViewResolvers(viewResolver())
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
    void assignmentPageBuildsOneFormForEverySnapshotStudent() throws Exception {
        when(scoring.workspace(41L, 51L)).thenReturn(workspace());

        var response = mvc.perform(get("/lecturer/scoring/41/51"))
                .andExpect(status().isOk())
                .andExpect(view().name("scoring/form"))
                .andExpect(model().attribute("students", workspace().students()))
                .andExpect(model().attributeExists("groupScoreForm"))
                .andExpect(content().string(containsString("Sinh viên A")))
                .andExpect(content().string(containsString("Sinh viên B")))
                .andExpect(content().string(containsString("Lưu nháp toàn bộ")))
                .andExpect(content().string(containsString("Nộp toàn bộ")))
                .andExpect(content().string(containsString("name=\"rows[0].studentId\"")))
                .andExpect(content().string(containsString("name=\"rows[1].studentId\"")))
                .andReturn();

        GroupScoreForm form = (GroupScoreForm) response.getModelAndView()
                .getModel().get("groupScoreForm");
        assertEquals(4L, form.getCouncilVersion());
        assertEquals(List.of(31L, 32L), form.getRows().stream()
                .map(GroupScoreForm.Row::getStudentId).toList());
        assertEquals("8", form.getRows().getFirst().getScore().toPlainString());
    }

    @Test
    void draftRouteWhitelistsRowsAndRedirectsToGroupWorkspace() throws Exception {
        mvc.perform(post("/lecturer/scoring/41/51/draft")
                        .param("councilVersion", "4")
                        .param("rows[0].studentId", "31")
                        .param("rows[0].score", "8.50")
                        .param("rows[0].feedback", "Nhận xét A")
                        .param("rows[0].version", "3")
                        .param("rows[1].studentId", "32")
                        .param("rows[1].score", "9.00")
                        .param("rows[1].feedback", "Nhận xét B")
                        .param("rows[1].version", "1")
                        .param("rows[0].evaluatorId", "999")
                        .param("rows[0].status", "SUBMITTED"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/scoring/41/51"));

        verify(scoring).saveGroupDraft(eq(41L), eq(51L), argThat(command ->
                command.expectedCouncilVersion() == 4L
                        && command.rows().size() == 2
                        && command.rows().getFirst().studentId() == 31L
                        && command.rows().getFirst().score().equals(new BigDecimal("8.50"))
                        && command.rows().getFirst().feedback().equals("Nhận xét A")
                        && command.rows().getFirst().expectedVersion() == 3L));
    }

    @Test
    void submitRouteChoosesSubmittedStateForTheWholeGroup() throws Exception {
        mvc.perform(post("/lecturer/scoring/41/51/submit")
                        .param("councilVersion", "4")
                        .param("rows[0].studentId", "31")
                        .param("rows[0].score", "9.00")
                        .param("rows[0].version", "3")
                        .param("rows[1].studentId", "32")
                        .param("rows[1].score", "8.75")
                        .param("rows[1].version", "1")
                        .param("status", "DRAFT"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/scoring/41/51"));

        verify(scoring).submitGroup(eq(41L), eq(51L), argThat(command ->
                command.rows().size() == 2
                        && command.rows().get(1).score().equals(new BigDecimal("8.75"))));
    }

    @Test
    void invalidRowReturnsGroupFormWithoutWriting() throws Exception {
        when(scoring.workspace(41L, 51L)).thenReturn(workspace());

        mvc.perform(post("/lecturer/scoring/41/51/submit")
                        .param("councilVersion", "4")
                        .param("rows[0].studentId", "31")
                        .param("rows[0].score", "8.55")
                        .param("rows[0].version", "3")
                        .param("rows[1].studentId", "32")
                        .param("rows[1].score", "8.75")
                        .param("rows[1].version", "1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("scoring/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "groupScoreForm", "rows[0].score"));

        verify(scoring, never()).submitGroup(
                eq(41L), eq(51L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void individualStudentRouteIsNoLongerExposed() throws Exception {
        mvc.perform(get("/lecturer/scoring/41/51/students/31"))
                .andExpect(status().isNotFound());
    }

    private static ScoreWorkspace workspace() {
        return new ScoreWorkspace(
                assignment(),
                List.of(
                        new StudentScoreView(
                                31L, "Sinh viên A", 1, true,
                                new EvaluationView(
                                        71L, new BigDecimal("8.00"), "Bản cũ",
                                        EvaluationStatus.SUBMITTED, NOW, NOW, 3L)),
                        new StudentScoreView(
                                32L, "Sinh viên B", 2, false,
                                new EvaluationView(
                                        72L, new BigDecimal("7.50"), "Bản cũ",
                                        EvaluationStatus.DRAFT, NOW, null, 1L))));
    }

    private static ScoringAssignmentView assignment() {
        return new ScoringAssignmentView(
                41L,
                "Hội đồng KLTN",
                51L,
                21L,
                "Đề tài thử nghiệm",
                NOW,
                "Phòng A1-01",
                true,
                4L,
                false);
    }

    private static ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");
        return viewResolver;
    }
}
