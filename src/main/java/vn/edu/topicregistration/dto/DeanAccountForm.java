package vn.edu.topicregistration.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.AdminAccountService.CreateDeanCommand;

public class DeanAccountForm {
    @NotBlank(message = "Vui lòng nhập tên đăng nhập.")
    @Size(max = 191, message = "Tên đăng nhập không được dài quá 191 ký tự.")
    @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$",
            message = "Tên đăng nhập phải bắt đầu và kết thúc bằng chữ hoặc số; ở giữa có thể dùng dấu chấm, gạch dưới hoặc gạch ngang.")
    private String username;

    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(max = 255, message = "Họ tên không được dài quá 255 ký tự.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập mật khẩu tạm thời.")
    @Size(min = 8, max = 72, message = "Mật khẩu tạm thời phải có từ 8 đến 72 ký tự.")
    private String password;

    public CreateDeanCommand toCommand() {
        return new CreateDeanCommand(username, fullName, password);
    }

    @AssertTrue(message = "Mật khẩu tạm thời không được quá 72 byte UTF-8.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
