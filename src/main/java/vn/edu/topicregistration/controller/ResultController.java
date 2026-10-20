package vn.edu.topicregistration.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.topicregistration.dto.ResultActionForm;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.ResultService;
import vn.edu.topicregistration.service.ResultService.EvaluationResultView;
import vn.edu.topicregistration.service.ResultService.ResultEvidenceView;
import vn.edu.topicregistration.service.ResultService.FacultyResultDetailView;
import vn.edu.topicregistration.service.ResultService.FacultyResultLecturerView;
import vn.edu.topicregistration.service.ResultService.FacultyResultSummaryView;
import vn.edu.topicregistration.service.ResultService.FacultyResultListView;
import vn.edu.topicregistration.service.ResultService.ResultListFilter;
import vn.edu.topicregistration.service.ResultService.StudentResultDetailView;
import vn.edu.topicregistration.service.ResultService.StudentResultSummaryView;
import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.util.ScoreFormat;

@Controller
@Profile("!test")
public class ResultController {
    private static final Logger log = LoggerFactory.getLogger(ResultController.class);
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
            @RequestParam(required = false) Long period,
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long registrationId,
            @RequestParam(required = false) String topicType,
            @RequestParam(required = false) String topicOrGroup,
            @RequestParam(required = false) Long grader,
            @RequestParam(required = false) Long lecturerId,
            @RequestParam(required = false) FinalResultStatus status,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        if (period != null && periodId != null && !period.equals(periodId)
                || grader != null && lecturerId != null && !grader.equals(lecturerId)) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        Long selectedPeriod = period != null ? period : periodId;
        Long selectedGrader = grader != null ? grader : lecturerId;
        String selectedTopicType = blankToNull(topicType);
        FacultyResultListView listing = results.listPageForCurrentDean(new ResultListFilter(
                selectedPeriod, registrationId, selectedTopicType, topicOrGroup,
                selectedGrader, status, sort, page, size));
        common(model);
        model.addAttribute("periodOptions", listing.periods().stream()
                .map(option -> new FacultyResultPeriodOption(option.id(), option.name()))
                .toList());
        model.addAttribute("lecturerOptions", listing.graders().stream()
                .map(option -> new FacultyResultLecturerOption(option.id(), option.name()))
                .toList());
        model.addAttribute("selectedPeriodId", selectedPeriod);
        model.addAttribute("selectedRegistrationId", registrationId);
        model.addAttribute("selectedTopicType", selectedTopicType);
        model.addAttribute("selectedTopicOrGroup", topicOrGroup);
        model.addAttribute("selectedLecturerId", selectedGrader);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("selectedSize", size);
        model.addAttribute("page", listing.page());
        model.addAttribute("totalPages", listing.totalPages());
        model.addAttribute("totalGroups", listing.totalGroups());
        model.addAttribute("hasPrevious", listing.page() > 0);
        model.addAttribute("hasNext", listing.page() + 1 < listing.totalPages());
        List<FacultyResultGroupPageView> groups = listing.groups().stream()
                .map(group -> new FacultyResultGroupPageView(group.registrationId(),
                        group.students().stream().map(this::display).toList()))
                .toList();
        model.addAttribute("groups", groups);
        model.addAttribute("periodGroups", groupByPeriodAndTopic(groups));
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
        model.addAttribute("reviewerName", detail.reviewer() == null
                ? null : detail.reviewer().reviewerName());
        model.addAttribute("reviewerScore", detail.reviewer() == null
                ? null : score(detail.reviewer().score()));
        model.addAttribute("reviewerSubmittedAt", detail.reviewer() == null
                ? null : format(detail.reviewer().submittedAt()));
        model.addAttribute("reviewerFeedback", detail.reviewer() == null
                ? null : detail.reviewer().feedback());
        model.addAttribute("evidence", display(detail.evidence()));
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
        model.addAttribute("evidence", display(detail.evidence()));
        return "results/student-detail";
    }

    @PostMapping("/faculty/boards/{id}/complete")
    public Object complete(
            @PathVariable long id,
            @Valid @ModelAttribute("resultActionForm") ResultActionForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        requireValidAction(bindingResult, response);
        try {
            results.complete(id, form.getVersion());
        } catch (OptimisticLockingFailureException exception) {
            log.warn("Council completion rejected stale version: councilId={}, expectedVersion={}",
                    id, form.getVersion(), exception);
            redirectAttributes.addFlashAttribute(
                    "completionConflictMessage",
                    "Dữ liệu hội đồng đã thay đổi. Vui lòng kiểm tra lại thông tin mới nhất trước khi tổng hợp kết quả.");
            return WebRedirects.seeOther("/faculty/boards/" + id);
        } catch (RuntimeException exception) {
            log.error("Council completion failed: councilId={}, expectedVersion={}",
                    id, form.getVersion(), exception);
            throw exception;
        }
        log.info("Council completion succeeded: councilId={}, expectedVersion={}",
                id, form.getVersion());
        return WebRedirects.seeOther("/faculty/boards/" + id);
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
                result.topicId(),
                result.topicTitle(),
                result.councilId(),
                result.councilName(),
                result.mode(),
                result.topicType(),
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

    private static List<FacultyResultPeriodPageView> groupByPeriodAndTopic(
            List<FacultyResultGroupPageView> groups) {
        Map<Long, List<FacultyResultGroupPageView>> groupsByPeriod = new LinkedHashMap<>();
        for (FacultyResultGroupPageView group : groups) {
            groupsByPeriod.computeIfAbsent(
                            group.first().periodId(), ignored -> new ArrayList<>())
                    .add(group);
        }
        return groupsByPeriod.values().stream()
                .map(periodGroups -> {
                    FacultyResultPageView first = periodGroups.getFirst().first();
                    Map<Long, List<FacultyResultGroupPageView>> groupsByTopic =
                            new LinkedHashMap<>();
                    for (FacultyResultGroupPageView group : periodGroups) {
                        groupsByTopic.computeIfAbsent(
                                group.first().topicId(), ignored -> new ArrayList<>())
                                .add(group);
                    }
                    List<FacultyResultTopicPageView> topics = groupsByTopic.values().stream()
                            .map(topicGroups -> new FacultyResultTopicPageView(
                                    topicGroups.getFirst().first().topicId(),
                                    topicGroups.getFirst().first().topicTitle(),
                                    topicGroups))
                            .toList();
                    return new FacultyResultPeriodPageView(
                            first.periodId(), first.periodName(), first.topicType(), topics);
                })
                .toList();
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

    private EvidencePageView display(ResultEvidenceView evidence) {
        if (evidence == null) {
            return null;
        }
        return new EvidencePageView(evidence.formula(), evidence.expectedSourceCount(),
                evidence.submittedSourceCount(), score(evidence.mean()),
                evidence.matchesFinalScore(), evidence.sources().stream()
                        .map(source -> new SourcePageView(source.actorName(), source.role(),
                                source.status(), score(source.score()), source.feedback(),
                                format(source.submittedAt())))
                        .toList());
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

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
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
            Long topicId,
            String topicTitle,
            Long councilId,
            String councilName,
            CouncilMode mode,
            String topicType,
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

    public record FacultyResultTopicPageView(
            Long topicId, String topicTitle, List<FacultyResultGroupPageView> groups) {
        public FacultyResultTopicPageView {
            groups = List.copyOf(groups);
        }

        public int groupCount() {
            return groups.size();
        }

        public int studentCount() {
            return groups.stream().mapToInt(group -> group.students().size()).sum();
        }

        public boolean containsRegistration(Long registrationId) {
            return registrationId != null && groups.stream()
                    .anyMatch(group -> registrationId.equals(group.registrationId()));
        }
    }

    public record FacultyResultPeriodPageView(
            Long periodId,
            String periodName,
            String topicType,
            List<FacultyResultTopicPageView> topics) {
        public FacultyResultPeriodPageView {
            topics = List.copyOf(topics);
        }

        public int groupCount() {
            return topics.stream().mapToInt(FacultyResultTopicPageView::groupCount).sum();
        }

        public int studentCount() {
            return topics.stream().mapToInt(FacultyResultTopicPageView::studentCount).sum();
        }

        public boolean containsRegistration(Long registrationId) {
            return topics.stream().anyMatch(topic -> topic.containsRegistration(registrationId));
        }
    }

    public record FacultyResultGroupPageView(
            Long registrationId, List<FacultyResultPageView> students) {
        public FacultyResultGroupPageView {
            students = List.copyOf(students);
        }

        public FacultyResultPageView first() {
            return students.getFirst();
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

    public record EvidencePageView(
            String formula, int expectedSourceCount, int submittedSourceCount,
            String mean, boolean matchesFinalScore, List<SourcePageView> sources) {
    }

    public record SourcePageView(
            String actorName, String role, String status, String score,
            String feedback, String submittedAt) {
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
