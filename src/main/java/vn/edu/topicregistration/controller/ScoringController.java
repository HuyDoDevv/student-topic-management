package vn.edu.topicregistration.controller;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

import vn.edu.topicregistration.dto.GroupScoreForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.ScoreWorkspace;
import vn.edu.topicregistration.service.ScoringService.ScoringAssignmentView;
import vn.edu.topicregistration.util.ProjectTime;

@Controller
@Profile("!test")
public class ScoringController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));

    private final ScoringService scoring;
    private final ProjectTime projectTime;

    public ScoringController(ScoringService scoring, ProjectTime projectTime) {
        this.scoring = scoring;
        this.projectTime = projectTime;
    }

    @InitBinder("groupScoreForm")
    void whitelistFields(WebDataBinder binder) {
        binder.setAllowedFields(
                "councilVersion",
                "rows[*].studentId",
                "rows[*].score",
                "rows[*].feedback",
                "rows[*].version");
        binder.setAutoGrowCollectionLimit(3);
    }

    @GetMapping("/lecturer/scoring")
    public String list(Model model) {
        common(model);
        model.addAttribute("assignments", scoring.listForCurrentLecturer().stream()
                .map(this::display)
                .toList());
        return "scoring/list";
    }

    @GetMapping("/lecturer/scoring/{councilId}/{registrationId}")
    public String form(
            @PathVariable long councilId,
            @PathVariable long registrationId,
            Model model) {
        ScoreWorkspace workspace = scoring.workspace(councilId, registrationId);
        return renderGroup(model, workspace, GroupScoreForm.from(workspace));
    }

    @PostMapping("/lecturer/scoring/{councilId}/{registrationId}/draft")
    public Object saveDraft(
            @PathVariable long councilId,
            @PathVariable long registrationId,
            @Valid @ModelAttribute("groupScoreForm") GroupScoreForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        return mutate(
                councilId, registrationId,
                form, bindingResult, response, model, false);
    }

    @PostMapping("/lecturer/scoring/{councilId}/{registrationId}/submit")
    public Object submit(
            @PathVariable long councilId,
            @PathVariable long registrationId,
            @Valid @ModelAttribute("groupScoreForm") GroupScoreForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        return mutate(
                councilId, registrationId,
                form, bindingResult, response, model, true);
    }

    private Object mutate(
            long councilId,
            long registrationId,
            GroupScoreForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model,
            boolean submit) {
        rejectInvalidScoreIncrements(form, bindingResult);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderGroup(model, scoring.workspace(councilId, registrationId), form);
        }
        try {
            if (submit) {
                scoring.submitGroup(councilId, registrationId, form.toCommand());
            } else {
                scoring.saveGroupDraft(councilId, registrationId, form.toCommand());
            }
            return redirect(councilId, registrationId);
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.INVALID_SCORE) {
                throw exception;
            }
            bindingResult.reject("score.invalid",
                    "Điểm phải từ 0 đến 10 và tăng theo bước 0,25.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderGroup(model, scoring.workspace(councilId, registrationId), form);
        }
    }

    private String renderGroup(Model model, ScoreWorkspace workspace, GroupScoreForm form) {
        common(model);
        if (!model.containsAttribute("groupScoreForm")) {
            model.addAttribute("groupScoreForm", form);
        }
        model.addAttribute("assignment", display(workspace.assignment()));
        model.addAttribute("students", workspace.students());
        model.addAttribute("allSubmitted", !workspace.students().isEmpty()
                && workspace.students().stream().allMatch(student ->
                        student.evaluation() != null
                                && student.evaluation().status()
                                == vn.edu.topicregistration.entity.EvaluationStatus.SUBMITTED));
        return "scoring/form";
    }

    private static void rejectInvalidScoreIncrements(
            GroupScoreForm form, BindingResult bindingResult) {
        for (int index = 0; index < form.getRows().size(); index++) {
            if (!form.getRows().get(index).hasValidScoreIncrement()) {
                bindingResult.rejectValue("rows[" + index + "].score", "score.increment",
                        "Điểm phải tăng theo bước 0,25.");
            }
        }
    }

    private AssignmentPageView display(ScoringAssignmentView assignment) {
        return new AssignmentPageView(
                assignment.councilId(),
                assignment.councilName(),
                assignment.registrationId(),
                assignment.groupId(),
                assignment.topicTitle(),
                format(assignment.scheduledAt()),
                assignment.location(),
                assignment.currentChair(),
                assignment.councilVersion(),
                assignment.directGrading(),
                assignment.progressStatus(),
                assignment.submittedCount(),
                assignment.totalCount(),
                format(assignment.fullySubmittedAt()));
    }

    private String format(Instant value) {
        return value == null ? null : DATE_TIME.format(projectTime.toBusinessTime(value));
    }

    private static Object redirect(long councilId, long registrationId) {
        return WebRedirects.seeOther(
                "/lecturer/scoring/" + councilId + "/" + registrationId);
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "scoring");
        model.addAttribute("pageSection", "Chấm điểm");
    }

    public record AssignmentPageView(
            Long councilId,
            String councilName,
            Long registrationId,
            Long groupId,
            String topicTitle,
            String scheduledAt,
            String location,
            boolean currentChair,
            long councilVersion,
            boolean directGrading,
            String progressStatus,
            int submittedCount,
            int totalCount,
            String fullySubmittedAt) {
    }
}
