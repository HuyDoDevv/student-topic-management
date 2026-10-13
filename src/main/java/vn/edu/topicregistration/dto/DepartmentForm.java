package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentCommand;

public class DepartmentForm {
    @NotNull(message = "Vui lòng chọn Khoa.")
    @Positive(message = "Khoa không hợp lệ.")
    private Long facultyId;

    @NotBlank(message = "Vui lòng nhập tên Bộ môn.")
    @Size(max = 255, message = "Tên Bộ môn không được dài quá 255 ký tự.")
    private String name;

    public DepartmentForm() {
    }

    public DepartmentForm(Long facultyId, String name) {
        this.facultyId = facultyId;
        this.name = name;
    }

    public DepartmentCommand toCommand() {
        return new DepartmentCommand(facultyId, name);
    }

    public Long getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(Long facultyId) {
        this.facultyId = facultyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
