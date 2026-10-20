# Quản lý đề tài sinh viên

Ứng dụng Spring Boot MVC quản lý toàn bộ vòng đời đề tài sinh viên: cấp tài khoản,
tạo đợt đăng ký, đề xuất và duyệt đề tài, lập nhóm, đăng ký đề tài, nộp báo cáo,
phản biện, chấm điểm và công bố kết quả.

## Công nghệ

- Java 21
- Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA
- Thymeleaf và Tabler
- MySQL 8.4 LTS
- Flyway
- Maven Wrapper
- JUnit 5 và Playwright

## Vai trò

- `ADMIN`: quản lý Bộ môn, tài khoản, thông báo cấp trường và kiểm duyệt khẩn cấp.
- `DEAN`: quản lý đợt, duyệt đề tài/đăng ký, phản biện, hội đồng và kết quả trong Khoa.
- `LECTURER`: đề xuất đề tài, phản biện và chấm các nhiệm vụ được phân công.
- `STUDENT`: lập nhóm, đăng ký đề tài, nộp báo cáo và xem kết quả đã công bố.

Chi tiết cách kiểm tra từng nghiệp vụ và chuỗi phối hợp giữa các vai trò nằm trong
[hướng dẫn kiểm thử nghiệp vụ](workspace/docs/manual-business-test-guide.md).

## Quy tắc làm việc

Dự án dùng Git Flow với `main`, `develop`, `feature/*`, `release/*` và `hotfix/*`.
Đọc [AGENTS.md](AGENTS.md) trước khi sửa repository. Không phát triển trực tiếp trên
`main` hoặc `develop` và không commit secrets hay cấu hình riêng của máy.

## Chạy ứng dụng local

### 1. Yêu cầu

- JDK 21; `./mvnw --version` phải hiển thị Java 21.
- MySQL 8.4 đang chạy.
- User MySQL có quyền kết nối và tạo database local khi database chưa tồn tại.

### 2. Cấu hình

Sao chép file mẫu rồi thay các giá trị dành riêng cho máy:

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Giữ `SPRING_PROFILES_ACTIVE=local` và cấu hình tối thiểu:

```dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/topic_registration_local?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=topic_local
SPRING_DATASOURCE_PASSWORD=YOUR_LOCAL_PASSWORD
```

Không commit `.env`. Nếu cấu hình `REPORT_STORAGE_ROOT`, giá trị phải là đường dẫn
tuyệt đối tới một thư mục lưu PDF, ví dụ `/Users/me/topic-registration-reports` hoặc
`C:/topic-registration-reports`. Không đặt JDBC URL vào biến này; làm vậy có thể tạo
cây thư mục bất thường bên trong repository.

### 3. Khởi động

macOS/Linux:

```bash
./workspace/scripts/run.sh
```

Windows PowerShell hoặc Command Prompt:

```bat
workspace\scripts\run.cmd
```

Mở <http://127.0.0.1:8088>. Dừng ứng dụng bằng `Ctrl+C`.

Hai script chạy tự nạp `.env`. Nên đặt `JAVA_HOME` trong hệ điều hành; nếu cần có thể
khai báo đường dẫn JDK 21 riêng của máy trong `.env`.

## Database local

Flyway đọc migration clean-slate duy nhất [V1__baseline.sql](database/V1__baseline.sql)
từ thư mục `database/`. Với JDBC URL mẫu có `createDatabaseIfNotExist=true`:

1. MySQL tạo `topic_registration_local` nếu database chưa tồn tại.
2. Flyway tạo schema và ghi lịch sử vào `flyway_schema_history`.
3. Baseline tạo tài khoản local `admin`, chỉ có role `ADMIN`.
4. Baseline tạo `Khoa Công nghệ Thông tin`.

Thông tin đăng nhập bootstrap chỉ dành cho local:

```text
Username: admin
Password: 123123123
```

Baseline dành cho database local rỗng. Dự án không còn cung cấp script xóa toàn bộ dữ
liệu. Khi cần chạy lại quy trình từ đầu, hãy dừng ứng dụng, sao lưu nếu cần, sau đó tự
tạo một database local rỗng mới hoặc xóa database local bằng công cụ MySQL của bạn. Không
thực hiện thao tác này trên database dùng chung, test của người khác hoặc production.

Hướng dẫn kết nối và kiểm tra database nằm tại
[workspace/database/README.md](workspace/database/README.md).

## Dữ liệu demo local

Để có fixture phục vụ xem giao diện, cấu hình tạm thời:

```dotenv
DEMO_SEED_ENABLED=true
DEMO_SEED_PASSWORD=YOUR_TEMPORARY_DEMO_PASSWORD
```

Khởi động ứng dụng một lần, sau đó đổi `DEMO_SEED_ENABLED=false`. Seeder tạo các tài
khoản `admin.demo`, `dean.demo`, `lecturer1.demo` đến `lecturer5.demo` và
`student1.demo` đến `student7.demo` bằng mật khẩu vừa cấu hình.

Seeder chỉ bổ sung fixture còn thiếu; không reset các trạng thái đã được thao tác. Muốn
kiểm thử một quy trình clean-slate, ưu tiên database local rỗng và tạo dữ liệu theo
[hướng dẫn kiểm thử nghiệp vụ](workspace/docs/manual-business-test-guide.md).

## Kiểm thử

Unit và web tests, không truy cập database local:

```bash
./mvnw test
```

Integration tests yêu cầu `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` và
`TEST_DATABASE_PASSWORD`. Tên database test bắt buộc chứa chuỗi `test`:

```bash
./mvnw verify -Pintegration
```

E2E tests còn yêu cầu `E2E_ACCOUNT_PASSWORD` và Playwright Chromium:

```bash
./mvnw verify -Pe2e
```

Không trỏ integration/E2E test vào database local đang dùng, database chia sẻ hoặc
production.

## Cấu trúc repository

```text
topic-registration-management/
├── .agents/              # canonical AI skills
├── .specify/             # template và workflow SpecKit
├── .mvn/                 # Maven Wrapper
├── specs/                # đặc tả, kế hoạch và bằng chứng feature
├── database/             # Flyway migration chạy thật
├── workspace/
│   ├── docs/             # tài liệu dự án và hướng dẫn kiểm thử
│   ├── database/         # tài liệu database
│   ├── scripts/          # run.sh và run.cmd
│   └── licenses/         # license và third-party notices
├── src/
│   ├── main/             # mã nguồn và resources Spring Boot
│   └── test/             # unit, web, integration và E2E tests
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

Package production chính:

```text
vn.edu.topicregistration/
├── TopicRegistrationApplication.java
├── config/       # cấu hình Spring
├── controller/   # HTTP, form binding, redirect và view
├── dto/          # form input và view DTO
├── entity/       # persistence/domain state
├── repository/   # persistence và query
├── service/      # nghiệp vụ, transaction và authorization
├── security/     # đăng nhập và policy quyền
├── exception/    # lỗi ứng dụng
├── seed/         # dữ liệu demo local
└── util/         # tiện ích dùng chung
```

Luồng kiến trúc chuẩn là `Controller → Service → Repository → Database`.

Các thư mục quan trọng:

- `database/V1__baseline.sql`: source of truth cho schema clean-slate, bao gồm
  timestamp audit của toàn bộ bảng.
- `workspace/docs/customer/`: yêu cầu gốc và bản phân tích.
- `workspace/docs/manual-business-test-guide.md`: hướng dẫn kiểm thử theo vai trò.
- `.agents/skills/`: canonical skill cho AI trong repository.
- `.specify/` và `specs/`: hạ tầng và artifact SpecKit.
- `workspace/licenses/`: license tài nguyên UI và `THIRD_PARTY_NOTICES.md`.
- `target/`: output Maven tự sinh, không phải mã nguồn.
