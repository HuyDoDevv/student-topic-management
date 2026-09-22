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

import vn.edu.topicregistration.dto.ScoreForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.ScoringService.ScoreWorkspace;
import vn.edu.topicregistration.service.ScoringService.ScoringAssignmentView;
import vn.edu.topicregistration.service.ScoringService.StudentScoreWorkspace;
import vn.edu.topicregistration.util.ProjectTime;

@Controller
@Profile("!preview & !test")
public class ScoringController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));

    private final ScoringService scoring;
    private final ProjectTime projectTime;

    public ScoringController(ScoringService scoring, ProjectTime projectTime) {
        this.scoring = scoring;
        this.projectTime = projectTime;
    }

    @InitBinder("scoreForm")
    void whitelistFields(WebDataBinder binder) {
        binder.setAllowedFields("score", "feedback", "version");
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
        common(model);
        model.addAttribute("assignment", display(workspace.assignment()));
        model.addAttribute("students", workspace.students());
        model.addAttribute("allSubmitted", !workspace.students().isEmpty()
                && workspace.students().stream().allMatch(student ->
                        student.evaluation() != null
                                && student.evaluation().status()
                                == vn.edu.topicregistration.entity.EvaluationStatus.SUBMITTED));
        return "scoring/form";
    }

    @GetMapping("/lecturer/scoring/{councilId}/{registrationId}/students/{studentId}")
    public String studentForm(
            @PathVariable long councilId,
            @PathVariable long registrationId,
            @PathVariable long studentId,
            Model model) {
        StudentScoreWorkspace workspace = scoring.studentWorkspace(
                councilId, registrationId, studentId);
        return renderStudent(model, workspace,
                ScoreForm.from(workspace.student().evaluation()));
    }

    @PostMapping("/lecturer/scoring/{councilId}/{registrationId}/students/{studentId}/draft")
    public Object saveDraft(
            @PathVariable long councilId,
            @PathVariable long registrationId,
            @PathVariable long studentId,
            @Valid @ModelAttribute("scoreForm") ScoreForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        return mutate(
                councilId, registrationId, studentId,
                form, bindingResult, response, model, false);
    }

    @PostMapping("/lecturer/scoring/{councilId}/{registrationId}/students/{studentId}/submit")
    public Object submit(
            @PathVariable long councilId,
            @PathVariable long registrationId,
            @PathVariable long studentId,
            @Valid @ModelAttribute("scoreForm") ScoreForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        return mutate(
                councilId, registrationId, studentId,
                form, bindingResult, response, model, true);
    }

    private Object mutate(
            long councilId,
            long registrationId,
            long studentId,
            ScoreForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model,
            boolean submit) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderStudent(model,
                    scoring.studentWorkspace(councilId, registrationId, studentId), form);
        }
        try {
            if (submit) {
                scoring.submit(councilId, registrationId, studentId, form.toCommand());
            } else {
                scoring.saveDraft(councilId, registrationId, studentId, form.toCommand());
            }
            return redirect(councilId, registrationId, studentId);
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.INVALID_SCORE) {
                throw exception;
            }
            bindingResult.rejectValue("score", "score.invalid",
                    "Điểm phải từ 0 đến 10 và tăng theo bước 0,25.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderStudent(model,
                    scoring.studentWorkspace(councilId, registrationId, studentId), form);
        }
    }

    private String renderStudent(
            Model model, StudentScoreWorkspace workspace, ScoreForm form) {
        common(model);
        if (!model.containsAttribute("scoreForm")) {
            model.addAttribute("scoreForm", form);
        }
        model.addAttribute("assignment", display(workspace.assignment()));
        model.addAttribute("student", workspace.student());
        model.addAttribute("evaluation", workspace.student().evaluation());
        model.addAttribute("submitted",
                workspace.student().evaluation() != null
                        && workspace.student().evaluation().status()
                                == vn.edu.topicregistration.entity.EvaluationStatus.SUBMITTED);
        return "scoring/student-form";
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
                assignment.directGrading());
    }

    private String format(Instant value) {
        return value == null ? null : DATE_TIME.format(projectTime.toBusinessTime(value));
    }

    private static Object redirect(long councilId, long registrationId, long studentId) {
        return WebRedirects.seeOther(
                "/lecturer/scoring/" + councilId + "/" + registrationId
                        + "/students/" + studentId);
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
            boolean directGrading) {
    }
}
