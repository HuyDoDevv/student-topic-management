# Research: Quản lý Bộ môn

## R1 — Department là phạm vi tổ chức, không phải role

**Decision**: Giữ bốn role `ADMIN`, `STUDENT`, `LECTURER`, `DEAN`. Department là quan hệ dữ liệu.

**Rationale**: Quyền và phạm vi là hai khái niệm khác nhau; thêm role theo từng Bộ môn làm sai
mô hình và gây bùng nổ role.

## R2 — DEAN quản lý Faculty, Topic/Supervisor dùng Department

**Decision**: DEAN tiếp tục kiểm tra `faculty_id`; Topic và giảng viên hướng dẫn kiểm tra
`department_id` nằm trong Faculty đó.

**Rationale**: Trưởng khoa cần duyệt xuyên Bộ môn; yêu cầu cùng Bộ môn chỉ áp dụng luồng đề tài.

## R3 — Council không thuộc Department

**Decision**: Không thêm `councils.department_id`.

**Rationale**: Hội đồng được lập theo đợt/phạm vi Khoa và có thể xử lý đề tài từ nhiều Bộ môn.

## R4 — Chuyển đổi ba bước

**Decision**: Expand nullable → backfill có chứng cứ → enforce bằng migration mới.

**Rationale**: V13 đã xóa dữ liệu Department cũ; không có cơ sở phục hồi chính xác tự động.
Một migration tạo “Chưa phân công” sẽ che mất lỗi dữ liệu và vi phạm quyết định người dùng.

**T007 owner decision (2026-10-12)**: Chủ dự án chọn `Công nghệ phần mềm` trong
`Khoa Công nghệ Thông tin` là Bộ môn thật để gán các LECTURER/DEAN và Topic còn thiếu
Department. Đây là quy tắc gán mới, không phải khôi phục Bộ môn lịch sử. Chỉ cập nhật
giá trị NULL; giữ nguyên các gán đã có. Nếu phép gán dự kiến làm lệch Faculty,
tác giả hoặc GVHD, migration phải thất bại trước khi ghi dữ liệu.

## R5 — Department của Topic do backend suy ra

**Decision**: Khi tạo Topic, service lấy Department từ actor đã xác thực, không tin `departmentId`
do form gửi lên.

**Rationale**: Ngăn giả mạo phạm vi và giữ rule tác giả/GVHD cùng Department nhất quán.

## Alternatives Rejected

- Giữ Faculty-only: không khớp yêu cầu khách hàng.
- Một Council cho mỗi Department: không có yêu cầu và làm sai mô hình hội đồng.
- Sửa lại V13: phá lịch sử Flyway trên database đã áp dụng.
- Backfill theo tên/email: không đủ bằng chứng và có nguy cơ gán sai nghiệp vụ.
