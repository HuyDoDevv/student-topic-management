# Research: Báo cáo, hội đồng, chấm điểm và kết quả

## R-01 — Mở rộng schema placeholder bằng V8

**Decision**: Giữ nguyên các bảng V1 và thêm `V8__implement_review_and_results.sql` để bổ
sung trạng thái, version, department scope, uniqueness và CHECK còn thiếu.

**Rationale**: V1–V7 có thể đã chạy trên database chia sẻ; Flyway yêu cầu migration bất biến.
Các bảng placeholder đã chứa quan hệ nền phù hợp nên không cần tạo hệ bảng song song.

**Alternatives considered**: sửa V1 (vi phạm migration); bỏ qua ràng buộc DB (không đủ bảo
vệ concurrency); tạo bảng v2 trùng lặp (làm mô hình khó hiểu và tăng migration dữ liệu).

## R-02 — Lưu PDF trên filesystem local

**Decision**: Lưu metadata trong MySQL và bytes trong root cấu hình bằng
`REPORT_STORAGE_ROOT`; server sinh UUID storage key, ghi file tạm trong cùng root rồi move
thành file hoàn chỉnh. Tên gốc chỉ dùng cho Content-Disposition đã làm sạch.

**Rationale**: Ứng dụng chỉ chạy local, không có object storage/integration được yêu cầu.
Tách file khỏi DB tránh BLOB lớn và giữ schema V1. Root ngoài source/JAR giúp không commit
báo cáo thật.

**Alternatives considered**: BLOB MySQL (backup/schema nặng); lưu trong classpath/JAR
(không ghi ổn định); S3/MinIO (mở rộng tích hợp ngoài phạm vi).

## R-03 — Thay báo cáo an toàn

**Decision**: Mỗi lần thay tạo một row/version mới, row cũ có `replaced_at`; generated
guard chỉ cho một row hiện hành mỗi registration. File mới được ghi trước metadata; lỗi DB
thì xóa file mới, sau commit giữ row/file cũ như lịch sử nội bộ nhưng chỉ bản hiện hành được
hiển thị/tải qua UI.

**Rationale**: Không thể tạo transaction phân tán giữa filesystem và MySQL. Version lịch sử
tránh xóa dữ liệu trước khi transaction mới chắc chắn thành công và hỗ trợ điều tra lỗi.

**Alternatives considered**: ghi đè cùng đường dẫn (reader có thể thấy file dở); xóa bản cũ
trước insert (mất file nếu DB lỗi); XA transaction (quá mức cho ứng dụng local).

## R-04 — Council scope và khóa giao dịch

**Decision**: Council thuộc đúng period và department, chỉ period TLCN/KLTN. Một council có
nhiều registration cùng period/department; mỗi registration chỉ thuộc một council. Mutation
khóa `registration_periods` trước, sau đó council và child theo ID tăng dần.

**Rationale**: Khớp Q2:C, workflow và protocol khóa đã dùng ở feature 001; cho phép kiểm tra
faculty/department/supervisor nhất quán khi phân công hoặc activate.

**Alternatives considered**: council toàn faculty (không khớp workflow same-department);
một council/registration (trái lựa chọn nhiều nhóm); chỉ optimistic lock (không đủ cho
complete tranh chấp submit).

## R-05 — Một điểm tổng trên component mặc định

**Decision**: Mỗi period TLCN/KLTN có đúng một component hệ thống tên `Điểm hội đồng`,
min 0, max 10. Mỗi council member có một evaluation/registration/student; score DECIMAL
theo bước 0.25, DRAFT hoặc SUBMITTED. Giao diện bỏ số 0 thập phân dư. Final score là mean
của toàn bộ member scores,
làm tròn số học HALF_UP hai chữ số.

**Rationale**: Khớp Q3:A và xác nhận bước 0.25 ngày 2026-09-21; ví dụ
8.5/9/8 → 8.5, đồng thời tái sử dụng bảng
`score_components` V1 mà không thêm weighted rubric ngoài phạm vi.

**Alternatives considered**: nhiều component (người dùng không chọn); Chair nhập điểm thủ
công (không đảm bảo arithmetic mean); tính từ DRAFT (công bố dữ liệu chưa submit).

## R-06 — Tách quyền consolidate và publish

**Decision**: Chỉ Chair hiện tại của council ACTIVE được complete/consolidate khi mọi member
đã SUBMITTED cho mọi assigned registration. Chỉ DEAN đúng faculty được publish council
COMPLETED; publish toàn bộ result READY của council trong một transaction.

**Rationale**: REQ-22 giao consolidate cho Chair, REQ-26 giao publication cho faculty.
Human Workflow vẫn chạy được khi DEAN đồng thời là Chair nhưng backend không suy ra hai quyền
là một.

**Alternatives considered**: DEAN complete mọi council (cấp rộng hơn REQ-22); Chair publish
(không có nguồn); publish từng row (có thể để council công bố dở dang).

## R-07 — Result visibility theo registration snapshot

**Decision**: Student access dựa trên `registration_members` snapshot; chỉ result PUBLISHED.
DEAN xem result trong faculty; assigned council members xem report/scoring nhưng không được
quyền xem danh sách result quản trị. ADMIN không đọc học vụ theo feature 001.

**Rationale**: Giữ lịch sử chính xác nếu group thay đổi và chặn IDOR; không phá chính sách
ADMIN đã xác nhận.

**Alternatives considered**: membership hiện tại (mất quyền của người tham gia ban đầu hoặc
lộ cho người vào sau); public result catalogue (trái lựa chọn own-group privacy).
