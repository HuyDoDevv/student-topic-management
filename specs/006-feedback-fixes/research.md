# Research: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

## Phạm vi nghiên cứu

Tài liệu này ghi các quyết định thiết kế đã chốt cho feature 006 và bằng chứng từ
baseline `develop` tại commit `6d29a78`. Nó không phải bằng chứng implementation
đã hoàn thành và không cấp quyền sửa code/DB ngoài từng task được duyệt.

## R-001 — DEAN là vai trò quản trị độc lập

**Decision**: DEAN mới chỉ có role `DEAN`; cấm phát sinh `DEAN + LECTURER`.
Quyền đọc lịch sử không phụ thuộc vào việc giữ role giảng viên trên tài khoản cũ.

**Rationale**: Baseline tạo DEAN với hai role, trong khi ứng viên chấm/hội đồng
được chọn theo `LECTURER`. Việc chỉ ẩn DEAN trên UI không chặn POST giả mạo và
không sửa authorization.

**Alternatives rejected**:

- Giữ role kép và thêm `if (!DEAN)` ở template: không bảo vệ backend.
- Xóa tài khoản DEAN cũ: làm mất audit và quan hệ lịch sử.

## R-002 — Một DEAN hoạt động trên mỗi Khoa

**Decision**: khóa DEAN cũ trước khi kích hoạt người mới; kiểm tra phải chịu được
hai request concurrent. Dữ liệu lịch sử tiếp tục trỏ tới account cũ.

**Rationale**: check-then-save không có lock có thể cho hai DEAN cùng vượt qua.
Cơ chế cụ thể được quyết định ở plan T002 sau khi kiểm tra transaction và DB,
nhưng acceptance bắt buộc có race test.

## R-003 — Ma trận chấm theo loại đợt

| Loại | GVPB | Người chấm trực tiếp | Hội đồng | Công thức từng SV |
|---|---:|---:|---:|---|
| COURSE | 0 | 1 | 0 | Điểm người chấm |
| NCKH | 0 | 1 | 0 | Điểm người chấm |
| TLCN | 1 | 1 người thứ hai | 0 | `(GVPB + người chấm) / 2` |
| KLTN | 1 | 0 | 3–5 người có quyền chấm | `(GVPB + tổng điểm thành viên) / (1 + số thành viên)` |

**Baseline evidence**: `DirectGradingRules.SUPPORTED_TYPES` hiện là COURSE/NCKH;
`CouncilRules.SUPPORTED_TYPES` hiện là TLCN/KLTN. Feature phải loại TLCN khỏi
hội đồng và không làm thay đổi luồng đúng của COURSE/NCKH.

## R-004 — Mở rộng bảng phản biện hiện hữu bằng migration mới

**Decision**: dùng `V23__support_reviewer_assessments.sql` để mở rộng mô hình từ
baseline V21; không sửa V1. Tạo runtime model cho assignment, report và điểm
phản biện theo sinh viên.

**Baseline evidence**: V1 đã có `reviewer_assignments(registration_id,
lecturer_id)` nhưng chưa có entity/repository, deadline, lifecycle, version,
report metadata hoặc score theo student. `project_reports` là báo cáo của nhóm,
không phải báo cáo GVPB.

**Alternatives rejected**:

- Dùng `evaluations.council_id` cho GVPB: buộc TLCN có hội đồng giả và làm mờ
  vai trò GVPB.
- Lưu điểm GVPB trong JSON/report: không đảm bảo FK, uniqueness và truy vấn đủ
  đầu điểm theo từng sinh viên.
- Sửa migration V1: vi phạm lịch sử Flyway đã dùng.

## R-005 — Một submission phản biện là đơn vị nguyên tử

**Decision**: GVPB nộp PDF và điểm của mọi sinh viên trong một transaction. Chỉ
khi toàn bộ file/điểm/ID/version/deadline hợp lệ mới chuyển assignment sang đã nộp.

**Rationale**: báo cáo không có điểm hoặc điểm thiếu một sinh viên không đủ căn
cứ để mở phân công thứ hai hay tổng hợp. Transaction ngăn trạng thái nửa hoàn tất.

## R-006 — Deadline kết thúc cuối ngày Việt Nam

**Decision**: `reviewer_score_deadline` là ngày nghiệp vụ; thời điểm chốt là cuối
ngày đó theo `Asia/Ho_Chi_Minh`, được tính bằng clock server. Request ở biên đúng
hạn được nhận, request sau biên bị từ chối.

**Rationale**: dùng timezone trình duyệt hoặc UTC date trực tiếp tạo sai lệch ngày.
Test phải cố định clock quanh biên, không phụ thuộc giờ máy chạy test.

## R-007 — Chấm toàn nhóm thay vì từng sinh viên

**Decision**: một `GroupScoreForm` chứa dòng riêng cho toàn bộ sinh viên snapshot
của registration. Lưu nháp/nộp toàn bộ trong một transaction và dùng optimistic
locking.

**Baseline evidence**: route hiện tại là `/lecturer/scoring/{councilId}/
{registrationId}/students/{studentId}` và POST riêng `draft`/`submit`, đúng với
phản hồi phải mở từng sinh viên.

**Security rule**: service lấy lại danh sách sinh viên được chấm từ DB; không tin
danh sách/registration/student/council do form gửi. Payload thừa, thiếu hoặc lặp
student đều bị từ chối.

## R-008 — Trả đề tài đã duyệt về DRAFT có điều kiện

**Decision**: chỉ tác giả được đưa `APPROVED → DRAFT` khi chưa `PUBLISHED`, chưa
có registration và còn trong giai đoạn cho phép. Sau sửa phải `SUBMIT` lại.

**Rationale**: sửa trực tiếp record `APPROVED` làm nội dung đã duyệt khác nội dung
được công bố. DEAN không được thay tác giả sửa nội dung.

## R-009 — Tiến trình sinh viên là projection, không phải state mới

**Decision**: chín bước tiến trình được dẫn xuất từ registration decision, report,
reviewer/direct/council assignment, evaluation completeness và final result.

**Rationale**: lưu thêm một cột status tổng hợp dễ lệch khi nguồn thay đổi. Chỉ
thêm state persistence nếu task chi tiết chứng minh không thể suy ra nhất quán.

## R-010 — Notification target phụ thuộc người nhận

**Decision**: event không mặc định trỏ vào trang quản lý của actor phát sự kiện.
`NotificationTargetResolver` chọn route người nhận có quyền; target đã hết hiệu
lực dẫn về inbox với message có chủ đích.

**Rationale**: cùng một business object có route khác nhau cho DEAN và lecturer.
404 không phân biệt record không tồn tại với nhiệm vụ đã đóng.

## R-011 — Hội đồng KLTN có vòng đời chỉnh sửa rõ ràng

**Decision**: sửa member khi `DRAFT`/`ASSIGNED` và chưa có điểm; thay GVPB khi
chưa nộp report/score; khóa sau khi có điểm hoặc council `ACTIVE`.

**Rationale**: cho sửa mãi làm đổi tập người tham gia sau khi chấm; khóa quá sớm
lại không xử lý được phân công nhầm như feedback.

## R-012 — Lịch bảo vệ dùng clock server

**Decision**: thời gian mới phải `> now`; template có `min` để hỗ trợ nhưng service
kiểm tra lại cả tạo và sửa. Lỗi validation quay lại form, không dùng trang 422 chung.

## R-013 — Tổng hợp bất biến sau công bố

**Decision**: kết quả `PUBLISHED` là snapshot bất biến cho feature 006. Công thức
mới chỉ áp dụng khi tạo/tổng hợp kết quả chưa công bố; điểm kết quả làm tròn hai
chữ số `HALF_UP`.

**Baseline evidence**: `FinalResultEntity` lưu `evaluationSummary`, actor/time
tổng hợp/công bố và `finalScore`; đây là nền để hiển thị căn cứ nhưng phải bổ sung
nguồn GVPB và completeness theo loại đợt.

## R-014 — Quyền xem tiến độ không đồng nghĩa quyền xem điểm nháp

**Decision**: Chủ tịch xem trạng thái và số lượng đã chấm của thành viên trong
hội đồng mình; DEAN xem trong Khoa. Điểm nháp chỉ dành cho chính người chấm và
quyền nghiệp vụ rõ ràng sau submission; sinh viên chỉ xem sau publication.

## R-015 — Danh sách kết quả là projection có phân trang

**Decision**: query hỗ trợ filter server-side theo đợt, loại, đề tài/nhóm, người
chấm, trạng thái; sort theo allow-list; pagination ổn định; view group theo
registration/topic nhưng vẫn giữ mỗi row kết quả theo sinh viên.

**Rationale**: tải toàn bộ rồi lọc/group trong template không mở rộng được và dễ
lộ dữ liệu ngoài scope.

## Câu hỏi đã đóng và giới hạn còn lại

- Đã chốt: DEAN không kiêm giảng viên; TLCN không có hội đồng; KLTN có GVPB và
  hội đồng; deadline cuối ngày Việt Nam; HALF_UP hai chữ số; không tính lại lịch sử.
- Chưa quyết định ở T001: tên cột/index/FK chính xác của V23, DTO shape cuối cùng,
  URL cụ thể nếu code hiện tại buộc dùng convention khác. Các quyết định này phải
  nằm trong plan chi tiết của task tương ứng và không được làm đổi contract nghiệp
  vụ trong [spec.md](spec.md).
