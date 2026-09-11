> Cập nhật ngày 2026-09-09: database dùng MySQL 8.4 LTS, cổng mặc định 3306.
> Flyway tự áp dụng V1 để tạo 20 bảng, V2 để bắt buộc ngày theo loại đợt và V3 để
> tách hai khoảng đăng ký GV/SV khi chạy
> profile local. DBeaver dùng để tạo database/tài khoản và kiểm tra schema.

# Quickstart — Kế hoạch chạy và kiểm chứng local

**Đã có nền tảng giới hạn.** Hướng dẫn chạy hiện tại ở [README](../../README.md)
và bằng chứng ở [foundation-report.md](foundation-report.md). Nội dung dưới đây là
kế hoạch toàn tính năng: profiles integration/e2e, seed nghiệp vụ và các luồng đăng ký
chưa được triển khai. Không dùng chúng làm bằng chứng tính năng đã hoàn thành.
Không cần máy chủ/hosting. Xem [plan](plan.md), [contracts](contracts/web.md),
[data model](data-model.md). Các ca phụ thuộc Q chỉ nghiệm thu sau xác nhận.

## Prerequisites

- JDK 21; kiểm tra `java -version`.
- MySQL 8.4 local, service đang chạy trên 127.0.0.1:3306; không coi DBeaver là server.
- DBeaver với MySQL JDBC driver; tải driver lần đầu có thể cần mạng.
- Maven Wrapper được tạo khi triển khai; lần build đầu cần mạng để tải dependencies.
- Chromium được Playwright Java cài cho E2E. Không yêu cầu Node hoặc thư mục Tabler
  nguồn để chạy JAR. PowerShell phục vụ Spec Kit, không bắt buộc để chạy app.

## 1. Chuẩn bị database qua DBeaver

Mở New Database Connection → MySQL → điền host 127.0.0.1, port 3306,
database quản trị sẵn có và thông tin tài khoản MySQL do người chạy tự cấp.
Test Connection, tải driver nếu được hỏi, rồi Finish.

Tạo database riêng `topic_registration_local` và tài khoản local sở hữu schema.
Tạo database riêng `topic_registration_test` cho kiểm thử, không trỏ test vào DB demo.
Vì V1 tạo trigger, nếu MySQL local bật binary log thì tài khoản quản trị chạy
`SET PERSIST log_bin_trust_function_creators = 1` một lần; nếu thiếu, Flyway trả lỗi 1419.
Thực hiện provisioning database/user bằng UI/SQL quản trị; đây không phải thay thế
migration cho bảng ứng dụng. Không ghi password thật vào SQL file trong repository.

Tạo connection thứ hai bằng tài khoản ứng dụng, database topic_registration_local.
Chạy ứng dụng bằng profile local để Flyway tạo schema, rồi Refresh database trong
DBeaver để kiểm tra Tables/Indexes/Constraints. DBeaver dùng cùng
host/port/database/user/password như cấu hình ứng dụng và không tự đọc `.env`.

Nguồn: [DBeaver Create Connection](https://dbeaver.com/docs/dbeaver/Create-Connection/).

## 2. Biến môi trường

| Biến | Giá trị/cách cấp dự kiến |
|---|---|
| SPRING_PROFILES_ACTIVE | local |
| SERVER_ADDRESS | 127.0.0.1 |
| SERVER_PORT | 8080 |
| SPRING_DATASOURCE_URL | jdbc:mysql://127.0.0.1:3306/topic_registration_local?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true |
| SPRING_DATASOURCE_USERNAME | Tài khoản local tự tạo |
| SPRING_DATASOURCE_PASSWORD | Secret do người chạy nhập, không commit |
| TEST_DATABASE_URL | jdbc:mysql://127.0.0.1:3306/topic_registration_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true |
| TEST_DATABASE_USERNAME / TEST_DATABASE_PASSWORD | Tài khoản test; chỉ có quyền trên DB test |
| E2E_ACCOUNT_PASSWORD | Mật khẩu tạm chỉ dùng để tạo tài khoản trong DB test khi chạy browser E2E |
| DEMO_SEED_ENABLED | false mặc định; true chỉ khi muốn tạo fixture demo |
| DEMO_SEED_PASSWORD | Secret nhập khi bật demo; hash trước lưu, không log |

Ví dụ zsh tại root dự án sau triển khai (tên user chỉ là ví dụ, password nhập kín):

```sh
export SPRING_PROFILES_ACTIVE=local
export SERVER_ADDRESS=127.0.0.1
export SERVER_PORT=8080
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/topic_registration_local?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export SPRING_DATASOURCE_USERNAME=topic_local
read -rs 'SPRING_DATASOURCE_PASSWORD?Mật khẩu database: '
export SPRING_DATASOURCE_PASSWORD
./mvnw spring-boot:run
```

Không có secret mặc định. Thiếu kết nối/secret phải báo lỗi cấu hình rõ, không chuyển sang
DB in-memory. File .env nếu người dùng tự dùng phải được ignore; Spring Boot không mặc
định tự nạp .env, cần export biến vào process. Ví dụ cấu hình commit chỉ chứa placeholder.

Cấu hình cố định: ddl-auto=validate; sql.init.mode=never; open-in-view=false. Flyway là
cơ chế duy nhất tạo và thay đổi bảng ứng dụng.

## 3. Migration, build và seed

```sh
./mvnw test
./mvnw package -DskipTests
java -jar target/topic-registration.jar
```

Tên JAR cố định trên phải được cấu hình trong build. Không dùng skipTests làm bằng
chứng nghiệm thu; lệnh package trên chỉ tạo bản chạy sau unit test.

Kiểm tra đủ 20 bảng, generated columns, unique keys và triggers trong DBeaver.
Restart ứng dụng không tạo lại schema hoặc xóa dữ liệu. Không sửa file SQL đã áp dụng;
thay đổi tiếp theo phải tạo migration mới sau V3 và sao lưu trước khi chạy.

Để demo, bật DEMO_SEED_ENABLED và nhập DEMO_SEED_PASSWORD bằng cơ chế nhập kín tương tự.
Seeder chạy sau migration, chỉ profile local, lặp lại không nhân đôi/reset dữ liệu.
Tạo dữ liệu tổng hợp được mô tả trong data-model; không lấy dữ liệu SV thật.
Cấp mật khẩu ngoài repository, không ghi password/hash sử dụng được vào fixture commit.
Tắt seed sau lần tạo. Tài khoản local được seed/import theo Q-18; seeder chỉ phục vụ
local và không thay thế kiểm tra role/scope ở backend.

Các ngày demo được ghi rõ khi seed, không tự dịch ngày ở lần restart.
Để đi xuyên hai giai đoạn không chồng lấn, chuẩn bị một đợt mới có lịch GV
từ ngày hiện tại hoặc sau đó, gửi/duyệt topic, rồi đợi tới lịch SV. Automated E2E dùng clock
cố định thay vì đợi thời gian thực. Không có endpoint đổi giờ hoặc mở lại đợt đã khóa.

## 4. Automated validation dự kiến

```sh
./mvnw test
./mvnw verify -Pintegration
./mvnw -Pe2e exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
./mvnw verify -Pe2e
```

Các Maven profiles integration/e2e và TEST_DATABASE_* là contract cần triển khai;
integration khởi tạo/migrate DB test riêng, E2E tự khởi động app test.
Trước thao tác reset fixture, harness phải kiểm tra rõ tên DB test và từ chối DB local;
không cung cấp lệnh xóa DB demo mặc định. Tests không dùng mật khẩu hardcode.
Nếu chưa có biến DB test, fail cấu hình thay vì skip rồi báo thành công.

- Unit: AT-01–05,12–13 với clock giả; test nghiệp vụ không cần web server.
- HTTP/security: AT-06,09–10, từng route có ca sai role/quan hệ/CSRF và dữ liệu không đổi.
- MySQL: AT-07–08,11, nhiều connection thật và barrier; kiểm tra invariants ở commit.
- E2E: US1–5 từ login tới accept, thêm rejected/withdrawn/reapply; ánh xạ FR/REQ/AS
  trong tên hoặc metadata của test. Q còn mở phải được báo blocked, không passed.
- Migration: DB rỗng → mới nhất; phiên bản trước có dữ liệu → mới nhất; restart ổn định.

## 5. Kịch bản nghiệm thu và DBeaver

| Bước | Thao tác | Kết quả cần kiểm tra |
|---|---|---|
| 1 | Dean tạo đợt với lịch hợp lệ | Đúng 4 mốc và loại; lịch sai bị từ chối; ngày phụ chờ Q-02 |
| 2 | GV tạo draft, nhập đủ nội dung, có thể chọn một GVHD phối hợp cùng bộ môn, gửi trong hạn | Tác giả tự ở slot 1; PENDING_APPROVAL; thiếu trường/khác bộ môn không gửi được |
| 3 | Dean duyệt và công bố | APPROVED → PUBLISHED; GV không tự duyệt được |
| 4 | SV tạo nhóm, leader gửi lời mời, SV accept/reject; thành viên tự rời | Membership chỉ sinh sau accept; tổng member + PENDING tối đa 3; một PENDING/SV/đợt; leader một người được giải tán |
| 5 | Hai nhóm gửi cùng topic trong hạn SV | Hai PENDING được phép; một nhóm gửi thêm bị từ chối |
| 6 | Dean accept một đăng ký | Một ACCEPTED; mọi PENDING cạnh tranh thành REJECTED với reason chuẩn |
| 7 | Leader nhóm bị reject chọn topic khác còn mở | PENDING mới trong hạn, hàng cũ không bị ghi đè |
| 8 | Leader rút PENDING rồi gửi lại | WITHDRAWN cũ, PENDING mới; không rút ACCEPTED hoặc ngoài hạn |
| 9 | Gọi backend bằng tài khoản sai quyền | Không có thay đổi DB; kiểm tra cả ID của nhóm khác |
| 10 | Sau ngày kết thúc SV | Gửi/rút/đăng ký lại bị từ chối, không tự gia hạn |

Các bước có scope chưa chốt dùng fixture kiểm thử có quan hệ xác định, chưa chứng minh
policy tổng thể Q-18. Nhánh từ chối thủ công chờ chốt bắt buộc reason; tranh chấp
rút/accept chờ chính sách ưu tiên.

Trong DBeaver kiểm tra read-only:
- group_memberships không có student_id lặp theo period_id; mỗi nhóm 1–3 và leader
  trỏ tới một hàng membership.
- registrations không có hai PENDING/ACCEPTED cùng group/period, không có hai ACCEPTED
  cùng topic/period; topic đã accepted không còn PENDING.
- FK cùng đợt, generated-column unique keys và sáu triggers xuất hiện trong database.
- Ca denied/rollback không thay đổi số hàng/trạng thái nghiệp vụ.
Không sửa status trực tiếp để mô phỏng một ca nghiệp vụ đã đạt.

## 6. Portability và hoàn thành

Build JAR, chạy ở thư mục khác với MySQL local; browser tải CSS/JS/ảnh cần thiết
từ ứng dụng, không cần đường dẫn Tabler gốc/CDN. Kiểm tra layout bảng/form/modal,
tab keyboard và thông báo lỗi. Giấy phép đi cùng artifact; không bỏ banner.

Ghi báo cáo môi trường Java/Boot/MySQL thực dùng, lệnh và kết quả tests, AS/AT đã
chứng minh, gate còn mở. Chỉ kết luận hoàn thành khi tiêu chí nghiệm thu đã được
xác nhận và tests liên quan đạt theo constitution X.
