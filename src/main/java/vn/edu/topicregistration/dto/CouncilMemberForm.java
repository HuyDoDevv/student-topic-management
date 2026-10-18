package vn.edu.topicregistration.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.CouncilService.UpdateMembersCommand;

public class CouncilMemberForm {
    @NotNull(message = "Vui lòng chọn Chủ tịch.")
    @Positive(message = "Chủ tịch không hợp lệ.")
    private Long chairId;

    @NotNull(message = "Vui lòng chọn Thư ký.")
    @Positive(message = "Thư ký không hợp lệ.")
    private Long secretaryId;

    @Size(min = 1, max = 3, message = "Chọn từ một đến ba thành viên bổ sung.")
    private List<@Positive(message = "Thành viên không hợp lệ.") Long> memberIds =
            new ArrayList<>();

    @NotNull(message = "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.")
    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public UpdateMembersCommand toCommand() {
        return new UpdateMembersCommand(chairId, secretaryId, memberIds, version);
    }

    public Long getChairId() { return chairId; }
    public void setChairId(Long chairId) { this.chairId = chairId; }
    public Long getSecretaryId() { return secretaryId; }
    public void setSecretaryId(Long secretaryId) { this.secretaryId = secretaryId; }
    public List<Long> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Long> memberIds) {
        this.memberIds = memberIds == null ? new ArrayList<>() : new ArrayList<>(memberIds);
    }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
