# Acceptance Matrix: Quản lý Bộ môn

| Requirement | Scenario | Planned evidence | Task | Status |
|---|---|---|---|---|
| FR-004-01,08 | V16 khôi phục schema, giữ dữ liệu, không fake backfill | `FacultyScopeMigrationIT`, `SchemaSeedMigrationIT` | T001 | Complete |
| FR-004-01 | Tên Department hợp lệ/unique theo Faculty | Repository/service/integration tests | T002–T003 | Complete |
| FR-004-02–03 | Chỉ ADMIN gán Department đúng Faculty cho LECTURER/DEAN | Unit + MockMvc security tests | T004 | Planned |
| FR-004-04 | Topic lấy Department từ actor, không tin request | Topic author unit/web tests | T005 | Planned |
| FR-004-05 | Tác giả/GVHD phối hợp cùng Department | Topic rule/unit/web tests | T006 | Planned |
| FR-004-06 | DEAN quản lý mọi Department trong Faculty | Moderation regression tests | T005–T006 | Planned |
| FR-004-07 | Student/Council không bị đổi scope | Registration/Council regression tests | T006,T010 | Planned |
| FR-004-08 | Backfill có evidence trước enforce | Migration integration tests | T007–T008 | Data gate |
| FR-004-09 | Hiển thị/lọc không làm lộ cross-Faculty | MockMvc/E2E tests | T009 | Planned |
| FR-004-10 | Không xóa Department đang được dùng | Service/integration tests; không có route xóa | T003 | Complete |

## Current Evidence

- Ngày 2026-10-12, V16 đã migrate/validate thành công trên MySQL 8.4 test cô lập
  từ database rỗng, fixture V6, V12 và V15.
- `./mvnw verify -Pintegration -Dit.test=SchemaSeedMigrationIT,FacultyScopeMigrationIT`:
  4 test pass, 0 failure, 0 error.
- `./mvnw test`: 262 test pass, 0 failure, 0 error.
- `FacultyScopeMigrationIT` xác nhận V13 giữ business rows và V16 không tự tạo
  Department/backfill `department_id`; `SchemaSeedMigrationIT` xác nhận schema V16, constraint,
  trigger và lịch sử migration V1–V16.
- `DepartmentPersistenceIT`: 3 test pass, 0 failure, 0 error trên MySQL 8.4; xác nhận
  repository truy vấn theo Faculty, account/topic lưu được Department và legacy row NULL
  vẫn load được.
- Sau T002, `./mvnw test`: 262 test pass, 0 failure, 0 error; runtime tạo account/topic
  hiện tại vẫn giữ `department_id = NULL` cho đến T004/T005.
- T003 trên MySQL 8.4 cổng 3307: `./mvnw -q verify -Pintegration
  -Dit.test=AdminDepartmentCatalogIT,DepartmentPersistenceIT,AccountProvisioningIT` exit 0.
  Báo cáo T003: `AdminDepartmentServiceTest` 5 test, `AdminDepartmentControllerTest`
  6 test, `AdminDepartmentCatalogIT` 4 test; tổng 15 test, 0 failure, 0 error.
