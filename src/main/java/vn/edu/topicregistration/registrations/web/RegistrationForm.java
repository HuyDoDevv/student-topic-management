package vn.edu.topicregistration.registrations.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class RegistrationForm {
    @NotNull(message = "Vui lòng chọn đề tài.")
    @Positive(message = "Đề tài không hợp lệ.")
    private Long topicId;

    @NotNull(message = "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.")
    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
