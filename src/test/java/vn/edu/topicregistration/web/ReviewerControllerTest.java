package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import vn.edu.topicregistration.controller.ReviewerController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.AssignmentView;
import vn.edu.topicregistration.service.ReviewerAssignmentService.DeanWorkspace;
import vn.edu.topicregistration.service.ReviewerAssignmentService.RegistrationOption;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReviewerCandidate;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReviewerTask;
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
    void deanWorkspaceRendersSeparateLocalizedStatusColumnAndActions() throws Exception {
        ReviewerCandidate candidate = new ReviewerCandidate(11L, "Giảng viên A");
        when(reviewers.deanWorkspace()).thenReturn(new DeanWorkspace(List.of(
                new RegistrationOption(51L, 0L, "TLCN 2026", "TLCN",
                        "Đề tài chưa phân công", null, List.of(candidate)),
                new RegistrationOption(52L, 0L, "TLCN 2026", "TLCN",
                        "Đề tài đang phản biện",
                        new AssignmentView(71L, "Giảng viên B",
                                ReviewerAssignmentEntity.Status.ASSIGNED, 1L),
                        List.of(candidate)),
                new RegistrationOption(53L, 0L, "KLTN 2026", "KLTN",
                        "Đề tài đã nộp",
                        new AssignmentView(72L, "Giảng viên C",
                                ReviewerAssignmentEntity.Status.SUBMITTED, 2L),
                        List.of(candidate)),
                new RegistrationOption(54L, 0L, "TLCN cũ", "TLCN",
                        "Đề tài cần xác minh",
                        new AssignmentView(73L, "Giảng viên D",
                                ReviewerAssignmentEntity.Status.LEGACY_UNVERIFIED, 0L),
                        List.of(candidate)),
                new RegistrationOption(55L, 0L, "KLTN cũ", "KLTN",
                        "Đề tài đã thay GVPB",
                        new AssignmentView(74L, "Giảng viên E",
                                ReviewerAssignmentEntity.Status.REPLACED, 3L),
                        List.of(candidate)))));

        renderedMvc().perform(get("/faculty/reviewers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5 đăng ký")))
                .andExpect(content().string(containsString("<th>Trạng thái</th>")))
                .andExpect(content().string(containsString("Chưa phân công")))
                .andExpect(content().string(containsString("Chờ phản biện")))
                .andExpect(content().string(containsString("Đã nộp phản biện")))
                .andExpect(content().string(containsString("Cần xác minh")))
                .andExpect(content().string(containsString("Đã thay thế")))
                .andExpect(content().string(containsString("Giảng viên B")))
                .andExpect(content().string(not(containsString("Giảng viên B · ASSIGNED"))))
                .andExpect(content().string(not(containsString("Giảng viên C · SUBMITTED"))))
                .andExpect(content().string(containsString("Phân công")))
                .andExpect(content().string(containsString("Thay GVPB")))
                .andExpect(content().string(containsString("Đã khóa sau khi nộp")))
                .andExpect(content().string(containsString("Không thể thao tác")));
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

    @Test
    void taskListRendersResponsiveTableWithReadableStatusesDatesAndActions() throws Exception {
        when(reviewers.reviewerTasks()).thenReturn(List.of(
                new ReviewerTask(71L, "Đề tài cần phản biện", "TLCN 2026",
                        LocalDate.of(2026, 10, 16), ReviewerAssignmentEntity.Status.ASSIGNED),
                new ReviewerTask(72L, "Đề tài đã hoàn tất", "KLTN 2026",
                        LocalDate.of(2026, 10, 20), ReviewerAssignmentEntity.Status.SUBMITTED)));

        renderedMvc().perform(get("/lecturer/reviewers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 phân công")))
                .andExpect(content().string(containsString("<th>Đề tài</th>")))
                .andExpect(content().string(containsString("<th>Đợt</th>")))
                .andExpect(content().string(containsString("<th>Hạn nộp</th>")))
                .andExpect(content().string(containsString("<th>Trạng thái</th>")))
                .andExpect(content().string(containsString("Chưa nộp")))
                .andExpect(content().string(containsString("Đã nộp")))
                .andExpect(content().string(containsString("16/10/2026")))
                .andExpect(content().string(containsString("href=\"/lecturer/reviewers/71\"")))
                .andExpect(content().string(containsString("Mở phản biện")))
                .andExpect(content().string(containsString("Xem chi tiết")));
    }

    @Test
    void emptyTaskListExplainsWhenAssignmentsWillAppear() throws Exception {
        when(reviewers.reviewerTasks()).thenReturn(List.of());

        renderedMvc().perform(get("/lecturer/reviewers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có phân công phản biện")))
                .andExpect(content().string(containsString("Khi trưởng khoa phân công đề tài")));
    }

    private MockMvc renderedMvc() {
        return standaloneSetup(new ReviewerController(reviewers))
                .setViewResolvers(viewResolver()).build();
    }

    private static ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");
        return viewResolver;
    }
}
