package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.service.PeriodService;
import vn.edu.topicregistration.service.RegistrationService;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.service.TopicReviewService;
import vn.edu.topicregistration.dto.PeriodForm;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.service.PeriodService.PeriodView;
import vn.edu.topicregistration.service.PeriodRules.RegistrationState;
import vn.edu.topicregistration.service.PeriodRules.TopicType;
import vn.edu.topicregistration.service.RegistrationService.RegistrationSummaryView;
import vn.edu.topicregistration.service.TopicReviewService.ReviewTopicView;
import vn.edu.topicregistration.service.TopicReviewService.ReviewWorkspace;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

@Controller
@Profile("!test")
@RequestMapping("/periods")
public class PeriodController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));

    private final PeriodService periodService;
    private final TopicReviewService topicReviewService;
    private final RegistrationService registrationService;
    private final AuthorizationPolicy authorization;
    private final ProjectTime projectTime;

    public PeriodController(
            PeriodService periodService,
            TopicReviewService topicReviewService,
            RegistrationService registrationService,
            AuthorizationPolicy authorization,
            ProjectTime projectTime) {
        this.periodService = periodService;
        this.topicReviewService = topicReviewService;
        this.registrationService = registrationService;
        this.authorization = authorization;
        this.projectTime = projectTime;
    }

    @GetMapping
    String list(Model model) {
        common(model);
        List<PeriodPageView> periods = periodService.listForCurrentDean().stream()
                .map(this::displayWithStatistics)
                .toList();
        model.addAttribute("periods", periods);
        return "periods/list";
    }

    @GetMapping("/new")
    String createForm(Model model) {
        authorization.requireRole(RoleCode.DEAN);
        return renderForm(model, new PeriodForm(), false, null, FieldLocks.unlocked());
    }

    @PostMapping
    Object create(
            @Valid @ModelAttribute("form") PeriodForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        authorization.requireRole(RoleCode.DEAN);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, false, null, FieldLocks.unlocked());
        }
        try {
            PeriodView created = periodService.create(form.toConfiguration(projectTime));
            return redirectTo(created.id());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            bindingResult.reject("period.invalid",
                    "Lịch chưa hợp lệ: không chọn ngày trước hôm nay, ngày sinh viên phải sau ngày kết thúc của giảng viên và các ngày phải đúng với loại đề tài.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, false, null, FieldLocks.unlocked());
        }
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model) {
        common(model);
        PeriodView period = periodService.getForCurrentDean(id);
        ReviewSummary summary = reviewSummary(topicReviewService.reviewWorkspace(id));
        model.addAttribute("period", display(period));
        model.addAttribute("reviewSummary", summary);
        List<RegistrationRequestPageView> registrationRequests = registrationService
                .listForDeanPeriod(id).stream()
                .map(this::display)
                .toList();
        model.addAttribute("registrationRequests", registrationRequests);
        model.addAttribute("pendingRegistrationCount", registrationRequests.stream()
                .filter(RegistrationRequestPageView::pending)
                .count());
        return "periods/detail";
    }

    @GetMapping("/{id}/topics/review")
    String reviewTopics(@PathVariable long id, Model model) {
        common(model);
        PeriodView period = periodService.getForCurrentDean(id);
        ReviewWorkspace workspace = topicReviewService.reviewWorkspace(id);
        ReviewSummary summary = reviewSummary(workspace);
        model.addAttribute("period", display(period));
        model.addAttribute("reviewTopics", workspace.topics().stream()
                .map(ReviewTopicPageView::from)
                .toList());
        model.addAttribute("reviewSummary", summary);
        model.addAttribute("reviewNotice", reviewNotice(period, summary));
        return "topics/review";
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable long id, Model model) {
        PeriodView period = periodService.getForCurrentDean(id);
        return renderForm(
                model,
                PeriodForm.from(period, projectTime),
                true,
                display(period),
                locksFor(period));
    }

    @PostMapping("/{id}")
    Object update(
            @PathVariable long id,
            @Valid @ModelAttribute("form") PeriodForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        authorization.requireRole(RoleCode.DEAN);
        if (form.getVersion() == null) {
            bindingResult.rejectValue("version", "period.version.required",
                    "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.");
        }
        if (bindingResult.hasErrors()) {
            return renderInvalidEdit(id, form, bindingResult, response, model);
        }
        try {
            PeriodView updated = periodService.update(
                    id, form.getVersion(), form.toConfiguration(projectTime));
            return redirectTo(updated.id());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            bindingResult.reject("period.invalid",
                    "Chỉ được kéo dài mốc còn hiệu lực; thời gian giảng viên phải kết thúc trước khi thời gian sinh viên bắt đầu.");
            return renderInvalidEdit(id, form, bindingResult, response, model);
        }
    }

    private String renderInvalidEdit(
            long id,
            PeriodForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
        PeriodView current = periodService.getForCurrentDean(id);
        return renderForm(model, form, true, display(current), locksFor(current));
    }

    private String renderForm(
            Model model,
            PeriodForm form,
            boolean editing,
            PeriodPageView period,
            FieldLocks locks) {
        common(model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", form);
        }
        model.addAttribute("editing", editing);
        model.addAttribute("period", period);
        model.addAttribute("locks", locks);
        return "periods/form";
    }

    private void common(Model model) {
        model.addAttribute("activeNav", "periods");
        model.addAttribute("pageSection", "Quản lý học vụ");
        model.addAttribute("today", projectTime.today());
    }

    private RedirectView redirectTo(long id) {
        return WebRedirects.seeOther("/periods/" + id);
    }

    private FieldLocks locksFor(PeriodView period) {
        Instant now = projectTime.now();
        LocalDate today = projectTime.toBusinessTime(now).toLocalDate();
        boolean base = !now.isBefore(period.lecturerStart());
        return new FieldLocks(
                base,
                now.isAfter(period.lecturerEnd()),
                now.isAfter(period.studentEnd()),
                period.reviewerScoreDeadline() == null
                        ? base
                        : !today.isBefore(period.reviewerScoreDeadline()),
                period.councilReportDate() == null
                        ? base
                        : !today.isBefore(period.councilReportDate()));
    }

    private PeriodPageView display(PeriodView period) {
        return display(period, 0, 0, 0);
    }

    private PeriodPageView displayWithStatistics(PeriodView period) {
        ReviewSummary topics = reviewSummary(topicReviewService.reviewWorkspace(period.id()));
        long pendingRegistrations = registrationService.listForDeanPeriod(period.id()).stream()
                .filter(registration -> registration.status() == RegistrationStatus.PENDING)
                .count();
        return display(
                period,
                topics.pendingCount(),
                pendingRegistrations,
                topics.approvedCount());
    }

    private PeriodPageView display(
            PeriodView period,
            long pendingTopicCount,
            long pendingRegistrationCount,
            long approvedTopicCount) {
        return new PeriodPageView(
                period.id(),
                period.name(),
                typeLabel(period.topicType()),
                format(period.lecturerStart()),
                format(period.lecturerEnd()),
                format(period.studentStart()),
                format(period.studentEnd()),
                period.reviewerScoreDeadline() == null
                        ? null : DATE.format(period.reviewerScoreDeadline()),
                period.councilReportDate() == null
                        ? null : DATE.format(period.councilReportDate()),
                stateLabel(period.state()),
                stateClass(period.state()),
                period.version(),
                pendingTopicCount,
                pendingRegistrationCount,
                approvedTopicCount);
    }

    private String format(Instant instant) {
        return DATE.format(projectTime.toBusinessDate(instant));
    }

    private RegistrationRequestPageView display(RegistrationSummaryView registration) {
        return new RegistrationRequestPageView(
                registration.id(),
                "N" + String.format("%03d", registration.groupId()),
                registration.leaderName(),
                registration.memberCount(),
                registration.topicTitle(),
                DATE_TIME.format(projectTime.toBusinessTime(registration.submittedAt())),
                registrationStatusLabel(registration.status()),
                registrationStatusClass(registration.status()),
                registration.rejectionReason(),
                registration.status() == RegistrationStatus.PENDING);
    }

    private static String registrationStatusLabel(RegistrationStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xét";
            case ACCEPTED -> "Đã chấp nhận";
            case REJECTED -> "Đã từ chối";
            case WITHDRAWN -> "Đã rút";
        };
    }

    private static String registrationStatusClass(RegistrationStatus status) {
        return switch (status) {
            case PENDING -> "bg-yellow-lt";
            case ACCEPTED -> "bg-green-lt";
            case REJECTED -> "bg-red-lt";
            case WITHDRAWN -> "bg-secondary-lt";
        };
    }

    private static ReviewSummary reviewSummary(ReviewWorkspace workspace) {
        return new ReviewSummary(
                workspace.pendingCount(),
                workspace.approvedCount(),
                workspace.publishedCount());
    }

    private ReviewNotice reviewNotice(PeriodView period, ReviewSummary summary) {
        String studentStart = format(period.studentStart());
        long unfinished = summary.pendingCount() + summary.approvedCount();
        boolean studentWindowStarted = !projectTime.now().isBefore(period.studentStart());
        if (studentWindowStarted && unfinished > 0) {
            return new ReviewNotice(
                    true,
                    "Đã đến thời gian sinh viên đăng ký",
                    "Còn " + unfinished
                            + " đề tài chưa hoàn tất duyệt hoặc công bố. Hãy xử lý ngay để sinh viên có danh sách đăng ký.");
        }
        return new ReviewNotice(
                false,
                "Hoàn tất trước ngày " + studentStart,
                "Trưởng khoa cần duyệt và công bố các đề tài trước khi sinh viên bắt đầu đăng ký.");
    }

    private static String typeLabel(TopicType type) {
        return switch (type) {
            case COURSE -> "Đồ án môn học";
            case NCKH -> "Nghiên cứu khoa học";
            case TLCN -> "Tiểu luận chuyên ngành";
            case KLTN -> "Khóa luận tốt nghiệp";
        };
    }

    private static String stateLabel(RegistrationState state) {
        return switch (state) {
            case BEFORE_LECTURER -> "Sắp nhận đề tài GV";
            case LECTURER_OPEN -> "Đang nhận đề tài GV";
            case BETWEEN_WINDOWS -> "Chờ đăng ký SV";
            case STUDENT_OPEN -> "Đang nhận đăng ký SV";
            case REGISTRATION_CLOSED -> "Đã kết thúc";
        };
    }

    private static String stateClass(RegistrationState state) {
        return switch (state) {
            case BEFORE_LECTURER -> "bg-azure-lt";
            case LECTURER_OPEN, STUDENT_OPEN -> "bg-green-lt";
            case BETWEEN_WINDOWS -> "bg-yellow-lt";
            case REGISTRATION_CLOSED -> "bg-secondary-lt";
        };
    }

    public record PeriodPageView(
            Long id,
            String name,
            String type,
            String lecturerStart,
            String lecturerEnd,
            String studentStart,
            String studentEnd,
            String reviewerScoreDeadline,
            String councilReportDate,
            String status,
            String statusClass,
            long version,
            long pendingTopicCount,
            long pendingRegistrationCount,
            long approvedTopicCount) {
        public String lecturerWindow() {
            return lecturerStart + " – " + lecturerEnd;
        }

        public String studentWindow() {
            return studentStart + " – " + studentEnd;
        }
    }

    public record FieldLocks(
            boolean base,
            boolean lecturerEnd,
            boolean studentEnd,
            boolean reviewerScoreDeadline,
            boolean councilReportDate) {
        static FieldLocks unlocked() {
            return new FieldLocks(false, false, false, false, false);
        }
    }

    public record ReviewSummary(
            long pendingCount,
            long approvedCount,
            long publishedCount) {
    }

    public record ReviewNotice(
            boolean warning,
            String title,
            String message) {
    }

    public record RegistrationRequestPageView(
            long id,
            String groupCode,
            String leaderName,
            int memberCount,
            String topicTitle,
            String submittedAt,
            String status,
            String statusClass,
            String rejectionReason,
            boolean pending) {
    }

    public record ReviewTopicPageView(
            long id,
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
            boolean pending,
            boolean approved,
            boolean published) {
        static ReviewTopicPageView from(ReviewTopicView topic) {
            return new ReviewTopicPageView(
                    topic.id(),
                    topic.department(),
                    topic.author(),
                    topic.title(),
                    topic.description(),
                    topic.objectives(),
                    topic.studentRequirements(),
                    topic.expectedResults(),
                    statusLabel(topic.status()),
                    statusClass(topic.status()),
                    topic.version(),
                    topic.supervisors(),
                    topic.status() == TopicStatus.PENDING_APPROVAL,
                    topic.status() == TopicStatus.APPROVED,
                    topic.status() == TopicStatus.PUBLISHED);
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
