package vn.edu.topicregistration.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.CouncilService.CreateCouncilCommand;

public class CouncilForm {
    @NotNull(message = "Vui lòng chọn đợt đăng ký.")
    @Positive(message = "Đợt đăng ký không hợp lệ.")
    private Long periodId;

    @NotBlank(message = "Vui lòng nhập tên hội đồng.")
    @Size(max = 255, message = "Tên hội đồng không được dài quá 255 ký tự.")
    private String name;

    @NotNull(message = "Vui lòng chọn Chủ tịch hội đồng.")
    @Positive(message = "Chủ tịch hội đồng không hợp lệ.")
    private Long chairId;

    @NotNull(message = "Vui lòng chọn Thư ký hội đồng.")
    @Positive(message = "Thư ký hội đồng không hợp lệ.")
    private Long secretaryId;

    @Size(min = 1, max = 3,
            message = "Vui lòng chọn từ một đến ba thành viên bổ sung.")
    private List<@Positive(message = "Thành viên hội đồng không hợp lệ.") Long> memberIds =
            new ArrayList<>();

    public CreateCouncilCommand toCommand() {
        return new CreateCouncilCommand(
                periodId,
                name,
                chairId,
                secretaryId,
                memberIds);
    }

    public Long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Long periodId) {
        this.periodId = periodId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getChairId() {
        return chairId;
    }

    public void setChairId(Long chairId) {
        this.chairId = chairId;
    }

    public Long getSecretaryId() {
        return secretaryId;
    }

    public void setSecretaryId(Long secretaryId) {
        this.secretaryId = secretaryId;
    }

    public List<Long> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<Long> memberIds) {
        this.memberIds = memberIds == null ? new ArrayList<>() : new ArrayList<>(memberIds);
    }
}
