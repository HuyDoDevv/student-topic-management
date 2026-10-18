# Database toàn hệ thống — MySQL 8.4 LTS

Flyway quản lý schema từ
[`V1__full_system.sql`](../src/main/resources/db/migration/V1__full_system.sql) đến
[`V18__enforce_topic_department.java`](../src/main/java/db/migration/V18__enforce_topic_department.java).
Schema hiện có 25 bảng nghiệp vụ InnoDB dùng `utf8mb4` và một bảng
`flyway_schema_history`. Migration không tạo tài khoản ứng dụng mẫu.

## Tạo database local bằng DBeaver

1. MySQL Server 8.4 phải đang chạy. DBeaver chỉ là chương trình kết nối tới server.
2. Chọn **New Database Connection → MySQL**.
3. Nhập host `127.0.0.1`, port `3307`, tài khoản quản trị MySQL và chọn **Test Connection**.
4. Mở SQL Editor trên connection quản trị và tạo database cùng tài khoản local. Thay mật khẩu
   ví dụ trước khi chạy:

```sql
CREATE DATABASE topic_registration_local
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'topic_local'@'localhost' IDENTIFIED BY 'THAY_BANG_MAT_KHAU_THAT';
GRANT ALL PRIVILEGES ON topic_registration_local.* TO 'topic_local'@'localhost';
```

Nếu MySQL local bật binary log, tài khoản quản trị cần cho phép tài khoản ứng dụng tạo trigger
một lần:

```sql
SET PERSIST log_bin_trust_function_creators = 1;
```

Nếu thiếu bước này, Flyway báo MySQL error `1419` khi chạy tới trigger đầu tiên. Chỉ chạy câu lệnh
cấu hình server trên MySQL local do nhóm quản lý. Nếu ứng dụng kết nối bằng `127.0.0.1` và MySQL
phân biệt host của user, tạo hoặc cấp quyền tương ứng cho `'topic_local'@'127.0.0.1'`.

## Áp dụng migration V1–V18

1. Tạo connection DBeaver bằng `topic_local`, database `topic_registration_local`.
2. Cấu hình `.env` theo phần dưới và chạy ứng dụng bằng profile `local`.
3. Flyway tự chạy lần lượt V1–V18, mỗi migration đúng một lần.
4. Refresh database trong DBeaver; phải thấy 25 bảng nghiệp vụ và
   `flyway_schema_history`. Mở **ER Diagram** hoặc xem [class-diagram.mmd](class-diagram.mmd).

Nếu migration lỗi trên database local chưa dùng, sửa nguyên nhân rồi tạo lại database rỗng trước
khi chạy lại. Không chỉnh sửa migration đã được dùng trên database chung; migration mới phải dùng
phiên bản V19 trở lên.

## Cấu hình ứng dụng

Đổi `.env` sang profile `local` và điền đúng thông tin database local:

```dotenv
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8088
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/topic_registration_local?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=topic_local
SPRING_DATASOURCE_PASSWORD=THAY_BANG_MAT_KHAU_THAT
```

Chạy `./run.sh` trên macOS/Linux hoặc `run.cmd` trên Windows. Ứng dụng sử dụng
profile `local` và luôn kết nối database.

## 25 bảng nghiệp vụ

| Bảng | Nội dung |
|---|---|
| `faculties` | Khoa |
| `departments` | Bộ môn thuộc khoa |
| `roles` | Danh mục vai trò |
| `user_accounts` | Tài khoản, mật khẩu hash và trạng thái enabled |
| `student_profiles` | Mã số, lớp và khóa học của sinh viên |
| `user_roles` | Quan hệ tài khoản–vai trò |
| `registration_periods` | Đợt và các mốc thời gian |
| `topics` | Đề tài, nội dung, tác giả và trạng thái duyệt |
| `topic_supervisors` | Giảng viên hướng dẫn |
| `student_groups` | Nhóm, trưởng nhóm và trạng thái giải thể |
| `group_memberships` | Thành viên hiện tại theo đợt |
| `group_invitations` | Lời mời nhóm và lịch sử phản hồi |
| `registrations` | Đăng ký đề tài và xét duyệt |
| `registration_members` | Snapshot bất biến thành viên lúc gửi đăng ký |
| `reviewer_assignments` | Giảng viên phản biện |
| `project_reports` | Metadata và lịch sử file báo cáo PDF |
| `councils` | Hội đồng hoặc phiên chấm trực tiếp |
| `council_members` | Thành viên hội đồng/người chấm |
| `council_topics` | Phân công đăng ký và lịch báo cáo |
| `score_components` | Định nghĩa điểm thành phần |
| `evaluations` | Điểm/nhận xét từng sinh viên, từng giảng viên |
| `final_results` | Kết quả tổng hợp/công bố từng sinh viên |
| `announcements` | Bản nháp và thông báo chính thức trường/khoa |
| `notification_events` | Sự kiện thông báo bất biến và target nội bộ |
| `user_notifications` | Bản nhận/read-state riêng của từng tài khoản |

## Tóm tắt lịch sử migration

| Phiên bản | Nội dung chính |
|---|---|
| V1–V3 | Schema nền và quy tắc ngày của đợt |
| V4–V7 | Duyệt đề tài, lời mời nhóm, rejection metadata và snapshot đăng ký |
| V8–V11 | Báo cáo, hội đồng/chấm trực tiếp, workflow kết quả và điểm riêng từng sinh viên |
| V12 | Metadata draft/publish cho announcement, `notification_events` và `user_notifications` |
| V13–V15 | Hợp nhất Faculty scope và chuẩn hóa dữ liệu legacy |
| V16 | Khôi phục Department và cột tham chiếu nullable trên staff/Topic; Council không có Department |
| V17 | Chỉ backfill staff và Topic còn thiếu về `Công nghệ phần mềm` thuộc `Khoa Công nghệ Thông tin` sau khi kiểm tra mâu thuẫn |
| V18 | Kiểm tra Topic, tác giả và GVHD rồi siết `topics.department_id NOT NULL` |

V12 giữ dữ liệu announcement cũ; `created_by` được backfill từ `published_by` khi có thể và để
nullable cho dữ liệu legacy không xác định. Hai unique constraints `event_key` và
`(event_id, recipient_id)` bảo vệ idempotency. Các index recipient/read/time phục vụ badge,
top 5, inbox và read-all.

## Các điểm riêng của MySQL

- `BIGINT AUTO_INCREMENT` thay cho PostgreSQL identity.
- `TIMESTAMP(6)` lưu thời điểm; JDBC/Hibernate dùng UTC.
- Generated columns kết hợp unique key bảo vệ đăng ký đang hoạt động, đề tài đã nhận, lời mời
  pending và báo cáo hiện hành.
- Trigger chốt snapshot thành viên, tự thêm trưởng nhóm/chủ tịch/thư ký và bảo vệ các quan hệ đó.
- `DECIMAL(12,4)` lưu điểm; DB và backend giới hạn thang điểm, còn giao diện định dạng gọn.
- Backend vẫn phải kiểm tra quyền, scope khoa, hạn thời gian, transition và xung đột người chấm;
  database constraints không thay thế service policy.

File [`full-schema-check.sql`](../src/test/resources/database/full-schema-check.sql) chứa fixture
kiểm tra schema MySQL và chỉ được chạy trên database test đã được Flyway migrate.

[`Thiet-ke-co-so-du-lieu-toan-he-thong.docx`](Thiet-ke-co-so-du-lieu-toan-he-thong.docx)
là tài liệu lịch sử V1/PostgreSQL, không phản ánh schema MySQL V18 hiện tại.
