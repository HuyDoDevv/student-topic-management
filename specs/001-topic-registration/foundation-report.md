> Cập nhật hiện trạng ngày 2026-09-09: database dùng MySQL 8.4 LTS.
> SQL hiện tại là `database/V1__full_system.sql` (20 bảng trong database đang chọn), import thủ công.
> Đã bỏ Flyway, scripts Python và cache .tools; chạy bằng ./run.sh.
> Nội dung bên dưới là bằng chứng lịch sử của nền tảng PostgreSQL trước khi chuyển đổi;
> không dùng các lệnh Flyway/scripts cũ để chạy phiên bản hiện tại. Xem README.md gốc.

# Báo cáo triển khai nền tảng

Ngày: 2026-09-08. Phạm vi được người dùng yêu cầu: làm nền tảng trước, tiếp tục sau lần ngắt.
Không xác nhận thay khách hàng các gate Q-18, ngày/nhóm/đăng ký hoặc tiêu chí nghiệm thu.

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
analyze, không miễn trừ constitution I/X. T006/Q-18 và những gate tiếp theo vẫn cần
câu trả lời trước phần triển khai phụ thuộc. Không tuyên bố feature hoàn thành.

Không có extension hooks before_implement/after_implement trong workspace.
