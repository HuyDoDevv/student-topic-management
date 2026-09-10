> Cập nhật hiện trạng ngày 2026-09-09: database dùng MySQL 8.4 LTS; Flyway tự áp dụng
> `src/main/resources/db/migration/V1__full_system.sql` để tạo 20 bảng. Chạy bằng
> `./run.sh` trên macOS/Linux hoặc `run.cmd` trên Windows.
> Nội dung bên dưới là bằng chứng lịch sử của nền tảng PostgreSQL trước khi chuyển đổi;
> không dùng các lệnh/scripts PostgreSQL cũ để chạy phiên bản hiện tại.

# Báo cáo triển khai nền tảng

Ngày: 2026-09-08. Phạm vi được người dùng yêu cầu: làm nền tảng trước, tiếp tục sau lần ngắt.
Q-18 và tiêu chí nghiệm thu/Q-20 đã được người dùng xác nhận ngày 2026-09-09. Các gate
ngày/đề tài/nhóm/đăng ký còn lại chưa được xác nhận thay khách hàng.

## Foundation identity/security/test — 2026-09-10

- T001–T016 đã hoàn thành trên nhánh feature theo Git Flow; identity ánh xạ đúng
  `faculties`, `departments`, `roles`, `user_accounts`, `user_roles` của V1.
- Spring Security dùng form login, BCrypt, session fixation protection, CSRF, logout POST,
  JSESSIONID HttpOnly/SameSite=Lax và không có self-registration.
- `AuthorizationPolicy` đọc lại tài khoản, role và scope từ database cho mỗi kiểm tra;
  STUDENT không được kết hợp role khác, LECTURER+DEAN hợp lệ, ADMIN chỉ có policy khẩn cấp.
- Clock nghiệp vụ cố định Asia/Ho_Chi_Minh; thời điểm lưu là UTC; cửa sổ tính cả đầu và cuối
  ngày đã chọn. Phản hồi web chuẩn hóa 400/403/404/409/422, redirect thành công 303 và không
  đưa chi tiết lỗi SQL vào view.
- Maven có ba tầng: unit/web, MySQL integration và Playwright Chromium E2E. Database test
  bắt buộc có tên chứa `test`; thiếu biến môi trường làm suite thất bại thay vì bị skip.
- Local datasource dùng READ COMMITTED theo transaction protocol trong data model.

Kết quả trên Java 21.0.12.1, MySQL 8.4.11 tạm và Playwright Java 1.62.0:

| Lệnh | Kết quả |
|---|---|
| `./mvnw test` | PASS — 19 unit/web tests |
| `./mvnw verify -Pintegration` | PASS — 19 unit/web + 4 MySQL integration tests |
| `./mvnw verify -Pe2e` | PASS — 19 unit/web + 1 Chromium login E2E |

MySQL tạm xác nhận 20 bảng nghiệp vụ, 6 trigger, 4 role, rollback, fixture xác định,
hai connection đồng thời, Hibernate validate và đăng nhập browser. Không reset database
local. Khi MySQL local bật binary log, quản trị viên phải bật
`log_bin_trust_function_creators` như hướng dẫn trong `database/README.md` để tài khoản
ứng dụng tạo trigger qua Flyway.

## Kiểm chứng MySQL hiện tại — 2026-09-09

- Java 21.0.12.1 và Spring Boot 4.1.1: `./mvnw clean test` đạt 3/3 test với profile preview.
- MySQL 8.4.11: ứng dụng profile local kết nối thành công trên instance kiểm thử riêng.
- Flyway V1: migrate từ database rỗng thành công; tạo 20 bảng nghiệp vụ, 6 trigger và
  một bản ghi thành công trong `flyway_schema_history`.
- Chạy lại trên cùng database: schema ở phiên bản 1 và không có migration cần chạy lại.
- Fixture `src/test/resources/database/full-schema-check.sql`: PASS toàn bộ kiểm tra
  khóa ngoại, unique key, check constraint và trigger.
- HTTP sau migration: trang gốc phản hồi 200. Instance/database kiểm thử tạm đã được tắt
  và xóa sau khi kiểm chứng; MariaDB cổng 3306 không bị thay đổi.

## Đã làm ở phiên bản nền tảng cũ

- Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.11; executable JAR.
- Cấu hình MVC/Thymeleaf, JPA/PostgreSQL/Flyway/Validation/Security qua BOM Boot.
- Local bind 127.0.0.1, DB credentials từ môi trường; Hibernate validate, không tự update.
- Baseline migration V0 kỹ thuật (SELECT 1), không tạo bảng nghiệp vụ trước gate.
- Trang chào và layout Tabler 1.4.0; CSS/JS/maps local, không cần thư mục Tabler nguồn.
- Giữ ba MIT notices Tabler/Bootstrap/Popper và manifest checksum, đóng gói trong JAR.
- Chỉ GET trang chào/assets được mở; đường dẫn khác bị từ chối, CSRF được giữ.
  Không cung cấp login/role/quyền giả hoặc tài khoản mật khẩu mặc định.
- Profile preview chủ động tắt DB cho xem UI; không phải fallback khi local lỗi DB.
- scripts/local-db.py quản lý cluster PostgreSQL riêng, mặc định .local/postgres và 55432;
  tài khoản kết nối DB không superuser, password do người chạy cấp.
- scripts/check-foundation.py kiểm chứng JAR và migration bằng cluster tạm riêng.
- README có lệnh chạy, dừng, biến môi trường và thông số kết nối DBeaver.

## Bằng chứng lịch sử trước khi chuyển MySQL

| Kiểm tra | Kết quả |
|---|---|
| Maven verify với JDK 21.0.7 | BUILD SUCCESS; 2 tests, 0 failure/error/skipped |
| FoundationWebTest | Render HTML/assets 200; đường dẫn nghiệp vụ/login/register chưa triển khai và POST bị 403 |
| PostgreSQL 18.6 smoke test | PASS: profile local khởi động, Flyway V0 thành công |
| Restart cùng DB test | PASS: đúng một V0 thành công trong history, không migration trùng |
| JAR chạy từ thư mục khác | PASS: HTML, CSS/JS vendor và app tải 200 |
| JAR notices | Có Tabler/Bootstrap/Popper LICENSE và THIRD_PARTY_NOTICES.md |
| Bộ dữ liệu test | Cluster mới, password ngẫu nhiên; dừng/xóa sau test; không đụng DB có sẵn |

Lệnh build trong phiên: JAVA_HOME trỏ openjdk@21, Maven 3.9.11 với cache .tools/m2;
lệnh chuẩn cho người dùng: ./mvnw verify. Log test/build trong target/ là dữ liệu sinh ra.
Chưa chạy kiểm tra hiển thị bằng browser thực/Playwright; MockMvc và HTTP smoke không
thay thế kiểm tra thị giác/E2E đầy đủ. DBeaver có hướng dẫn kết nối, chưa điều khiển UI DBeaver.

PostgreSQL 18.6 đã cài bằng Homebrew; công cụ cài đặt cũng nâng dependency krb5/readline.
Không link thay PostgreSQL 14, không bật brew service tự chạy khi login. Cluster mặc định
Homebrew tạo không được dùng trong smoke test. Không thay Java mặc định của hệ thống.

## Tasks và giới hạn tại thời điểm báo cáo cũ

T001, T003, T004, T005 được đánh dấu hoàn thành. T002 vẫn chưa đánh dấu vì phần khóa
Playwright chưa thực hiện; Maven Wrapper 3.9.11 và PostgreSQL 18.6 đã xác định.
Hạ tầng smoke/check ở đây là bổ sung kiểm chứng setup, không đánh dấu T014–T016 hoặc
T060–T064 là hoàn thành. Không có fixture/seeder/tài khoản nghiệp vụ và không có feature US1–5.

Checklist yêu cầu vẫn 12/16; không sửa markers. User cho phép làm setup độc lập sau
analyze, không miễn trừ constitution I/X. T006/Q-18 và T007/Q-20 đã hoàn tất; những gate tiếp theo vẫn cần
câu trả lời trước phần triển khai phụ thuộc. Không tuyên bố feature hoàn thành.

Không có extension hooks before_implement/after_implement trong workspace.
