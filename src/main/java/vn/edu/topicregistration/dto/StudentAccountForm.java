package vn.edu.topicregistration.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.AdminAccountService.CreateStudentCommand;

public class StudentAccountForm {
    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(max = 255, message = "Họ tên không được dài quá 255 ký tự.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập MSSV.")
    @Pattern(regexp = "^[0-9]{8}$", message = "MSSV phải gồm đúng 8 chữ số.")
    private String studentCode;

    @NotBlank(message = "Vui lòng nhập lớp.")
    @Size(max = 64, message = "Lớp không được dài quá 64 ký tự.")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Lớp chỉ gồm chữ không dấu và số.")
    private String className;

    @NotBlank(message = "Vui lòng nhập khóa.")
    @Pattern(regexp = "(?i)^K[0-9]{4}$", message = "Khóa phải có định dạng K2026.")
    private String cohortCode;

    @NotBlank(message = "Vui lòng nhập mật khẩu tạm thời.")
    @Size(min = 8, max = 72, message = "Mật khẩu tạm thời phải có từ 8 đến 72 ký tự.")
    private String password;

    public CreateStudentCommand toCommand() {
        return new CreateStudentCommand(studentCode, fullName, className, cohortCode, password);
    }

    @AssertTrue(message = "Mật khẩu tạm thời không được quá 72 byte UTF-8.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getCohortCode() { return cohortCode; }
    public void setCohortCode(String cohortCode) { this.cohortCode = cohortCode; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
