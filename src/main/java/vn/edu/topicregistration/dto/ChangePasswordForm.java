package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.edu.topicregistration.security.PasswordPolicy;
import vn.edu.topicregistration.service.AccountSecurityService.ChangePasswordCommand;

public class ChangePasswordForm {
    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    @Size(max = 72, message = "Mật khẩu hiện tại không được dài quá 72 ký tự.")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = 8, max = 72, message = "Mật khẩu mới phải có từ 8 đến 72 ký tự.")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới.")
    private String confirmPassword;

    @AssertTrue(message = "Xác nhận mật khẩu mới không khớp.")
    public boolean isPasswordConfirmationMatching() {
        return newPassword == null || confirmPassword == null
                || newPassword.equals(confirmPassword);
    }

    @AssertTrue(message = "Mật khẩu hiện tại không được quá 72 byte UTF-8.")
    public boolean isCurrentPasswordWithinBcryptLimit() {
        return PasswordPolicy.isWithinBcryptLimit(currentPassword);
    }

    @AssertTrue(message = "Mật khẩu mới không được quá 72 byte UTF-8.")
    public boolean isNewPasswordWithinBcryptLimit() {
        return PasswordPolicy.isWithinBcryptLimit(newPassword);
    }

    public ChangePasswordCommand toCommand() {
        return new ChangePasswordCommand(currentPassword, newPassword, confirmPassword);
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
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
