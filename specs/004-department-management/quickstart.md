# Quickstart Verification: Quản lý Bộ môn

## Prerequisites

- JDK 21.
- MySQL 8.4 test database riêng, được phép xóa hoàn toàn.
- `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` trỏ đúng
  `topic_registration_test` trên `127.0.0.1:3307` theo `MySqlTestSupport`.

Không dùng database dev/prod cho migration test.

## T001 — Expand Migration

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./mvnw test-compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./mvnw verify \
  -Pintegration -Dit.test=SchemaSeedMigrationIT,FacultyScopeMigrationIT
git diff --check
```

Expected:

- Flyway áp dụng V1–V16 và validate thành công.
- Có `departments`, `user_accounts.department_id`, `topics.department_id`.
- Không có `councils.department_id`.
- Dữ liệu legacy còn nguyên, Department rỗng, các cột mới NULL.
- FK/check/unique từ chối dữ liệu sai.

## Feature Acceptance Sau T010

### Lệnh nghiệm thu T010

Chạy tuần tự trên DB test riêng `127.0.0.1:3307/topic_registration_test`;
không chạy đồng thời các migration/integration/E2E test vì chúng reset cùng một DB.
Nạp `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` từ môi trường
trước khi chạy; không ghi mật khẩu vào lệnh hoặc log. JDK 21 cần được chọn qua `JAVA_HOME`.

```bash
./mvnw -q test
./mvnw -q verify -Pintegration
./mvnw -q verify -Pe2e
git diff --check
```

Sau khi Flyway migrate DB test tới V18, chạy fixture SQL trên chính DB test bằng client MySQL
(port 3307). Fixture tự rollback business rows, nhưng dùng `DROP/CREATE PROCEDURE`, nên
không chạy trên DB dev/shared. Kết quả cuối phải là `PASS: MySQL full-system fixture and
constraint rejection checks`. Nếu một bước không đạt, giữ T010 chưa hoàn thành và ghi rõ lỗi
trong `acceptance-matrix.md`.

```bash
MYSQL_PWD="$TEST_DATABASE_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -P 3307 \
  -u "$TEST_DATABASE_USERNAME" topic_registration_test \
  < src/test/resources/database/full-schema-check.sql
```

Migration integration test kiểm tra fixture từ V12 và V15 tới V18: số tài khoản, Topic,
Council giữ nguyên, Department được gán đúng, Council không nhận cột Department,
Flyway validate và migrate lần hai không thay đổi dữ liệu.

1. ADMIN tạo hai Department trong cùng Faculty và không thể tạo tên trùng.
2. ADMIN gán Department đúng Faculty cho LECTURER/DEAN; request chéo Faculty bị từ chối.
3. LECTURER tạo Topic: Department được backend tự gán.
4. Danh sách GVHD chỉ có lecturer cùng Department; request giả mạo bị từ chối.
5. DEAN duyệt được Topic của nhiều Department trong Faculty mình nhưng không qua Faculty khác.
6. Council hiện hữu, phân công và chấm điểm không yêu cầu Department riêng.
7. Migration backfill/enforce chỉ chạy với dữ liệu đối chiếu đã xác nhận.

## Completion Rule

Không đánh dấu task hoàn thành nếu test bắt buộc bị bỏ qua hoặc thiếu MySQL test configuration.
