# Quản lý đề tài sinh viên

Java 21 · Spring Boot 4.1.1 · Thymeleaf/Tabler · MySQL 8.4 LTS.

## Chạy cơ bản

1. Có JDK 21 và MySQL 8.4 local đang chạy.
2. Nếu chưa có `.env`: `cp .env.example .env`.
3. Muốn xem giao diện: đặt `SPRING_PROFILES_ACTIVE=preview` trong `.env`.
4. Chạy `./run.sh`, mở http://localhost:8080. Dừng bằng Ctrl+C.

`run.sh` tự nạp `.env` và chọn Java từ JAVA_HOME. Không cần Python, Node,
`.tools/` hoặc các script khởi tạo database riêng.

## Database toàn hệ thống

- Mở [database/V1__full_system.sql](database/V1__full_system.sql) trong DBeaver,
  kết nối đúng database và Execute SQL Script trong một transaction.
- File tạo **20 bảng trong database đang chọn**; hướng dẫn và danh sách bảng ở
  [database/README.md](database/README.md).
- Dùng database mới hoặc chưa có các bảng cùng tên. File không xóa hay ghi đè dữ liệu.
- Đổi `.env` sang `SPRING_PROFILES_ACTIVE=local`, điền URL/user/password thực tế rồi
  chạy `./run.sh`. Cổng MySQL trong URL phải trùng DBeaver (mặc định 3306).

Dự án dùng SQL có phiên bản, import thủ công; đã bỏ Flyway để không tạo bảng lần hai.
Các thay đổi sau tạo file V2, V3…; không chạy lại V1 trên database đã import.
Database PostgreSQL cũ không bị ứng dụng tự xóa hoặc tự chuyển dữ liệu sang MySQL.

## Trạng thái thực tế

Schema bao phủ toàn hệ thống. Phần giao diện hiện có 27 template Thymeleaf và các route
preview cho đăng nhập, dashboard, đợt đăng ký, đề tài, nhóm và nguyện vọng. Dữ liệu trên
các trang này vẫn là dữ liệu mẫu; service, repository, đăng nhập thật và CRUD kết nối DB
chưa được triển khai. Profile preview không truy cập DB; local bật kết nối JPA.
Những quy tắc nghiệp vụ còn mở được ghi ở database/README.md.

## Kiểm thử và cấu trúc

`./run.sh` chạy ứng dụng; kiểm thử Java bằng `./mvnw verify` sau khi chọn JDK 21.
Kiểm thử SQL nằm ở `src/test/resources/database/full-schema-check.sql`, chạy trên DB
kiểm thử đã import V1; dữ liệu fixture được rollback.

- `src/`, `pom.xml`, `mvnw`, `.mvn/`: ứng dụng và build.
- `database/`: SQL toàn hệ thống và hướng dẫn import.
- `docs/customer/`, `specs/`, `.specify/`, `.agents/`: giữ yêu cầu gốc và Spec Kit.
- `licenses/`, `THIRD_PARTY_NOTICES.md`: giấy phép các tài nguyên UI đang sử dụng.
- `target/`: Maven tự sinh khi build; không phải mã nguồn.

Không lưu mật khẩu thật vào Git. `.env` đã được ignore.
