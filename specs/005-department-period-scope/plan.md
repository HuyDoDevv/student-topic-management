# Implementation Plan: Đợt đăng ký theo Bộ môn

**Branch**: `feature/005-department-period-scope` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

## Summary

Đợt hiện gắn với Khoa, còn đề tài/GVHD đã có Bộ môn từ feature 004. Feature 005 bổ sung một Bộ môn cho mỗi đợt, kiểm tra đề tài và giảng viên theo Bộ môn của đợt, giữ quyền toàn Khoa của DEAN và STUDENT, đồng thời giới hạn ngoại lệ người chấm/hội đồng theo phân công cụ thể. Chuyển đổi theo `expand → inventory/confirmed mapping → runtime adoption → enforce → acceptance`; không sửa migration V1–V18, không tự backfill dữ liệu mơ hồ.

## Technical Context

- Java 21, Spring Boot MVC, Spring Data JPA, Thymeleaf, Spring Security.
- MySQL 8.4 LTS; Flyway là source of truth. Bản hiện tại có V1–V18 và `registration_periods.faculty_id`, chưa có `registration_periods.department_id`.
- Luồng chuẩn: Controller → Service → Repository → Database. Quyết định phạm vi/quyền đặt ở service; repository chỉ truy vấn có tham số phạm vi.
- Test: JUnit 5, Mockito, MockMvc, MySQL integration/E2E trên DB test riêng cổng 3307.
- Baseline 004: T001–T009 đã check, T010 còn mở do full integration/E2E chưa đạt; không xem 005 là đã có baseline acceptance hoàn chỉnh.

## Constitution Check

| Principle | Design gate |
|---|---|
| I–II nguồn/truy vết | U-005-01–07 tách khỏi REQ của PDF; [acceptance-matrix.md](acceptance-matrix.md) ánh xạ FR→task→evidence. |
| III–IV quyền/đầu vào | Form có `departmentId` nhưng service tải lại Bộ môn, kiểm tra Khoa/role/trạng thái và khóa URL trực tiếp; ngoại lệ người chấm chỉ theo phân công. |
| V layering | Controller không gọi repository trực tiếp; business scope ở service. |
| VI đồng thời | Kiểm tra cùng transaction/lock nơi đổi Bộ môn đợt, phân công chấm và kích hoạt DEAN; cần test race. |
| VII migration | Chỉ thêm migration mới, expand/backfill/enforce tách bước và có data gate. |
| VIII testing | Mỗi user story có kiểm thử hợp lệ, từ chối quyền và kịch bản độc lập trong [quickstart.md](quickstart.md). |
| IX secrets | Chỉ dùng `TEST_DATABASE_*`; không ghi credential vào tài liệu/fixture. |
| X evidence | Không tick task hay tuyên bố hoàn thành khi gate dữ liệu hoặc suite bắt buộc còn thất bại. |

## Delivery Sequence

1. **Setup/inventory**: chụp baseline schema và kiểm kê đợt cũ, bao gồm đợt có đề tài nhiều Bộ môn; lập bảng ánh xạ chờ chủ dự án duyệt. Không ghi DB thật ở bước này.
2. **Expand foundation**: migration mới thêm quan hệ Bộ môn nullable cho đợt và persistence tương ứng; giữ dữ liệu cũ đọc được.
3. **US1 — DEAN/period**: chọn Bộ môn trong Khoa khi tạo đợt, hiển thị danh sách/chi tiết, chặn đổi Bộ môn sau khi mở hoặc có dữ liệu phụ thuộc.
4. **US2 — LECTURER/topic**: lọc danh sách và khóa truy cập trực tiếp; kiểm tra tác giả/đề tài/GVHD cùng Bộ môn đợt.
5. **US3 — STUDENT**: giữ danh sách đợt toàn Khoa; hiển thị Bộ môn, chỉ trả đề tài đã công bố.
6. **US4 — grading/council**: hội đồng suy ra Bộ môn từ đợt; người chấm khác Bộ môn chỉ xem đúng nhiệm vụ hợp lệ, GVHD không chấm đề tài mình.
7. **US5 — DEAN uniqueness**: chặn tạo/kích hoạt DEAN thứ hai đang hoạt động cùng Khoa, kể cả thao tác đồng thời; thay người không đổi đợt cũ.
8. **Backfill/enforce**: chỉ sau bảng ánh xạ được xác nhận và migration test bảo toàn dữ liệu, thêm migration enforce mới. Đợt trộn Bộ môn phải dừng để quyết định riêng.
9. **Acceptance**: chạy unit, integration, E2E, migration upgrade và rà ma trận. Lỗi baseline 004 phải được phân loại, không tự sửa ngoài task.

## Project Structure

```text
specs/005-department-period-scope/
├── spec.md
├── checklists/requirements.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/web.md
├── quickstart.md
├── tasks.md
└── acceptance-matrix.md
```

Các vùng code dự kiến cho **task triển khai sau này**: `src/main/resources/db/migration/`, `entity/`, `repository/`, `service/`, `controller/`, `dto/`, `templates/periods/`, `templates/topics/`, `templates/councils/`, và test unit/web/integration/E2E tương ứng. Tài liệu này không cấp quyền sửa các vùng đó.

## Risk Controls and Gates

- `registration_periods.department_id` phải cùng Khoa với `faculty_id`; đề tài/đăng ký/hội đồng hiện hữu không được mất hoặc âm thầm chuyển phạm vi.
- Đợt cũ không có ánh xạ duy nhất, đặc biệt đợt chứa đề tài của nhiều Bộ môn, là **STOP gate** trước backfill/enforce.
- Không thay đổi quyền STUDENT theo Bộ môn; không tạo `councils.department_id` hay danh mục môn học cụ thể.
- Không xem phân công hội đồng là quyền xem cả đợt; quyền phải biến mất khi phân công không còn hiệu lực.
- US5 có thể đòi hỏi cơ chế nhất quán DB/transaction cho DEAN hoạt động; chốt trong task riêng trước khi sửa protected schema/auth.
- Mỗi task cần plan, approval và verify riêng theo `AGENTS.md`; checklist trong `tasks.md` không tự cấp quyền implementation.
