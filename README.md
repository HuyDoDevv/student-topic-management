# Quản lý đề tài sinh viên

Java 21 · Spring Boot 4.1.1 · Thymeleaf/Tabler · MySQL 8.4 LTS.

Các phiên bản đã kiểm chứng trong bộ test nền tảng: Maven Wrapper 3.9.11,
MySQL 8.4.11 và Playwright Java 1.62.0 (Chromium).

## Quy trình phát triển

Dự án dùng Git Flow với `main`, `develop`, `feature/*`, `release/*` và `hotfix/*`.
Mỗi thành viên và AI phải đọc [AGENTS.md](AGENTS.md) trước khi sửa code. Không phát triển
trực tiếp trên `main` hoặc `develop`; mỗi user story/feature dùng một nhánh riêng và chỉ
merge về `develop` sau khi task, test và bằng chứng nghiệm thu đã hoàn tất.

## Chạy cơ bản

1. Có JDK 21 và MySQL 8.4 local đang chạy.
2. Sao chép `.env.example` thành `.env` và điền cấu hình riêng của máy.
3. Muốn xem giao diện: đặt `SPRING_PROFILES_ACTIVE=preview` trong `.env`.
4. Chạy lệnh tương ứng bên dưới, mở http://localhost:8080. Dừng bằng Ctrl+C.

Kiểm tra `./mvnw --version` phải hiển thị Java 21. Nếu terminal đang có `JAVA_HOME`
trỏ tới Java cũ, khai báo đúng JDK 21 trong `.env` và chạy qua script bên dưới.

macOS/Linux:

```bash
cp .env.example .env
./run.sh
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
.\run.cmd
```

Windows Command Prompt:

```bat
copy .env.example .env
run.cmd
```

`run.sh` và `run.cmd` tự nạp cùng định dạng `.env`. Mỗi thành viên giữ `.env` riêng
vì file này đã được Git ignore. Nên cấu hình `JAVA_HOME` của JDK 21 trong hệ điều hành;
nếu cần, mỗi người có thể thêm đường dẫn riêng vào `.env`. Không commit đường dẫn JDK,
mật khẩu MySQL hoặc cấu hình chỉ dùng trên một máy.

## Database toàn hệ thống

- Tạo database MySQL 8.4 rỗng và tài khoản local theo [hướng dẫn database](database/README.md).
- Đổi `.env` sang `SPRING_PROFILES_ACTIVE=local`, điền URL/user/password rồi chạy
  `./run.sh` trên macOS hoặc `run.cmd` trên Windows.
- Flyway tự chạy [V1__full_system.sql](src/main/resources/db/migration/V1__full_system.sql)
  để tạo 20 bảng, V2 để áp dụng quy tắc ngày theo loại đợt và V3 để tách hai khoảng
  đăng ký GV/SV; các migration V4–V7 bổ sung moderation, lời mời nhóm, metadata từ chối
  và snapshot thành viên/trưởng nhóm lúc gửi đăng ký. Form đợt chỉ nhận ngày; ngày bắt đầu tính từ đầu ngày, ngày kết thúc
  tính trọn ngày theo Asia/Ho_Chi_Minh. Lịch sử migration được ghi vào
  `flyway_schema_history`.
- Các thay đổi schema tiếp theo phải là migration mới sau `V7__...sql`; không
  sửa migration đã được dùng trên database chung.

Nếu đã từng import V1 thủ công, hãy dùng database local rỗng mới trước lần chạy Flyway
đầu tiên. Ứng dụng không tự xóa hoặc tự chuyển đổi database cũ.

## Trạng thái thực tế

Foundation T001–T016 đã hoàn thành: schema 20 bảng, identity repository, đăng nhập
Spring Security bằng tài khoản được cấp, policy role/scope, clock nghiệp vụ, ánh xạ lỗi
và hạ tầng unit/integration/E2E. Profile `local` dùng MySQL và Hibernate validate;
profile `preview` chỉ hiển thị catalogue UI mà không truy cập database.

Các luồng đợt đăng ký, đề xuất/duyệt đề tài, nhóm sinh viên và đăng ký đề tài đã kết nối
MySQL trên profile `local`. Dự án không tự tạo tài khoản mặc định. Khi cần dữ liệu để xem
giao diện, bật `DEMO_SEED_ENABLED=true` và đặt `DEMO_SEED_PASSWORD` trong `.env`, chạy ứng
dụng một lần rồi tắt cờ. Các tài khoản được tạo là `dean.demo`, `lecturer1.demo` đến
`lecturer3.demo` và `student1.demo` đến `student7.demo`, cùng dùng mật khẩu đã nhập.
Seeder chỉ thêm fixture còn thiếu và không reset dữ liệu hoặc lịch đã tồn tại khi khởi động lại.
`dean.demo` quản lý đợt, duyệt và công bố; tài khoản này không được tạo/sửa/gửi duyệt đề tài.
Các thao tác tác giả đề tài dùng `lecturer1.demo` đến `lecturer3.demo`.

## Kiểm thử và cấu trúc

Chạy kiểm thử bằng `./mvnw verify` trên macOS hoặc `.\mvnw.cmd verify` trên Windows.
Kiểm thử SQL nằm ở `src/test/resources/database/full-schema-check.sql`, chạy trên DB
kiểm thử đã được Flyway migrate; dữ liệu fixture được rollback.

Ứng dụng dùng Spring MVC theo package ngang. Chỉ
`vn.edu.topicregistration.TopicRegistrationApplication` là entrypoint chạy Spring Boot;
tên package `application` cũ chỉ từng là tầng use case của mỗi nhóm chức năng, không phải
các ứng dụng Spring Boot riêng. Cấu trúc production hiện tại:

```text
vn.edu.topicregistration/
├── TopicRegistrationApplication.java
├── config/       # cấu hình Spring
├── controller/   # MVC controllers và xử lý HTTP/view
├── dto/          # form input và view DTO
├── entity/       # JPA entities và enum trạng thái lưu trữ
├── repository/   # Spring Data/JDBC queries
├── service/      # nghiệp vụ, transaction và rules
├── security/     # đăng nhập và authorization
├── exception/    # lỗi ứng dụng
├── seed/         # dữ liệu demo local
└── util/         # tiện ích thời gian dùng chung
```

- `src/`, `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`: ứng dụng và build đa nền tảng.
- `src/main/resources/db/migration/`: migration SQL toàn hệ thống.
- `database/`: tài liệu thiết kế, luồng dữ liệu và hướng dẫn MySQL/DBeaver.
- `docs/customer/`, `specs/`, `.specify/`, `.agents/`: giữ yêu cầu gốc và Spec Kit.
- `licenses/`, `THIRD_PARTY_NOTICES.md`: giấy phép các tài nguyên UI đang sử dụng.
- `target/`: Maven tự sinh khi build; không phải mã nguồn.

Không lưu mật khẩu thật vào Git. `.env` đã được ignore.
