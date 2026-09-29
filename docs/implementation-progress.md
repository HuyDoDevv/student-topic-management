# Implementation Progress

File này ghi lại các task đã triển khai theo yêu cầu hiện tại của chủ dự án. Mỗi task chỉ
được cập nhật sau khi đã inspect, có plan được duyệt, implement và chạy verification phù hợp.

## TASK-001 — Bỏ chức năng import tài khoản

- Ngày: 2026-09-28
- Trạng thái: Completed
- Commit: `7b9defb` (`feat(admin): remove account import`)
- Yêu cầu: ADMIN chỉ tạo từng tài khoản; loại bỏ upload CSV và file mẫu import.
- Ngoài phạm vi: schema, role, Khoa/Bộ môn, authorization và nội dung form tạo từng tài khoản.

### File đã thay đổi

- `src/main/java/vn/edu/topicregistration/controller/AdminAccountController.java`
- `src/main/java/vn/edu/topicregistration/service/AdminAccountService.java`
- `src/main/java/vn/edu/topicregistration/service/DashboardService.java`
- `src/main/resources/templates/admin/accounts/form.html`
- `src/test/java/vn/edu/topicregistration/unit/AdminAccountServiceTest.java`
- `src/test/java/vn/edu/topicregistration/web/AdminAccountControllerTest.java`
- `specs/001-topic-registration/spec.md`
- `specs/001-topic-registration/contracts/web.md`
- `specs/001-topic-registration/data-model.md`
- `specs/001-topic-registration/acceptance-matrix.md`
- `docs/implementation-progress.md`

### Thay đổi

- Xóa `POST /admin/accounts/import` và `GET /admin/accounts/import-template.csv`.
- Xóa xử lý, validation, parser và DTO dành riêng cho CSV trong service.
- Xóa form upload, nút tải file mẫu và bảng mã Bộ môn dùng cho CSV.
- Giữ nguyên luồng tạo từng tài khoản STUDENT, LECTURER và DEAN.
- Cập nhật Dashboard, spec, web contract, data model và acceptance matrix theo quyết định mới.

### Verification

- `env -u JAVA_HOME ./mvnw clean -Dtest=AdminAccountServiceTest,AdminAccountControllerTest,DashboardServiceTest test`: PASS, 17/17 test.
- `env -u JAVA_HOME ./mvnw test`: 220 test, 208 đạt, 12 lỗi môi trường do thiếu `TEST_DATABASE_URL` trong `AuthorizationMatrixTest` và `PeriodContractTest`; không có test failure.
- Hai endpoint import cũ được kiểm tra trả 404.
- `git diff --check`: PASS.

### Chưa verify / rủi ro còn lại

- Chưa chạy được 12 test dùng MySQL vì môi trường chưa cung cấp `TEST_DATABASE_URL`.
- Các URL import cũ không còn tương thích và sẽ trả 404 theo yêu cầu.
- Không có migration hoặc thay đổi dữ liệu.

## TASK-002 — Loại bỏ Bộ môn, dùng phạm vi Khoa

- Ngày: 2026-09-28
- Trạng thái: Completed; verification MySQL chờ môi trường test
- Commit: `HEAD` (`refactor(scope): replace departments with faculty`)
- Yêu cầu: Loại bỏ hoàn toàn Department/Bộ môn; giữ Faculty, seed một Khoa Công nghệ Thông tin, chưa có CRUD khoa; bảo toàn luồng đề tài, đăng ký, báo cáo và chấm điểm.
- Ngoài phạm vi: thêm/sửa/xóa khoa, thay role, đổi công thức chấm điểm và push.

### File đã thay đổi

- Migration: `src/main/resources/db/migration/V13__replace_departments_with_faculty_scope.sql`.
- Entity và DTO: `DepartmentEntity.java` (xóa), `UserAccountEntity.java`, `TopicEntity.java`, `CouncilEntity.java`, `AdminAccountForm.java`, `CouncilForm.java`.
- Repository: `DepartmentRepository.java` (xóa), `FacultyRepository.java` và các repository account/topic/registration/council/dashboard liên quan scope.
- Security: `AccountDetailsService.java`, `AccountPrincipal.java`, `AuthorizationPolicy.java`.
- Service: `AdminAccountService.java` và các service/rules của dashboard, topic, group, registration, council và direct grading.
- Controller: các controller account, period, topic author, registration, council và direct grading.
- Dữ liệu mẫu: `src/main/java/vn/edu/topicregistration/seed/LocalDemoSeeder.java`.
- Giao diện: form/danh sách account, topic, registration, council và direct grading trong `src/main/resources/templates/`.
- Test/schema: `FacultyScopeMigrationIT.java`, fixture integration, unit/web tests liên quan và `src/test/resources/database/full-schema-check.sql`.
- Tài liệu: các artifact trong `specs/001-topic-registration/`, `specs/002-review-results/`, `specs/003-notifications/` và `docs/implementation-progress.md`.

### Thay đổi

- Thêm migration `V13__replace_departments_with_faculty_scope.sql`: hợp nhất dữ liệu học vụ vào Khoa Công nghệ Thông tin, thêm `user_accounts.faculty_id`, bỏ `department_id` khỏi account/topic/council và xóa bảng `departments`.
- Xóa `DepartmentEntity` và `DepartmentRepository`; account học vụ liên kết trực tiếp `FacultyEntity`; topic/council kế thừa khoa từ registration period.
- Đổi toàn bộ authorization, repository query và business rule “cùng bộ môn” thành “cùng khoa”.
- Bỏ lựa chọn Bộ môn khỏi form tạo tài khoản và hội đồng; backend tự gán khoa CNTT mặc định khi ADMIN tạo STUDENT/LECTURER/DEAN.
- Cập nhật seeder, template, test fixture, full-schema check và tài liệu của luồng đăng ký/đề xuất/báo cáo/chấm điểm.
- Thêm `FacultyScopeMigrationIT` để kiểm tra nâng cấp V12→V13, không mất dữ liệu và vẫn hỗ trợ `faculty_id` cho mở rộng nhiều khoa sau này.

### Verification

- `env -u JAVA_HOME ./mvnw clean -DskipTests compile`: PASS.
- `env -u JAVA_HOME ./mvnw -DskipTests test-compile`: PASS.
- Bộ test mục tiêu cho authorization, topic, council, direct grading, account, controller và schema contract: PASS, 78/78 test.
- `env -u JAVA_HOME ./mvnw test`: 221 test, 209 đạt, 12 lỗi môi trường do thiếu `TEST_DATABASE_URL`; 0 failure.
- Không còn tham chiếu Department/Bộ môn trong `src/main/java` hoặc templates runtime.
- `git diff --check`: PASS.

### Chưa verify / rủi ro còn lại

- Chưa chạy được migration/integration/concurrency/E2E trên MySQL vì `TEST_DATABASE_URL` chưa được cấu hình; đặc biệt V13 và số lượng constraint trong schema cần được xác nhận trên MySQL 8.4 test riêng.
- V13 cố ý hợp nhất mọi dữ liệu khoa cũ về Khoa Công nghệ Thông tin và xóa các khoa khác theo phạm vi một khoa hiện tại; cần backup trước khi áp dụng lên database có dữ liệu cần giữ tách khoa.

## TASK-003 — Tách luồng tạo tài khoản theo vai trò

- Ngày: 2026-09-29
- Trạng thái: Completed; verification MySQL chờ môi trường test
- Commit: Chưa commit theo yêu cầu
- Yêu cầu: Tách việc tạo STUDENT, LECTURER và DEAN thành ba trang/route riêng; STUDENT dùng MSSV làm username và sinh email theo domain trường; LECTURER/DEAN chưa nhập email khi ADMIN tạo.
- Ngoài phạm vi: trang để giảng viên/trưởng khoa tự cập nhật email, CRUD khoa, thay đổi role, import tài khoản, commit và push.

### File đã thay đổi

- Controller/DTO: `AdminAccountController.java`; xóa `AdminAccountForm.java`; thêm `StudentAccountForm.java`, `LecturerAccountForm.java`, `DeanAccountForm.java`.
- Service/domain/repository: `AdminAccountService.java`, `DashboardService.java`, `UserAccountEntity.java`, `StudentProfileEntity.java`, `ApplicationErrorCode.java`, `UserAccountRepository.java`, `StudentProfileRepository.java`.
- Migration/seed: `V14__add_account_email_and_student_profiles.sql`, `LocalDemoSeeder.java`.
- Giao diện: xóa `admin/accounts/form.html`; thêm `student-form.html`, `lecturer-form.html`, `dean-form.html`; cập nhật `admin/accounts/list.html` và `fragments/sidebar.html`.
- Test/schema: `AdminAccountServiceTest.java`, `AdminAccountControllerTest.java`, `DashboardServiceTest.java`, `AuthorizationMatrixTest.java`, `ReviewSchemaContractTest.java`, `AccountProfileMigrationIT.java`, `AccountProvisioningIT.java`, các migration integration test liên quan, `FixtureSupport.java` và `full-schema-check.sql`.
- Tài liệu: `spec.md`, `contracts/web.md`, `data-model.md`, `acceptance-matrix.md`, `implementation-progress.md`.

### Thay đổi

- Thêm ba cặp route riêng: `/admin/accounts/students/new|students`, `/lecturers/new|lecturers`, `/deans/new|deans`; route GET cũ `/admin/accounts/new` chuyển hướng về form sinh viên và không còn POST tạo tài khoản gộp.
- Khóa role theo route và whitelist field riêng, nên request không thể tự khai `accountType`, email, username sinh viên hoặc khoa.
- Sinh viên nhập họ tên, MSSV, lớp, khóa dạng `K2026` và mật khẩu tạm thời. Backend chuẩn hóa MSSV thành username, sinh email `{mssv}@student.hcmute.edu.vn`, tự gán Khoa Công nghệ Thông tin và lưu profile cùng account/role trong một transaction.
- Giảng viên và trưởng khoa chỉ nhập họ tên, tên đăng nhập, mật khẩu tạm thời; email lưu null. DEAN tiếp tục nhận đồng thời role DEAN và LECTURER.
- Thêm migration V14 với `user_accounts.email` nullable unique và bảng `student_profiles`; account cũ được giữ nguyên và có thể chưa có profile.
- Danh sách tài khoản hiển thị email; danh sách sinh viên hiển thị thêm lớp/khóa. Mỗi danh sách có nút tạo đúng loại; sidebar bỏ mục tạo tài khoản gộp.
- Seeder bổ sung email/profile cho sinh viên mẫu nhưng giữ username demo cũ để không phá các fixture và luồng đăng nhập hiện hữu.

### Verification

- `env -u JAVA_HOME ./mvnw clean -DskipTests compile`: PASS.
- `env -u JAVA_HOME ./mvnw -DskipTests test-compile`: PASS.
- `AdminAccountServiceTest, AdminAccountControllerTest, DashboardServiceTest, ReviewSchemaContractTest`: PASS, 24/24 test.
- `env -u JAVA_HOME ./mvnw test`: 224 test, 212 đạt, 12 lỗi môi trường do thiếu `TEST_DATABASE_URL` trong `AuthorizationMatrixTest` và `PeriodContractTest`; 0 failure.
- Không còn DTO/service tạo account gộp hoặc POST `/admin/accounts`; các endpoint import vẫn không có mapping.
- `git diff --check`: PASS.

### Chưa verify / rủi ro còn lại

- Chưa chạy được `AccountProfileMigrationIT`, `AccountProvisioningIT` và các integration test MySQL vì môi trường chưa cung cấp `TEST_DATABASE_URL`; số lượng constraint V14 và rollback transaction cần được xác nhận trên MySQL 8.4 test riêng.
- Trang tự cập nhật email cho LECTURER/DEAN chưa được triển khai đúng theo quyết định tách thành task sau.
- Student account cũ trước V14 được giữ nguyên và không backfill profile giả; danh sách sẽ hiển thị “Chưa cập nhật” cho lớp/khóa của các account đó.

## Trạng thái Git sau khi hoàn thành

- Branch: `feature/admin-account-management`.
- TASK-001 và TASK-002 nằm trong hai commit đã có; TASK-003 hiện là uncommitted changes.
- Working tree có thay đổi của TASK-003, không có commit mới.
- Chưa push lên remote.
