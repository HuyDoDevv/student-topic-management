# Database toàn hệ thống — MySQL 8.4 LTS

Schema chính nằm trong
[V1__full_system.sql](../src/main/resources/db/migration/V1__full_system.sql). Flyway
tự áp dụng file này để tạo 20 bảng InnoDB dùng `utf8mb4`; migration không có `DROP`
và không tạo tài khoản ứng dụng mẫu. Sau đó V2 bắt buộc đúng các ngày phụ theo loại đợt,
V3 bắt buộc khoảng GV kết thúc trước khi khoảng SV bắt đầu.

## Tạo database trong DBeaver

1. MySQL Server 8.4 phải đang chạy. DBeaver chỉ là chương trình kết nối tới server.
2. Chọn **New Database Connection → MySQL**.
3. Nhập host `127.0.0.1`, port `3306`, tài khoản quản trị MySQL và chọn **Test Connection**.
4. Mở SQL Editor trên connection quản trị và tạo database cùng tài khoản local. Thay
   mật khẩu ví dụ trước khi chạy:

```sql
CREATE DATABASE topic_registration_local
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'topic_local'@'localhost' IDENTIFIED BY 'THAY_BANG_MAT_KHAU_THAT';
GRANT ALL PRIVILEGES ON topic_registration_local.* TO 'topic_local'@'localhost';
```

V1 có trigger để giữ các quan hệ nhóm và hội đồng. Nếu MySQL local bật binary log,
tài khoản quản trị cần cho phép tài khoản ứng dụng tạo trigger một lần:

```sql
SET PERSIST log_bin_trust_function_creators = 1;
```

Nếu thiếu bước này, Flyway báo MySQL error `1419` khi chạy tới trigger đầu tiên.
Chỉ chạy câu lệnh cấu hình server trên MySQL local do nhóm quản lý.

Nếu ứng dụng kết nối bằng `127.0.0.1` và MySQL phân biệt host của user, tạo hoặc cấp
quyền tương ứng cho `'topic_local'@'127.0.0.1'`.

## Tạo 20 bảng bằng Flyway

1. Tạo connection DBeaver bằng `topic_local`, database `topic_registration_local`.
2. Cấu hình `.env` theo phần dưới và chạy ứng dụng bằng profile `local`.
3. Flyway tự chạy migration V1, V2 rồi V3, mỗi migration đúng một lần.
4. Refresh database trong DBeaver; phải thấy 20 bảng nghiệp vụ và bảng
   `flyway_schema_history`. Mở **ER Diagram** để xem quan hệ.

Nếu migration lỗi trên database local chưa dùng, hãy sửa nguyên nhân rồi tạo lại database
rỗng trước khi chạy lại. Không chỉnh sửa migration đã được dùng trên database chung;
migration tiếp theo phải dùng số phiên bản mới sau V3.

Bạn vẫn có thể mở migration V1 trong DBeaver để đọc hoặc chạy trên database thử nghiệm
rỗng. Không chạy thủ công trên database mà Flyway đang quản lý.

## Cấu hình ứng dụng

Đổi `.env` sang profile `local` và điền đúng thông tin DBeaver đang dùng:

```dotenv
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8088
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/topic_registration_local?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=topic_local
SPRING_DATASOURCE_PASSWORD=THAY_BANG_MAT_KHAU_THAT
```

Sau đó chạy `./run.sh` trên macOS hoặc `run.cmd` trên Windows. Profile `preview` vẫn
chạy giao diện mà không kết nối database.

## 20 bảng

| Bảng | Nội dung |
|---|---|
| faculties | Khoa |
| departments | Bộ môn thuộc khoa |
| roles | Danh mục vai trò |
| user_accounts | Tài khoản và mật khẩu hash |
| user_roles | Quan hệ tài khoản–vai trò |
| registration_periods | Đợt và mốc thời gian |
| topics | Đề tài, nội dung, tác giả |
| topic_supervisors | Giảng viên hướng dẫn |
| student_groups | Nhóm và trưởng nhóm |
| group_memberships | Thành viên theo đợt |
| registrations | Đăng ký và xét duyệt |
| reviewer_assignments | Giảng viên phản biện |
| project_reports | Metadata file báo cáo |
| councils | Hội đồng, chủ tịch, thư ký |
| council_members | Thành viên hội đồng |
| council_topics | Phân công đề tài và lịch báo cáo |
| score_components | Định nghĩa điểm thành phần |
| evaluations | Điểm và nhận xét từng giảng viên |
| final_results | Tổng hợp và công bố kết quả |
| announcements | Thông báo trường/khoa |

## Các thay đổi riêng cho MySQL

- `BIGINT AUTO_INCREMENT` thay cho PostgreSQL identity.
- `TIMESTAMP(6)` lưu thời điểm; JDBC và Hibernate được đặt timezone UTC.
- Generated columns trong `registrations` kết hợp unique key bảo vệ một đăng ký đang
  hoạt động trên mỗi nhóm và một nhóm được nhận trên mỗi đề tài.
- MySQL không có deferred foreign key. Trigger tự thêm trưởng nhóm vào
  `group_memberships`, tự thêm chủ tịch/thư ký vào `council_members`, đồng thời chặn xóa
  các membership đang giữ vai trò này.
- `DECIMAL(12,4)` dùng cho điểm; kiểu này không nhận NaN hoặc Infinity.

Ứng dụng vẫn phải kiểm tra role/quyền, hạn thời gian, cùng bộ môn, quy trình chuyển trạng
thái, số thành viên tối thiểu của hội đồng và cách tổng hợp điểm. Ràng buộc database không
thay thế các kiểm tra nghiệp vụ trong service.

File [full-schema-check.sql](../src/test/resources/database/full-schema-check.sql) chứa
fixture kiểm tra schema MySQL và phải chạy trên database kiểm thử đã được Flyway migrate.
