# Implementation Plan: Quản lý Bộ môn

**Branch**: `feature/restore-department-scope` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

## Summary

Chuyển hệ thống từ Faculty-only về mô hình Faculty → Department theo chiến lược expand,
adopt, backfill, enforce. V16 chỉ khôi phục bảng/cột nullable. Các task sau mới nối JPA,
thêm workspace ADMIN, áp dụng rule đề tài/GVHD, backfill có chứng cứ và siết NOT NULL.
Council tiếp tục thuộc RegistrationPeriod/Faculty.

## Technical Context

- Java 21, Spring Boot MVC, Spring Data JPA, Thymeleaf, Spring Security.
- MySQL 8.4 LTS; Flyway là source of truth.
- Luồng chuẩn: Controller → Service → Repository → Database.
- Test: JUnit 5, Mockito, MockMvc, MySQL integration profile.
- Không sửa migration V1–V15; không dùng Hibernate để mutate schema.

## Constitution Check

| Principle | Result | Evidence |
|---|---|---|
| I–II nguồn/truy vết | PASS | Phân biệt REQ và quyết định U-004; ma trận acceptance truy vết FR. |
| III phân quyền | PASS có điều kiện | ADMIN và DEAN được kiểm tra trong service; cần test request giả mạo. |
| IV validation | PASS | Tên, Faculty/Department consistency và supervisor scope được kiểm tra backend. |
| V layering | PASS | Controller không truy cập repository trực tiếp. |
| VI đồng thời | PASS có điều kiện | Unique DB bảo vệ tên; update account/topic cần transaction và test stale state. |
| VII migration | PASS | Expand → backfill → enforce, không sửa migration đã dùng. |
| VIII testing | PASS có điều kiện | T001 chờ MySQL test; mỗi task có test trước implementation. |
| IX secrets | PASS | Chỉ dùng biến `TEST_DATABASE_*`, không lưu credential. |
| X evidence | PASS có điều kiện | Chỉ tick task sau khi test tương ứng đạt. |

## Delivery Sequence

1. **Expand**: V16 khôi phục schema nullable, bảo toàn dữ liệu.
2. **Persistence adoption**: entity/repository/mapping đọc được Department nullable.
3. **Catalog and assignment**: ADMIN quản lý Department và gán cho LECTURER/DEAN.
4. **Runtime adoption**: Topic và supervisor dùng Department; DEAN vẫn scope Faculty.
5. **Backfill**: chỉ dùng bảng ánh xạ được xác nhận.
6. **Enforce**: migration mới siết constraint sau khi dữ liệu sạch.
7. **Presentation and acceptance**: hiển thị, lọc, E2E, tài liệu.

## Project Structure

```text
.project-kit/features/004-department-management/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/web.md
├── quickstart.md
├── acceptance-matrix.md
└── tasks.md
```

Các vùng code dự kiến ở task sau: `entity/`, `repository/`, `service/`, `controller/`,
`templates/admin/`, migration Flyway và test unit/web/integration tương ứng.

## Risk Controls

- Không backfill theo suy đoán; T007 có data gate riêng.
- Giữ cột nullable trong giai đoạn chuyển tiếp để deploy không phá dữ liệu cũ.
- Không thay rule Faculty cho period, student, council, grading và result.
- Kiểm tra Department và Faculty trong cùng transaction khi tạo Topic/gán tài khoản.
- Không xóa Department đang được tham chiếu.
