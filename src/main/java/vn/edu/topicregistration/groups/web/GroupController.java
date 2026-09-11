package vn.edu.topicregistration.groups.web;

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
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.groups.application.GroupService;
import vn.edu.topicregistration.groups.application.GroupService.GroupPeriodView;
import vn.edu.topicregistration.groups.application.GroupService.GroupView;
import vn.edu.topicregistration.groups.application.GroupService.GroupWorkspace;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.time.ProjectTime;
import vn.edu.topicregistration.shared.web.WebRedirects;

@Controller
@Profile("!preview & !test")
public class GroupController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

    private final GroupService groups;
    private final ProjectTime projectTime;

    public GroupController(GroupService groups, ProjectTime projectTime) {
        this.groups = groups;
        this.projectTime = projectTime;
    }

    @InitBinder("form")
    void whitelistMemberFields(WebDataBinder binder) {
        binder.setAllowedFields("studentId", "version");
    }

    @GetMapping("/groups")
    String periods(Model model) {
        common(model);
        model.addAttribute("periods", groups.listPeriodsForCurrentStudent().stream()
                .map(this::display)
                .toList());
        return "groups/list";
    }

    @GetMapping("/periods/{periodId}/groups")
    Object workspace(@PathVariable long periodId, Model model) {
        GroupWorkspace workspace = groups.workspace(periodId);
        if (workspace.group().isEmpty()) {
            return WebRedirects.seeOther("/periods/" + periodId + "/groups/new");
        }
        return renderDetail(model, workspace, new GroupMemberForm());
    }

    @GetMapping("/periods/{periodId}/groups/new")
    Object createForm(@PathVariable long periodId, Model model) {
        GroupWorkspace workspace = groups.workspace(periodId);
        if (workspace.group().isPresent()) {
            return redirectTo(workspace.group().orElseThrow().id());
        }
        common(model);
        model.addAttribute("period", display(workspace.period()));
        return "groups/form";
    }

    @PostMapping("/periods/{periodId}/groups")
    RedirectView create(@PathVariable long periodId) {
        return redirectTo(groups.create(periodId).id());
    }

    @GetMapping("/groups/{id}")
    String detail(@PathVariable long id, Model model) {
        GroupView group = groups.getVisible(id);
        return renderDetail(model, workspaceFor(group), new GroupMemberForm());
    }

    @PostMapping("/groups/{id}/members")
    Object addMember(
            @PathVariable long id,
            @Valid @ModelAttribute("form") GroupMemberForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCurrentDetail(id, model, form);
        }
        groups.addMember(id, form.getVersion(), form.getStudentId());
        return redirectTo(id);
    }

    @PostMapping("/groups/{id}/members/{studentId}/remove")
    RedirectView removeMember(
            @PathVariable long id,
            @PathVariable long studentId,
            @RequestParam(name = "version", required = false) Long version) {
        groups.removeMember(id, requiredVersion(version), studentId);
        return redirectTo(id);
    }

    @PostMapping("/groups/{id}/leader")
    Object transferLeadership(
            @PathVariable long id,
            @Valid @ModelAttribute("form") GroupMemberForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCurrentDetail(id, model, form);
        }
        groups.transferLeadership(id, form.getVersion(), form.getStudentId());
        return redirectTo(id);
    }

    private String renderCurrentDetail(long groupId, Model model, GroupMemberForm form) {
        GroupView group = groups.getVisible(groupId);
        return renderDetail(model, workspaceFor(group), form);
    }

    private GroupWorkspace workspaceFor(GroupView group) {
        GroupWorkspace workspace = groups.workspace(group.periodId());
        if (workspace.group().isEmpty()
                || workspace.group().orElseThrow().id() != group.id()) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return workspace;
    }

    private String renderDetail(
            Model model,
            GroupWorkspace workspace,
            GroupMemberForm form) {
        common(model);
        GroupView group = workspace.group()
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!model.containsAttribute("form")) {
            form.setVersion(group.version());
            model.addAttribute("form", form);
        }
        model.addAttribute("period", display(workspace.period()));
        model.addAttribute("group", group);
        model.addAttribute("groupCode", "N" + String.format("%03d", group.id()));
        model.addAttribute("candidates", workspace.candidates());
        return "groups/detail";
    }

    private void common(Model model) {
        model.addAttribute("activeNav", "groups");
        model.addAttribute("pageSection", "Nhóm sinh viên");
    }

    private GroupPeriodPageView display(GroupPeriodView period) {
        return new GroupPeriodPageView(
                period.id(),
                period.name(),
                DATE.format(projectTime.toBusinessDate(period.studentStart())),
                DATE.format(projectTime.toBusinessDate(period.studentEnd())),
                period.studentOpen(),
                period.hasGroup());
    }

    private static long requiredVersion(Long version) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return version;
    }

    private static RedirectView redirectTo(long groupId) {
        return WebRedirects.seeOther("/groups/" + groupId);
    }

    public record GroupPeriodPageView(
            long id,
            String name,
            String studentStart,
            String studentEnd,
            boolean studentOpen,
            boolean hasGroup) {
        public String studentWindow() {
            return studentStart + " – " + studentEnd;
        }

        public String status() {
            return studentOpen ? "Đang mở đăng ký" : "Ngoài thời gian đăng ký";
        }

        public String statusClass() {
            return studentOpen ? "bg-green-lt" : "bg-secondary-lt";
        }
    }
}
