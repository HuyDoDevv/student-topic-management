package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

import vn.edu.topicregistration.controller.ResultController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ResultService.BoardResultView;
import vn.edu.topicregistration.service.ResultService.EvaluationResultView;
import vn.edu.topicregistration.service.ResultService.FacultyResultDetailView;
import vn.edu.topicregistration.service.ResultService.FacultyResultLecturerView;
import vn.edu.topicregistration.service.ResultService.FacultyResultSummaryView;
import vn.edu.topicregistration.service.ResultService.FacultyResultFilterOption;
import vn.edu.topicregistration.service.ResultService.FacultyResultGroupView;
import vn.edu.topicregistration.service.ResultService.FacultyResultListView;
import vn.edu.topicregistration.service.ResultService.ResultListFilter;
import vn.edu.topicregistration.service.ResultService.ResultEvidenceView;
import vn.edu.topicregistration.service.ResultService.SourceEvidenceView;
import vn.edu.topicregistration.service.ResultService.StudentResultDetailView;
import vn.edu.topicregistration.service.ResultService.StudentResultSummaryView;
import vn.edu.topicregistration.util.ProjectTime;

class ResultControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-21T02:00:00Z");

    ResultService results;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        results = mock(ResultService.class);
        ResultController controller = new ResultController(
                results, new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void listsOnlyFacultyResultsReturnedByService() throws Exception {
        when(results.listPageForCurrentDean(any())).thenReturn(listing(summary()));

        var response = mvc.perform(get("/faculty/results"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/faculty-list"))
                .andExpect(model().attributeExists("groups"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultGroupPageView> groups =
                (List<ResultController.FacultyResultGroupPageView>) response.getModelAndView()
                        .getModel().get("groups");
        assertEquals("8.5", groups.getFirst().first().finalScore());
        assertEquals("Đợt KLTN 2026", groups.getFirst().first().periodName());
        verify(results).listPageForCurrentDean(new ResultListFilter(
                null, null, null, null, null, null, "recent", 0, 10));
    }

    @Test
    void filtersFacultyResultsByPeriodAndKeepsAllPeriodOptions() throws Exception {
        FacultyResultSummaryView anotherPeriod = new FacultyResultSummaryView(
                52L,
                31L,
                "Sinh viên A",
                22L,
                8L,
                "Đợt môn học 2026",
                "Đề tài môn học",
                42L,
                "Chấm độc lập",
                List.of(new FacultyResultLecturerView(12L, "Giảng viên B")),
                FinalResultStatus.PUBLISHED,
                new BigDecimal("7.75"),
                NOW,
                NOW,
                1L);
        when(results.listPageForCurrentDean(any())).thenReturn(listing(anotherPeriod));

        var response = mvc.perform(get("/faculty/results").param("periodId", "8"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/faculty-list"))
                .andExpect(model().attribute("selectedPeriodId", 8L))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultGroupPageView> groups =
                (List<ResultController.FacultyResultGroupPageView>) response.getModelAndView()
                        .getModel().get("groups");
        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultPeriodOption> periodOptions =
                (List<ResultController.FacultyResultPeriodOption>) response.getModelAndView()
                        .getModel().get("periodOptions");
        assertEquals(1, groups.size());
        assertEquals(8L, groups.getFirst().first().periodId());
        assertEquals(2, periodOptions.size());
        verify(results).listPageForCurrentDean(new ResultListFilter(
                8L, null, null, null, null, null, "recent", 0, 10));
    }

    @Test
    void filtersFacultyResultsToTheSelectedRegistrationBeforePublication() throws Exception {
        when(results.listPageForCurrentDean(any())).thenReturn(listing(summary()));

        var response = mvc.perform(get("/faculty/results")
                        .param("periodId", "7")
                        .param("registrationId", "51"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedPeriodId", 7L))
                .andExpect(model().attribute("selectedRegistrationId", 51L))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultGroupPageView> groups =
                (List<ResultController.FacultyResultGroupPageView>) response.getModelAndView()
                        .getModel().get("groups");
        assertEquals(1, groups.size());
        assertEquals(51L, groups.getFirst().registrationId());
        assertEquals(FinalResultStatus.READY, groups.getFirst().first().status());
        verify(results).listPageForCurrentDean(new ResultListFilter(
                7L, 51L, null, null, null, null, "recent", 0, 10));
    }

    @Test
    void combinesLecturerAndPublicationStatusFilters() throws Exception {
        when(results.listPageForCurrentDean(any())).thenReturn(listing(summary()));

        var response = mvc.perform(get("/faculty/results")
                        .param("lecturerId", "11")
                        .param("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedLecturerId", 11L))
                .andExpect(model().attribute("selectedStatus", FinalResultStatus.READY))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultGroupPageView> groups =
                (List<ResultController.FacultyResultGroupPageView>) response.getModelAndView()
                        .getModel().get("groups");
        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultLecturerOption> lecturerOptions =
                (List<ResultController.FacultyResultLecturerOption>) response.getModelAndView()
                        .getModel().get("lecturerOptions");
        assertEquals(1, groups.size());
        assertEquals(51L, groups.getFirst().registrationId());
        assertEquals(2, lecturerOptions.size());
        verify(results).listPageForCurrentDean(new ResultListFilter(
                null, null, null, null, 11L, FinalResultStatus.READY, "recent", 0, 10));
    }

    @Test
    void canonicalFiltersAndPaginationReachServiceWithoutClientFacultyScope() throws Exception {
        when(results.listPageForCurrentDean(any())).thenReturn(listing(summary()));

        mvc.perform(get("/faculty/results")
                        .param("period", "7")
                        .param("topicType", "KLTN")
                        .param("topicOrGroup", "N021")
                        .param("grader", "11")
                        .param("sort", "topic")
                        .param("page", "2")
                        .param("size", "20")
                        .param("facultyId", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSort", "topic"));

        verify(results).listPageForCurrentDean(new ResultListFilter(
                7L, null, "KLTN", "N021", 11L, null, "topic", 2, 20));
    }

    @Test
    void conflictingCanonicalAndLegacyFiltersAreRejected() throws Exception {
        mvc.perform(get("/faculty/results").param("period", "7")
                        .param("periodId", "8"))
                .andExpect(status().isUnprocessableEntity());
        verify(results, never()).listPageForCurrentDean(any());
    }

    @Test
    void rendersFacultyResultDetailWithEvaluations() throws Exception {
        when(results.detailForCurrentDean(51L, 31L)).thenReturn(detail());

        var response = mvc.perform(get("/faculty/results/51/31"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/faculty-detail"))
                .andExpect(model().attributeExists("result", "evaluations"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ResultController.EvaluationResultPageView> evaluations =
                (List<ResultController.EvaluationResultPageView>) response.getModelAndView()
                        .getModel().get("evaluations");
        assertEquals("8.5", evaluations.getFirst().score());
    }

    @Test
    void completeRouteWhitelistsVersionAndUsesServerSideActorAndState() throws Exception {
        when(results.complete(41L, 4L)).thenReturn(
                new BoardResultView(41L, CouncilStatus.COMPLETED, 1, 5L));

        mvc.perform(post("/faculty/boards/41/complete")
                        .param("version", "4")
                        .param("actorId", "999")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/scoring"));

        verify(results).complete(41L, 4L);
    }

    @Test
    void publishRouteWhitelistsVersionAndRedirectsToFacultyResults() throws Exception {
        when(results.publish(41L, 6L)).thenReturn(
                new BoardResultView(41L, CouncilStatus.PUBLISHED, 1, 7L));

        mvc.perform(post("/faculty/boards/41/publish")
                        .param("version", "6")
                        .param("publishedBy", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/results"));

        verify(results).publish(41L, 6L);
    }

    @Test
    void missingVersionIsRejectedWithoutMutation() throws Exception {
        mvc.perform(post("/faculty/boards/41/complete"))
                .andExpect(status().isUnprocessableContent());

        verify(results, never()).complete(eq(41L), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void studentListRendersOnlyServiceScopedPublishedResults() throws Exception {
        when(results.listForCurrentStudent()).thenReturn(List.of(studentSummary()));

        mvc.perform(get("/student/results"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/student-list"))
                .andExpect(model().attributeExists("results"));
    }

    @Test
    void studentDetailRendersPublishedOwnResult() throws Exception {
        when(results.detailForCurrentStudent(51L)).thenReturn(new StudentResultDetailView(
                studentSummary(),
                "3 điểm đã nộp; trung bình 8.50",
                detail().evaluations()));

        mvc.perform(get("/student/results/51"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/student-detail"))
                .andExpect(model().attributeExists("result", "evaluations"));
    }

    @Test
    void facultyDetailFormatsSourceEvidenceWithoutDraftScore() throws Exception {
        ResultEvidenceView evidence = new ResultEvidenceView(
                "(Điểm GVPB + điểm người chấm thứ hai) / 2", 2, 1,
                null, false, List.of(
                        new SourceEvidenceView(14L, "Giảng viên PB", "GVPB",
                                "SUBMITTED", new BigDecimal("8.50"), "Đạt", NOW),
                        new SourceEvidenceView(11L, "Giảng viên chấm", "Người chấm thứ hai",
                                "DRAFT", null, null, null)));
        when(results.detailForCurrentDean(51L, 31L)).thenReturn(
                new FacultyResultDetailView(summary(), "Chưa đủ nguồn", "Trưởng khoa",
                        null, List.of(), null, evidence));

        var response = mvc.perform(get("/faculty/results/51/31"))
                .andExpect(status().isOk()).andReturn();

        var page = (ResultController.EvidencePageView) response
                .getModelAndView().getModel().get("evidence");
        assertEquals("8.5", page.sources().getFirst().score());
        assertEquals("21/09/2026 09:00", page.sources().getFirst().submittedAt());
        assertEquals(null, page.sources().get(1).score());
    }

    private static FacultyResultDetailView detail() {
        return new FacultyResultDetailView(
                summary(),
                "3 điểm đã nộp; trung bình 8.50",
                "Chủ tịch",
                null,
                List.of(new EvaluationResultView(
                        11L,
                        "Giảng viên A",
                        new BigDecimal("8.50"),
                        "Đạt",
                        EvaluationStatus.SUBMITTED,
                        NOW)));
    }

    private static FacultyResultSummaryView summary() {
        return new FacultyResultSummaryView(
                51L,
                31L,
                "Sinh viên A",
                21L,
                7L,
                "Đợt KLTN 2026",
                "Đề tài thử nghiệm",
                41L,
                "Hội đồng KLTN",
                List.of(new FacultyResultLecturerView(11L, "Giảng viên A")),
                FinalResultStatus.READY,
                new BigDecimal("8.50"),
                NOW,
                null,
                0L);
    }

    private static FacultyResultListView listing(FacultyResultSummaryView result) {
        return new FacultyResultListView(
                List.of(new FacultyResultGroupView(result.registrationId(), List.of(result))),
                1, 1, 0, 10,
                List.of(new FacultyResultFilterOption(7L, "Đợt KLTN 2026"),
                        new FacultyResultFilterOption(8L, "Đợt môn học 2026")),
                List.of(new FacultyResultFilterOption(11L, "Giảng viên A"),
                        new FacultyResultFilterOption(12L, "Giảng viên B")));
    }

    private static StudentResultSummaryView studentSummary() {
        return new StudentResultSummaryView(
                51L,
                21L,
                "Đề tài thử nghiệm",
                "Hội đồng KLTN",
                new BigDecimal("8.50"),
                NOW);
    }
}
