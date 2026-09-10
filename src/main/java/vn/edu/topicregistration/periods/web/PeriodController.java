package vn.edu.topicregistration.periods.web;

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

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.periods.application.PeriodService;
import vn.edu.topicregistration.periods.application.PeriodService.PeriodView;
import vn.edu.topicregistration.periods.domain.PeriodRules.RegistrationState;
import vn.edu.topicregistration.periods.domain.PeriodRules.TopicType;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.time.ProjectTime;
import vn.edu.topicregistration.shared.web.WebRedirects;

@Controller
@Profile("!preview & !test")
@RequestMapping("/periods")
public class PeriodController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

    private final PeriodService periodService;
    private final AuthorizationPolicy authorization;
    private final ProjectTime projectTime;

    public PeriodController(
            PeriodService periodService,
            AuthorizationPolicy authorization,
            ProjectTime projectTime) {
        this.periodService = periodService;
        this.authorization = authorization;
        this.projectTime = projectTime;
    }

    @GetMapping
    String list(Model model) {
        common(model);
        List<PeriodPageView> periods = periodService.listForCurrentDean().stream()
                .map(this::display)
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
                    "Lịch đăng ký hoặc các ngày theo loại đề tài chưa hợp lệ.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, false, null, FieldLocks.unlocked());
        }
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model) {
        common(model);
        model.addAttribute("period", display(periodService.getForCurrentDean(id)));
        return "periods/detail";
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
                    "Chỉ được kéo dài mốc còn hiệu lực và lịch sau sửa phải đúng thứ tự.");
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
                !now.isBefore(period.lecturerEnd()),
                !now.isBefore(period.studentEnd()),
                period.reviewerScoreDeadline() == null
                        ? base
                        : !today.isBefore(period.reviewerScoreDeadline()),
                period.councilReportDate() == null
                        ? base
                        : !today.isBefore(period.councilReportDate()));
    }

    private PeriodPageView display(PeriodView period) {
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
                period.version());
    }

    private String format(Instant instant) {
        return DATE_TIME.format(projectTime.toBusinessTime(instant));
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
            case BEFORE_LECTURER -> "Chưa mở đề xuất";
            case LECTURER_OPEN -> "Đang nhận đề tài";
            case BETWEEN_WINDOWS -> "Chờ đăng ký";
            case STUDENT_OPEN -> "Đang nhận đăng ký";
            case REGISTRATION_CLOSED -> "Đã đóng";
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
            long version) {
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
}
