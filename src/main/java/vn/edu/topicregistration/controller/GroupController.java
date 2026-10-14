package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.service.GroupService;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.dto.GroupMemberForm;

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
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.topicregistration.service.GroupService.GroupPeriodView;
import vn.edu.topicregistration.service.GroupService.GroupView;
import vn.edu.topicregistration.service.GroupService.GroupWorkspace;
import vn.edu.topicregistration.service.GroupService.InvitationView;
import vn.edu.topicregistration.service.GroupService.StudentPeriodPhase;

@Controller
@Profile("!test")
public class GroupController {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy", VIETNAMESE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", VIETNAMESE);

    private final GroupService groups;
    private final ProjectTime projectTime;

    public GroupController(GroupService groups, ProjectTime projectTime) {
        this.groups = groups;
        this.projectTime = projectTime;
    }

    @InitBinder({"invitationForm", "leadershipForm"})
    void whitelistMemberFields(WebDataBinder binder) {
        binder.setAllowedFields("studentId", "version");
    }

    @GetMapping("/groups")
    String periods(Model model) {
        common(model);
        List<GroupPeriodPageView> periodViews = groups.listPeriodsForCurrentStudent().stream()
                .map(this::display)
                .toList();
        model.addAttribute("upcomingPeriods", periodsInPhase(
                periodViews, StudentPeriodPhase.UPCOMING));
        model.addAttribute("openPeriods", periodsInPhase(
                periodViews, StudentPeriodPhase.OPEN));
        model.addAttribute("endedPeriods", periodsInPhase(
                periodViews, StudentPeriodPhase.ENDED));
        model.addAttribute("pendingInvitationCount", periodViews.stream()
                .filter(GroupPeriodPageView::hasPendingInvitation)
                .count());
        return "groups/list";
    }

    private static List<GroupPeriodPageView> periodsInPhase(
            List<GroupPeriodPageView> periods,
            StudentPeriodPhase phase) {
        return periods.stream()
                .filter(period -> period.phase() == phase)
                .toList();
    }

    @GetMapping("/group-invitations")
    String invitations(Model model) {
        common(model);
        model.addAttribute("activeNav", "group-invitations");
        model.addAttribute("invitations", groups.listInvitationsForCurrentStudent().stream()
                .map(this::display)
                .toList());
        return "groups/invitations";
    }

    @GetMapping("/periods/{periodId}/groups")
    Object workspace(@PathVariable long periodId, Model model) {
        GroupWorkspace workspace = groups.workspace(periodId);
        if (workspace.group().isEmpty()) {
            return WebRedirects.seeOther("/periods/" + periodId + "/groups/new");
        }
        return renderDetail(
                model, workspace, new GroupMemberForm(), new GroupMemberForm());
    }

    @GetMapping("/periods/{periodId}/groups/new")
    Object createForm(@PathVariable long periodId, Model model) {
        GroupWorkspace workspace = groups.workspace(periodId);
        if (workspace.group().isPresent()) {
            return redirectToGroup(workspace.group().orElseThrow().id());
        }
        common(model);
        model.addAttribute("period", display(workspace.period()));
        return "groups/form";
    }

    @PostMapping("/periods/{periodId}/groups")
    RedirectView create(@PathVariable long periodId) {
        return redirectToGroup(groups.create(periodId).id());
    }

    @GetMapping("/groups/{id}")
    String detail(@PathVariable long id, Model model) {
        GroupView group = groups.getVisible(id);
        return renderDetail(
                model, workspaceFor(group), new GroupMemberForm(), new GroupMemberForm());
    }

    @PostMapping("/groups/{id}/invitations")
    Object invite(
            @PathVariable long id,
            @Valid @ModelAttribute("invitationForm") GroupMemberForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCurrentDetail(id, model, form, new GroupMemberForm());
        }
        groups.inviteMember(id, form.getVersion(), form.getStudentId());
        return redirectToGroup(id);
    }

    @PostMapping("/group-invitations/{id}/accept")
    RedirectView acceptInvitation(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version,
            RedirectAttributes redirectAttributes) {
        long groupId = groups.acceptInvitation(id, requiredVersion(version));
        redirectAttributes.addFlashAttribute(
                "invitationResult",
                "Bạn đã chấp nhận lời mời và trở thành thành viên của nhóm N"
                        + String.format("%03d", groupId) + ".");
        return redirectToGroup(groupId);
    }

    @PostMapping("/group-invitations/{id}/reject")
    RedirectView rejectInvitation(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version,
            RedirectAttributes redirectAttributes) {
        groups.rejectInvitation(id, requiredVersion(version));
        redirectAttributes.addFlashAttribute(
                "invitationResult",
                "Bạn đã từ chối lời mời. Bạn không được thêm vào nhóm.");
        return WebRedirects.seeOther("/group-invitations");
    }

    @PostMapping("/group-invitations/{id}/cancel")
    RedirectView cancelInvitation(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version) {
        return redirectToGroup(groups.cancelInvitation(id, requiredVersion(version)));
    }

    @PostMapping("/groups/{id}/members/{studentId}/remove")
    RedirectView removeMember(
            @PathVariable long id,
            @PathVariable long studentId,
            @RequestParam(name = "version", required = false) Long version) {
        groups.removeMember(id, requiredVersion(version), studentId);
        return redirectToGroup(id);
    }

    @PostMapping("/groups/{id}/leader")
    Object transferLeadership(
            @PathVariable long id,
            @Valid @ModelAttribute("leadershipForm") GroupMemberForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCurrentDetail(id, model, new GroupMemberForm(), form);
        }
        groups.transferLeadership(id, form.getVersion(), form.getStudentId());
        return redirectToGroup(id);
    }

    @PostMapping("/groups/{id}/leave")
    RedirectView leave(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version) {
        groups.leave(id, requiredVersion(version));
        return WebRedirects.seeOther("/groups");
    }

    @PostMapping("/groups/{id}/dissolve")
    RedirectView dissolve(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version) {
        groups.dissolve(id, requiredVersion(version));
        return WebRedirects.seeOther("/groups");
    }

    private String renderCurrentDetail(
            long groupId,
            Model model,
            GroupMemberForm invitationForm,
            GroupMemberForm leadershipForm) {
        GroupView group = groups.getVisible(groupId);
        return renderDetail(model, workspaceFor(group), invitationForm, leadershipForm);
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
            GroupMemberForm invitationForm,
            GroupMemberForm leadershipForm) {
        common(model);
        GroupView group = workspace.group()
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!model.containsAttribute("invitationForm")) {
            invitationForm.setVersion(group.version());
            model.addAttribute("invitationForm", invitationForm);
        }
        if (!model.containsAttribute("leadershipForm")) {
            leadershipForm.setVersion(group.version());
            model.addAttribute("leadershipForm", leadershipForm);
        }
        model.addAttribute("period", display(workspace.period()));
        model.addAttribute("group", group);
        model.addAttribute("groupCode", "N" + String.format("%03d", group.id()));
        model.addAttribute("candidates", workspace.candidates());
        model.addAttribute("invitations", workspace.invitations().stream()
                .map(this::display)
                .toList());
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
                period.departmentName(),
                DATE.format(projectTime.toBusinessDate(period.studentStart())),
                DATE.format(projectTime.toBusinessDate(period.studentEnd())),
                period.phase(),
                period.hasGroup(),
                period.hasPendingInvitation());
    }

    private InvitationPageView display(InvitationView invitation) {
        return new InvitationPageView(
                invitation.id(),
                invitation.groupId(),
                invitation.periodId(),
                invitation.periodName(),
                invitation.studentName(),
                invitation.studentUsername(),
                invitation.invitedByName(),
                invitation.slot(),
                statusLabel(invitation.status()),
                statusClass(invitation.status()),
                format(invitation.createdAt()),
                invitation.respondedAt() == null ? null : format(invitation.respondedAt()),
                invitation.version(),
                invitation.groupActive(),
                invitation.canViewGroup(),
                invitation.canAccept(),
                invitation.canReject(),
                invitation.canCancel());
    }

    private String format(Instant instant) {
        return DATE_TIME.format(projectTime.toBusinessTime(instant));
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Đang chờ phản hồi";
            case "ACCEPTED" -> "Đã chấp nhận";
            case "REJECTED" -> "Đã từ chối";
            case "CANCELLED" -> "Đã hủy";
            case "EXPIRED" -> "Đã hết hiệu lực";
            default -> status;
        };
    }

    private static String statusClass(String status) {
        return switch (status) {
            case "PENDING" -> "bg-yellow-lt";
            case "ACCEPTED" -> "bg-green-lt";
            case "REJECTED" -> "bg-red-lt";
            case "EXPIRED", "CANCELLED" -> "bg-secondary-lt";
            default -> "bg-secondary-lt";
        };
    }

    private static long requiredVersion(Long version) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return version;
    }

    private static RedirectView redirectToGroup(long groupId) {
        return WebRedirects.seeOther("/groups/" + groupId);
    }

    public record GroupPeriodPageView(
            long id,
            String name,
            String departmentName,
            String studentStart,
            String studentEnd,
            StudentPeriodPhase phase,
            boolean hasGroup,
            boolean hasPendingInvitation) {
        public String studentWindow() {
            return studentStart + " – " + studentEnd;
        }

        public String status() {
            return switch (phase) {
                case UPCOMING -> "Sắp đăng ký";
                case OPEN -> "Đang đăng ký";
                case ENDED -> "Đã kết thúc";
            };
        }

        public String statusClass() {
            return switch (phase) {
                case UPCOMING -> "bg-azure-lt";
                case OPEN -> "bg-green-lt";
                case ENDED -> "bg-secondary-lt";
            };
        }

        public boolean studentOpen() {
            return phase == StudentPeriodPhase.OPEN;
        }

        public boolean actionDisabled() {
            return !hasGroup && !hasPendingInvitation && !studentOpen();
        }

        public String actionLabel() {
            if (hasGroup) {
                return "Xem nhóm của tôi";
            }
            if (hasPendingInvitation) {
                return "Phản hồi lời mời";
            }
            return switch (phase) {
                case UPCOMING -> "Chưa đến thời gian";
                case OPEN -> "Tạo nhóm";
                case ENDED -> "Đã kết thúc";
            };
        }
    }

    public record InvitationPageView(
            long id,
            long groupId,
            long periodId,
            String periodName,
            String studentName,
            String studentUsername,
            String invitedByName,
            int slot,
            String status,
            String statusClass,
            String createdAt,
            String respondedAt,
            long version,
            boolean groupActive,
            boolean canViewGroup,
            boolean canAccept,
            boolean canReject,
            boolean canCancel) {
    }
}
