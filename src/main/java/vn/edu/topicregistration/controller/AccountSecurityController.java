package vn.edu.topicregistration.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import vn.edu.topicregistration.dto.ChangePasswordForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AccountSecurityService;

@Controller
@Profile("!test")
public class AccountSecurityController {
    private final AccountSecurityService security;

    public AccountSecurityController(AccountSecurityService security) {
        this.security = security;
    }

    @InitBinder("changePasswordForm")
    void whitelistFields(WebDataBinder binder) {
        binder.setAllowedFields("currentPassword", "newPassword", "confirmPassword");
    }

    @GetMapping("/account/password")
    public String form(Model model) {
        return render(model, new ChangePasswordForm());
    }

    @PostMapping("/account/password")
    public Object changePassword(
            @Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return render(model, form);
        }
        try {
            security.changePassword(form.toCommand());
        } catch (ApplicationException exception) {
            reject(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return render(model, form);
        }

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return WebRedirects.seeOther("/login?passwordChanged");
    }

    private static String render(Model model, ChangePasswordForm form) {
        if (!model.containsAttribute("changePasswordForm")) {
            model.addAttribute("changePasswordForm", form);
        }
        model.addAttribute("pageSection", "Bảo mật tài khoản");
        return "account/change-password";
    }

    private static void reject(
            ApplicationException exception, BindingResult bindingResult) {
        if (exception.code() == ApplicationErrorCode.ACCOUNT_CURRENT_PASSWORD_INVALID) {
            bindingResult.rejectValue("currentPassword", "account.password.invalid",
                    "Mật khẩu hiện tại không đúng.");
        } else if (exception.code() == ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED) {
            bindingResult.rejectValue("newPassword", "account.password.reused",
                    "Mật khẩu mới phải khác mật khẩu hiện tại.");
        } else if (exception.code() == ApplicationErrorCode.VALIDATION_FAILED) {
            bindingResult.reject("account.password.invalid",
                    "Thông tin đổi mật khẩu chưa hợp lệ.");
        } else {
            throw exception;
        }
    }
}
