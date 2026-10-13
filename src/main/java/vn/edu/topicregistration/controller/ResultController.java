package vn.edu.topicregistration.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.topicregistration.dto.ResultActionForm;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ResultService.EvaluationResultView;
import vn.edu.topicregistration.service.ResultService.FacultyResultDetailView;
import vn.edu.topicregistration.service.ResultService.FacultyResultLecturerView;
import vn.edu.topicregistration.service.ResultService.FacultyResultSummaryView;
import vn.edu.topicregistration.service.ResultService.StudentResultDetailView;
import vn.edu.topicregistration.service.ResultService.StudentResultSummaryView;
import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.util.ScoreFormat;

@Controller
@Profile("!test")
public class ResultController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));

    private final ResultService results;
    private final ProjectTime projectTime;

    public ResultController(ResultService results, ProjectTime projectTime) {
        this.results = results;
        this.projectTime = projectTime;
    }

    @InitBinder("resultActionForm")
    void whitelistFields(WebDataBinder binder) {
        binder.setAllowedFields("version");
    }

    @GetMapping("/faculty/results")
    public String list(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long registrationId,
            @RequestParam(required = false) Long lecturerId,
            @RequestParam(required = false) FinalResultStatus status,
            Model model) {
        common(model);
        var allResults = results.listForCurrentDean().stream()
                .map(this::display)
                .toList();
        model.addAttribute("periodOptions", allResults.stream()
                .map(result -> new FacultyResultPeriodOption(
                        result.periodId(), result.periodName()))
                .distinct()
                .toList());
        model.addAttribute("lecturerOptions", allResults.stream()
                .flatMap(result -> result.lecturers().stream())
                .map(lecturer -> new FacultyResultLecturerOption(
                        lecturer.id(), lecturer.name()))
                .distinct()
                .toList());
        model.addAttribute("selectedPeriodId", periodId);
        model.addAttribute("selectedRegistrationId", registrationId);
        model.addAttribute("selectedLecturerId", lecturerId);
        model.addAttribute("selectedStatus", status);
        var filteredResults = allResults.stream()
                .filter(result -> periodId == null || periodId.equals(result.periodId()))
                .filter(result -> registrationId == null
                        || registrationId.equals(result.registrationId()))
                .filter(result -> lecturerId == null || result.lecturers().stream()
                        .anyMatch(lecturer -> lecturerId.equals(lecturer.id())))
                .filter(result -> status == null || status == result.status())
                .toList();
        model.addAttribute("results", filteredResults);
        model.addAttribute("selectedGroupId", registrationId == null || filteredResults.isEmpty()
                ? null : filteredResults.getFirst().groupId());
        return "results/faculty-list";
    }

    @GetMapping("/faculty/results/{registrationId}/{studentId}")
    public String detail(
            @PathVariable long registrationId,
            @PathVariable long studentId,
            Model model) {
        FacultyResultDetailView detail = results.detailForCurrentDean(
                registrationId, studentId);
        common(model);
        model.addAttribute("result", display(detail.result()));
        model.addAttribute("evaluationSummary", detail.evaluationSummary());
        model.addAttribute("consolidatedBy", detail.consolidatedBy());
        model.addAttribute("publishedBy", detail.publishedBy());
        model.addAttribute("evaluations", detail.evaluations().stream()
                .map(this::display)
                .toList());
        return "results/faculty-detail";
    }

    @GetMapping("/student/results")
    public String studentList(Model model) {
        common(model);
        model.addAttribute("results", results.listForCurrentStudent().stream()
                .map(this::display)
                .toList());
        return "results/student-list";
    }

    @GetMapping("/student/results/{registrationId}")
    public String studentDetail(@PathVariable long registrationId, Model model) {
        StudentResultDetailView detail = results.detailForCurrentStudent(registrationId);
        common(model);
        model.addAttribute("result", display(detail.result()));
        model.addAttribute("evaluationSummary", detail.evaluationSummary());
        model.addAttribute("evaluations", detail.evaluations().stream()
                .map(this::display)
                .toList());
        return "results/student-detail";
    }

    @PostMapping("/faculty/boards/{id}/complete")
    public Object complete(
            @PathVariable long id,
            @Valid @ModelAttribute("resultActionForm") ResultActionForm form,
            BindingResult bindingResult,
            HttpServletResponse response) {
        requireValidAction(bindingResult, response);
        results.complete(id, form.getVersion());
        return WebRedirects.seeOther("/lecturer/scoring");
    }

    @PostMapping("/faculty/boards/{id}/publish")
    public Object publish(
            @PathVariable long id,
            @Valid @ModelAttribute("resultActionForm") ResultActionForm form,
            BindingResult bindingResult,
            HttpServletResponse response) {
        requireValidAction(bindingResult, response);
        results.publish(id, form.getVersion());
        return WebRedirects.seeOther("/faculty/results");
    }

    private static void requireValidAction(
            BindingResult bindingResult,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private FacultyResultPageView display(FacultyResultSummaryView result) {
        return new FacultyResultPageView(
                result.registrationId(),
                result.studentId(),
                result.studentName(),
                result.groupId(),
                result.periodId(),
                result.periodName(),
                result.topicTitle(),
                result.councilId(),
                result.councilName(),
                result.lecturers().stream()
                        .map(this::display)
                        .toList(),
                result.status(),
                statusLabel(result.status()),
                statusClass(result.status()),
                score(result.finalScore()),
                format(result.consolidatedAt()),
                format(result.publishedAt()),
                result.version());
    }

    private FacultyResultLecturerPageView display(FacultyResultLecturerView lecturer) {
        return new FacultyResultLecturerPageView(lecturer.id(), lecturer.name());
    }

    private EvaluationResultPageView display(EvaluationResultView evaluation) {
        return new EvaluationResultPageView(
                evaluation.evaluatorId(),
                evaluation.evaluatorName(),
                score(evaluation.score()),
                evaluation.feedback(),
                evaluation.status(),
                evaluation.status() == EvaluationStatus.SUBMITTED
                        ? "Đã nộp" : "Bản nháp",
                format(evaluation.submittedAt()));
    }

    private StudentResultPageView display(StudentResultSummaryView result) {
        return new StudentResultPageView(
                result.registrationId(),
                result.groupId(),
                result.topicTitle(),
                result.councilName(),
                score(result.finalScore()),
                format(result.publishedAt()));
    }

    private String format(Instant value) {
        return value == null ? null : DATE_TIME.format(projectTime.toBusinessTime(value));
    }

    private static String score(BigDecimal value) {
        return ScoreFormat.display(value);
    }

    private static String statusLabel(FinalResultStatus status) {
        return status == FinalResultStatus.PUBLISHED ? "Đã công bố" : "Sẵn sàng";
    }

    private static String statusClass(FinalResultStatus status) {
        return status == FinalResultStatus.PUBLISHED ? "bg-green-lt" : "bg-purple-lt";
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "results");
        model.addAttribute("pageSection", "Kết quả hội đồng");
    }

    public record FacultyResultPageView(
            Long registrationId,
            Long studentId,
            String studentName,
            Long groupId,
            Long periodId,
            String periodName,
            String topicTitle,
            Long councilId,
            String councilName,
            List<FacultyResultLecturerPageView> lecturers,
            FinalResultStatus status,
            String statusLabel,
            String statusClass,
            String finalScore,
            String consolidatedAt,
            String publishedAt,
            long version) {
        public FacultyResultPageView {
            lecturers = List.copyOf(lecturers);
        }
    }

    public record FacultyResultPeriodOption(Long id, String name) {
    }

    public record FacultyResultLecturerOption(Long id, String name) {
    }

    public record FacultyResultLecturerPageView(Long id, String name) {
    }

    public record EvaluationResultPageView(
            Long evaluatorId,
            String evaluatorName,
            String score,
            String feedback,
            EvaluationStatus status,
            String statusLabel,
            String submittedAt) {
    }

    public record StudentResultPageView(
            Long registrationId,
            Long groupId,
            String topicTitle,
            String councilName,
            String finalScore,
            String publishedAt) {
    }
}
