# Web / HTML contracts — Thiết kế triển khai

Spring MVC + Thymeleaf, HTML cùng origin, session cookie. Đây là thiết kế giao diện,
không phải REST API công khai. Các route chưa được triển khai.
Nguồn [spec](../spec.md), [model](../data-model.md), [gates](../plan.md#các-quyết-định-còn-chặn).

## Authorization matrix

Mỗi dòng yêu cầu đã login, role đáng tin cậy **và** quan hệ tài nguyên. Trưởng nhóm là
quan hệ trong nhóm, không phải role tài khoản. Không suy rộng quyền do đồng thời là GVHD.

| Thao tác | Role/quan hệ được phép | Điều kiện dữ liệu/thời gian | FR |
|---|---|---|---|
| Tạo đợt | DEAN | Tên/loại/lịch ngày hợp lệ, không có ngày đã qua; TLCN/KLTN có hạn điểm GVPB, KLTN có ngày hội đồng; chỉ trong khoa của DEAN | 02–04 |
| Sửa cấu hình đợt | DEAN | Chỉ đợt trong khoa của DEAN; trước lecturer_start hiện hành được sửa tên/loại/bốn mốc; sau đó khóa tên/loại/mốc bắt đầu và chỉ tăng mốc kết thúc chưa hết hạn; ngày phụ chỉ dời muộn hơn trước ngày đang lưu | 04,15 |
| Tạo/sửa nháp | LECTURER tác giả, không có DEAN | Chỉ mở form/tạo mới trong hạn GV và kiểm tra lại dưới khóa đợt; đề tài hiện hữu của mình trong đúng một đợt, bộ môn mình, DRAFT vẫn sửa được ngoài hạn; backend tự gán tác giả làm GVHD chính, chỉ nhận tối đa một GVHD phối hợp hợp lệ cùng bộ môn; tài khoản có DEAN bị từ chối dù đồng thời có LECTURER | 01,03,05–06,14 |
| Gửi duyệt | LECTURER tác giả, không có DEAN | Hạn GV, 5 trường, tác giả ở slot 1 và tối đa một GVHD phối hợp cùng bộ môn; khóa nội dung/GVHD từ PENDING_APPROVAL trở đi; tài khoản có DEAN bị từ chối | 01,05–06,14 |
| Duyệt/từ chối topic | DEAN | PENDING_APPROVAL; đúng khoa của DEAN | 07,14 |
| Công bố | DEAN | Toàn bộ APPROVED trong đúng đợt/khoa, nguyên tử; không đổi DRAFT/PENDING_APPROVAL | 07,14 |
| Tạo nhóm | STUDENT người tạo | Trong hạn SV; trở thành leader, chưa nhóm khác cùng đợt | 08–09 |
| Gửi/hủy lời mời | STUDENT leader hiện tại của đúng nhóm | Hạn SV, không active; tổng thành viên + PENDING tối đa 3; ứng viên cùng khoa, chưa có nhóm/lời mời PENDING trong đợt | 08–09 |
| Chấp nhận/từ chối lời mời | STUDENT được mời | Chấp nhận trong hạn SV và nhóm không active; từ chối bất cứ lúc nào; chỉ accept mới tạo membership | 08–09 |
| Xóa/tự rời/giải tán | Leader xóa thành viên thường; chính thành viên tự rời; leader nhóm một người giải tán | Rời/giải tán trong hạn SV, không active; leader có thành viên khác phải chuyển quyền trước | 08–09 |
| Chuyển leader | Leader hiện tại | Người nhận đã là thành viên; hạn SV, không active | 08 |
| Gửi/đăng ký lại | Leader hiện tại của đúng nhóm | Hạn SV; nhóm có đúng 3 thành viên chính thức; PUBLISHED cùng đợt và cùng khoa, chưa accepted, không active, không còn invitation PENDING; lưu snapshot thành viên/vị trí/leader lúc gửi | 10,13 |
| Rút | Leader hiện tại của đúng nhóm | PENDING, hạn SV | 13 |
| Chấp nhận/từ chối registration | DEAN | PENDING thuộc đợt trong khoa của DEAN; từ chối thủ công cần reason không rỗng sau khi trim; auto-reject khi accept | 11–12 |
| Xem danh sách/chi tiết được bảo vệ | Vai trò/quan hệ cần cho hành trình | DEAN theo khoa, LECTURER theo bộ môn, STUDENT xem đề tài công bố trong khoa và dữ liệu nhóm/đăng ký mình có quyền | 01,07,10 |
| Xem và cấp tài khoản | ADMIN | Xem toàn bộ; tạo/import chỉ nhận STUDENT, LECTURER, DEAN; bộ môn tồn tại; mật khẩu được băm; import nguyên tử theo file mẫu | 01 |
| Xử lý nội dung vi phạm | ADMIN | Có lý do; ẩn mềm nội dung, lưu actor/thời gian/lý do và khóa tài khoản đăng nội dung | 01 |

Với từng dòng, người không có role/quan hệ phải bị từ chối: GVHD không tự có quyền
duyệt/accept; thành viên thường không gửi/rút hoặc quản lý người khác; leader nhóm khác không
thao tác nhóm này; leader cũ mất quyền sau chuyển. Kiểm tra cả truy vấn danh sách và chi tiết,
không chỉ POST. Thành viên thường chỉ được tự rời chính nhóm mình. Leader có thành viên khác
phải chuyển quyền trước khi rời; leader nhóm một người dùng thao tác giải tán. Không tự chọn
leader thay thế và không để lại nhóm rỗng.
ADMIN được xem và cấp tài khoản theo ba loại cố định, đồng thời xử lý khẩn cấp khi có nội
dung do kẻ gian đăng: ẩn nội dung khỏi website nhưng giữ dữ liệu, lưu actor/thời gian/lý do
và khóa tài khoản đăng nội dung. ADMIN không được tạo thêm ADMIN, thay role tùy ý, sửa/xóa
tài khoản hiện hữu, sửa nội dung, xóa cứng, làm nghiệp vụ học vụ/đăng ký, quản lý danh mục
khoa/bộ môn hoặc chuyển giảng viên. Mọi kiểm tra quyền này phải nằm ở backend.
## Routes and payloads

Path ID chỉ định tài nguyên; backend đọc quan hệ thật. ID, author, role, status và scope
không lấy từ hidden form để tin cậy. version là token chống ghi đè, không cấp quyền.
Mọi POST cần CSRF; actor lấy từ session. Payload whitelist, input không hợp lệ không ghi.

| Method / path dự kiến | Payload chính | Thành công / điều kiện | FR |
|---|---|---|---|
| GET /login | — | 200 form Tabler | 01 |
| POST /login | username,password,CSRF | Redirect tới trang được phép; sai trả thông báo chung | 01 |
| POST /logout | CSRF | Hủy session, redirect login | 01; kỹ thuật session |
| GET /; GET /dashboard | — | 200 dashboard theo vai trò đã xác thực, số liệu truy vấn trong scope từ database: DEAN theo khoa và chỉ đếm đợt chưa qua `student_end`, LECTURER theo tác giả/bộ môn/khoa, STUDENT theo tài khoản/khoa và snapshot registration; DEAN+LECTURER dùng DEAN, ADMIN không xem số liệu học vụ. Các lối tắt không cấp thêm quyền. | 01,03,10–11 |
| GET /periods; GET /periods/{id} | — | 200 theo scope; danh sách DEAN chỉ có đợt chưa qua `student_end`, có lọc theo nhãn giai đoạn và các số chờ duyệt/chờ xét/chờ công bố; chi tiết vẫn truy cập đợt cũ đúng quyền và có thao tác review/publish | 01–04,07,11 |
| POST /periods | name,type,4 dates (`yyyy-MM-dd`),reviewerScoreDeadline,councilReportDate | 303 tới period mới; từ chối giá trị có giờ hoặc ngày đã qua; ngày bắt buộc/null đúng theo loại | 02–04 |
| POST /periods/{id} | name,type,4 dates (`yyyy-MM-dd`),reviewerScoreDeadline,councilReportDate,version | 303 sau sửa hợp lệ; áp dụng khóa và quy tắc chỉ dời muộn hơn của FR-04/FR-15 | 04,15 |
| GET /topics | — | 200 danh sách đợt của tác giả, tách còn hiệu lực/đã hết hạn theo `student_end`; cả hai nhóm đều mở được danh sách đề tài cũ | 03,05 |
| GET /periods/{id}/topics | — | 200; scope/lọc theo trạng thái quyền đã chốt; nút tạo mới chỉ có khi hạn GV đang mở | 05,07 |
| GET /periods/{id}/topics/new; POST /periods/{id}/topics | 5 nội dung,coSupervisorIds | Chỉ trong hạn GV; GET ngoài hạn và POST trực tiếp đều trả WINDOW_CLOSED. Khi hợp lệ, tạo DRAFT thuộc riêng đợt trong path; author/department từ identity, tác giả tự động là GVHD chính, tối đa một GVHD phối hợp lấy từ danh mục đã lọc | 03,05–06,14 |
| GET /topics/{id}; GET /topics/{id}/edit | — | Chỉ dữ liệu/view được policy cho phép | 01,05 |
| POST /topics/{id}/draft | 5 nội dung,coSupervisorIds,version | 303; chỉ tác giả và DRAFT; tác giả luôn là GVHD chính; nếu bị từ chối về DRAFT thì được sửa người phối hợp/gửi lại trong hạn GV | 05–06,14 |
| POST /topics/{id}/submit | version | DRAFT → PENDING_APPROVAL | 05–06,14 |
| POST /topics/{id}/approve | version | PENDING_APPROVAL → APPROVED | 07,14 |
| POST /topics/{id}/reject | version | PENDING_APPROVAL → DRAFT; không tự đặt reason bắt buộc | 14 |
| POST /periods/{id}/topics/publish | — | Khóa đợt; toàn bộ APPROVED của đợt → PUBLISHED nguyên tử; không đổi DRAFT/PENDING_APPROVAL; không có APPROVED thì không ghi | 07,14 |
| GET /groups | — | 200 danh sách đợt của SV, tách “Sắp đăng ký”, “Đang đăng ký” và “Đã kết thúc” theo cửa sổ SV; chỉ đợt đang mở có lối tạo nhóm mới, nhóm/lời mời đã có vẫn có lối xem đúng quyền | 03,08 |
| POST /periods/{id}/groups | CSRF | Trong hạn SV; nhóm mới, người tạo là leader và thành viên đầu tiên | 08–09 |
| GET /groups/{id} | — | Chi tiết nhóm; mọi thành viên hiện tại thấy tên/trạng thái đăng ký PENDING hoặc ACCEPTED và liên kết chi tiết; ACCEPTED có xác nhận đăng ký thành công; các trường khác theo policy | 01,08,10–11 |
| GET /group-invitations | — | SV chỉ thấy lời mời gửi cho chính mình; PENDING sau hạn hiển thị hết hiệu lực | 01,08 |
| POST /groups/{id}/invitations | studentId,version | Leader gửi lời mời giữ slot; chưa tạo membership | 08–09 |
| POST /group-invitations/{id}/accept | version | Chỉ SV được mời; PENDING → ACCEPTED và tạo membership nguyên tử | 08–09 |
| POST /group-invitations/{id}/reject | version | Chỉ SV được mời; PENDING → REJECTED, được phép cả sau hạn | 08–09 |
| POST /group-invitations/{id}/cancel | version | Chỉ leader đúng nhóm; PENDING → CANCELLED trong hạn SV, không active | 08–09 |
| POST /groups/{id}/members/{studentId}/remove | version | Xóa thành viên, giữ leader hợp lệ và tối thiểu 1 | 08 |
| POST /groups/{id}/leader | studentId,version | Chuyển cho thành viên hiện hữu | 08 |
| POST /groups/{id}/leave | version | Thành viên thường tự rời chính nhóm trong hạn SV, không active | 08–09 |
| POST /groups/{id}/dissolve | version | Chỉ leader nhóm một người; giải tán mềm trong hạn SV, không active; giữ lịch sử và loại khỏi nhóm đang hoạt động | 08–09 |
| POST /groups/{id}/registrations | topicId,version | Tạo PENDING mới chỉ khi nhóm đủ đúng 3 thành viên; snapshot được tạo nguyên tử | 10,13 |
| GET /registrations/{id} | — | Trạng thái/kết quả theo policy; thành viên và leader lấy từ snapshot lúc gửi, không từ nhóm hiện tại | 01,10–13 |
| POST /registrations/{id}/withdraw | version | PENDING → WITHDRAWN | 13 |
| POST /registrations/{id}/accept | version | ACCEPTED và auto-reject nguyên tử | 11 |
| POST /registrations/{id}/reject | reason,version | PENDING → REJECTED khi reason không rỗng sau khi trim; thiếu reason trả lỗi validation và không đổi trạng thái | 12 |
| GET /admin/accounts | — | Redirect 303 sang `/admin/accounts/students`; không còn workspace gộp chung | 01 |
| GET /admin/accounts/students | query,facultyId,departmentId,enabled | ADMIN nhận 200 danh sách chỉ STUDENT đã lọc; loại tài khoản do backend khóa theo route | 01 |
| GET /admin/accounts/lecturers | query,facultyId,departmentId,enabled | ADMIN nhận 200 danh sách chỉ LECTURER không gồm DEAN; loại do backend khóa theo route | 01 |
| GET /admin/accounts/deans | query,facultyId,departmentId,enabled | ADMIN nhận 200 danh sách chỉ DEAN; loại do backend khóa theo route | 01 |
| GET /admin/accounts/new | — | ADMIN nhận trang cấp tài khoản riêng gồm form STUDENT/LECTURER/DEAN, import CSV và bảng mã bộ môn | 01 |
| POST /admin/accounts | accountType,username,fullName,password,departmentId | 303 sau khi tạo một tài khoản; password được băm; DEAN nhận DEAN+LECTURER; username trùng hoặc input sai trả 422 và không ghi | 01 |
| POST /admin/accounts/import | multipart `file` | CSV UTF-8 tối đa 1 MB/5.000 dòng, header cố định; kiểm tra toàn file trước khi ghi; có lỗi trả 422 kèm số dòng và rollback toàn bộ, hợp lệ trả 303 | 01 |
| GET /admin/accounts/import-template.csv | — | Tải CSV UTF-8 có BOM với header `account_type,username,full_name,password,department_id`; chỉ ADMIN | 01 |
| POST /admin/moderation/topics/{id}/hide | reason,version | Ẩn mềm đề tài, ghi audit và khóa tài khoản tác giả trong một transaction | 01 |

Không có GET gây mutation. Không route tự chuyển sang nhóm khác; SV rời nhóm cũ rồi chỉ tham
gia nhóm mới sau khi chấp nhận lời mời hợp lệ. Không có route xóa nhóm nhiều người, tái dùng/
sao chép topic hoặc đổi GVHD từ PENDING_APPROVAL trở đi, xóa lịch sử hoặc tự đăng ký
tài khoản. Các registration REJECTED/WITHDRAWN được giữ không thời hạn và đăng ký lại tạo
hàng mới. Gia hạn chỉ đi qua route sửa đợt và phải thỏa đúng quy tắc FR-04/FR-15.

## Response and validation contract

- GET hợp lệ trả 200 HTML UTF-8. POST nghiệp vụ thành công trả 303 tới trang kết quả
  để refresh không gửi lại form. Authentication redirect tuân theo Spring Security.
- Chưa login: redirect /login cho browser, tuyệt đối không ghi. Sai quyền/CSRF: 403.
  ID không tồn tại: 404; không tiết lộ dữ liệu bằng thông báo lỗi.
- Kiểu/cấu trúc sai: 400; thiếu trường hoặc vi phạm validation: 422 với lỗi theo trường,
  giữ nội dung không nhạy cảm đã nhập. Không hiện lại mật khẩu.
- `reason` của từ chối registration thủ công là bắt buộc; giá trị chỉ gồm khoảng trắng được
  coi là thiếu và trả 422 mà không đổi đăng ký khỏi PENDING.
- Stale version, trạng thái không còn hợp lệ hoặc tranh chấp: 409 với thông báo tải lại;
  không trả SQL/stack trace. Unique conflict sau rollback cũng dùng phản hồi này.
- Với rút/chấp nhận đồng thời, request lấy khóa period trước được hoàn tất. Request sau đọc
  lại registration không còn PENDING, trả 409 và không thay đổi kết quả đã commit trước.
- Hết hạn hoặc vượt ràng buộc nghiệp vụ: 422 với mã lỗi nội bộ ổn định
  WINDOW_CLOSED, GROUP_FULL, GROUP_INCOMPLETE, ACTIVE_REGISTRATION_EXISTS, TOPIC_TAKEN,
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
| Đợt | Table, form inputs, badges thời gian; chi tiết có danh sách topic và lời nhắc trước ngày SV | AS-1.*, AS-3.*, AT-01–02,06,12–13 |
| Đề tài | Table, detail card, form, status badge | AS-2.*, AS-3.*, AT-03,12 |
| Nhóm | Member table, select, action modal | AS-4.*, AT-04,06 |
| Đăng ký | Topic list, form confirmation, result badge | AS-5.*, AT-05,11 |
| Quản lý tài khoản | Metric cards, bộ lọc, danh sách responsive, form tạo, import CSV và lỗi theo dòng | AS-1.17, AT-10 |

Menu theo role chỉ hỗ trợ UX. Nút disabled/hidden không thay thế backend.
Danh sách rỗng hiển thị thông báo; POST đang gửi có chỉ báo và hạn chế double-click;
backend vẫn chống duplicate/race. Lỗi gắn label/field, focus vào thông báo lỗi;
không dùng màu badge làm dấu hiệu trạng thái duy nhất. Đây là tiêu chí UX đề xuất.

Mỗi route bảo vệ có MockMvc test cho phép/từ chối và xác nhận DB không đổi khi từ chối.
Browser E2E chứng minh toàn luồng, nhưng không thay thế ca backend gọi trực tiếp
với ID giả, role giả, CSRF thiếu và stale version.
