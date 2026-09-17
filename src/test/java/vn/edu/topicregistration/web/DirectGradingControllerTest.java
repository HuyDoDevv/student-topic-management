package vn.edu.topicregistration.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.DirectGradingController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.DirectGradingService.DirectGradingWorkspace;
import vn.edu.topicregistration.service.ResultService;

class DirectGradingControllerTest {
    DirectGradingService directGrading;
    ResultService results;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        directGrading = mock(DirectGradingService.class);
        results = mock(ResultService.class);
        mvc = standaloneSetup(new DirectGradingController(directGrading, results))
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void deanOpensSeparateDirectGradingWorkspace() throws Exception {
        when(directGrading.workspaceForCurrentDean()).thenReturn(
                new DirectGradingWorkspace(List.of(), List.of()));

        mvc.perform(get("/faculty/direct-grading"))
                .andExpect(status().isOk())
                .andExpect(view().name("direct-grading/list"))
                .andExpect(model().attributeExists("availableRegistrations"))
                .andExpect(model().attributeExists("assignments"));
    }

    @Test
    void assignmentWhitelistsRegistrationAndGraderOnly() throws Exception {
        when(directGrading.assign(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        mvc.perform(post("/faculty/direct-grading")
                        .param("registrationId", "51")
                        .param("graderId", "11")
                        .param("status", "PUBLISHED")
                        .param("actorId", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/direct-grading"));

        verify(directGrading).assign(argThat(command ->
                command.registrationId() == 51L && command.graderId() == 11L));
    }

    @Test
    void directGraderCompletesAndDeanPublishesThroughExistingResultService() throws Exception {
        mvc.perform(post("/lecturer/direct-grading/41/complete")
                        .param("version", "4"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/scoring"));
        verify(results).complete(41L, 4L);

        mvc.perform(post("/faculty/direct-grading/41/publish")
                        .param("version", "5"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/direct-grading"));
        verify(results).publish(41L, 5L);
    }

    @SuppressWarnings("unused")
    private static CouncilStatus statusForCompilation() {
        return CouncilStatus.ACTIVE;
    }
}
