package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import vn.edu.topicregistration.service.ReviewerAssignmentService.AssignCommand;

public class ReviewerAssignmentForm {
    @NotNull @Positive
    private Long registrationId;
    @NotNull @Positive
    private Long reviewerId;
    @NotNull @PositiveOrZero
    private Long version;

    public AssignCommand toCommand() {
        return new AssignCommand(registrationId, reviewerId, version);
    }

    public Long getRegistrationId() { return registrationId; }
    public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
