# Acceptance Matrix: Quản lý Bộ môn

| Requirement | Scenario | Planned evidence | Task | Status |
|---|---|---|---|---|
| FR-004-01,08 | V16 khôi phục schema, giữ dữ liệu, không fake backfill | `FacultyScopeMigrationIT`, `SchemaSeedMigrationIT` | T001 | Complete |
| FR-004-01 | Tên Department hợp lệ/unique theo Faculty | Repository/service/integration tests | T002–T003 | Complete |
| FR-004-02–03 | Chỉ ADMIN gán Department đúng Faculty cho LECTURER/DEAN | Unit + MockMvc security tests | T004 | Complete |
| FR-004-04 | Topic lấy Department từ actor, không tin request | `TopicAuthorServiceTest`, `TopicDepartmentIT` | T005 | Complete |
| FR-004-05 | Tác giả/GVHD phối hợp cùng Department | Topic rule/unit/web tests | T006 | Complete |
| FR-004-06 | DEAN quản lý mọi Department trong Faculty | `TopicDepartmentIT`; Faculty-wide regression | T005–T006 | Complete |
| FR-004-07 | Student/Council không bị đổi scope | Registration/Council regression tests | T006,T010 | T006 verified; T010 pending |
| FR-004-08 | Backfill có evidence trước enforce | Migration integration tests | T007–T008 | T007–T008 verified on isolated test DB |
| FR-004-09 | Hiển thị/lọc không làm lộ cross-Faculty | MockMvc/DB tests | T009 | Verified on isolated test DB; full E2E pending T010 |
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
- T004 trên MySQL 8.4 cổng 3307, DB `topic_registration_test`: `./mvnw -q verify
  -Pintegration -Dtest=AdminAccountServiceTest,AdminAccountControllerTest
  -Dit.test=AccountProvisioningIT` exit 0. Báo cáo: 17 service test, 20 web test,
  4 integration test; 0 failure, 0 error. `./mvnw -q test` exit 0.
- T004 chỉ gán Department lần đầu cho staff cũ; chuyển tài khoản đã thuộc Bộ môn khác
  không nằm trong phiên bản này và bị service từ chối.
- T005: `TopicAuthorServiceTest` 6 test và `TopicDepartmentIT` 6 test đều pass; MySQL
  8.4 `127.0.0.1:3307/topic_registration_test`, 0 failure, 0 error. Test xác nhận
  Topic mới lấy Department từ tác giả, bỏ qua `departmentId` giả mạo, từ chối actor thiếu
  Department/khác Faculty, không gửi nháp cũ thiếu Department, và DEAN duyệt xuyên Bộ môn
  trong Khoa nhưng không qua Khoa khác. Full `./mvnw test` exit 0.
- T006: `TopicRulesTest`, `TopicReviewRulesTest`, `TopicAuthorServiceTest`,
  `CouncilServiceTest`, `DirectGradingServiceTest` và `TopicDepartmentIT` đều pass.
  Form chỉ liệt kê GVHD cùng Department; backend từ chối ID khác Department khi tạo/sửa,
  kiểm tra lại lúc gửi duyệt; tác giả ở slot 1, GVHD phối hợp ở slot 2. Student vẫn thấy
  đề tài từ hai Department cùng Faculty; Council và grading giữ Faculty scope.
  `TopicDepartmentIT`: 11 test pass trên MySQL 8.4
  `127.0.0.1:3307/topic_registration_test`; full `./mvnw test` exit 0.
- Full `./mvnw verify -Pintegration` chưa đạt: 50 integration test, 3 failure và
  10 error ở fixture/migration test ngoài T006 (trùng tên Faculty khi tạo fixture,
  assertion schema cũ, seeder dùng cột chưa có ở V7). Không coi full integration/E2E
  của T010 là hoàn thành; cần xử lý bằng plan riêng.
- T007: Chủ dự án xác nhận gán LECTURER/DEAN và Topic còn thiếu Department về
  `Công nghệ phần mềm` thuộc `Khoa Công nghệ Thông tin`, giữ nguyên gán đã có.
  `DepartmentBackfillMigrationIT`: 5 test pass trên MySQL 8.4
  `127.0.0.1:3307/topic_registration_test`, gồm tạo/dùng lại Department, giữ nguyên
  bản ghi đã gán và rollback khi Faculty/tác giả/GVHD mâu thuẫn. Targeted
  `./mvnw -q verify -Pintegration -Dtest=TopicRulesTest
  -Dit.test=DepartmentBackfillMigrationIT,FacultyScopeMigrationIT,SchemaSeedMigrationIT,AccountProvisioningIT`
  exit 0; full `./mvnw -q test` exit 0. Chưa chạy migration trên DB dev/shared;
  full integration/E2E vẫn chưa nghiệm thu.
- T008: `DepartmentEnforceMigrationIT` (3 test) xác nhận V18 nâng cấp hợp lệ,
  validate idempotent và từ chối Topic NULL hoặc tác giả khác Bộ môn trước khi
  đổi schema. V18 giữ FK Department và đặt `topics.department_id NOT NULL`.
  Targeted `./mvnw -q verify -Pintegration -Dtest=PeriodContractTest
  -Dit.test=SchemaSeedMigrationIT,DepartmentPersistenceIT,RegistrationNotificationIT,DepartmentEnforceMigrationIT`
  exit 0 trên MySQL 8.4 DB test `127.0.0.1:3307/topic_registration_test`;
  `AuthorizationMatrixTest` chạy riêng đạt 5/5 và full `./mvnw -q test` exit 0.
  Test cố ý cho V18 thất bại tạo Flyway history `success=0` trên MySQL;
  cần reset DB test trước khi chạy suite khác. Chưa chạy migration trên DB dev/shared,
  chưa verify full integration/E2E T010.
- T009: ADMIN xem/lọc Bộ môn trên danh sách LECTURER/DEAN; danh sách STUDENT không
  nhận bộ lọc này. Danh sách đề tài của giảng viên hiện Bộ môn; DEAN lọc trang duyệt
  theo Bộ môn trong Khoa của đợt nhưng thống kê và công bố vẫn toàn đợt.
  `AdminAccountServiceTest` 18 test, `AdminAccountControllerTest` 21 test,
  `TopicDepartmentIT` 13 test và `AccountProvisioningIT` 4 test đều đạt trên DB test
  MySQL 8.4 cổng 3307. Full `./mvnw -q test` exit 0; full integration/E2E T010 chưa chạy.
