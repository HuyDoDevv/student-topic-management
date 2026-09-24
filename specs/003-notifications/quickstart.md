# Quickstart Validation: Thông báo trong hệ thống

> Tài liệu này mô tả cách nghiệm thu sau khi feature được duyệt và triển khai. Ở trạng thái plan
> hiện tại chưa có code/migration tương ứng, vì vậy các scenario chưa được đánh dấu PASS.

## 1. Prerequisites

- Java 21.
- MySQL 8.4 test database riêng, không dùng database local có dữ liệu thật.
- Branch `feature/003-notifications` sau khi các task implementation hoàn tất.
- Tài khoản test: một ADMIN, một DEAN Khoa Công nghệ thông tin, ít nhất hai LECTURER và ba
  STUDENT thuộc khoa; thêm một account ngoài scope nếu fixture hỗ trợ.
- PN-01–PN-12 và ma trận sự kiện trong [spec.md](spec.md) đã được chủ dự án duyệt.

## 2. Static and unit checks

```bash
./mvnw test
git diff --check
```

Expected:

- Unit/web tests pass.
- Không có whitespace error, secret, `.env`, `target/` hoặc file máy cá nhân trong diff.
- Test bao phủ event-key, recipient scope, target resolver, validation, CSRF và IDOR.

## 3. Flyway and MySQL integration

Cấu hình profile integration tới database test theo convention hiện có rồi chạy:

```bash
./mvnw verify -Pintegration
```

Expected:

- Clean migration V1 → V12 thành công.
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

Expected:

- Mỗi account nhận đúng một item `ANNOUNCEMENT`, badge tăng một.
- Dropdown chỉ hiển thị tối đa năm mục, newest first.
- Mở item đánh dấu đọc và tới đúng detail announcement.
- Account tạo sau publish không nhận ngược item này.
- Published announcement không còn sửa/publish lại được.

## 5. Manual scenario B — faculty scope and authorization

1. Đăng nhập DEAN Khoa Công nghệ thông tin và publish announcement khoa.
2. Kiểm tra STUDENT/LECTURER cùng khoa và account ngoài scope.
3. Dùng LECTURER thử gọi route quản lý; dùng DEAN thử ID draft ngoài scope.

Expected:

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

Expected:

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

Expected: đúng một item/người nhận khi commit; không có item khi rollback; không ai ngoài scope
nhận hoặc mở được tài nguyên.

## 8. Query/performance check

Tạo bộ dữ liệu test 2.000 accounts và 100.000 `user_notifications`, sau đó chạy `EXPLAIN` cho:

- unread count theo recipient;
- top 5 header;
- inbox ALL/UNREAD theo category;
- mark-all có cutoff.

Expected:

- Query dùng index recipient/read/time, không full scan toàn bảng recipient.
- Header và trang đầu inbox hoàn tất dưới 2 giây trong môi trường test dự án.
- Không phát sinh N+1 event/account query trong log SQL kiểm thử.

## 9. E2E suite

```bash
./mvnw verify -Pe2e
```

Minimum journeys:

- ADMIN draft → publish → STUDENT sees bell → open → read.
- DEAN faculty publish and out-of-scope denial.
- Registration decision/result publication creates per-student notification.
- Keyboard navigation, visible focus, empty state, unread text/badge and mobile header.

## 10. Acceptance evidence

Trước khi đánh dấu feature hoàn thành:

- Ghi PASS/FAIL cho SC-001–SC-006 và từng user story.
- Ghi test class/command thực tế, database profile và kết quả concurrency.
- Cập nhật traceability/acceptance matrix cho REQ-29–30 và các PN đã duyệt.
- Kiểm tra từng task ID có đúng một commit riêng; không gộp hoặc đánh dấu chỉ vì build thành công.
- Chạy lại `./mvnw test`, integration, E2E phù hợp và `git diff --check`.
