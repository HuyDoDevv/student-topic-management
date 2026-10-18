package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import vn.edu.topicregistration.service.AnnouncementService.AnnouncementView;
import vn.edu.topicregistration.service.AnnouncementService.DraftCommand;

public class AnnouncementForm {
    @NotBlank(message = "Vui lòng nhập tiêu đề thông báo.")
    @Size(max = 200, message = "Tiêu đề không được dài quá 200 ký tự.")
    private String title;

    @NotBlank(message = "Vui lòng nhập nội dung thông báo.")
    @Size(max = 5_000, message = "Nội dung không được dài quá 5.000 ký tự.")
    private String body;

    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public static AnnouncementForm from(AnnouncementView announcement) {
        AnnouncementForm form = new AnnouncementForm();
        form.title = announcement.title();
        form.body = announcement.body();
        form.version = announcement.version();
        return form;
    }

    public DraftCommand toCommand() {
        return new DraftCommand(title, body);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
