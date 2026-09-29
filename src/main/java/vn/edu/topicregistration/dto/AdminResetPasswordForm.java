package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.edu.topicregistration.security.PasswordPolicy;
import vn.edu.topicregistration.service.AdminAccountService.ResetPasswordCommand;

public class AdminResetPasswordForm {
    @NotBlank(message = "Vui lòng nhập mật khẩu tạm thời mới.")
    @Size(min = 8, max = 72, message = "Mật khẩu tạm thời phải có từ 8 đến 72 ký tự.")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu tạm thời mới.")
    @Size(max = 72, message = "Xác nhận mật khẩu không được dài quá 72 ký tự.")
    private String confirmPassword;

    @AssertTrue(message = "Xác nhận mật khẩu tạm thời không khớp.")
    public boolean isPasswordConfirmationMatching() {
        return newPassword == null || confirmPassword == null
                || newPassword.equals(confirmPassword);
    }

    @AssertTrue(message = "Mật khẩu tạm thời không được quá 72 byte UTF-8.")
    public boolean isNewPasswordWithinBcryptLimit() {
        return PasswordPolicy.isWithinBcryptLimit(newPassword);
    }

    public ResetPasswordCommand toCommand(long accountId) {
        return new ResetPasswordCommand(accountId, newPassword, confirmPassword);
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
