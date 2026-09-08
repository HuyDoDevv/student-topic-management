# Web / HTML contracts — Draft có điều kiện

Spring MVC + Thymeleaf, HTML cùng origin, session cookie. Đây là thiết kế giao diện,
không phải REST API công khai. Các route chưa được triển khai.
Nguồn [spec](../spec.md), [model](../data-model.md), [gates](../plan.md#các-quyết-định-còn-chặn).

## Authorization matrix

Mỗi dòng yêu cầu đã login, role đáng tin cậy **và** quan hệ tài nguyên. Trưởng nhóm là
quan hệ trong nhóm, không phải role tài khoản. Không suy rộng quyền do đồng thời là GVHD.
Ký hiệu B: scope hoặc điều kiện còn BLOCKED; không cấp toàn quyền khi thiếu policy.

| Thao tác | Role/quan hệ được phép | Điều kiện dữ liệu/thời gian | FR |
|---|---|---|---|
| Tạo đợt | DEAN | Tên/loại/lịch hợp lệ; scope B Q-18, conditional dates Q-02 | 02–04 |
| Sửa loại/bốn mốc | DEAN | Trước lecturer_start hiện hành; scope B | 15 |
| Tạo/sửa nháp | LECTURER tác giả | Đề tài của mình, bộ môn mình, DRAFT | 05–06,14 |
| Gửi duyệt | LECTURER tác giả | Hạn GV, 5 trường, 1–2 GVHD cùng bộ môn | 05–06,14 |
| Duyệt/từ chối topic | DEAN | PENDING_APPROVAL; scope B | 07,14 |
| Công bố | DEAN | APPROVED; scope B | 07,14 |
| Tạo nhóm | STUDENT người tạo | Trở thành leader, chưa nhóm khác cùng đợt; thời điểm B Q-07 | 08–09 |
| Thêm/xóa thành viên | STUDENT leader hiện tại của đúng nhóm | Hạn SV, không active, 1–3 thành viên, một leader | 08–09 |
| Chuyển leader | Leader hiện tại | Người nhận đã là thành viên; hạn SV, không active | 08 |
| Gửi/đăng ký lại | Leader hiện tại của đúng nhóm | Hạn SV; PUBLISHED cùng đợt, chưa accepted, không active; scope SV B | 10,13 |
| Rút | Leader hiện tại của đúng nhóm | PENDING, hạn SV | 13 |
| Chấp nhận/từ chối registration | DEAN | PENDING, scope B; auto-reject khi accept | 11–12 |
| Xem danh sách/chi tiết được bảo vệ | Vai trò/quan hệ cần cho hành trình | Phạm vi dữ liệu SV/dean và dữ liệu thành viên hiển thị B Q-18 | 01,07,10 |

Với từng dòng, người không có role/quan hệ phải bị từ chối: GVHD không tự có quyền
duyệt/accept; thành viên thường không gửi/rút/sửa; leader nhóm khác không thao tác nhóm này;
leader cũ mất quyền sau chuyển. Kiểm tra cả truy vấn danh sách và chi tiết, không chỉ POST.
Không tự bổ sung role ADMIN, endpoint quản trị tài khoản hay xem dữ liệu toàn hệ thống.
Các hàng B là chỗ cần hoàn thiện policy trước triển khai, không phải chính sách “deny tất cả”
được khách hàng phê duyệt.

## Routes and payloads

Path ID chỉ định tài nguyên; backend đọc quan hệ thật. ID, author, role, status và scope
không lấy từ hidden form để tin cậy. version là token chống ghi đè, không cấp quyền.
Mọi POST cần CSRF; actor lấy từ session. Payload whitelist, input không hợp lệ không ghi.

| Method / path dự kiến | Payload chính | Thành công / điều kiện | FR |
|---|---|---|---|
| GET /login | — | 200 form Tabler | 01 |
| POST /login | username,password,CSRF | Redirect tới trang được phép; sai trả thông báo chung | 01 |
| POST /logout | CSRF | Hủy session, redirect login | 01; kỹ thuật session |
| GET /periods; GET /periods/{id} | — | 200 danh sách/chi tiết theo scope | 01–04 |
| POST /periods | name,type,4 timestamps,conditional dates | 303 tới period mới; Q-02/18 gate | 02–04 |
| POST /periods/{id}/schedule | type,4 timestamps,version | 303 sau sửa hợp lệ; không bind tên/ngày phụ đang Q | 15 |
| GET /periods/{id}/topics | — | 200; scope/lọc theo trạng thái quyền đã chốt | 05,07 |
| POST /periods/{id}/topics | 5 nội dung,supervisorIds | Tạo DRAFT; author/department từ identity | 05–06,14 |
| GET /topics/{id}; GET /topics/{id}/edit | — | Chỉ dữ liệu/view được policy cho phép | 01,05 |
| POST /topics/{id}/draft | 5 nội dung,supervisorIds,version | 303; tác giả và DRAFT | 05–06,14 |
| POST /topics/{id}/submit | version | DRAFT → PENDING_APPROVAL | 05–06,14 |
| POST /topics/{id}/approve | version | PENDING_APPROVAL → APPROVED | 07,14 |
| POST /topics/{id}/reject | version | PENDING_APPROVAL → DRAFT; không tự đặt reason bắt buộc | 14 |
| POST /periods/{id}/topics/publish | topicIds,versions | Đề xuất tập APPROVED → PUBLISHED nguyên tử; chốt granularity trong AC | 07,14 |
| POST /periods/{id}/groups | CSRF | Nhóm mới, người tạo leader; Q-07/18 gate | 08–09 |
| GET /groups/{id} | — | Chi tiết nhóm; chỉ trường policy cho phép | 01,08 |
| POST /groups/{id}/members | studentId,version | Thêm trực tiếp, không invitation | 08–09 |
| POST /groups/{id}/members/{studentId}/remove | version | Xóa thành viên, giữ leader hợp lệ và tối thiểu 1 | 08 |
| POST /groups/{id}/leader | studentId,version | Chuyển cho thành viên hiện hữu | 08 |
| POST /groups/{id}/registrations | topicId,version | Tạo PENDING mới | 10,13 |
| GET /registrations/{id} | — | Trạng thái/kết quả theo policy | 01,10–13 |
| POST /registrations/{id}/withdraw | version | PENDING → WITHDRAWN | 13 |
| POST /registrations/{id}/accept | version | ACCEPTED và auto-reject nguyên tử | 11 |
| POST /registrations/{id}/reject | reason,version | REJECTED; required reason Q-09 | 12 |

Không có GET gây mutation. Không route tự rời nhóm, tái dùng topic, đổi GVHD sau công bố,
xóa lịch sử, gia hạn sau mở GV hoặc tự đăng ký tài khoản. URI route là thiết kế đề xuất,
không khẳng định các phần B đã được phép triển khai.

## Response and validation contract

- GET hợp lệ trả 200 HTML UTF-8. POST nghiệp vụ thành công trả 303 tới trang kết quả
  để refresh không gửi lại form. Authentication redirect tuân theo Spring Security.
- Chưa login: redirect /login cho browser, tuyệt đối không ghi. Sai quyền/CSRF: 403.
  ID không tồn tại: 404; không tiết lộ dữ liệu bằng thông báo lỗi.
- Kiểu/cấu trúc sai: 400; thiếu trường hoặc vi phạm validation: 422 với lỗi theo trường,
  giữ nội dung không nhạy cảm đã nhập. Không hiện lại mật khẩu.
- Stale version, trạng thái không còn hợp lệ hoặc tranh chấp: 409 với thông báo tải lại;
  không trả SQL/stack trace. Unique conflict sau rollback cũng dùng phản hồi này.
- Hết hạn hoặc vượt ràng buộc nghiệp vụ: 422 với mã lỗi nội bộ ổn định
  WINDOW_CLOSED, GROUP_FULL, ACTIVE_REGISTRATION_EXISTS, TOPIC_TAKEN,
  INVALID_TOPIC_STATE. Không biến lỗi nghiệp vụ thành 500.
- Chuỗi nội dung escape mặc định bởi Thymeleaf, không render HTML thô từ mô tả đề tài.
  Không tự đặt độ dài tối đa nghiệp vụ; giới hạn tài nguyên request kỹ thuật cần ghi
  rõ khi triển khai và không giả làm yêu cầu PDF.
- Phản hồi auto-reject hiển thị chính xác “Đề tài đã nhận nhóm” trên trạng thái đăng ký;
  không thêm email/push hoặc module thông báo.

## Tabler UI and acceptance mapping

| Trang | Component tái sử dụng | Kiểm tra |
|---|---|---|
| Đăng nhập | Auth card, labels, error alert | AT-10, không tự đăng ký |
| Đợt | Table, form inputs, badges thời gian | AS-1.*, AT-01–02,13 |
| Đề tài | Table, detail card, form, status badge | AS-2.*, AS-3.*, AT-03,12 |
| Nhóm | Member table, select, action modal | AS-4.*, AT-04,06 |
| Đăng ký | Topic list, form confirmation, result badge | AS-5.*, AT-05,11 |

Menu theo role chỉ hỗ trợ UX. Nút disabled/hidden không thay thế backend.
Danh sách rỗng hiển thị thông báo; POST đang gửi có chỉ báo và hạn chế double-click;
backend vẫn chống duplicate/race. Lỗi gắn label/field, focus vào thông báo lỗi;
không dùng màu badge làm dấu hiệu trạng thái duy nhất. Đây là tiêu chí UX đề xuất.

Mỗi route bảo vệ có MockMvc test cho phép/từ chối và xác nhận DB không đổi khi từ chối.
Browser E2E chứng minh toàn luồng, nhưng không thay thế ca backend gọi trực tiếp
với ID giả, role giả, CSRF thiếu và stale version.

