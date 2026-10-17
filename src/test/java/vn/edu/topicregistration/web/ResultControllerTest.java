package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

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
                .andExpect(model().attributeExists("groups", "periodGroups"))
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
    void groupsResultCardsByPeriodThenTopicWithoutMergingDuplicateTitles() throws Exception {
        FacultyResultSummaryView firstGroup = summary(
                51L, 21L, 7L, "Đợt KLTN 2026", 101L, "Đề tài trùng tên");
        FacultyResultSummaryView secondGroup = summary(
                52L, 22L, 7L, "Đợt KLTN 2026", 101L, "Đề tài trùng tên");
        FacultyResultSummaryView differentTopic = summary(
                53L, 23L, 7L, "Đợt KLTN 2026", 102L, "Đề tài trùng tên");
        FacultyResultSummaryView differentPeriod = summary(
                54L, 24L, 8L, "Đợt KLTN 2027", 103L, "Đề tài khác");
        when(results.listPageForCurrentDean(any())).thenReturn(listing(List.of(
                new FacultyResultGroupView(51L, List.of(firstGroup)),
                new FacultyResultGroupView(52L, List.of(secondGroup)),
                new FacultyResultGroupView(53L, List.of(differentTopic)),
                new FacultyResultGroupView(54L, List.of(differentPeriod)))));

        var response = mvc.perform(get("/faculty/results"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ResultController.FacultyResultPeriodPageView> periods =
                (List<ResultController.FacultyResultPeriodPageView>) response.getModelAndView()
                        .getModel().get("periodGroups");
        assertEquals(2, periods.size());
        assertEquals(2, periods.getFirst().topics().size());
        assertEquals(3, periods.getFirst().groupCount());
        assertEquals(2, periods.getFirst().topics().getFirst().groupCount());
        assertEquals(101L, periods.getFirst().topics().getFirst().topicId());
        assertEquals(102L, periods.getFirst().topics().get(1).topicId());
        assertEquals(1, periods.get(1).groupCount());
    }

    @Test
    void rendersPeriodTypeAndGroupBadgesWithoutRedundantCounts() throws Exception {
        FacultyResultSummaryView firstGroup = summary(
                51L, 21L, 7L, "Đợt KLTN 2026", 101L, "Đề tài gom nhóm");
        FacultyResultSummaryView secondGroup = summary(
                52L, 22L, 7L, "Đợt KLTN 2026", 101L, "Đề tài gom nhóm");
        when(results.listPageForCurrentDean(any())).thenReturn(listing(List.of(
                new FacultyResultGroupView(51L, List.of(firstGroup)),
                new FacultyResultGroupView(52L, List.of(secondGroup)))));

        renderedMvc().perform(get("/faculty/results"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"accordion\" data-result-accordion")))
                .andExpect(content().string(containsString("data-result-period-toggle")))
                .andExpect(content().string(containsString("data-result-topic-toggle")))
                .andExpect(content().string(containsString("class=\"accordion-button-toggle\"")))
                .andExpect(content().string(containsString("result-type-badge\">KLTN</span>")))
                .andExpect(content().string(containsString("data-result-topic-groups")))
                .andExpect(content().string(containsString("result-topic-7-101")))
                .andExpect(content().string(containsString("Nhóm N021")))
                .andExpect(content().string(containsString("Nhóm N022")))
                .andExpect(content().string(not(containsString("nhóm trên trang"))))
                .andExpect(content().string(not(containsString("1 sinh viên"))));
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
                102L,
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

        renderedMvc().perform(get("/faculty/results")
                        .param("period", "7")
                        .param("topicType", "KLTN")
                        .param("topicOrGroup", "N021")
                        .param("grader", "11")
                        .param("sort", "topic")
                        .param("page", "2")
                        .param("size", "20")
                        .param("facultyId", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSort", "topic"))
                .andExpect(model().attribute("selectedTopicOrGroup", "N021"))
                .andExpect(content().string(containsString("class=\"card data-card mb-4\"")))
                .andExpect(content().string(containsString("data-result-filters")))
                .andExpect(content().string(containsString(
                        "class=\"col-md-2 result-filter-submit\"")))
                .andExpect(content().string(containsString("value=\"N021\"")));

        verify(results).listPageForCurrentDean(new ResultListFilter(
                7L, null, "KLTN", "N021", 11L, null, "topic", 2, 20));
    }

    @Test
    void blankOptionalSelectValuesDoNotRejectGroupSearch() throws Exception {
        when(results.listPageForCurrentDean(any())).thenReturn(listing(summary()));

        mvc.perform(get("/faculty/results")
                        .param("period", "1")
                        .param("topicType", "")
                        .param("topicOrGroup", "N010")
                        .param("grader", "")
                        .param("status", "")
                        .param("sort", "recent")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedTopicOrGroup", "N010"));

        verify(results).listPageForCurrentDean(new ResultListFilter(
                1L, null, null, "N010", null, null, "recent", 0, 10));
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
        return summary(51L, 21L, 7L, "Đợt KLTN 2026", 101L, "Đề tài thử nghiệm");
    }

    private static FacultyResultSummaryView summary(
            long registrationId,
            long groupId,
            long periodId,
            String periodName,
            long topicId,
            String topicTitle) {
        return new FacultyResultSummaryView(
                registrationId,
                31L,
                "Sinh viên A",
                groupId,
                periodId,
                periodName,
                topicId,
                topicTitle,
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
        return listing(List.of(
                new FacultyResultGroupView(result.registrationId(), List.of(result))));
    }

    private static FacultyResultListView listing(List<FacultyResultGroupView> groups) {
        return new FacultyResultListView(
                groups,
                groups.size(), 1, 0, 10,
                List.of(new FacultyResultFilterOption(7L, "Đợt KLTN 2026"),
                        new FacultyResultFilterOption(8L, "Đợt môn học 2026")),
                List.of(new FacultyResultFilterOption(11L, "Giảng viên A"),
                        new FacultyResultFilterOption(12L, "Giảng viên B")));
    }

    private MockMvc renderedMvc() {
        ResultController controller = new ResultController(
                results, new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        return standaloneSetup(controller)
                .setControllerAdvice(new WebExceptionHandler())
                .setViewResolvers(viewResolver())
                .build();
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
