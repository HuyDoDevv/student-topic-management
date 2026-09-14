package vn.edu.topicregistration.web;

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
import vn.edu.topicregistration.service.ResultService.FacultyResultSummaryView;
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
        when(results.listForCurrentDean()).thenReturn(List.of(summary()));

        mvc.perform(get("/faculty/results"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/faculty-list"))
                .andExpect(model().attributeExists("results"));
    }

    @Test
    void rendersFacultyResultDetailWithEvaluations() throws Exception {
        when(results.detailForCurrentDean(51L)).thenReturn(detail());

        mvc.perform(get("/faculty/results/51"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/faculty-detail"))
                .andExpect(model().attributeExists("result", "evaluations"));
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
                .andExpect(redirectedUrl("/faculty/boards/41"));

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
                21L,
                "Đề tài thử nghiệm",
                41L,
                "Hội đồng KLTN",
                FinalResultStatus.READY,
                new BigDecimal("8.50"),
                NOW,
                null,
                0L);
    }
}
