package vn.edu.topicregistration.exception;

import org.springframework.http.HttpStatus;

public enum ApplicationErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Vui lòng kiểm tra dữ liệu gửi lên."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu", "Dữ liệu yêu cầu không tồn tại hoặc bạn không thể truy cập."),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu đã thay đổi", "Vui lòng tải lại trang và thử lại."),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "Dữ liệu chưa hợp lệ", "Vui lòng kiểm tra các trường được đánh dấu."),
    ACCOUNT_USERNAME_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "Tên đăng nhập đã tồn tại", "Vui lòng chọn tên đăng nhập khác."),
    ACCOUNT_EMAIL_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "Email đã tồn tại", "Email này đã được sử dụng."),
    ACCOUNT_STUDENT_CODE_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "MSSV đã tồn tại", "Vui lòng kiểm tra lại MSSV."),
    DEAN_ALREADY_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Khoa đã có Trưởng khoa đang hoạt động", "Vui lòng khóa tài khoản Trưởng khoa hiện tại trước khi tạo hoặc kích hoạt người khác."),
    DEPARTMENT_NAME_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "Tên Bộ môn đã tồn tại", "Vui lòng chọn tên khác trong Khoa này."),
    ACCOUNT_CURRENT_PASSWORD_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "Mật khẩu hiện tại không đúng", "Vui lòng nhập lại mật khẩu hiện tại."),
    ACCOUNT_PASSWORD_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Mật khẩu mới chưa hợp lệ", "Mật khẩu mới phải khác mật khẩu hiện tại."),
    WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "Đã hết thời gian thao tác", "Khoảng thời gian cho thao tác này đã đóng."),
    GROUP_FULL(HttpStatus.UNPROCESSABLE_CONTENT, "Nhóm đã đủ thành viên", "Một nhóm chỉ có tối đa ba sinh viên."),
    GROUP_INCOMPLETE(HttpStatus.UNPROCESSABLE_CONTENT, "Nhóm chưa đủ thành viên", "Nhóm phải có đúng ba sinh viên trước khi đăng ký đề tài."),
    GROUP_MEMBERSHIP_CONFLICT(HttpStatus.UNPROCESSABLE_CONTENT, "Sinh viên đã thuộc nhóm", "Mỗi sinh viên chỉ được thuộc một nhóm trong cùng đợt đăng ký."),
    GROUP_INVITATION_CONFLICT(HttpStatus.UNPROCESSABLE_CONTENT, "Đã có lời mời đang chờ", "Sinh viên cần phản hồi lời mời nhóm hiện tại trước khi thực hiện thao tác này."),
    INVALID_INVITATION_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "Lời mời không còn hợp lệ", "Lời mời đã được phản hồi, bị hủy hoặc đã hết hiệu lực."),
    INVALID_GROUP_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "Trạng thái nhóm không hợp lệ", "Nhóm không thể thực hiện thao tác này ở trạng thái hiện tại."),
    ACTIVE_REGISTRATION_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "Nhóm đã có đăng ký đang xử lý", "Hãy xử lý đăng ký hiện tại trước khi gửi đăng ký mới."),
    TOPIC_TAKEN(HttpStatus.UNPROCESSABLE_CONTENT, "Đề tài đã nhận nhóm", "Hãy chọn một đề tài khác."),
    TOPIC_DEPARTMENT_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Đề tài thiếu Bộ môn hợp lệ", "Giảng viên hoặc đề tài chưa có Bộ môn thuộc đúng Khoa. Vui lòng liên hệ quản trị viên để cập nhật trước khi tạo hoặc gửi đề tài."),
    INVALID_TOPIC_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "Trạng thái đề tài không hợp lệ", "Đề tài không thể thực hiện thao tác này ở trạng thái hiện tại."),
    TOPIC_REGISTRATION_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "Đề tài đã có đăng ký", "Đề tài đã có đăng ký nên không thể trả về bản nháp."),
    INVALID_REGISTRATION_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "Trạng thái đăng ký không hợp lệ", "Đăng ký phải được chấp nhận trước khi thực hiện thao tác này."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Chưa có báo cáo", "Đăng ký này chưa có báo cáo có thể xem."),
    INVALID_REPORT_FILE(HttpStatus.UNPROCESSABLE_CONTENT, "Báo cáo chưa hợp lệ", "Chỉ chấp nhận file PDF không quá 20 MB."),
    REPORT_LOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "Báo cáo đã bị khóa", "Không thể thay báo cáo từ khi hội đồng bắt đầu làm việc."),
    REPORT_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu báo cáo", "Hệ thống chưa thể lưu file báo cáo. Vui lòng thử lại."),
    INVALID_COUNCIL_MEMBERS(HttpStatus.UNPROCESSABLE_CONTENT, "Thành viên hội đồng chưa hợp lệ", "Hội đồng phải có từ ba đến năm giảng viên hợp lệ."),
    INVALID_COUNCIL_ASSIGNMENT(HttpStatus.UNPROCESSABLE_CONTENT, "Phân công hội đồng chưa hợp lệ", "Đăng ký không thể được phân công cho hội đồng này."),
    INVALID_COUNCIL_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "Trạng thái hội đồng không hợp lệ", "Hội đồng chưa đủ điều kiện thực hiện thao tác này."),
    INVALID_SCORE(HttpStatus.UNPROCESSABLE_CONTENT, "Điểm chưa hợp lệ", "Điểm phải từ 0 đến 10 và tăng theo bước 0,25."),
    EVALUATIONS_INCOMPLETE(HttpStatus.UNPROCESSABLE_CONTENT, "Chưa đủ điểm", "Mọi thành viên hội đồng phải nộp điểm trước khi tổng hợp."),
    RESULT_NOT_READY(HttpStatus.UNPROCESSABLE_CONTENT, "Kết quả chưa sẵn sàng", "Kết quả chưa đủ điều kiện để công bố.");

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
