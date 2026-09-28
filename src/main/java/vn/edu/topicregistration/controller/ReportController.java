package vn.edu.topicregistration.controller;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import vn.edu.topicregistration.dto.ReportUploadForm;
import vn.edu.topicregistration.service.ReportService;
import vn.edu.topicregistration.service.ReportService.ReportDownload;
import vn.edu.topicregistration.service.ReportService.ReportUpload;
import vn.edu.topicregistration.service.ReportService.ReportWorkspace;
import vn.edu.topicregistration.util.ProjectTime;

@Controller
@Profile("!test")
public class ReportController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm",
            Locale.forLanguageTag("vi-VN"));

    private final ReportService reports;
    private final ProjectTime projectTime;

    public ReportController(ReportService reports, ProjectTime projectTime) {
        this.reports = reports;
        this.projectTime = projectTime;
    }

    @InitBinder("reportUploadForm")
    void whitelistUploadFields(WebDataBinder binder) {
        binder.setAllowedFields("file", "version");
    }

    @GetMapping("/reports/{registrationId}")
    public String detail(@PathVariable long registrationId, Model model) {
        return render(model, reports.workspace(registrationId), new ReportUploadForm());
    }

    @PostMapping(
            value = "/reports/{registrationId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(
            @PathVariable long registrationId,
            @Valid @ModelAttribute("reportUploadForm") ReportUploadForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return render(model, reports.workspace(registrationId), form);
        }

        reports.upload(
                registrationId,
                form.getVersion(),
                new ReportUpload(
                        form.getFile().getOriginalFilename(),
                        form.getFile().getContentType(),
                        form.getFile().getSize(),
                        form.getFile()::getInputStream));
        return WebRedirects.seeOther("/reports/" + registrationId);
    }

    @GetMapping("/reports/{registrationId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long registrationId) {
        ReportDownload download = reports.download(registrationId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.content()));
    }

    private String render(
            Model model,
            ReportWorkspace workspace,
            ReportUploadForm form) {
        model.addAttribute("activeNav", "registrations");
        model.addAttribute("pageSection", "Báo cáo đề tài");
        model.addAttribute("workspace", workspace);
        model.addAttribute("registrationId", workspace.registrationId());
        model.addAttribute("groupCode", "N" + String.format("%03d", workspace.groupId()));
        model.addAttribute("report", workspace.report());
        model.addAttribute("canUpload", workspace.canUpload());
        if (workspace.report() != null) {
            model.addAttribute("submittedAt", DATE_TIME.format(
                    projectTime.toBusinessTime(workspace.report().submittedAt())));
        }
        if (!model.containsAttribute("reportUploadForm")) {
            if (workspace.report() != null) {
                form.setVersion(workspace.report().version());
            }
            model.addAttribute("reportUploadForm", form);
        }
        return "reports/detail";
    }
}
