package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.TopicAuthorService;
import vn.edu.topicregistration.dto.TopicDraftForm;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.service.TopicAuthorService.TopicFormContext;
import vn.edu.topicregistration.service.TopicAuthorService.TopicView;
import vn.edu.topicregistration.service.TopicAuthorService.TopicWorkspace;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

@Controller
@Profile("!test")
public class TopicAuthorController {
    private final TopicAuthorService topics;

    public TopicAuthorController(TopicAuthorService topics) {
        this.topics = topics;
    }

    @InitBinder("form")
    void whitelistTopicFields(WebDataBinder binder) {
        binder.setAllowedFields(
                "title",
                "description",
                "objectives",
                "studentRequirements",
                "expectedResults",
                "coSupervisorIds",
                "version");
    }

    @GetMapping("/topics")
    String periods(Model model) {
        common(model);
        var periods = topics.listPeriodsForAuthor();
        model.addAttribute("activePeriods", periods.stream()
                .filter(TopicAuthorService.TopicPeriodView::active)
                .toList());
        model.addAttribute("expiredPeriods", periods.stream()
                .filter(period -> !period.active())
                .toList());
        return "topics/period-selection";
    }

    @GetMapping("/periods/{periodId}/topics")
    String list(@PathVariable long periodId, Model model) {
        TopicWorkspace workspace = topics.listOwnForPeriod(periodId);
        common(model);
        model.addAttribute("period", workspace.period());
        model.addAttribute("topics", workspace.topics().stream().map(TopicPageView::from).toList());
        return "topics/list";
    }

    @GetMapping("/periods/{periodId}/topics/new")
    String createForm(@PathVariable long periodId, Model model) {
        TopicFormContext context = topics.createFormContext(periodId);
        return renderForm(model, new TopicDraftForm(), context, null, false);
    }

    @PostMapping("/periods/{periodId}/topics")
    Object create(
            @PathVariable long periodId,
            @Valid @ModelAttribute("form") TopicDraftForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        TopicFormContext context = topics.createFormContext(periodId);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, context, null, false);
        }
        try {
            TopicView topic = topics.createDraft(periodId, form.toCommand());
            return redirectTo(topic.id());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            bindingResult.reject("topic.invalid",
                    "Bản nháp có giảng viên hướng dẫn không hợp lệ hoặc không cùng Bộ môn.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, context, null, false);
        }
    }

    @GetMapping("/topics/{id}")
    String detail(@PathVariable long id, Model model) {
        TopicView topic = topics.getOwn(id);
        common(model);
        model.addAttribute("topic", TopicPageView.from(topic));
        model.addAttribute("period", topics.formContext(topic.periodId()).period());
        return "topics/detail";
    }

    @GetMapping("/topics/{id}/edit")
    String editForm(@PathVariable long id, Model model) {
        TopicView topic = topics.getOwnDraft(id);
        TopicFormContext context = topics.formContext(topic.periodId());
        return renderForm(model, TopicDraftForm.from(topic), context, TopicPageView.from(topic), true);
    }

    @PostMapping("/topics/{id}/draft")
    Object saveDraft(
            @PathVariable long id,
            @Valid @ModelAttribute("form") TopicDraftForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        TopicView current = topics.getOwnDraft(id);
        TopicFormContext context = topics.formContext(current.periodId());
        if (form.getVersion() == null) {
            bindingResult.rejectValue("version", "topic.version.required",
                    "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.");
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, context, TopicPageView.from(current), true);
        }
        try {
            TopicView saved = topics.saveDraft(id, form.getVersion(), form.toCommand());
            return redirectTo(saved.id());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            bindingResult.reject("topic.invalid",
                    "Bản nháp có giảng viên hướng dẫn không hợp lệ hoặc không cùng Bộ môn.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, context, TopicPageView.from(current), true);
        }
    }

    @PostMapping("/topics/{id}/submit")
    RedirectView submit(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        TopicView submitted = topics.submit(id, version);
        return redirectTo(submitted.id());
    }

    private String renderForm(
            Model model,
            TopicDraftForm form,
            TopicFormContext context,
            TopicPageView topic,
            boolean editing) {
        common(model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", form);
        }
        model.addAttribute("editing", editing);
        model.addAttribute("period", context.period());
        model.addAttribute("facultyName", context.facultyName());
        model.addAttribute("primarySupervisor", context.primarySupervisor());
        model.addAttribute("coSupervisors", context.coSupervisors());
        model.addAttribute("topic", topic);
        return "topics/form";
    }

    private void common(Model model) {
        model.addAttribute("activeNav", "topics");
        model.addAttribute("pageSection", "Đề tài");
    }

    private RedirectView redirectTo(long topicId) {
        return WebRedirects.seeOther("/topics/" + topicId);
    }

    public record TopicPageView(
            Long id,
            Long periodId,
            String faculty,
            String department,
            String author,
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            String status,
            String statusClass,
            long version,
            List<String> supervisors,
            boolean draft) {
        static TopicPageView from(TopicView topic) {
            return new TopicPageView(
                    topic.id(),
                    topic.periodId(),
                    topic.facultyName(),
                    topic.departmentName(),
                    topic.authorName(),
                    topic.title(),
                    topic.description(),
                    topic.objectives(),
                    topic.studentRequirements(),
                    topic.expectedResults(),
                    statusLabel(topic.status()),
                    statusClass(topic.status()),
                    topic.version(),
                    topic.supervisors().stream().map(supervisor -> supervisor.fullName()).toList(),
                    topic.status() == TopicStatus.DRAFT);
        }

        public String supervisorNames() {
            return supervisors.isEmpty() ? "Chưa chọn" : String.join(", ", supervisors);
        }

        private static String statusLabel(TopicStatus status) {
            return switch (status) {
                case DRAFT -> "Nháp";
                case PENDING_APPROVAL -> "Chờ duyệt";
                case APPROVED -> "Đã duyệt";
                case PUBLISHED -> "Đã công bố";
            };
        }

        private static String statusClass(TopicStatus status) {
            return switch (status) {
                case DRAFT -> "bg-secondary-lt";
                case PENDING_APPROVAL -> "bg-yellow-lt";
                case APPROVED -> "bg-azure-lt";
                case PUBLISHED -> "bg-green-lt";
            };
        }
    }
}
