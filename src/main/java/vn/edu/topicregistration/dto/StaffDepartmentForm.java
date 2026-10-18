package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class StaffDepartmentForm {
    @NotNull(message = "Vui lòng chọn Bộ môn.")
    @Positive(message = "Bộ môn chưa hợp lệ.")
    private Long departmentId;

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }
}
