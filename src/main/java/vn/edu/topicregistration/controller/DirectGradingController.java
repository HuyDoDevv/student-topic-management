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

import vn.edu.topicregistration.dto.DirectGradingAssignmentForm;
import vn.edu.topicregistration.dto.ResultActionForm;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.DirectGradingService;
import vn.edu.topicregistration.service.DirectGradingService.DirectAssignmentView;
import vn.edu.topicregistration.service.DirectGradingService.DirectGradingWorkspace;
import vn.edu.topicregistration.service.ResultService;

@Controller
@Profile("!test")
public class DirectGradingController {
    private final DirectGradingService directGrading;
    private final ResultService results;

    public DirectGradingController(
            DirectGradingService directGrading,
            ResultService results) {
        this.directGrading = directGrading;
        this.results = results;
    }

    @InitBinder("directGradingAssignmentForm")
    void whitelistAssignmentFields(WebDataBinder binder) {
        binder.setAllowedFields("registrationId", "graderId");
    }

    @InitBinder("resultActionForm")
    void whitelistResultFields(WebDataBinder binder) {
        binder.setAllowedFields("version");
    }

    @GetMapping("/faculty/direct-grading")
    public String list(Model model) {
        return render(model, new DirectGradingAssignmentForm(),
                directGrading.workspaceForCurrentDean());
    }

    @PostMapping("/faculty/direct-grading")
    public Object assign(
            @Valid @ModelAttribute("directGradingAssignmentForm")
                    DirectGradingAssignmentForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return render(model, form, directGrading.workspaceForCurrentDean());
        }
        try {
            directGrading.assign(form.toCommand());
            return WebRedirects.seeOther("/faculty/direct-grading");
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT
                    && exception.code() != ApplicationErrorCode.INVALID_COUNCIL_STATE) {
                throw exception;
            }
            bindingResult.reject("directGrading.invalid",
                    "Chỉ phân công đề tài Môn học/NCKH đã có báo cáo; người chấm phải là giảng viên khác mọi GVHD và cùng bộ môn.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return render(model, form, directGrading.workspaceForCurrentDean());
        }
    }

    @PostMapping("/lecturer/direct-grading/{id}/complete")
    public Object complete(
            @PathVariable long id,
            @Valid @ModelAttribute("resultActionForm") ResultActionForm form,
            BindingResult bindingResult,
            HttpServletResponse response) {
        requireValidAction(bindingResult, response);
        results.complete(id, form.getVersion());
        return WebRedirects.seeOther("/lecturer/scoring");
    }

    @PostMapping("/faculty/direct-grading/{id}/publish")
    public Object publish(
            @PathVariable long id,
            @Valid @ModelAttribute("resultActionForm") ResultActionForm form,
            BindingResult bindingResult,
            HttpServletResponse response) {
        requireValidAction(bindingResult, response);
        results.publish(id, form.getVersion());
        return WebRedirects.seeOther("/faculty/direct-grading");
    }

    private static void requireValidAction(
            BindingResult bindingResult,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private static String render(
            Model model,
            DirectGradingAssignmentForm form,
            DirectGradingWorkspace workspace) {
        common(model);
        if (!model.containsAttribute("directGradingAssignmentForm")) {
            model.addAttribute("directGradingAssignmentForm", form);
        }
        model.addAttribute("availableRegistrations", workspace.availableRegistrations());
        model.addAttribute("assignments", workspace.assignments().stream()
                .map(DirectGradingController::display)
                .toList());
        return "direct-grading/list";
    }

    private static DirectAssignmentPageView display(DirectAssignmentView assignment) {
        return new DirectAssignmentPageView(
                assignment.id(),
                assignment.registrationId(),
                assignment.groupId(),
                assignment.periodId(),
                assignment.topicTitle(),
                assignment.periodName(),
                assignment.topicType(),
                assignment.departmentName(),
                assignment.graderName(),
                assignment.status(),
                statusLabel(assignment.status()),
                statusClass(assignment.status()),
                assignment.version());
    }

    private static String statusLabel(CouncilStatus status) {
        return switch (status) {
            case ACTIVE -> "Đang chấm";
            case COMPLETED -> "Chờ công bố";
            case PUBLISHED -> "Đã công bố";
            case DRAFT, ASSIGNED, SCHEDULED -> "Đang phân công";
        };
    }

    private static String statusClass(CouncilStatus status) {
        return switch (status) {
            case ACTIVE -> "bg-yellow-lt";
            case COMPLETED -> "bg-purple-lt";
            case PUBLISHED -> "bg-green-lt";
            case DRAFT, ASSIGNED, SCHEDULED -> "bg-secondary-lt";
        };
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "direct-grading");
        model.addAttribute("pageSection", "Phân công chấm độc lập");
    }

    public record DirectAssignmentPageView(
            Long id,
            Long registrationId,
            Long groupId,
            Long periodId,
            String topicTitle,
            String periodName,
            String topicType,
            String departmentName,
            String graderName,
            CouncilStatus status,
            String statusLabel,
            String statusClass,
            long version) {
    }
}
