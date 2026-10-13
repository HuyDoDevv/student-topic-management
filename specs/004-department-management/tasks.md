# Tasks: Quản lý Bộ môn

**Input**: Design documents from `/specs/004-department-management/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/web.md`,
`quickstart.md`, `acceptance-matrix.md`

Mỗi task implementation phải viết test trước phần behavior tương ứng. Chỉ đánh dấu `[X]`
khi verification bắt buộc đạt; code đã viết nhưng test bị chặn vẫn để `[ ]`.

## Phase 1: Expand database foundation

- [X] T001 [FR-004-01,FR-004-07–08] Thêm V16 expand-only khôi phục `departments`,
  `user_accounts.department_id` và `topics.department_id` nullable; không thêm
  `councils.department_id`, không backfill. Cập nhật `FacultyScopeMigrationIT` và
  `SchemaSeedMigrationIT` để chứng minh dữ liệu/constraint qua V13–V16. Đã
  verify thành công trên MySQL 8.4 test cô lập.

**Checkpoint**: Flyway validate thành công từ database rỗng và fixture V12/V15; không mất dữ liệu.

## Phase 2: Persistence adoption

- [X] T002 [FR-004-01,FR-004-03–04] Viết persistence test trước rồi thêm `DepartmentEntity`,
  `DepartmentRepository`, mapping nullable trên `UserAccountEntity` và `TopicEntity`; không đổi
  runtime behavior hoặc schema ngoài V16. Đã verify `DepartmentPersistenceIT` trên MySQL
  8.4 và full test suite.

**Checkpoint**: JPA đọc/ghi được Department và các quan hệ nullable; legacy row vẫn load được.

## Phase 3: ADMIN catalog and assignment

- [X] T003 [US1] [FR-004-01–02,FR-004-10] Viết service/web/integration test trước rồi triển khai
  danh sách, tạo, sửa Department trong workspace ADMIN; unique theo Faculty, không route xóa và
  không cho non-ADMIN truy cập. Đã verify trên MySQL 8.4 cổng 3307: 5 service test,
  6 web test và 4 integration test đều pass.
- [X] T004 [US2] [FR-004-02–03] Viết test trước rồi bổ sung Department vào form/service tạo và
  cập nhật LECTURER/DEAN; kiểm tra Department cùng Faculty ở backend. STUDENT/ADMIN không nhận
  Department từ các form này. Đã verify 17 service test, 20 web test, 4 integration test
  trên MySQL 8.4 cổng 3307 và full `./mvnw test`.

**Checkpoint**: ADMIN duy trì được danh mục và cấp tài khoản nhân sự đúng Department.

## Phase 4: Topic and supervisor rules

- [X] T005 [US3] [FR-004-04,FR-004-06] Viết test trước rồi cho Topic mới lấy Department từ tác
  giả đã xác thực; chặn actor thiếu Department/khác Faculty; DEAN vẫn duyệt toàn Faculty.
  Đã verify 6 unit test, 6 web/DB integration test trên MySQL 8.4 cổng 3307 và full
  `./mvnw test`. Nháp legacy thiếu Department chưa được gửi duyệt cho đến T007.
- [X] T006 [US3] [FR-004-05–07] Viết unit/web regression test trước rồi đổi danh mục và validation
  GVHD phối hợp từ cùng Faculty thành cùng Department; giữ tác giả ở slot 1 và chứng minh Student,
  Council, grading không bị đổi sang Department scope. Đã verify unit tests, 11
  `TopicDepartmentIT` web/DB tests trên MySQL 8.4 cổng 3307 và full `./mvnw test`.

**Checkpoint**: Topic mới có Department và mọi supervisor cùng Department; Council không đổi.

## Phase 5: Backfill and enforce

- [X] T007 [US4] [FR-004-08] Theo U-004-08 được chủ dự án xác nhận, tạo hoặc dùng lại
  `Công nghệ phần mềm` thuộc `Khoa Công nghệ Thông tin`, chỉ backfill LECTURER/DEAN và
  Topic còn thiếu Department; integration test chứng minh giữ nguyên gán cũ và dừng
  không ghi dở khi Faculty, tác giả hoặc GVHD mâu thuẫn. Đã verify 5 migration test
  T007, targeted integration suite trên MySQL 8.4 cổng 3307 và full `./mvnw test`.
- [X] T008 [US4] [FR-004-03–05,FR-004-08] Sau khi T007 chứng minh không còn Topic cần thiết NULL,
  tạo migration enforce riêng cho constraint cuối cùng và test nâng cấp/idempotent validation.
  V18 từ chối dữ liệu thiếu/sai Bộ môn trước khi siết `topics.department_id NOT NULL`;
  migration, schema/seed, persistence và các fixture liên quan đã verify trên DB test 3307.

**Checkpoint**: Dữ liệu hiện hữu được ánh xạ có bằng chứng; schema cuối bảo vệ runtime invariant.

## Phase 6: Presentation and acceptance

- [X] T009 [FR-004-06,FR-004-09] Viết MockMvc test trước rồi hiển thị/lọc Department trên các
  danh sách tài khoản, đề tài và moderation phù hợp; mọi query tiếp tục khóa Faculty ở backend.
  Đã verify lọc tài khoản staff và trang DEAN duyệt theo Bộ môn; danh sách đề tài của
  giảng viên hiển thị Bộ môn, thống kê/công bố vẫn áp dụng toàn đợt.
- [ ] T010 Chạy full unit/integration/E2E, kiểm tra migration từ các mốc hỗ trợ, cập nhật
  `acceptance-matrix.md`, `quickstart.md` và tài liệu database; không đóng feature khi còn data gate
  hoặc test bắt buộc chưa đạt.

## Dependencies

```text
T001 → T002 → T003 → T004 → T005 → T006
                                  └──────→ T009
T006 + confirmed mapping → T007 → T008
T008 + T009 → T010
```

- T001–T009 đã hoàn thành; T010 cần plan và approval riêng.
- T007 dùng quy tắc ánh xạ một Bộ môn U-004-08; không suy đoán lịch sử hoặc ghi đè gán cũ.
- T008 không được gộp vào T007 để giữ expand/backfill/enforce có thể kiểm chứng riêng.
- Mỗi task cần plan/approval riêng theo `AGENTS.md`; task chưa check không tự cấp quyền implement.
