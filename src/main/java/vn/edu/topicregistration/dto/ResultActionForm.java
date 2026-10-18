package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class ResultActionForm {
    @NotNull(message = "Phiên bản hội đồng là bắt buộc.")
    @PositiveOrZero(message = "Phiên bản hội đồng không hợp lệ.")
    private Long version;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
