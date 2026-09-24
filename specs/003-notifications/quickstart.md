# Quickstart Validation: Thông báo trong hệ thống

> Feature đã được triển khai trên `feature/003-notifications`. Bằng chứng tự động bên dưới được
> chạy ngày 2026-09-24 trên Java 21 và MySQL 8.4 test riêng; scenario thủ công vẫn được giữ để
> thành viên nhóm có thể kiểm tra lại giao diện.

## 1. Prerequisites

- Java 21.
- MySQL 8.4 test database riêng, không dùng database local có dữ liệu thật.
- Branch `feature/003-notifications` sau khi các task implementation hoàn tất.
- Tài khoản test: một ADMIN, một DEAN Khoa Công nghệ thông tin, ít nhất hai LECTURER và ba
  STUDENT thuộc khoa; thêm một account ngoài scope nếu fixture hỗ trợ.
- PN-01–PN-12 và ma trận sự kiện trong [spec.md](spec.md) đã được chủ dự án duyệt.

## 2. Static and unit checks

```bash
env -u JAVA_HOME ./mvnw test
git diff --check
```

Kết quả nghiệm thu:

- **PASS**: 234 unit/web tests, không failure/error.
- Không có whitespace error, secret, `.env`, `target/` hoặc file máy cá nhân trong diff.
- Test bao phủ event-key, recipient scope, target resolver, validation, CSRF và IDOR.

## 3. Flyway and MySQL integration

Cấu hình profile integration tới database test theo convention hiện có rồi chạy:

```bash
env -u JAVA_HOME \
TEST_DATABASE_URL="$TEST_DATABASE_URL" \
TEST_DATABASE_USERNAME="$TEST_DATABASE_USERNAME" \
TEST_DATABASE_PASSWORD="$TEST_DATABASE_PASSWORD" \
./mvnw verify -Pintegration
```

Kết quả nghiệm thu:

- **PASS**: 35 integration/concurrency tests trên MySQL 8.4; clean migration V1 → V12 và
  upgrade V6/V7/V11 → V12 thành công.
- Upgrade schema V11 có dữ liệu announcement mẫu → V12 giữ dữ liệu cũ.
- Unique event key và `(event_id, recipient_id)` được MySQL thực thi.
- Rollback service nguồn không để lại event/recipient.
- Hai request publish/event/mark-read đồng thời không tạo duplicate hoặc sai unread count.

## 4. Manual scenario A — official school announcement

1. Đăng nhập ADMIN.
2. Mở `/announcements/manage`, tạo draft với title/body hợp lệ.
3. Sửa draft, tải lại và xác nhận version mới.
4. Publish.
5. Đăng nhập lần lượt DEAN, LECTURER, STUDENT đang enabled.

Expected khi kiểm tra thủ công:

- Mỗi account nhận đúng một item `ANNOUNCEMENT`, badge tăng một.
- Dropdown chỉ hiển thị tối đa năm mục, newest first.
- Mở item đánh dấu đọc và tới đúng detail announcement.
- Account tạo sau publish không nhận ngược item này.
- Published announcement không còn sửa/publish lại được.

## 5. Manual scenario B — faculty scope and authorization

1. Đăng nhập DEAN Khoa Công nghệ thông tin và publish announcement khoa.
2. Kiểm tra STUDENT/LECTURER cùng khoa và account ngoài scope.
3. Dùng LECTURER thử gọi route quản lý; dùng DEAN thử ID draft ngoài scope.

Expected khi kiểm tra thủ công:

- Chỉ account enabled thuộc khoa tại thời điểm publish nhận.
- LECTURER/STUDENT không quản lý được announcement.
- Truy cập chéo scope bị 403/404 ở backend, không chỉ ẩn nút.
- Form không thể sửa `faculty_id`, `source_name`, recipient hoặc target URL.

## 6. Manual scenario C — inbox and read state

1. Seed/tạo ít nhất 25 notification cho một STUDENT, gồm hai category và một số đã đọc.
2. Kiểm tra `ALL`, `UNREAD`, category filters và trang 2.
3. Mark một item read, gọi lại request, rồi mark all.
4. Trong lúc mark-all, tạo một item mới sau cutoff.
5. Dùng account khác đoán recipient-notification ID.

Expected khi kiểm tra thủ công:

- Phân trang 20, order `delivered_at DESC, id DESC`, filter không lẫn account.
- Mark-one idempotent, giữ read timestamp đầu tiên.
- Mark-all không đánh dấu item đến sau cutoff.
- Badge chính xác; >99 hiển thị `99+`.
- Account khác nhận 404 và không làm thay đổi row.

## 7. Manual scenario D — automatic academic events

Thực hiện từng dòng đã duyệt trong ma trận:

| Action | Expected recipients |
|---|---|
| Tạo group invitation | Invitee |
| Accept/reject invitation | Group leader/inviter |
| Approve/reject lecturer topic | Topic author |
| Accept/reject/auto-reject registration | Mỗi registration snapshot student |
| Assign direct grader | Assigned lecturer |
| Add council member | Assigned lecturer |
| Publish result | Mỗi result/registration snapshot student |

Cho từng action:

1. Xác nhận người thực hiện không nhận thông báo thừa.
2. Retry cùng request hoặc mô phỏng concurrent requests.
3. Gây lỗi có chủ đích trước commit để kiểm tra rollback.
4. Mở notification và kiểm tra target authorization.

Expected khi kiểm tra thủ công: đúng một item/người nhận khi commit; không có item khi rollback; không ai ngoài scope
nhận hoặc mở được tài nguyên.

## 8. Query/performance check

Tạo bộ dữ liệu test 2.000 accounts và 100.000 `user_notifications`, sau đó chạy `EXPLAIN` cho:

- unread count theo recipient;
- top 5 header;
- inbox ALL/UNREAD theo category;
- mark-all có cutoff.

Kết quả `EXPLAIN ANALYZE` trên fixture 2.000 accounts/100.000 deliveries:

- **Unread count**: dùng covering index `idx_user_notifications_recipient_read_time`,
  actual time khoảng **0,016 ms**.
- **Top 5 header**: lookup theo recipient bằng index và join event bằng PK, actual time khoảng
  **0,067 ms**.
- **Inbox unread + category**: không scan `user_notifications`; lookup unique
  `(event_id, recipient_id)`, actual time khoảng **2,71 ms** trên fixture.
- **Mark-all cutoff**: plan dùng range trên `idx_user_notifications_recipient_read_time`.
- Không query nào full scan bảng 100.000 `user_notifications`; tất cả thấp hơn ngưỡng 2 giây.
- Repository dùng `@EntityGraph(attributePaths = "event")` cho header/inbox để tránh N+1 ở tầng JPA.

## 9. E2E suite

```bash
env -u JAVA_HOME \
TEST_DATABASE_URL="$TEST_DATABASE_URL" \
TEST_DATABASE_USERNAME="$TEST_DATABASE_USERNAME" \
TEST_DATABASE_PASSWORD="$TEST_DATABASE_PASSWORD" \
E2E_ACCOUNT_PASSWORD="$E2E_ACCOUNT_PASSWORD" \
./mvnw verify -Pe2e
```

Minimum journeys:

- ADMIN draft → publish → STUDENT sees bell → open → read.
- DEAN faculty publish and out-of-scope denial.
- Registration decision/result publication creates per-student notification.
- Keyboard navigation, visible focus, empty state, unread text/badge and mobile header.

Kết quả: **PASS** — 7 Playwright tests, gồm `NotificationFlowE2ETest`; full-suite evidence được ghi trong
[acceptance-matrix.md](acceptance-matrix.md).

## 10. Acceptance evidence

Trước khi đánh dấu feature hoàn thành:

- Ghi PASS/FAIL cho SC-001–SC-006 và từng user story.
- Ghi test class/command thực tế, database profile và kết quả concurrency.
- Cập nhật traceability/acceptance matrix cho REQ-29–30 và các PN đã duyệt.
- Kiểm tra từng task ID có đúng một commit riêng; không gộp hoặc đánh dấu chỉ vì build thành công.
- Chạy lại `./mvnw test`, integration, E2E phù hợp và `git diff --check`.

Ma trận truy vết và trạng thái cuối nằm tại [acceptance-matrix.md](acceptance-matrix.md).
