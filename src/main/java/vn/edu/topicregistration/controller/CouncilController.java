package vn.edu.topicregistration.controller;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

import vn.edu.topicregistration.dto.CouncilAssignmentForm;
import vn.edu.topicregistration.dto.CouncilForm;
import vn.edu.topicregistration.dto.CouncilMemberForm;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.CouncilService;
import vn.edu.topicregistration.service.CouncilService.CouncilAssignmentView;
import vn.edu.topicregistration.service.CouncilService.CouncilDetailView;
import vn.edu.topicregistration.service.CouncilService.CouncilSummaryView;
import vn.edu.topicregistration.service.CouncilService.CreationWorkspace;
import vn.edu.topicregistration.util.ProjectTime;

@Controller
@Profile("!test")
public class CouncilController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter SCHEDULE_INPUT = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm");

    private final CouncilService councils;
    private final ProjectTime projectTime;

    public CouncilController(CouncilService councils, ProjectTime projectTime) {
        this.councils = councils;
        this.projectTime = projectTime;
    }

    @InitBinder("councilForm")
    void whitelistCouncilFields(WebDataBinder binder) {
        binder.setAllowedFields("periodId", "name", "chairId", "secretaryId", "memberIds");
    }

    @InitBinder("assignmentForm")
    void whitelistAssignmentFields(WebDataBinder binder) {
        binder.setAllowedFields("registrationId", "scheduledAt", "location", "version");
    }

    @InitBinder("memberForm")
    void whitelistMemberFields(WebDataBinder binder) {
        binder.setAllowedFields("chairId", "secretaryId", "memberIds", "version");
    }

    @GetMapping("/faculty/boards")
    public String list(Model model) {
        common(model);
        model.addAttribute("boards", councils.listForCurrentDean().stream()
                .map(CouncilController::display)
                .toList());
        model.addAttribute("creationOptions", councils.creationOptionsForCurrentDean());
        return "councils/list";
    }

    @GetMapping("/faculty/boards/new")
    public String createForm(
            @RequestParam long periodId,
            Model model) {
        CouncilForm form = new CouncilForm();
        form.setPeriodId(periodId);
        return renderCreate(model, form, councils.creationWorkspace(periodId));
    }

    @PostMapping("/faculty/boards")
    public Object create(
            @Valid @ModelAttribute("councilForm") CouncilForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCreate(model, form, creationWorkspace(form));
        }
        try {
            CouncilSummaryView created = councils.create(form.toCommand());
            return WebRedirects.seeOther("/faculty/boards/" + created.id());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.INVALID_COUNCIL_MEMBERS
                    && exception.code() != ApplicationErrorCode.INVALID_COUNCIL_STATE
                    && exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            bindingResult.reject("council.invalid",
                    "Hội đồng phải có 3–5 giảng viên cùng khoa, Chủ tịch và Thư ký khác nhau.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCreate(model, form, creationWorkspace(form));
        }
    }

    @GetMapping("/faculty/boards/{id}")
    public String detail(@PathVariable long id, Model model) {
        CouncilDetailView detail = councils.detailForCurrentDean(id);
        CouncilAssignmentForm form = new CouncilAssignmentForm();
        form.setVersion(detail.council().version());
        return renderDetail(model, detail, form, memberForm(detail));
    }

    @PostMapping("/faculty/boards/{id}/members")
    public Object updateMembers(
            @PathVariable long id,
            @Valid @ModelAttribute("memberForm") CouncilMemberForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            CouncilDetailView detail = councils.detailForCurrentDean(id);
            return renderDetail(model, detail, assignmentForm(detail), form);
        }
        try {
            councils.updateMembers(id, form.toCommand());
            return WebRedirects.seeOther("/faculty/boards/" + id);
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.INVALID_COUNCIL_MEMBERS
                    && exception.code() != ApplicationErrorCode.INVALID_COUNCIL_STATE) {
                throw exception;
            }
            bindingResult.reject("members.invalid",
                    "Chỉ sửa hội đồng nháp/chờ phân công chưa có điểm; chọn 3–5 giảng viên hợp lệ, không trùng GVPB hoặc GVHD của các đề tài.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            CouncilDetailView detail = councils.detailForCurrentDean(id);
            return renderDetail(model, detail, assignmentForm(detail), form);
        }
    }

    @PostMapping("/faculty/boards/{id}/assignments")
    public Object saveAssignment(
            @PathVariable long id,
            @Valid @ModelAttribute("assignmentForm") CouncilAssignmentForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            CouncilDetailView detail = councils.detailForCurrentDean(id);
            return renderDetail(model, detail, form, memberForm(detail));
        }
        try {
            councils.saveAssignment(id, form.toCommand(projectTime));
            return WebRedirects.seeOther("/faculty/boards/" + id);
        } catch (CouncilService.InvalidScheduleException exception) {
            bindingResult.rejectValue("scheduledAt", "assignment.schedule.past",
                    "Lịch bảo vệ phải sau thời điểm hiện tại.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            CouncilDetailView detail = councils.detailForCurrentDean(id);
            return renderDetail(model, detail, form, memberForm(detail));
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT
                    && exception.code() != ApplicationErrorCode.INVALID_COUNCIL_STATE) {
                throw exception;
            }
            bindingResult.reject("assignment.invalid",
                    "Đăng ký phải cùng đợt và khoa, đã được chấp nhận và không xung đột giảng viên hướng dẫn.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            CouncilDetailView detail = councils.detailForCurrentDean(id);
            return renderDetail(model, detail, form, memberForm(detail));
        }
    }

    @PostMapping("/faculty/boards/{id}/assign")
    public Object assign(
            @PathVariable long id,
            @RequestParam(required = false) Long version) {
        return transition(id, version, CouncilStatus.ASSIGNED);
    }

    @PostMapping("/faculty/boards/{id}/schedule")
    public Object schedule(
            @PathVariable long id,
            @RequestParam(required = false) Long version) {
        return transition(id, version, CouncilStatus.SCHEDULED);
    }

    @PostMapping("/faculty/boards/{id}/activate")
    public Object activate(
            @PathVariable long id,
            @RequestParam(required = false) Long version) {
        return transition(id, version, CouncilStatus.ACTIVE);
    }

    private Object transition(long id, Long version, CouncilStatus target) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        councils.transition(id, version, target);
        return WebRedirects.seeOther("/faculty/boards/" + id);
    }

    private String renderCreate(
            Model model,
            CouncilForm form,
            CreationWorkspace workspace) {
        common(model);
        if (!model.containsAttribute("councilForm")) {
            model.addAttribute("councilForm", form);
        }
        model.addAttribute("workspace", workspace);
        model.addAttribute("lecturers", workspace.lecturers());
        return "councils/form";
    }

    private String renderDetail(
            Model model,
            CouncilDetailView detail,
            CouncilAssignmentForm form,
            CouncilMemberForm memberForm) {
        common(model);
        if (!model.containsAttribute("assignmentForm")) {
            model.addAttribute("assignmentForm", form);
        }
        if (!model.containsAttribute("memberForm")) {
            model.addAttribute("memberForm", memberForm);
        }
        model.addAttribute("board", display(detail.council()));
        model.addAttribute("members", detail.members());
        model.addAttribute("assignments", detail.assignments().stream()
                .map(this::display)
                .toList());
        model.addAttribute("progress", detail.progress().stream()
                .map(topic -> new TopicProgressPageView(topic.registrationId(),
                        topic.topicTitle(),
                        topic.members().stream()
                                .map(member -> new MemberProgressPageView(
                                        member.memberName(), member.role(), member.status(),
                                        member.submittedCount(), member.totalCount(),
                                        format(member.submittedAt())))
                                .toList()))
                .toList());
        model.addAttribute("availableRegistrations", detail.availableRegistrations());
        model.addAttribute("minScheduleAt", projectTime.toBusinessTime(projectTime.now())
                .truncatedTo(ChronoUnit.MINUTES).plusMinutes(1).format(SCHEDULE_INPUT));
        model.addAttribute("canEditAssignments",
                detail.canPublish()
                        && (detail.council().status() == CouncilStatus.DRAFT
                            || detail.council().status() == CouncilStatus.ASSIGNED));
        model.addAttribute("canViewFacultyResults", detail.canPublish());
        model.addAttribute("canEditMembers", detail.canEditMembers());
        model.addAttribute("memberCandidates", detail.canEditMembers()
                ? councils.creationWorkspace(detail.council().periodId()).lecturers()
                : List.of());
        model.addAttribute("nextAction", nextAction(detail));
        return "councils/detail";
    }

    public record TopicProgressPageView(
            Long registrationId, String topicTitle,
            List<MemberProgressPageView> members) {
    }

    public record MemberProgressPageView(
            String memberName, String role, String status,
            int submittedCount, int totalCount, String submittedAt) {
    }

    private static CouncilAssignmentForm assignmentForm(CouncilDetailView detail) {
        CouncilAssignmentForm form = new CouncilAssignmentForm();
        form.setVersion(detail.council().version());
        return form;
    }

    private static CouncilMemberForm memberForm(CouncilDetailView detail) {
        CouncilMemberForm form = new CouncilMemberForm();
        form.setVersion(detail.council().version());
        form.setChairId(detail.chairId());
        form.setSecretaryId(detail.secretaryId());
        form.setMemberIds(detail.members().stream()
                .filter(member -> !member.id().equals(detail.chairId())
                        && !member.id().equals(detail.secretaryId()))
                .map(CouncilService.CouncilMemberView::id)
                .toList());
        return form;
    }

    private CreationWorkspace creationWorkspace(CouncilForm form) {
        if (form.getPeriodId() == null || form.getPeriodId() <= 0) {
            return new CreationWorkspace(0L, "", 0L, "", List.of());
        }
        return councils.creationWorkspace(form.getPeriodId());
    }

    private CouncilAssignmentPageView display(CouncilAssignmentView assignment) {
        return new CouncilAssignmentPageView(
                assignment.registrationId(),
                assignment.groupId(),
                assignment.topicTitle(),
                format(assignment.scheduledAt()),
                assignment.location());
    }

    private static CouncilPageView display(CouncilSummaryView council) {
        return new CouncilPageView(
                council.id(),
                council.periodId(),
                council.periodName(),
                council.facultyId(),
                council.facultyName(),
                council.name(),
                council.status(),
                statusLabel(council.status()),
                statusClass(council.status()),
                council.version());
    }

    private String format(Instant value) {
        return value == null ? null : DATE_TIME.format(projectTime.toBusinessTime(value));
    }

    private static NextAction nextAction(CouncilDetailView detail) {
        return switch (detail.council().status()) {
            case DRAFT -> detail.canPublish()
                    ? new NextAction("assign", "Xác nhận phân công") : null;
            case ASSIGNED -> detail.canPublish()
                    ? new NextAction("schedule", "Xác nhận lịch bảo vệ") : null;
            case SCHEDULED -> detail.canPublish()
                    ? new NextAction("activate", "Kích hoạt hội đồng") : null;
            case ACTIVE -> detail.canComplete()
                    ? new NextAction("complete", "Tổng hợp kết quả") : null;
            case COMPLETED -> detail.canPublish()
                    ? new NextAction("publish", "Công bố kết quả") : null;
            case PUBLISHED -> null;
        };
    }

    private static String statusLabel(CouncilStatus status) {
        return switch (status) {
            case DRAFT -> "Nháp";
            case ASSIGNED -> "Đã phân công";
            case SCHEDULED -> "Đã xếp lịch";
            case ACTIVE -> "Đang chấm";
            case COMPLETED -> "Đã hoàn tất";
            case PUBLISHED -> "Đã công bố";
        };
    }

    private static String statusClass(CouncilStatus status) {
        return switch (status) {
            case DRAFT -> "bg-secondary-lt";
            case ASSIGNED -> "bg-blue-lt";
            case SCHEDULED -> "bg-azure-lt";
            case ACTIVE -> "bg-yellow-lt";
            case COMPLETED -> "bg-purple-lt";
            case PUBLISHED -> "bg-green-lt";
        };
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "boards");
        model.addAttribute("pageSection", "Hội đồng");
    }

    public record CouncilPageView(
            Long id,
            Long periodId,
            String periodName,
            Long facultyId,
            String facultyName,
            String name,
            CouncilStatus status,
            String statusLabel,
            String statusClass,
            long version) {
    }

    public record CouncilAssignmentPageView(
            Long registrationId,
            Long groupId,
            String topicTitle,
            String scheduledAt,
            String location) {
    }

    public record NextAction(String route, String label) {
    }
}
