package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class RegistrationDecisionForm {
    @NotNull(message = "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.")
    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    private String reason;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
