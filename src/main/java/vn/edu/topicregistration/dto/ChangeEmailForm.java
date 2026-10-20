package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.edu.topicregistration.service.AccountSecurityService.ChangeEmailCommand;

public class ChangeEmailForm {
    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 254, message = "Email không được dài quá 254 ký tự.")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    public ChangeEmailCommand toCommand() {
        return new ChangeEmailCommand(email);
    }
}
