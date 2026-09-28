package vn.edu.topicregistration.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.topicregistration.dto.AdminAccountForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountFilter;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.ImportIssue;
import vn.edu.topicregistration.service.AdminAccountService.ImportResult;

@Controller
@Profile("!test")
public class AdminAccountController {
    private final AdminAccountService accounts;

    public AdminAccountController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @InitBinder("adminAccountForm")
    void whitelistAccountFields(WebDataBinder binder) {
        binder.setAllowedFields(
                "accountType", "username", "fullName", "password", "departmentId");
    }

    @GetMapping("/admin/accounts")
    public Object accountsRoot() {
        return WebRedirects.seeOther(AccountSection.STUDENTS.path);
    }

    @GetMapping("/admin/accounts/students")
    public String students(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            Model model) {
        return renderList(model, new AccountFilter(
                query, AccountType.STUDENT, facultyId, departmentId, enabled),
                AccountSection.STUDENTS);
    }

    @GetMapping("/admin/accounts/lecturers")
    public String lecturers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            Model model) {
        return renderList(model, new AccountFilter(
                query, AccountType.LECTURER, facultyId, departmentId, enabled),
                AccountSection.LECTURERS);
    }

    @GetMapping("/admin/accounts/deans")
    public String deans(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            Model model) {
        return renderList(model, new AccountFilter(
                query, AccountType.DEAN, facultyId, departmentId, enabled),
                AccountSection.DEANS);
    }

    @GetMapping("/admin/accounts/new")
    public String createForm(Model model) {
        return renderForm(model, new AdminAccountForm());
    }

    @PostMapping("/admin/accounts")
    public Object create(
            @Valid @ModelAttribute("adminAccountForm") AdminAccountForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form);
        }
        try {
            accounts.create(form.toCommand());
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.ACCOUNT_USERNAME_EXISTS
                    && exception.code() != ApplicationErrorCode.VALIDATION_FAILED) {
                throw exception;
            }
            if (exception.code() == ApplicationErrorCode.ACCOUNT_USERNAME_EXISTS) {
                bindingResult.rejectValue("username", "account.username.exists",
                        "Tên đăng nhập đã tồn tại.");
            } else {
                bindingResult.reject("account.invalid",
                        "Thông tin tài khoản hoặc bộ môn chưa hợp lệ.");
            }
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form);
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã tạo tài khoản " + form.getUsername().trim() + ".");
        return WebRedirects.seeOther(managementPath(form.getAccountType()));
    }

    @PostMapping(
            value = "/admin/accounts/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object importCsv(
            @RequestParam("file") MultipartFile file,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        ImportResult result;
        try {
            result = accounts.importCsv(file.getBytes());
        } catch (IOException exception) {
            result = new ImportResult(
                    0, List.of(new ImportIssue(1, "Không thể đọc file CSV đã chọn.")));
        }
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            model.addAttribute("importIssues", result.issues());
            return renderForm(model, new AdminAccountForm());
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã import " + result.importedCount() + " tài khoản.");
        return WebRedirects.seeOther("/admin/accounts/new");
    }

    @GetMapping("/admin/accounts/import-template.csv")
    public ResponseEntity<byte[]> template() {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("mau-import-tai-khoan.csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(accounts.csvTemplate());
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
        model.addAttribute("accountBadgeClass", section.badgeClass);
        return "admin/accounts/list";
    }

    private String renderForm(Model model, AdminAccountForm form) {
        common(model, "admin-account-create");
        if (!model.containsAttribute("adminAccountForm")) {
            model.addAttribute("adminAccountForm", form);
        }
        var workspace = accounts.workspace(AccountFilter.empty());
        model.addAttribute("accountTypes", workspace.creatableTypes());
        model.addAttribute("departments", workspace.departments());
        return "admin/accounts/form";
    }

    private static String managementPath(AccountType type) {
        return switch (type) {
            case STUDENT -> AccountSection.STUDENTS.path;
            case LECTURER -> AccountSection.LECTURERS.path;
            case DEAN -> AccountSection.DEANS.path;
            case ADMIN -> "/admin/accounts/new";
        };
    }

    private static void common(Model model, String activeNav) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("pageSection", "Quản trị hệ thống");
    }

    private enum AccountSection {
        STUDENTS(
                "Quản lý sinh viên",
                "Tra cứu tài khoản sinh viên theo khoa, bộ môn và trạng thái.",
                "Tìm sinh viên", "sinh viên", "/admin/accounts/students",
                "admin-students", "bg-azure-lt"),
        LECTURERS(
                "Quản lý giảng viên",
                "Tra cứu tài khoản giảng viên theo khoa, bộ môn và trạng thái.",
                "Tìm giảng viên", "giảng viên", "/admin/accounts/lecturers",
                "admin-lecturers", "bg-blue-lt"),
        DEANS(
                "Quản lý trưởng khoa",
                "Tra cứu tài khoản trưởng khoa theo khoa, bộ môn và trạng thái.",
                "Tìm trưởng khoa", "trưởng khoa", "/admin/accounts/deans",
                "admin-deans", "bg-purple-lt");

        private final String title;
        private final String description;
        private final String searchTitle;
        private final String accountLabel;
        private final String path;
        private final String activeNav;
        private final String badgeClass;

        AccountSection(
                String title,
                String description,
                String searchTitle,
                String accountLabel,
                String path,
                String activeNav,
                String badgeClass) {
            this.title = title;
            this.description = description;
            this.searchTitle = searchTitle;
            this.accountLabel = accountLabel;
            this.path = path;
            this.activeNav = activeNav;
            this.badgeClass = badgeClass;
        }
    }
}
