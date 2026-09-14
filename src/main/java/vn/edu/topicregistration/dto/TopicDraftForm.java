package vn.edu.topicregistration.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.TopicAuthorService.TopicDraftCommand;
import vn.edu.topicregistration.service.TopicAuthorService.TopicView;

public class TopicDraftForm {
    @Size(max = 500, message = "Tên đề tài không được dài quá 500 ký tự.")
    private String title;

    private String description;

    private String objectives;

    private String studentRequirements;

    private String expectedResults;

    @Size(max = 1, message = "Chỉ được chọn tối đa một giảng viên hướng dẫn phối hợp.")
    private List<@Positive(message = "Giảng viên hướng dẫn phối hợp không hợp lệ.") Long>
            coSupervisorIds =
            new ArrayList<>();

    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public static TopicDraftForm from(TopicView topic) {
        TopicDraftForm form = new TopicDraftForm();
        form.title = topic.title();
        form.description = topic.description();
        form.objectives = topic.objectives();
        form.studentRequirements = topic.studentRequirements();
        form.expectedResults = topic.expectedResults();
        form.coSupervisorIds = topic.supervisors().stream()
                .filter(supervisor -> !supervisor.id().equals(topic.authorId()))
                .map(supervisor -> supervisor.id())
                .limit(1)
                .toList();
        form.version = topic.version();
        return form;
    }

    public TopicDraftCommand toCommand() {
        return new TopicDraftCommand(
                title,
                description,
                objectives,
                studentRequirements,
                expectedResults,
                coSupervisorIds);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getObjectives() {
        return objectives;
    }

    public void setObjectives(String objectives) {
        this.objectives = objectives;
    }

    public String getStudentRequirements() {
        return studentRequirements;
    }

    public void setStudentRequirements(String studentRequirements) {
        this.studentRequirements = studentRequirements;
    }

    public String getExpectedResults() {
        return expectedResults;
    }

    public void setExpectedResults(String expectedResults) {
        this.expectedResults = expectedResults;
    }

    public List<Long> getCoSupervisorIds() {
        return coSupervisorIds;
    }

    public void setCoSupervisorIds(List<Long> coSupervisorIds) {
        this.coSupervisorIds = coSupervisorIds == null
                ? new ArrayList<>()
                : new ArrayList<>(coSupervisorIds);
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
