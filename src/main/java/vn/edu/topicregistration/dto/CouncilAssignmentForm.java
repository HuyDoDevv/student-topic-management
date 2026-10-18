package vn.edu.topicregistration.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import vn.edu.topicregistration.service.CouncilService.AssignmentCommand;
import vn.edu.topicregistration.util.ProjectTime;

public class CouncilAssignmentForm {
    @NotNull(message = "Vui lòng chọn đăng ký đã được chấp nhận.")
    @Positive(message = "Đăng ký không hợp lệ.")
    private Long registrationId;

    @NotNull(message = "Vui lòng nhập lịch bảo vệ.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime scheduledAt;

    @NotBlank(message = "Vui lòng nhập địa điểm bảo vệ.")
    @Size(max = 500, message = "Địa điểm không được dài quá 500 ký tự.")
    private String location;

    @NotNull(message = "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.")
    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public AssignmentCommand toCommand(ProjectTime projectTime) {
        return new AssignmentCommand(
                registrationId,
                scheduledAt == null ? null : projectTime.toInstant(scheduledAt),
                location,
                version);
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
