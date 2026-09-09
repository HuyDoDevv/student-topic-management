package vn.edu.topicregistration.shared.error;

import org.springframework.http.HttpStatus;

public enum ApplicationErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Vui lòng kiểm tra dữ liệu gửi lên."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu", "Dữ liệu yêu cầu không tồn tại hoặc bạn không thể truy cập."),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu đã thay đổi", "Vui lòng tải lại trang và thử lại."),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "Dữ liệu chưa hợp lệ", "Vui lòng kiểm tra các trường được đánh dấu."),
    WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "Đã hết thời gian thao tác", "Khoảng thời gian cho thao tác này đã đóng."),
    GROUP_FULL(HttpStatus.UNPROCESSABLE_CONTENT, "Nhóm đã đủ thành viên", "Một nhóm chỉ có tối đa ba sinh viên."),
    ACTIVE_REGISTRATION_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "Nhóm đã có đăng ký đang xử lý", "Hãy xử lý đăng ký hiện tại trước khi gửi đăng ký mới."),
    TOPIC_TAKEN(HttpStatus.UNPROCESSABLE_CONTENT, "Đề tài đã nhận nhóm", "Hãy chọn một đề tài khác."),
    INVALID_TOPIC_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "Trạng thái đề tài không hợp lệ", "Đề tài không thể thực hiện thao tác này ở trạng thái hiện tại.");

    private final HttpStatus status;
    private final String title;
    private final String userMessage;

    ApplicationErrorCode(HttpStatus status, String title, String userMessage) {
        this.status = status;
        this.title = title;
        this.userMessage = userMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String userMessage() {
        return userMessage;
    }
}
