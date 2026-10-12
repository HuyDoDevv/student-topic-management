# Quickstart Verification: Quản lý Bộ môn

## Prerequisites

- JDK 21.
- MySQL 8.4 test database riêng, được phép xóa hoàn toàn.
- `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` trỏ đúng
  `topic_registration_test` trên `127.0.0.1:3306` theo `MySqlTestSupport`.

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

1. ADMIN tạo hai Department trong cùng Faculty và không thể tạo tên trùng.
2. ADMIN gán Department đúng Faculty cho LECTURER/DEAN; request chéo Faculty bị từ chối.
3. LECTURER tạo Topic: Department được backend tự gán.
4. Danh sách GVHD chỉ có lecturer cùng Department; request giả mạo bị từ chối.
5. DEAN duyệt được Topic của nhiều Department trong Faculty mình nhưng không qua Faculty khác.
6. Council hiện hữu, phân công và chấm điểm không yêu cầu Department riêng.
7. Migration backfill/enforce chỉ chạy với dữ liệu đối chiếu đã xác nhận.

## Completion Rule

Không đánh dấu task hoàn thành nếu test bắt buộc bị bỏ qua hoặc thiếu MySQL test configuration.
