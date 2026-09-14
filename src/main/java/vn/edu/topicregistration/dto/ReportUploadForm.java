package vn.edu.topicregistration.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public class ReportUploadForm {
    @NotNull(message = "Vui lòng chọn file PDF.")
    private MultipartFile file;

    @PositiveOrZero(message = "Phiên bản báo cáo không hợp lệ.")
    private Long version;

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
