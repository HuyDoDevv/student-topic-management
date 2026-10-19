# Database toàn hệ thống — MySQL 8.4 LTS

Flyway quản lý schema bằng một file clean-slate duy nhất:
[`V1__baseline.sql`](../../database/V1__baseline.sql). Schema hiện có 27 bảng nghiệp vụ InnoDB dùng
`utf8mb4`, 9 trigger và một bảng `flyway_schema_history`.

Baseline tạo dữ liệu local tối thiểu:

- bốn role hệ thống `ADMIN`, `STUDENT`, `LECTURER`, `DEAN`;
- khoa `Khoa Công nghệ Thông tin`;
- tài khoản `admin`, mật khẩu `123123123`, chỉ có role `ADMIN` và không thuộc khoa/bộ môn.

Mật khẩu chỉ được lưu dưới dạng BCrypt. Credential cố định này chỉ dành cho local.

## Chuẩn bị MySQL local bằng DBeaver

1. MySQL Server 8.4 phải đang chạy. DBeaver chỉ là chương trình kết nối tới server.
2. Chọn **New Database Connection → MySQL**.
3. Nhập host `127.0.0.1`, port `3307`, tài khoản quản trị MySQL và chọn **Test Connection**.
4. Mở SQL Editor trên connection quản trị và tạo tài khoản local. Không bắt buộc tạo database
   thủ công. Thay mật khẩu ví dụ trước khi chạy:

```sql
CREATE USER 'topic_local'@'localhost' IDENTIFIED BY 'THAY_BANG_MAT_KHAU_THAT';
GRANT ALL PRIVILEGES ON topic_registration_local.* TO 'topic_local'@'localhost';
```

Tham số `createDatabaseIfNotExist=true` trong `SPRING_DATASOURCE_URL` cho phép MySQL
Connector tự tạo `topic_registration_local` khi database chưa tồn tại. Tài khoản MySQL của
ứng dụng vẫn phải có quyền `CREATE` trên `topic_registration_local.*`; quyền này được bao gồm
trong câu lệnh `GRANT ALL PRIVILEGES` ở trên.

Khi ứng dụng khởi động:

- Nếu database chưa tồn tại, Connector tạo database rồi Flyway chạy `database/V1__baseline.sql`.
- Nếu database đã tồn tại nhưng rỗng, Flyway tạo toàn bộ schema và dữ liệu bootstrap.
- Nếu database đã có migration V1, Flyway chỉ validate và giữ nguyên dữ liệu.
- Nếu database có bảng nhưng mất `flyway_schema_history`, Flyway dừng thay vì tự nhận các bảng
  là đã migrate. Không xóa riêng bảng lịch sử migration.
- Nếu database local từng chạy một bản V1 khác và Flyway báo `checksum mismatch`, hãy xóa rồi
  tạo lại toàn bộ `topic_registration_local`; không dùng `repair` để che giấu schema cũ không
  khớp baseline hiện tại.

Nếu MySQL local bật binary log, tài khoản quản trị cần cho phép tài khoản ứng dụng tạo trigger
một lần:

```sql
SET PERSIST log_bin_trust_function_creators = 1;
```

Nếu thiếu bước này, Flyway báo MySQL error `1419` khi chạy tới trigger đầu tiên. Chỉ chạy câu lệnh
cấu hình server trên MySQL local do nhóm quản lý. Nếu ứng dụng kết nối bằng `127.0.0.1` và MySQL
phân biệt host của user, tạo hoặc cấp quyền tương ứng cho `'topic_local'@'127.0.0.1'`.

## Áp dụng baseline V1

1. Tạo connection DBeaver bằng `topic_local`, database `topic_registration_local`.
2. Cấu hình `.env` theo phần dưới và chạy ứng dụng bằng profile `local`.
3. Chạy ứng dụng từ root repository để Flyway đọc `filesystem:database` và áp dụng baseline.
4. Refresh database trong DBeaver; phải thấy 27 bảng nghiệp vụ và
   `flyway_schema_history`. Mở **ER Diagram** hoặc xem [class-diagram.mmd](class-diagram.mmd).

Nếu database đã từng chạy chuỗi V1–V26 cũ, hãy tạo lại database local rỗng. Baseline không nâng
cấp hoặc chuyển đổi lịch sử Flyway cũ.

## Cấu hình ứng dụng

Đổi `.env` sang profile `local` và điền đúng thông tin database local:

```dotenv
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8088
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/topic_registration_local?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=topic_local
SPRING_DATASOURCE_PASSWORD=THAY_BANG_MAT_KHAU_THAT
```

Chạy `./workspace/scripts/run.sh` trên macOS/Linux hoặc
`workspace\scripts\run.cmd` trên Windows. Ứng dụng sử dụng
profile `local` và luôn kết nối database.

## 27 bảng nghiệp vụ

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
| `reviewer_reports` | Báo cáo PDF được nộp cho lượt phản biện |
| `reviewer_evaluations` | Điểm và nhận xét phản biện theo từng sinh viên |
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

## Baseline clean-slate

V1 mô tả trực tiếp trạng thái schema cuối cùng. Các bước backfill/nâng cấp V1–V26 cũ đã được
loại bỏ vì dự án chỉ dùng database local có thể tạo lại. Mọi thay đổi schema trong tương lai phải
thêm migration version mới, không sửa baseline sau khi đã chia sẻ.

## Các điểm riêng của MySQL

- `BIGINT AUTO_INCREMENT` thay cho PostgreSQL identity.
- `TIMESTAMP(6)` lưu thời điểm; JDBC/Hibernate dùng UTC.
- Generated columns kết hợp unique key bảo vệ đăng ký đang hoạt động, đề tài đã nhận, lời mời
  pending và báo cáo hiện hành.
- Trigger chốt snapshot thành viên, tự thêm trưởng nhóm/chủ tịch/thư ký và bảo vệ các quan hệ đó.
- `DECIMAL(12,4)` lưu điểm; DB và backend giới hạn thang điểm, còn giao diện định dạng gọn.
- Backend vẫn phải kiểm tra quyền, scope khoa, hạn thời gian, transition và xung đột người chấm;
  database constraints không thay thế service policy.

File [`full-schema-check.sql`](../../src/test/resources/database/full-schema-check.sql) chứa fixture
kiểm tra schema MySQL và chỉ được chạy trên database test đã được Flyway migrate.

[`Thiet-ke-co-so-du-lieu-toan-he-thong.docx`](Thiet-ke-co-so-du-lieu-toan-he-thong.docx)
là tài liệu lịch sử V1/PostgreSQL, không phản ánh đầy đủ baseline MySQL hiện tại.
