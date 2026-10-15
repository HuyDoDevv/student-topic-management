package vn.edu.topicregistration.controller;

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

import vn.edu.topicregistration.dto.ReviewerAssignmentForm;
import vn.edu.topicregistration.dto.ReviewerSubmissionForm;
import vn.edu.topicregistration.service.ReviewerAssignmentService;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReviewerValidationException;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionWorkspace;

@Controller
@Profile("!test")
public class ReviewerController {
    private final ReviewerAssignmentService reviewers;

    public ReviewerController(ReviewerAssignmentService reviewers) {
        this.reviewers = reviewers;
    }

    @InitBinder("reviewerAssignmentForm")
    void assignmentFields(WebDataBinder binder) {
        binder.setAllowedFields("registrationId", "reviewerId", "version");
    }

    @InitBinder("reviewerSubmissionForm")
    void submissionFields(WebDataBinder binder) {
        binder.setAllowedFields("version", "file", "scores[*].studentId",
                "scores[*].score", "scores[*].feedback");
        binder.setAutoGrowCollectionLimit(100);
    }

    @GetMapping("/faculty/reviewers")
    public String assignments(Model model) {
        return renderAssignments(model, new ReviewerAssignmentForm());
    }

    @PostMapping("/faculty/reviewers")
    public Object assign(@Valid @ModelAttribute("reviewerAssignmentForm") ReviewerAssignmentForm form,
                         BindingResult result, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderAssignments(model, form);
        }
        try {
            reviewers.assign(form.toCommand());
            return WebRedirects.seeOther("/faculty/reviewers");
        } catch (ReviewerValidationException exception) {
            result.reject("reviewer.invalid", exception.getMessage());
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderAssignments(model, form);
        }
    }

    @PostMapping("/faculty/reviewers/{id}/replace")
    public Object replace(@PathVariable long id,
                          @Valid @ModelAttribute("reviewerAssignmentForm") ReviewerAssignmentForm form,
                          BindingResult result, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderAssignments(model, form);
        }
        try {
            reviewers.replace(id, form.toCommand());
            return WebRedirects.seeOther("/faculty/reviewers");
        } catch (ReviewerValidationException exception) {
            result.reject("reviewer.invalid", exception.getMessage());
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderAssignments(model, form);
        }
    }

    @GetMapping("/lecturer/reviewers")
    public String tasks(Model model) {
        common(model);
        model.addAttribute("tasks", reviewers.reviewerTasks());
        return "reviewers/submission";
    }

    @GetMapping("/lecturer/reviewers/{id}")
    public String submission(@PathVariable long id, Model model) {
        SubmissionWorkspace workspace = reviewers.submissionWorkspace(id);
        ReviewerSubmissionForm form = new ReviewerSubmissionForm();
        form.setVersion(workspace.version());
        return renderSubmission(model, workspace, form);
    }

    @PostMapping("/lecturer/reviewers/{id}/submit")
    public Object submit(@PathVariable long id,
                         @Valid @ModelAttribute("reviewerSubmissionForm") ReviewerSubmissionForm form,
                         BindingResult result, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderSubmission(model, reviewers.submissionWorkspace(id), form);
        }
        try {
            reviewers.submit(id, form.toCommand());
            return WebRedirects.seeOther("/lecturer/reviewers/" + id);
        } catch (ReviewerValidationException exception) {
            result.reject("reviewer.invalid", exception.getMessage());
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderSubmission(model, reviewers.submissionWorkspace(id), form);
        }
    }

    private String renderAssignments(Model model, ReviewerAssignmentForm form) {
        common(model);
        if (!model.containsAttribute("reviewerAssignmentForm")) {
            model.addAttribute("reviewerAssignmentForm", form);
        }
        model.addAttribute("workspace", reviewers.deanWorkspace());
        return "reviewers/assignments";
    }

    private String renderSubmission(Model model, SubmissionWorkspace workspace,
                                    ReviewerSubmissionForm form) {
        common(model);
        if (form.getScores() == null || form.getScores().size() != workspace.students().size()) {
            java.util.List<ReviewerSubmissionForm.StudentScore> rows = new java.util.ArrayList<>();
            workspace.students().forEach(student -> {
                ReviewerSubmissionForm.StudentScore row = new ReviewerSubmissionForm.StudentScore();
                row.setStudentId(student.id());
                rows.add(row);
            });
            form.setScores(rows);
        }
        if (!model.containsAttribute("reviewerSubmissionForm")) {
            model.addAttribute("reviewerSubmissionForm", form);
        }
        model.addAttribute("workspace", workspace);
        return "reviewers/submission";
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "reviewers");
        model.addAttribute("pageSection", "Phản biện");
    }
}
