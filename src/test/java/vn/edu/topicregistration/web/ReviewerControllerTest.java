package vn.edu.topicregistration.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.ReviewerController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.DeanWorkspace;
import vn.edu.topicregistration.service.ReviewerAssignmentService.StudentRow;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionWorkspace;

class ReviewerControllerTest {
    private ReviewerAssignmentService reviewers;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reviewers = mock(ReviewerAssignmentService.class);
        mvc = standaloneSetup(new ReviewerController(reviewers))
                .setControllerAdvice(new WebExceptionHandler()).build();
    }

    @Test
    void deanOpensAssignmentWorkspace() throws Exception {
        when(reviewers.deanWorkspace()).thenReturn(new DeanWorkspace(List.of()));
        mvc.perform(get("/faculty/reviewers"))
                .andExpect(status().isOk())
                .andExpect(view().name("reviewers/assignments"))
                .andExpect(model().attributeExists("workspace"));
    }

    @Test
    void assignmentAllowsOnlyExpectedFields() throws Exception {
        mvc.perform(post("/faculty/reviewers")
                        .param("registrationId", "51")
                        .param("reviewerId", "11")
                        .param("version", "0")
                        .param("status", "SUBMITTED")
                        .param("assignedBy", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/faculty/reviewers"));
        verify(reviewers).assign(argThat(command -> command.registrationId() == 51L
                && command.reviewerId() == 11L && command.version() == 0L));
    }

    @Test
    void replaceUsesIdFromPathAndVersionFromForm() throws Exception {
        mvc.perform(post("/faculty/reviewers/71/replace")
                        .param("registrationId", "51")
                        .param("reviewerId", "12")
                        .param("version", "2"))
                .andExpect(status().isSeeOther());
        verify(reviewers).replace(org.mockito.Mockito.eq(71L), argThat(command ->
                command.registrationId() == 51L && command.reviewerId() == 12L
                        && command.version() == 2L));
    }

    @Test
    void invalidAssignmentIsShownOnForm() throws Exception {
        when(reviewers.deanWorkspace()).thenReturn(new DeanWorkspace(List.of()));
        mvc.perform(post("/faculty/reviewers").param("registrationId", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("reviewers/assignments"));
    }

    @Test
    void uploadBindsEachStudentScoreAndIgnoresForgedStatus() throws Exception {
        byte[] pdf = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        mvc.perform(multipart("/lecturer/reviewers/71/submit")
                        .file(new MockMultipartFile("file", "review.pdf",
                                "application/pdf", pdf))
                        .param("version", "0")
                        .param("scores[0].studentId", "101")
                        .param("scores[0].score", "8.25")
                        .param("scores[0].feedback", "Tốt")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/lecturer/reviewers/71"));
        verify(reviewers).submit(org.mockito.Mockito.eq(71L), argThat(command ->
                command.version() == 0L && command.scores().size() == 1
                        && command.scores().getFirst().studentId() == 101L
                        && command.file().sizeBytes() == pdf.length));
    }

    @Test
    void submissionPageContainsSnapshotStudentRows() throws Exception {
        when(reviewers.submissionWorkspace(71L)).thenReturn(new SubmissionWorkspace(
                71L, 0L, "Đề tài", "Đợt", LocalDate.of(2026, 10, 15),
                ReviewerAssignmentEntity.Status.ASSIGNED,
                List.of(new StudentRow(101L, "Sinh viên A")), true));
        mvc.perform(get("/lecturer/reviewers/71"))
                .andExpect(status().isOk())
                .andExpect(view().name("reviewers/submission"))
                .andExpect(model().attributeExists("reviewerSubmissionForm"));
    }
}
