# T001 — Kiểm kê và đề xuất ánh xạ đợt cũ

**Trạng thái**: Kiểm kê hoàn tất; **chưa xác nhận ánh xạ**. Tài liệu này không cho phép chạy backfill T012.

## Nguồn và phạm vi

- Nguồn đọc: `topic_registration_local` tại `127.0.0.1:3307`, theo `SPRING_DATASOURCE_URL` trong `.env` của checkout `feature/005-department-period-scope` ngày 2026-10-13. Không ghi credential vào tài liệu.
- Flyway: version 18, `success=1` (installed rank 18). `registration_periods` chưa có `department_id`.
- Cách đọc: MySQL TCP, giao dịch `START TRANSACTION READ ONLY`, kết thúc `ROLLBACK`; chỉ dùng `SELECT`. Không chạy migration, reset, UPDATE hay DELETE trên database nguồn.
- Database test tách riêng: `topic_registration_test` ở cùng cổng 3307; chỉ dùng cho kiểm thử fixture.

## Tổng hợp

| Chỉ số | Kết quả |
|---|---:|
| Đợt hiện có | 1 |
| Bộ môn hiện có trong Khoa CNTT | 2 |
| Đợt có đúng một Bộ môn suy ra từ đề tài nhất quán | 0 |
| Đợt trộn nhiều Bộ môn | 0 |
| Đợt không có đủ bằng chứng để xác định Bộ môn | 1 |

Hai Bộ môn hiện có trong `Khoa Công nghệ Thông tin` là `Lập Trình Web` (ID 2) và `Lập Trình Python` (ID 3). Chúng là **lựa chọn có thể xem xét**, không phải ánh xạ đã được suy ra từ dữ liệu.

## Kiểm kê từng đợt

| Period ID | Tên / loại | Khoa | Đề tài | Nhóm | Đăng ký | Hội đồng | Thành phần điểm | Điểm | Kết quả | Bộ môn đề xuất | Phân loại |
|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| 1 | Đồ án môn học / COURSE | Khoa Công nghệ Thông tin (ID 1) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **Chưa xác định** | `NO_EVIDENCE` |

Vì đợt 1 không có đề tài, không có Bộ môn của đề tài, tác giả hoặc GVHD để đối chiếu. Tên `Đồ án môn học` và loại `COURSE` không đủ chứng cứ để chọn `Lập Trình Web` hay `Lập Trình Python`. Không gán theo tên đợt hoặc Bộ môn bất kỳ.

## Quy tắc đối chiếu cho các snapshot tiếp theo

1. Với mỗi `registration_periods.id`, gom **tất cả** `topics.department_id`; đối chiếu `departments.faculty_id` với `registration_periods.faculty_id`.
2. Với từng đề tài, đối chiếu `topics.department_id` với Bộ môn/Khoa của `topics.author_id` và từng `topic_supervisors.lecturer_id`.
3. Đếm dữ liệu phụ thuộc theo `period_id`: `student_groups`, `registrations`, `councils`, `score_components`, `evaluations`; `final_results` đếm qua `registrations.id` vì bảng kết quả không có `period_id`.
4. `SINGLE_CANDIDATE_REVIEW`: một Bộ môn duy nhất, mọi đối chiếu hợp lệ; vẫn chờ chủ dự án xác nhận. `NO_EVIDENCE`: không có đề tài, cần quyết định nghiệp vụ. `STOP_CONFLICT`: nhiều Bộ môn hoặc bất nhất Khoa/tác giả/GVHD, không tự backfill.
5. Trước T012, chụp lại snapshot vì dữ liệu có thể đổi; nếu số đợt hoặc phân loại khác tài liệu này, kiểm kê và xác nhận lại.

## Quyết định chờ chủ dự án

| Period ID | Câu hỏi | Quyết định | Người/ngày xác nhận |
|---:|---|---|---|
| 1 | Đợt `Đồ án môn học` thuộc `Lập Trình Web`, `Lập Trình Python`, Bộ môn khác, hay cần xử lý riêng? | **Chưa có** | **Chưa có** |

**STOP gate**: Không triển khai ánh xạ/backfill/enforce cho đợt 1 cho đến khi có quyết định rõ ràng của chủ dự án. T001 chỉ xác nhận kết quả kiểm kê, không xác nhận Bộ môn đích.
