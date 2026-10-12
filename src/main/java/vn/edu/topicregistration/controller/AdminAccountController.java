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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.topicregistration.dto.AdminResetPasswordForm;
import vn.edu.topicregistration.dto.DeanAccountForm;
import vn.edu.topicregistration.dto.LecturerAccountForm;
import vn.edu.topicregistration.dto.StudentAccountForm;
import vn.edu.topicregistration.dto.StaffDepartmentForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountFilter;
import vn.edu.topicregistration.service.AdminAccountService.AccountStatusResult;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.PasswordResetResult;
import vn.edu.topicregistration.service.AdminAccountService.StaffDepartmentTarget;

@Controller
@Profile("!test")
public class AdminAccountController {
    private final AdminAccountService accounts;

    public AdminAccountController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @InitBinder("studentAccountForm")
    void whitelistStudentFields(WebDataBinder binder) {
        binder.setAllowedFields("fullName", "studentCode", "className", "cohortCode", "password");
    }

    @InitBinder("lecturerAccountForm")
    void whitelistLecturerFields(WebDataBinder binder) {
        binder.setAllowedFields("fullName", "username", "password", "departmentId");
    }

    @InitBinder("deanAccountForm")
    void whitelistDeanFields(WebDataBinder binder) {
        binder.setAllowedFields("fullName", "username", "password", "departmentId");
    }

    @InitBinder("staffDepartmentForm")
    void whitelistStaffDepartmentFields(WebDataBinder binder) {
        binder.setAllowedFields("departmentId");
    }

    @InitBinder("adminResetPasswordForm")
    void whitelistResetPasswordFields(WebDataBinder binder) {
        binder.setAllowedFields("newPassword", "confirmPassword");
    }

    @GetMapping("/admin/accounts")
    public Object accountsRoot() {
        return WebRedirects.seeOther(AccountSection.STUDENTS.path);
    }

    @GetMapping("/admin/accounts/students")
    public String students(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Boolean enabled,
            Model model) {
        return renderList(model, new AccountFilter(
                query, AccountType.STUDENT, facultyId, enabled),
                AccountSection.STUDENTS);
    }

    @GetMapping("/admin/accounts/lecturers")
    public String lecturers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Boolean enabled,
            Model model) {
        return renderList(model, new AccountFilter(
                query, AccountType.LECTURER, facultyId, enabled),
                AccountSection.LECTURERS);
    }

    @GetMapping("/admin/accounts/deans")
    public String deans(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Boolean enabled,
            Model model) {
        return renderList(model, new AccountFilter(
                query, AccountType.DEAN, facultyId, enabled),
                AccountSection.DEANS);
    }

    @GetMapping("/admin/accounts/new")
    public Object legacyCreateForm() {
        return WebRedirects.seeOther(AccountSection.STUDENTS.createPath);
    }

    @GetMapping("/admin/accounts/students/new")
    public String studentForm(Model model) {
        return renderStudentForm(model, new StudentAccountForm());
    }

    @PostMapping("/admin/accounts/students")
    public Object createStudent(
            @Valid @ModelAttribute("studentAccountForm") StudentAccountForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderStudentForm(model, form);
        }
        try {
            accounts.createStudent(form.toCommand());
        } catch (ApplicationException exception) {
            rejectStudent(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderStudentForm(model, form);
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã tạo sinh viên " + form.getStudentCode().trim() + ".");
        return WebRedirects.seeOther(AccountSection.STUDENTS.path);
    }

    @GetMapping("/admin/accounts/lecturers/new")
    public String lecturerForm(Model model) {
        return renderLecturerForm(model, new LecturerAccountForm());
    }

    @PostMapping("/admin/accounts/lecturers")
    public Object createLecturer(
            @Valid @ModelAttribute("lecturerAccountForm") LecturerAccountForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderLecturerForm(model, form);
        }
        try {
            accounts.createLecturer(form.toCommand());
        } catch (ApplicationException exception) {
            rejectStaff(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderLecturerForm(model, form);
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã tạo giảng viên " + form.getUsername().trim() + ".");
        return WebRedirects.seeOther(AccountSection.LECTURERS.path);
    }

    @GetMapping("/admin/accounts/deans/new")
    public String deanForm(Model model) {
        return renderDeanForm(model, new DeanAccountForm());
    }

    @PostMapping("/admin/accounts/deans")
    public Object createDean(
            @Valid @ModelAttribute("deanAccountForm") DeanAccountForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderDeanForm(model, form);
        }
        try {
            accounts.createDean(form.toCommand());
        } catch (ApplicationException exception) {
            rejectStaff(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderDeanForm(model, form);
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã tạo trưởng khoa " + form.getUsername().trim() + ".");
        return WebRedirects.seeOther(AccountSection.DEANS.path);
    }

    @GetMapping("/admin/accounts/{accountId}/department")
    public String staffDepartmentForm(@PathVariable("accountId") long accountId, Model model) {
        StaffDepartmentTarget target = accounts.staffDepartmentTarget(accountId);
        StaffDepartmentForm form = new StaffDepartmentForm();
        form.setDepartmentId(target.departmentId());
        return renderStaffDepartmentForm(model, target, form);
    }

    @PostMapping("/admin/accounts/{accountId}/department")
    public Object assignStaffDepartment(
            @PathVariable("accountId") long accountId,
            @Valid @ModelAttribute("staffDepartmentForm") StaffDepartmentForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        StaffDepartmentTarget target = accounts.staffDepartmentTarget(accountId);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderStaffDepartmentForm(model, target, form);
        }
        try {
            target = accounts.assignStaffDepartment(accountId, form.getDepartmentId());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            bindingResult.rejectValue("departmentId", "staff.department.invalid",
                    "Bộ môn không hợp lệ, khác Khoa hoặc tài khoản đã thuộc Bộ môn khác.");
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderStaffDepartmentForm(model, target, form);
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã gán Bộ môn cho " + target.username() + ".");
        return WebRedirects.seeOther(sectionFor(target.accountType()).path);
    }

    @GetMapping("/admin/accounts/{accountId}/password/reset")
    public String resetPasswordForm(
            @PathVariable("accountId") long accountId,
            Model model) {
        return renderResetPasswordForm(model, accountId, new AdminResetPasswordForm());
    }

    @PostMapping("/admin/accounts/{accountId}/password/reset")
    public Object resetPassword(
            @PathVariable("accountId") long accountId,
            @Valid @ModelAttribute("adminResetPasswordForm") AdminResetPasswordForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderResetPasswordForm(model, accountId, form);
        }
        PasswordResetResult result;
        try {
            result = accounts.resetPassword(form.toCommand(accountId));
        } catch (ApplicationException exception) {
            rejectResetPassword(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderResetPasswordForm(model, accountId, form);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Đã đặt lại mật khẩu cho " + result.username()
                        + ". Người dùng phải đổi mật khẩu khi đăng nhập.");
        return WebRedirects.seeOther(sectionFor(result.accountType()).path);
    }

    @PostMapping("/admin/accounts/{accountId}/lock")
    public Object lockAccount(
            @PathVariable("accountId") long accountId,
            RedirectAttributes redirectAttributes) {
        return redirectAfterStatusChange(
                accounts.lockAccount(accountId), redirectAttributes);
    }

    @PostMapping("/admin/accounts/{accountId}/unlock")
    public Object unlockAccount(
            @PathVariable("accountId") long accountId,
            RedirectAttributes redirectAttributes) {
        return redirectAfterStatusChange(
                accounts.unlockAccount(accountId), redirectAttributes);
    }

    private String renderList(
            Model model, AccountFilter filter, AccountSection section) {
        common(model, section.activeNav);
        model.addAttribute("workspace", accounts.workspace(filter));
        model.addAttribute("managementTitle", section.title);
        model.addAttribute("managementDescription", section.description);
        model.addAttribute("searchTitle", section.searchTitle);
        model.addAttribute("accountLabel", section.accountLabel);
        model.addAttribute("managementPath", section.path);
        model.addAttribute("createPath", section.createPath);
        model.addAttribute("createLabel", section.createLabel);
        model.addAttribute("accountBadgeClass", section.badgeClass);
        return "admin/accounts/list";
    }

    private String renderStudentForm(Model model, StudentAccountForm form) {
        common(model, AccountSection.STUDENTS.activeNav);
        if (!model.containsAttribute("studentAccountForm")) {
            model.addAttribute("studentAccountForm", form);
        }
        model.addAttribute("facultyName", AdminAccountService.DEFAULT_FACULTY_NAME);
        return "admin/accounts/student-form";
    }

    private String renderLecturerForm(Model model, LecturerAccountForm form) {
        common(model, AccountSection.LECTURERS.activeNav);
        if (!model.containsAttribute("lecturerAccountForm")) {
            model.addAttribute("lecturerAccountForm", form);
        }
        model.addAttribute("departmentOptions", accounts.staffDepartmentOptions());
        return "admin/accounts/lecturer-form";
    }

    private String renderDeanForm(Model model, DeanAccountForm form) {
        common(model, AccountSection.DEANS.activeNav);
        if (!model.containsAttribute("deanAccountForm")) {
            model.addAttribute("deanAccountForm", form);
        }
        model.addAttribute("departmentOptions", accounts.staffDepartmentOptions());
        return "admin/accounts/dean-form";
    }

    private String renderStaffDepartmentForm(
            Model model, StaffDepartmentTarget target, StaffDepartmentForm form) {
        common(model, sectionFor(target.accountType()).activeNav);
        if (!model.containsAttribute("staffDepartmentForm")) {
            model.addAttribute("staffDepartmentForm", form);
        }
        model.addAttribute("target", target);
        model.addAttribute("departmentOptions", accounts.staffDepartmentOptionsForAccount(target.id()));
        model.addAttribute("cancelPath", sectionFor(target.accountType()).path);
        return "admin/accounts/department-form";
    }

    private String renderResetPasswordForm(
            Model model, long accountId, AdminResetPasswordForm form) {
        var target = accounts.passwordResetTarget(accountId);
        AccountSection section = sectionFor(target.accountType());
        common(model, section.activeNav);
        if (!model.containsAttribute("adminResetPasswordForm")) {
            model.addAttribute("adminResetPasswordForm", form);
        }
        model.addAttribute("target", target);
        model.addAttribute("cancelPath", section.path);
        return "admin/accounts/reset-password";
    }

    private static void rejectStudent(
            ApplicationException exception, BindingResult bindingResult) {
        switch (exception.code()) {
            case ACCOUNT_USERNAME_EXISTS, ACCOUNT_STUDENT_CODE_EXISTS ->
                    bindingResult.rejectValue("studentCode", "account.studentCode.exists",
                            "MSSV đã tồn tại.");
            case ACCOUNT_EMAIL_EXISTS ->
                    bindingResult.rejectValue("studentCode", "account.email.exists",
                            "Email sinh từ MSSV này đã tồn tại.");
            case VALIDATION_FAILED -> bindingResult.reject("account.invalid",
                    "Thông tin tài khoản hoặc Khoa mặc định chưa hợp lệ.");
            default -> throw exception;
        }
    }

    private static void rejectStaff(
            ApplicationException exception, BindingResult bindingResult) {
        if (exception.code() == ApplicationErrorCode.ACCOUNT_USERNAME_EXISTS) {
            bindingResult.rejectValue("username", "account.username.exists",
                    "Tên đăng nhập đã tồn tại.");
        } else if (exception.code() == ApplicationErrorCode.VALIDATION_FAILED) {
            bindingResult.rejectValue("departmentId", "account.department.invalid",
                    "Bộ môn không tồn tại hoặc không thuộc Khoa của tài khoản.");
        } else {
            throw exception;
        }
    }

    private static void rejectResetPassword(
            ApplicationException exception, BindingResult bindingResult) {
        if (exception.code() == ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED) {
            bindingResult.rejectValue("newPassword", "account.password.reused",
                    "Mật khẩu tạm thời phải khác mật khẩu hiện tại.");
        } else if (exception.code() == ApplicationErrorCode.VALIDATION_FAILED) {
            bindingResult.reject("account.password.invalid",
                    "Thông tin đặt lại mật khẩu chưa hợp lệ.");
        } else {
            throw exception;
        }
    }

    private static AccountSection sectionFor(AccountType type) {
        return switch (type) {
            case STUDENT -> AccountSection.STUDENTS;
            case LECTURER -> AccountSection.LECTURERS;
            case DEAN -> AccountSection.DEANS;
            case ADMIN -> throw new ApplicationException(
                    ApplicationErrorCode.RESOURCE_NOT_FOUND);
        };
    }

    private static Object redirectAfterStatusChange(
            AccountStatusResult result, RedirectAttributes redirectAttributes) {
        String message;
        if (result.enabled()) {
            message = result.changed()
                    ? "Đã mở khóa tài khoản " + result.username() + "."
                    : "Tài khoản " + result.username() + " đang hoạt động.";
        } else {
            message = result.changed()
                    ? "Đã khóa tài khoản " + result.username()
                            + ". Các phiên đăng nhập hiện tại đã bị thu hồi."
                    : "Tài khoản " + result.username() + " đã bị khóa.";
        }
        redirectAttributes.addFlashAttribute("successMessage", message);
        return WebRedirects.seeOther(sectionFor(result.accountType()).path);
    }

    private static void common(Model model, String activeNav) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("pageSection", "Quản trị hệ thống");
    }

    private enum AccountSection {
        STUDENTS(
                "Quản lý sinh viên",
                "Tra cứu tài khoản sinh viên theo khoa và trạng thái.",
                "Tìm sinh viên", "sinh viên", "/admin/accounts/students",
                "/admin/accounts/students/new", "Thêm sinh viên",
                "admin-students", "bg-azure-lt"),
        LECTURERS(
                "Quản lý giảng viên",
                "Tra cứu tài khoản giảng viên theo khoa và trạng thái.",
                "Tìm giảng viên", "giảng viên", "/admin/accounts/lecturers",
                "/admin/accounts/lecturers/new", "Thêm giảng viên",
                "admin-lecturers", "bg-blue-lt"),
        DEANS(
                "Quản lý trưởng khoa",
                "Tra cứu tài khoản trưởng khoa theo khoa và trạng thái.",
                "Tìm trưởng khoa", "trưởng khoa", "/admin/accounts/deans",
                "/admin/accounts/deans/new", "Thêm trưởng khoa",
                "admin-deans", "bg-purple-lt");

        private final String title;
        private final String description;
        private final String searchTitle;
        private final String accountLabel;
        private final String path;
        private final String createPath;
        private final String createLabel;
        private final String activeNav;
        private final String badgeClass;

        AccountSection(
                String title,
                String description,
                String searchTitle,
                String accountLabel,
                String path,
                String createPath,
                String createLabel,
                String activeNav,
                String badgeClass) {
            this.title = title;
            this.description = description;
            this.searchTitle = searchTitle;
            this.accountLabel = accountLabel;
            this.path = path;
            this.createPath = createPath;
            this.createLabel = createLabel;
            this.activeNav = activeNav;
            this.badgeClass = badgeClass;
        }
    }
}
