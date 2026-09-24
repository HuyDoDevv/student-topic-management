# Web Contract: Thông báo trong hệ thống

## Quy ước chung

- Mọi route yêu cầu đăng nhập; chưa đăng nhập redirect tới `/login` theo Spring Security hiện có.
- Mọi POST yêu cầu CSRF hợp lệ và dùng Post/Redirect/Get với HTTP 303 khi thành công.
- Backend lấy account/role/faculty từ principal và database; không nhận actor/recipient/faculty/URL
  tùy ý từ client.
- ID thuộc tài khoản hoặc scope khác trả 404 để hạn chế enumeration; role không có capability ở
  route quản lý trả 403 theo policy hiện có.
- Nội dung plain text được escape khi render; không dùng `th:utext`.

## 1. Recipient inbox

### `GET /notifications`

Hiển thị hộp thông báo của tài khoản hiện tại.

| Query | Giá trị | Mặc định | Quy tắc |
|---|---|---|---|
| `status` | `ALL`, `UNREAD` | `ALL` | Enum đóng |
| `category` | `ALL`, `ANNOUNCEMENT`, `ACADEMIC_EVENT` | `ALL` | Enum đóng |
| `page` | số nguyên >= 0 | 0 | Page size cố định 20 |

Model tối thiểu:

- `notifications`: page item gồm recipient row id, title, excerpt, category/type, target capability,
  deliveredAt, readAt.
- `unreadCount`, `selectedStatus`, `selectedCategory`, pagination metadata.

Không trả entity account/event thô hoặc thông tin recipient khác.

### `POST /notifications/{recipientNotificationId}/read`

Đánh dấu một row của chính account là đã đọc.

- Chỉ update khi `recipient_id = currentAccount.id` và `read_at IS NULL`.
- Gọi lại là idempotent; giữ `read_at` đầu tiên.
- Thành công: 303 về `/notifications` với flash “Đã đánh dấu thông báo là đã đọc.”
- ID không tồn tại/không sở hữu: 404, không thay đổi dữ liệu.

### `POST /notifications/read-all`

Đánh dấu mọi thông báo tồn tại của account tại thời điểm request bắt đầu.

- Cutoff do server tạo, client không gửi.
- Chỉ update `recipient_id = currentAccount.id`, `read_at IS NULL`, `delivered_at <= cutoff`.
- Thông báo đến sau cutoff vẫn chưa đọc.
- Thành công: 303 về `/notifications` kèm số row thay đổi trong flash message.

### `POST /notifications/{recipientNotificationId}/open`

Đánh dấu đã đọc rồi điều hướng tới target nội bộ.

1. Load row bằng `(id, currentAccount.id)`; không có thì 404.
2. Mark-read idempotent.
3. Resolve route từ `target_type + target_id` bằng mapping server-side.
4. 303 tới route đó; route đích kiểm tra lại quyền/tài nguyên.

Không có tham số `redirect`, `returnUrl` hoặc URL trong request. Với `target_type = NONE`, redirect
về `/notifications`. Nếu target không còn resolve được, giữ trạng thái đã đọc và redirect inbox với
flash “Nội dung liên quan không còn khả dụng.”

## 2. Header fragment contract

`NotificationModelAdvice` chỉ chạy cho principal hợp lệ và cung cấp:

- `headerUnreadCount`: số chưa đọc; UI hiển thị `99+` khi > 99.
- `headerNotifications`: tối đa 5 item mới nhất của current account.
- `hasUnreadNotifications`: boolean cho accessible label/style.

Bell button:

- `aria-label` chứa số chưa đọc khi có.
- Không có notification hiển thị empty state hiện tại.
- Mỗi item unread có text/badge, không chỉ dùng màu.
- Mỗi thao tác mở là form POST có CSRF.
- Footer dropdown có link GET `/notifications`.

Preview profile tiếp tục dùng dữ liệu giả/empty, không gọi database notification thật.

## 3. Announcement management

### `GET /announcements/manage`

- ADMIN: xem announcement cấp trường (`faculty_id IS NULL`).
- DEAN: xem announcement đúng faculty của actor.
- Lọc `status=ALL|DRAFT|PUBLISHED`, phân trang page size 20.
- Không hiển thị draft ngoài scope.

### `GET /announcements/new`

Hiển thị form tạo draft. Scope và source là read-only text do server xác định.

### `POST /announcements`

Tạo draft.

Form fields được phép:

| Field | Rule |
|---|---|
| `title` | Trim, 1–200 ký tự |
| `body` | Trim, 1–5.000 ký tự, plain text |

Server tự đặt `faculty_id`, `source_name`, `created_by`, timestamps và version. Invalid trả lại form
với lỗi field; không tạo row. Thành công 303 tới trang edit/detail draft.

### `GET /announcements/{id}/edit`

Chỉ manager đúng role/scope và chỉ DRAFT. Published hoặc ngoài scope không lộ form.

### `POST /announcements/{id}`

Sửa draft.

| Field | Rule |
|---|---|
| `title` | Như create |
| `body` | Như create |
| `version` | Bắt buộc, bằng version hiện tại |

- Stale version: 409 với thông báo tải lại, không ghi đè.
- Published: 409.
- Thành công: update `updated_at`, tăng version, 303 về edit/detail.

### `POST /announcements/{id}/publish`

Không nhận recipient/faculty/source từ form; chỉ nhận `version`.

Transaction:

1. Xác thực role/scope, lock/check DRAFT + version.
2. Validate title/body và query tập account đang enabled theo scope.
3. Nếu recipient rỗng, reject và giữ DRAFT.
4. Set `published_by`, `published_at`, tăng version.
5. Tạo event key `ANNOUNCEMENT:{id}:PUBLISHED` và một recipient row/account.
6. Commit toàn bộ hoặc rollback toàn bộ.

Kết quả:

- Thành công: 303 tới `/announcements/{id}`, flash số người nhận.
- Đã publish/stale: 409 hoặc redirect với conflict message theo convention controller hiện có.
- Unique conflict: không tạo thêm event/recipient; trả conflict có thể hiểu được.

### `GET /announcements/{id}`

Cho phép:

- Manager đúng scope xem draft/published.
- Recipient của event publication xem published announcement.

Tài khoản khác trả 404. Không đánh dấu notification đã đọc bằng GET; việc đó xảy ra ở endpoint
`/notifications/{id}/open` trước redirect.

## 4. Automatic event emission contract

Đây là internal service contract, không mở HTTP endpoint “tạo notification”.

```text
emit(eventType, sourceIdentity, actorIdentity, trustedRecipients, target)
  -> event + 0..n recipient rows in current transaction
```

Invariants:

- `eventType` quyết định title/body template, category, key format và target type.
- `sourceIdentity` là entity đã persist, không phải ID client chưa kiểm chứng.
- Recipients do service nguồn/repository đáng tin cậy suy ra và loại trùng trước insert.
- Actor bị loại khỏi recipient trừ rule sự kiện yêu cầu họ nhận.
- Recipient list rỗng ở event cá nhân là lỗi integration cần test; không im lặng tạo event rỗng.
- Không bắt exception rồi commit nghiệp vụ nếu notification write lỗi.

## 5. Event-to-page mapping

| Event | Target |
|---|---|
| Announcement published | `/announcements/{id}` |
| Invitation created | Trang nhóm/lời mời hiện có của invitee |
| Invitation accepted/rejected | `/groups/{groupId}` |
| Topic approved/rejected | Trang chi tiết đề tài của giảng viên |
| Registration accepted/rejected | `/registrations/{registrationId}` |
| Direct grader assigned | Trang chấm trực tiếp của assignment/registration |
| Council member assigned | Trang chi tiết hội đồng được phép xem/chấm |
| Result published | Trang kết quả của sinh viên cho registration |

Đường dẫn chính xác phải được kiểm tra lại với controller hiện có khi tạo tasks; resolver không được
tạo route mới chỉ để né kiểm tra quyền.

## 6. Error and security matrix

| Case | Expected |
|---|---|
| Unauthenticated | Redirect login |
| Missing/invalid CSRF on POST | 403; no change |
| Recipient ID of another account | 404; no read change |
| DEAN manages school/other faculty announcement | 404/403; no write |
| LECTURER/STUDENT accesses management | 403 |
| Blank/too long content | Form validation; no write |
| Client injects HTML/script | Rendered as text, not executable |
| Unknown filter/category/page | 400 or validated safe error; no broad query |
| Duplicate event key in race | At most one committed event; caller gets idempotent/conflict result |
| Source transaction rolls back | No event/recipient rows |
| Target permission revoked | Notification remains readable; target returns existing 403/404 policy |

## 7. Traceability

| Contract area | Spec |
|---|---|
| Inbox/header/read actions | US1, US4; FR-001–004, FR-012–015 |
| Announcement management | US2; FR-005–007 |
| Event emission | US3; FR-008–011 |
| Authorization/CSRF/target | FR-011–012; Constitution III–IV |
| Retry/transaction | FR-009–010; Constitution VI |
