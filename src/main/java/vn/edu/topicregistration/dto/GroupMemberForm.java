package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class GroupMemberForm {
    @NotNull(message = "Vui lòng chọn sinh viên.")
    @Positive(message = "Sinh viên không hợp lệ.")
    private Long studentId;

    @NotNull(message = "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.")
    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
