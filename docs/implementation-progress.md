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
- Commit: `42c5d28` (`feat(admin): separate account creation by role`)
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

## TASK-004 — Tinh chỉnh UX/UI form tạo tài khoản

- Ngày: 2026-09-29
- Trạng thái: Completed; visual QA trên session đã đăng nhập chờ kiểm tra
- Commit: `75af8be` (`style(admin): refine account creation forms`)
- Yêu cầu: Chuẩn hóa UX/UI cho ba form tạo STUDENT, LECTURER và DEAN mà không thay đổi nghiệp vụ TASK-003.
- Ngoài phạm vi: controller/service/DTO, validation backend, route, database, migration, phân quyền, commit và push.

### File đã thay đổi

- `src/main/resources/templates/admin/accounts/student-form.html`
- `src/main/resources/templates/admin/accounts/lecturer-form.html`
- `src/main/resources/templates/admin/accounts/dean-form.html`
- `src/main/resources/static/css/app.css`
- `docs/implementation-progress.md`

### Thay đổi

- Chuẩn hóa ba trang theo cùng một cấu trúc: header, card, ghi chú trường bắt buộc, panel phạm vi tài khoản, nhóm định danh, nhóm bảo mật và footer hành động.
- Thay trường Khoa readonly của sinh viên bằng panel chỉ đọc để không tạo cảm giác đây là dữ liệu có thể chỉnh sửa; giảng viên/trưởng khoa cũng hiển thị rõ role và Khoa CNTT được backend tự gán.
- Form sinh viên tách riêng thông tin định danh, thông tin học tập và bảo mật; hiển thị rõ MSSV được dùng làm username và tạo email trường.
- Thêm placeholder, hint định dạng, autocomplete phù hợp, `aria-describedby`, error summary có focus và `th:errorclass="is-invalid"` cho lỗi theo trường.
- Tái sử dụng `data-password-toggle` hiện có để cung cấp nút Hiện/Ẩn mật khẩu trên cả ba form, không thêm JavaScript mới.
- Footer có nút Hủy và nút tạo chính; CSS riêng bảo đảm form có độ rộng ổn định, nhóm thông tin rõ ràng và chuyển thành một cột/nút toàn hàng trên mobile.

### Verification

- `env -u JAVA_HOME ./mvnw -q -Dtest=AdminAccountControllerTest test`: PASS, 8/8 test.
- `git diff --check`: PASS.
- Kiểm tra tĩnh xác nhận cả ba template có error summary, `th:errorclass`, password toggle, action Hủy/Tạo và các class responsive dùng chung.
- Không có thay đổi Java/backend/schema trong TASK-004.

### Chưa verify / rủi ro còn lại

- Chưa chụp được visual QA sau đăng nhập: trình duyệt kiểm thử tách biệt bị chuyển tới `/login`, còn session Chrome đang đăng nhập không khả dụng cho công cụ kiểm tra. Cần reload ba trang trong session hiện tại để xác nhận trực quan desktop và mobile.
- Các thay đổi CSS được giới hạn bằng prefix `account-`; rủi ro ảnh hưởng form khác thấp nhưng vẫn cần kiểm tra trình duyệt thực tế tại breakpoint 768px và 576px.

## TASK-005 — Chuẩn hóa validation tạo tài khoản

- Ngày: 2026-09-29
- Trạng thái: Completed; verification MySQL chờ môi trường test
- Commit: `HEAD` (`fix(admin): strengthen account creation validation`)
- Yêu cầu: Rà soát validation của ba form tạo STUDENT, LECTURER và DEAN; không cho MSSV chứa chữ hoặc dữ liệu sai định dạng vẫn được tạo; sửa bố cục form bị vỡ và bỏ các helper note dư thừa.
- Ngoài phạm vi: sửa/xóa dữ liệu sai đã tồn tại, migration database, thay đổi role/khoa, commit và push.

### File đã thay đổi

- DTO: `StudentAccountForm.java`, `LecturerAccountForm.java`, `DeanAccountForm.java`.
- Service: `AdminAccountService.java`.
- Giao diện: `student-form.html`, `lecturer-form.html`, `dean-form.html`, `static/js/app.js`.
- Test: `AdminAccountServiceTest.java`, `AdminAccountControllerTest.java`.
- Tài liệu: `docs/implementation-progress.md`.

### Thay đổi

- MSSV phải gồm đúng 8 chữ số ở cả Bean Validation và tầng service; input dùng bàn phím số trên thiết bị hỗ trợ và lọc ký tự không phải số khi gõ/dán.
- Mã lớp là bắt buộc, chỉ gồm chữ không dấu và số, tối đa 64 ký tự; service chuẩn hóa thành chữ hoa trước khi lưu.
- Khóa chấp nhận `K2026` hoặc `k2026` ở form và luôn chuẩn hóa thành `K2026` trước khi lưu.
- Username của LECTURER/DEAN vẫn cho phép chữ không dấu, số, `.`, `_`, `-`, nhưng phải bắt đầu và kết thúc bằng chữ hoặc số.
- Họ tên giữ hỗ trợ Unicode, bắt buộc sau khi trim và tối đa 255 ký tự; mật khẩu tạm thời phải có 8–72 ký tự đồng thời không vượt 72 byte UTF-8.
- Giữ pattern phía trình duyệt cho MSSV, lớp, khóa, username và mật khẩu; bỏ helper note tĩnh dưới field để lỗi chỉ xuất hiện khi validation thất bại.
- Sửa thẻ đóng sai trong form LECTURER/DEAN để phần bảo mật và footer nằm đúng trong card; áp dụng bố cục gọn đồng nhất cho cả ba form.
- Bỏ ghi chú phụ trong card header, panel phạm vi, section và footer; footer chỉ còn nhóm nút Hủy/Tạo được căn phải.
- Thêm regression test cho MSSV chứa chữ như `sdfsdf`, MSSV sai độ dài, lớp sai ký tự/độ dài, khóa sai, username sai, họ tên rỗng/quá dài và mật khẩu sai giới hạn.

### Verification

- `env -u JAVA_HOME ./mvnw -q -Dtest=AdminAccountServiceTest,AdminAccountControllerTest test`: PASS, 18/18 test.
- `env -u JAVA_HOME ./mvnw -q -Dtest=AdminAccountControllerTest test` sau khi sửa layout: PASS, 11/11 test.
- `env -u JAVA_HOME ./mvnw test`: 229 test, 217 đạt, 12 lỗi môi trường do thiếu `TEST_DATABASE_URL` trong `AuthorizationMatrixTest` và `PeriodContractTest`; 0 failure.
- `git diff --check`: PASS.
- Kiểm tra tĩnh xác nhận MSSV dùng `inputmode="numeric"`, pattern đúng 8 chữ số và hook `data-digits-only`; ba form không còn helper note nhưng vẫn giữ error markup theo field.
- HTML Tidy không còn báo `unexpected </div>` hoặc thiếu thẻ đóng `div` ở cả ba template.

### Chưa verify / rủi ro còn lại

- Chưa chạy 12 test MySQL vì môi trường chưa cung cấp `TEST_DATABASE_URL`.
- Chưa thực hiện lại visual QA trong browser session đã đăng nhập sau khi sửa nesting; cần reload ba form để xác nhận bố cục thực tế.
- Bản ghi MSSV sai đã được tạo trước TASK-005 vẫn còn trong database; task này chỉ ngăn dữ liệu sai mới và không tự động sửa/xóa dữ liệu hiện hữu.
- Chưa thêm database CHECK cho định dạng MSSV/lớp vì dữ liệu sai hiện hữu có thể làm migration thất bại; mọi luồng tạo tài khoản của ứng dụng đã được chặn ở DTO và service.

## TASK-006 — Tự đổi mật khẩu và nền tảng thu hồi session

- Ngày: 2026-09-29
- Trạng thái: Completed; verification MySQL và visual QA chờ môi trường
- Commit: `HEAD` (`feat(security): add self-service password change`)
- Yêu cầu: Cho phép mọi người dùng đã đăng nhập tự đổi mật khẩu; tài khoản mới dùng mật khẩu tạm thời phải đổi ở lần đăng nhập đầu tiên; session cũ mất hiệu lực khi trạng thái bảo mật thay đổi.
- Ngoài phạm vi: ADMIN đặt lại mật khẩu cho người khác (TASK-007), khóa/mở khóa tài khoản (TASK-008), quên mật khẩu qua email, OTP, MFA, lịch sử mật khẩu và audit log.

### File đã thay đổi

- Migration/domain/repository: `V15__add_account_security_state.sql`, `UserAccountEntity.java`, `UserAccountRepository.java` và `full-schema-check.sql`.
- Security: `PasswordPolicy.java`, `AccountPrincipal.java`, `AccountDetailsService.java`, `AccountStateFilter.java`, `SecurityConfig.java` và `ApplicationErrorCode.java`.
- Luồng đổi mật khẩu: `ChangePasswordForm.java`, `AccountSecurityService.java`, `AccountSecurityController.java` và `account/change-password.html`.
- Tạo tài khoản/UI: `AdminAccountService.java`, `fragments/header.html` và `auth/login.html`.
- Test: `AccountSecurityServiceTest.java`, `AccountStateFilterTest.java`, `AccountDetailsServiceTest.java`, `AccountSecurityControllerTest.java`, `AccountSecurityMigrationIT.java`, `AdminAccountServiceTest.java`, `AccountProvisioningIT.java`, `AccountProfileMigrationIT.java`, `FacultyScopeMigrationIT.java`, `NotificationPersistenceIT.java`, `SchemaSeedMigrationIT.java` và `ReviewSchemaContractTest.java`.
- Tài liệu: `docs/implementation-progress.md`.

### Thay đổi

- V15 thêm `must_change_password` mặc định `FALSE` và `security_version` mặc định `0`; account cũ không bị ép đổi mật khẩu sau migration và version âm bị database từ chối.
- Account mới do ADMIN tạo được đánh dấu dùng mật khẩu tạm thời. Sau lần đăng nhập đầu, mọi route nghiệp vụ đều chuyển về `/account/password` cho tới khi người dùng đổi mật khẩu.
- Form đổi mật khẩu yêu cầu mật khẩu hiện tại, mật khẩu mới và xác nhận; áp dụng giới hạn 8–72 ký tự, tối đa 72 byte UTF-8, không cho dùng lại mật khẩu hiện tại và không trim mật khẩu.
- Đổi thành công cập nhật BCrypt hash, xóa cờ bắt buộc đổi, tăng `security_version`, hủy session hiện tại và chuyển về `/login?passwordChanged`.
- `AccountStateFilter` chỉ nằm trong Spring Security chain sau authentication; ở mỗi request nó đối chiếu account id/username, `enabled` và `security_version` với database. Account không tồn tại, bị khóa hoặc session cũ bị thu hồi sẽ bị đăng xuất về `/login?sessionExpired`.
- Filter vẫn cho phép login, logout, error và static resources; khi bắt buộc đổi mật khẩu chỉ trang đổi mật khẩu được truy cập.
- Header có mục “Đổi mật khẩu”; trang login hiển thị thông báo đổi mật khẩu thành công hoặc session không còn hiệu lực.

### Verification

- `env -u JAVA_HOME ./mvnw -q -DskipTests compile`: PASS.
- `env -u JAVA_HOME ./mvnw -q -DskipTests test-compile`: PASS.
- Bộ test tập trung cuối gồm authentication, authorization, principal, filter, service, controller, account provisioning unit và schema contract: PASS, 47/47 test.
- `env -u JAVA_HOME ./mvnw test`: 243 test, 231 đạt, 12 lỗi môi trường do thiếu `TEST_DATABASE_URL` trong `AuthorizationMatrixTest` và `PeriodContractTest`; 0 failure.
- `git diff --check` và kiểm tra trailing whitespace cho các file mới: PASS.

### Chưa verify / rủi ro còn lại

- Chưa chạy `AccountSecurityMigrationIT` và `AccountProvisioningIT` trên MySQL 8.4 vì chưa có `TEST_DATABASE_URL`; cần xác nhận V15, CHECK constraint và cờ mật khẩu tạm thời trên database test riêng.
- Chưa visual QA trang đổi mật khẩu trong browser session thực tế, đặc biệt trạng thái bắt buộc đổi mật khẩu và responsive mobile.
- Filter thêm một truy vấn account nhẹ cho mỗi request đã đăng nhập để thu hồi session ngay ở request tiếp theo; phù hợp phạm vi hiện tại nhưng cần theo dõi nếu tải hệ thống tăng lớn.
- `security_version` mới chỉ tăng khi người dùng tự đổi mật khẩu trong TASK-006; ADMIN reset và khóa/mở khóa sẽ sử dụng cùng cơ chế trong TASK-007/008.

## Trạng thái Git sau khi hoàn thành

- Branch: `feature/admin-account-management`.
- TASK-001 đến TASK-006 đã được tách thành sáu commit liên tiếp.
- Working tree chỉ còn `mvnw.cmd` nằm ngoài các task vì đây là chênh lệch line-ending đã tồn tại trước đó.
- Chưa push lên remote.
