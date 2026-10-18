package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import vn.edu.topicregistration.service.DirectGradingService.AssignCommand;

public class DirectGradingAssignmentForm {
    @NotNull(message = "Vui lòng chọn đề tài cần chấm.")
    @Positive(message = "Đề tài cần chấm không hợp lệ.")
    private Long registrationId;

    @NotNull(message = "Vui lòng chọn giảng viên chấm.")
    @Positive(message = "Giảng viên chấm không hợp lệ.")
    private Long graderId;

    @NotNull(message = "Vui lòng tải lại danh sách phân công.")
    private Long version;

    public AssignCommand toCommand() {
        return new AssignCommand(registrationId, graderId, version);
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public Long getGraderId() {
        return graderId;
    }

    public void setGraderId(Long graderId) {
        this.graderId = graderId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
