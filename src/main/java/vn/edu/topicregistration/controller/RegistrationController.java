package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.service.RegistrationDecisionService;
import vn.edu.topicregistration.service.RegistrationService;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.dto.RegistrationDecisionForm;
import vn.edu.topicregistration.dto.RegistrationForm;

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
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.service.RegistrationService.RegistrationDetailView;
import vn.edu.topicregistration.service.RegistrationService.RegistrationMemberView;
import vn.edu.topicregistration.service.RegistrationService.SubmissionWorkspace;
import vn.edu.topicregistration.service.RegistrationService.TopicOption;

@Controller
@Profile("!test")
public class RegistrationController {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", VIETNAMESE);

    private final RegistrationService registrations;
    private final RegistrationDecisionService decisions;
    private final ProjectTime projectTime;

    public RegistrationController(
            RegistrationService registrations,
            RegistrationDecisionService decisions,
            ProjectTime projectTime) {
        this.registrations = registrations;
        this.decisions = decisions;
        this.projectTime = projectTime;
    }

    @InitBinder("registrationForm")
    void whitelistRegistrationFields(WebDataBinder binder) {
        binder.setAllowedFields("topicId", "version");
    }

    @InitBinder({"withdrawForm", "acceptForm"})
    void whitelistVersionField(WebDataBinder binder) {
        binder.setAllowedFields("version");
    }

    @InitBinder("rejectForm")
    void whitelistRejectFields(WebDataBinder binder) {
        binder.setAllowedFields("version", "reason");
    }

    @GetMapping("/groups/{groupId}/registrations/new")
    Object createForm(@PathVariable long groupId, Model model) {
        SubmissionWorkspace workspace = registrations.submissionWorkspace(groupId);
        if (workspace.activeRegistrationId() != null) {
            return registrationDetail(workspace.activeRegistrationId());
        }
        RegistrationForm form = new RegistrationForm();
        form.setVersion(workspace.groupVersion());
        return renderForm(model, workspace, form);
    }

    @PostMapping("/groups/{groupId}/registrations")
    Object submit(
            @PathVariable long groupId,
            @Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(
                    model,
                    registrations.submissionWorkspace(groupId),
                    form);
        }
        long registrationId = registrations.submit(
                groupId, form.getVersion(), form.getTopicId()).id();
        return registrationDetail(registrationId);
    }

    @GetMapping("/registrations/{id}")
    String detail(@PathVariable long id, Model model) {
        return renderDetail(model, registrations.getVisible(id), new RegistrationDecisionForm());
    }

    @PostMapping("/registrations/{id}/withdraw")
    Object withdraw(
            @PathVariable long id,
            @Valid @ModelAttribute("withdrawForm") RegistrationDecisionForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderDetail(model, registrations.getVisible(id), form);
        }
        registrations.withdraw(id, form.getVersion());
        return registrationDetail(id);
    }

    @GetMapping("/registrations/{id}/decision")
    Object decision(@PathVariable long id, Model model) {
        RegistrationDetailView registration = registrations.getForDecision(id);
        if (!registration.canDecide()) {
            return registrationDetail(id);
        }
        RegistrationDecisionForm acceptForm = decisionForm(registration.version());
        RegistrationDecisionForm rejectForm = decisionForm(registration.version());
        return renderDecision(model, registration, acceptForm, rejectForm);
    }

    @PostMapping("/registrations/{id}/accept")
    Object accept(
            @PathVariable long id,
            @Valid @ModelAttribute("acceptForm") RegistrationDecisionForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            RegistrationDetailView registration = registrations.getForDecision(id);
            return renderDecision(
                    model,
                    registration,
                    form,
                    decisionForm(registration.version()));
        }
        decisions.accept(id, form.getVersion());
        return registrationDecision(id);
    }

    @PostMapping("/registrations/{id}/reject")
    Object reject(
            @PathVariable long id,
            @Valid @ModelAttribute("rejectForm") RegistrationDecisionForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (form.getReason() == null || form.getReason().strip().isEmpty()) {
            bindingResult.rejectValue(
                    "reason", "NotBlank", "Vui lòng nhập lý do từ chối.");
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            RegistrationDetailView registration = registrations.getForDecision(id);
            return renderDecision(
                    model,
                    registration,
                    decisionForm(registration.version()),
                    form);
        }
        decisions.reject(id, form.getVersion(), form.getReason());
        return registrationDecision(id);
    }

    private String renderForm(
            Model model,
            SubmissionWorkspace workspace,
            RegistrationForm form) {
        common(model);
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", form);
        }
        model.addAttribute("groupId", workspace.groupId());
        model.addAttribute("groupCode", groupCode(workspace.groupId()));
        model.addAttribute("period", new PeriodPageView(
                workspace.periodId(), workspace.periodName()));
        model.addAttribute("memberCount", workspace.memberCount());
        model.addAttribute("pendingInvitation", workspace.pendingInvitation());
        model.addAttribute("topics", workspace.topics().stream()
                .map(this::display)
                .toList());
        return "registrations/form";
    }

    private String renderDetail(
            Model model,
            RegistrationDetailView registration,
            RegistrationDecisionForm withdrawForm) {
        common(model);
        if (!model.containsAttribute("withdrawForm")) {
            withdrawForm.setVersion(registration.version());
            model.addAttribute("withdrawForm", withdrawForm);
        }
        addDetailModel(model, registration);
        return "registrations/detail";
    }

    private String renderDecision(
            Model model,
            RegistrationDetailView registration,
            RegistrationDecisionForm acceptForm,
            RegistrationDecisionForm rejectForm) {
        common(model);
        model.addAttribute("activeNav", "decisions");
        if (!model.containsAttribute("acceptForm")) {
            model.addAttribute("acceptForm", acceptForm);
        }
        if (!model.containsAttribute("rejectForm")) {
            model.addAttribute("rejectForm", rejectForm);
        }
        addDetailModel(model, registration);
        return "registrations/decision";
    }

    private void addDetailModel(Model model, RegistrationDetailView registration) {
        RegistrationPageView page = display(registration);
        model.addAttribute("registration", page);
        model.addAttribute("registrationId", page.id());
        model.addAttribute("registrationStatus", page.statusLabel());
        model.addAttribute("groupCode", groupCode(page.groupId()));
        model.addAttribute("topic", new TopicPageView(
                page.topicId(),
                page.topicTitle(),
                page.topicDepartment(),
                page.topicAuthor(),
                page.supervisors()));
        model.addAttribute("members", page.members());
    }

    private RegistrationPageView display(RegistrationDetailView registration) {
        return new RegistrationPageView(
                registration.id(),
                registration.periodId(),
                registration.periodName(),
                registration.groupId(),
                registration.topicId(),
                registration.topicTitle(),
                registration.topicAuthor(),
                registration.topicDepartment(),
                String.join(", ", registration.supervisors()),
                registration.members().stream().map(this::display).toList(),
                registration.status(),
                statusLabel(registration.status()),
                statusClass(registration.status()),
                registration.rejectionReason(),
                format(registration.submittedAt()),
                format(registration.decidedAt()),
                format(registration.withdrawnAt()),
                registration.version(),
                registration.canWithdraw(),
                registration.canDecide(),
                registration.deanViewer());
    }

    private TopicPageView display(TopicOption topic) {
        return new TopicPageView(
                topic.id(),
                topic.title(),
                topic.department(),
                null,
                String.join(", ", topic.supervisors()));
    }

    private MemberPageView display(RegistrationMemberView member) {
        return new MemberPageView(
                member.id(),
                member.fullName(),
                member.username(),
                member.leader() ? "Trưởng nhóm" : "Thành viên");
    }

    private void common(Model model) {
        model.addAttribute("activeNav", "registrations");
        model.addAttribute("pageSection", "Đăng ký đề tài");
    }

    private String format(Instant instant) {
        return instant == null
                ? null
                : DATE_TIME.format(projectTime.toBusinessTime(instant));
    }

    private static RegistrationDecisionForm decisionForm(long version) {
        RegistrationDecisionForm form = new RegistrationDecisionForm();
        form.setVersion(version);
        return form;
    }

    private static String groupCode(long groupId) {
        return "N" + String.format("%03d", groupId);
    }

    private static String statusLabel(RegistrationStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xét";
            case ACCEPTED -> "Đã chấp nhận";
            case REJECTED -> "Đã từ chối";
            case WITHDRAWN -> "Đã rút";
        };
    }

    private static String statusClass(RegistrationStatus status) {
        return switch (status) {
            case PENDING -> "bg-yellow-lt";
            case ACCEPTED -> "bg-green-lt";
            case REJECTED -> "bg-red-lt";
            case WITHDRAWN -> "bg-secondary-lt";
        };
    }

    private static RedirectView registrationDetail(long registrationId) {
        return WebRedirects.seeOther("/registrations/" + registrationId);
    }

    private static RedirectView registrationDecision(long registrationId) {
        return WebRedirects.seeOther("/registrations/" + registrationId + "/decision");
    }

    public record PeriodPageView(long id, String name) {
    }

    public record TopicPageView(
            long id,
            String title,
            String department,
            String author,
            String supervisors) {
    }

    public record MemberPageView(
            long id,
            String name,
            String code,
            String role) {
    }

    public record RegistrationPageView(
            long id,
            long periodId,
            String periodName,
            long groupId,
            long topicId,
            String topicTitle,
            String topicAuthor,
            String topicDepartment,
            String supervisors,
            List<MemberPageView> members,
            RegistrationStatus status,
            String statusLabel,
            String statusClass,
            String rejectionReason,
            String submittedAt,
            String decidedAt,
            String withdrawnAt,
            long version,
            boolean canWithdraw,
            boolean canDecide,
            boolean deanViewer) {
        public RegistrationPageView {
            members = List.copyOf(members);
        }
    }
}
