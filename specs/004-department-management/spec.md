# Feature Specification: Quản lý Bộ môn

**Feature Branch**: `feature/restore-department-scope`

**Created**: 2026-10-12

**Status**: Approved for planning; implementation follows the task gates in `tasks.md`

**Input**: Khôi phục quản lý Bộ môn theo yêu cầu khách hàng, thay cho hướng Faculty-only
đã triển khai trong feature 001.

## Nguồn và quyết định đã xác nhận

- **E — REQ-08–11, REQ-31**: Đề tài và quan hệ hướng dẫn có phạm vi Bộ môn; tài khoản
  nhân sự được cấp sẵn phạm vi tổ chức.
- **U-004-01 (2026-10-12)**: Khôi phục Department làm đơn vị trực thuộc Faculty.
- **U-004-02**: Department không phải role; không thêm role `DEPARTMENT`.
- **U-004-03**: ADMIN quản lý danh mục Bộ môn và gán Bộ môn cho LECTURER/DEAN.
- **U-004-04**: DEAN tiếp tục quản lý toàn Khoa, không bị giới hạn vào một Bộ môn khi
  thực hiện nghiệp vụ trưởng khoa.
- **U-004-05**: Mỗi đề tài thuộc đúng một Bộ môn; tác giả và GVHD phối hợp phải cùng Bộ môn.
- **U-004-06**: Council thuộc RegistrationPeriod/Faculty, không thuộc Department; một Bộ môn
  không đồng nghĩa có một hội đồng riêng.
- **U-004-07**: Không sửa migration V1–V15 và không tạo Bộ môn giả để backfill.
- **U-004-08 (2026-10-12)**: Chủ dự án xác nhận Bộ môn thật `Công nghệ phần mềm`
  thuộc `Khoa Công nghệ Thông tin` làm đích backfill T007. Chỉ gán LECTURER/DEAN
  và Topic có `department_id = NULL`; giữ nguyên mọi Department đã được gán.
  Nếu tác giả/GVHD hoặc Faculty mâu thuẫn với đích này, dừng migration để đối chiếu.

## Phạm vi

### Trong phạm vi

- Khôi phục schema, entity và repository Department.
- Quản lý danh mục Bộ môn trong workspace ADMIN.
- Gán Bộ môn cho LECTURER/DEAN.
- Gắn đề tài với Bộ môn của tác giả và kiểm tra GVHD cùng Bộ môn.
- Backfill có dữ liệu đối chiếu, siết constraint sau backfill và bổ sung hiển thị/bộ lọc.

### Ngoài phạm vi

- Role mới cho Bộ môn hoặc Trưởng Bộ môn.
- Gắn Department vào Council hoặc tạo một Council cho mỗi Department.
- Chuyển Bộ môn hàng loạt khi còn trách nhiệm học vụ đang hoạt động.
- Tự suy đoán Bộ môn từ tên, email, đề tài hoặc tạo bản ghi “Chưa phân công”.
- Thay đổi quyền nghiệp vụ cấp Khoa của DEAN.

## User Stories & Acceptance Scenarios

### US1 — ADMIN quản lý danh mục Bộ môn (P1)

ADMIN xem, tạo và cập nhật Bộ môn thuộc Khoa để có danh mục chuẩn dùng khi cấp tài khoản.

1. Tạo tên hợp lệ trong một Khoa thì Bộ môn được lưu và xuất hiện trong danh sách.
2. Tên rỗng hoặc trùng trong cùng Khoa bị từ chối mà không ghi dữ liệu.
3. Cùng tên ở hai Khoa khác nhau được phép khi hệ thống mở rộng nhiều Khoa.
4. Người không có ADMIN bị backend từ chối.

### US2 — ADMIN gán Bộ môn cho giảng viên (P1)

ADMIN chọn Bộ môn khi cấp hoặc cập nhật tài khoản LECTURER/DEAN.

1. Bộ môn phải tồn tại và thuộc cùng Khoa với tài khoản.
2. STUDENT chỉ dùng phạm vi Khoa; ADMIN không cần Faculty/Department.
3. Tài khoản DEAN vẫn có role DEAN+LECTURER nhưng dùng quyền trưởng khoa trên toàn Khoa.
4. Request giả mạo Department khác Khoa bị từ chối tại service.

### US3 — Đề tài và GVHD theo Bộ môn (P1)

Khi giảng viên tạo đề tài, backend lấy Bộ môn từ tài khoản đã xác thực và chỉ cho chọn
GVHD phối hợp đang hoạt động, có role LECTURER, cùng Bộ môn.

1. Client không thể tự chọn Department khác với tác giả.
2. Tác giả thiếu Department không thể tạo/gửi đề tài mới và nhận thông báo rõ ràng.
3. Topic mới lưu đúng một Department; tác giả tiếp tục là GVHD chính slot 1.
4. GVHD phối hợp khác Department, khác Khoa, bị khóa hoặc không có role đều bị từ chối.
5. DEAN tiếp tục duyệt đề tài toàn Khoa; Department chỉ là dữ liệu lọc/phân loại.

### US4 — Chuyển đổi dữ liệu an toàn (P1)

Hệ thống nâng cấp từ schema Faculty-only mà không mất dữ liệu và chỉ siết ràng buộc sau
khi mọi bản ghi cần thiết đã được gán Department bằng dữ liệu được xác nhận.

1. V16 khôi phục schema nullable mà không backfill hoặc tạo dữ liệu giả.
2. Tài khoản/đề tài cũ vẫn tồn tại và có `department_id = NULL` sau bước expand.
3. T007 dùng quyết định ánh xạ một Bộ môn U-004-08; không suy đoán Bộ môn lịch sử
   đã bị V13 xóa, không ghi đè Department hiện có và dừng khi phát hiện mâu thuẫn.
4. `topics.department_id` chỉ chuyển sang bắt buộc sau khi không còn Topic NULL.

## Functional Requirements

- **FR-004-01**: Department thuộc đúng một Faculty; tên trim không rỗng và duy nhất trong Faculty.
- **FR-004-02**: Chỉ ADMIN quản lý danh mục và gán Department; không tạo role mới.
- **FR-004-03**: LECTURER/DEAN phải có Department hợp lệ trước khi tham gia luồng đề tài mới.
- **FR-004-04**: Topic mới lấy Department từ tác giả ở backend và thuộc đúng một Department.
- **FR-004-05**: Mọi TopicSupervisor phải cùng Department với Topic tại thời điểm phân công.
- **FR-004-06**: DEAN tiếp tục đọc và xử lý mọi Department trong Faculty của mình.
- **FR-004-07**: STUDENT tiếp tục được scope theo Faculty; Council không có `department_id`.
- **FR-004-08**: Migration phải theo expand → backfill có chứng cứ → enforce; không sửa V1–V15.
- **FR-004-09**: Danh sách liên quan hiển thị/lọc Department nhưng vẫn kiểm tra quyền ở backend.
- **FR-004-10**: Không được xóa Department đang được tài khoản hoặc Topic tham chiếu.

## Success Criteria

- Migration từ V12/V15 đến phiên bản mới giữ nguyên số tài khoản, đề tài và hội đồng.
- 100% Topic mới có Department hợp lệ sau khi runtime rule được bật.
- Không thể gán tài khoản hoặc GVHD chéo Faculty/Department qua request trực tiếp.
- DEAN vẫn xử lý được đề tài của mọi Department trong Faculty.
- Không schema, entity, route hoặc UI nào gắn Department vào Council.

## Open Data Gate

U-004-08 đã chốt đích backfill cho T007 mà không cần danh sách ánh xạ từng dòng.
T008 vẫn phải chờ bằng chứng không còn Topic cần thiết `department_id = NULL` và
không có mâu thuẫn sau backfill; không tự gán dữ liệu của Khoa khác.
