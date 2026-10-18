package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.ReportController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.service.ReportService;
import vn.edu.topicregistration.service.ReportService.ReportDownload;
import vn.edu.topicregistration.service.ReportService.ReportView;
import vn.edu.topicregistration.service.ReportService.ReportWorkspace;
import vn.edu.topicregistration.util.ProjectTime;

class ReportControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final byte[] PDF = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);

    ReportService reports;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reports = mock(ReportService.class);
        ReportController controller = new ReportController(
                reports,
                new ProjectTime(Clock.fixed(NOW, ZoneOffset.UTC)));
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void rendersCurrentReportMetadataAndUploadPermission() throws Exception {
        when(reports.workspace(11L)).thenReturn(workspace());

        mvc.perform(get("/reports/11"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/detail"))
                .andExpect(model().attribute("canUpload", true))
                .andExpect(model().attribute("report", workspace().report()));
    }

    @Test
    void multipartUploadUsesWhitelistedFileAndRedirectsWithSeeOther() throws Exception {
        when(reports.upload(eq(11L), eq(4L), any())).thenReturn(workspace());
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", PDF);

        mvc.perform(multipart("/reports/11")
                        .file(file)
                        .param("version", "4")
                        .param("actorId", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/reports/11"));

        verify(reports).upload(eq(11L), eq(4L), any());
    }

    @Test
    void downloadStreamsPdfWithSafeAttachmentHeaders() throws Exception {
        when(reports.download(11L)).thenReturn(new ReportDownload(
                "report.pdf",
                PDF.length,
                new ByteArrayInputStream(PDF)));

        mvc.perform(get("/reports/11/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(PDF));
    }

    private static ReportWorkspace workspace() {
        return new ReportWorkspace(
                11L,
                7L,
                21L,
                "Đề tài thử nghiệm",
                new ReportView(
                        41L,
                        "report.pdf",
                        PDF.length,
                        "Sinh viên A",
                        NOW,
                        4L),
                true);
    }
}
