# Data Model: Thông báo trong hệ thống

## Tổng quan

Thiết kế giữ `announcements` là nguồn nội dung chính thức và thêm hai bảng:

```text
announcements (0..1) ─── (0..1) notification_events
user_accounts (actor) ── (0..*) notification_events
notification_events ──── (1..*) user_notifications
user_accounts (recipient) ─ (0..*) user_notifications
```

Một event lưu nội dung bất biến của một lần phát sinh; mỗi recipient có một row riêng để quản lý
đã đọc. Không dùng JSON recipient list hoặc danh sách ID do client cung cấp.

## 1. `announcements` — thông báo chính thức

### Cột hiện có được giữ nguyên

| Field | Type | Rule |
|---|---|---|
| `id` | BIGINT | PK |
| `title` | VARCHAR(500) | DB không rỗng; application giới hạn 200 ký tự |
| `body` | TEXT | DB không rỗng; application giới hạn 5.000 ký tự |
| `source_name` | VARCHAR(255) | Server suy ra: “Nhà trường” hoặc tên khoa |
| `faculty_id` | BIGINT nullable | `NULL` = cấp trường; có giá trị = đúng khoa |
| `published_by` | BIGINT nullable | Bắt buộc khi `published_at` có giá trị |
| `published_at` | TIMESTAMP(6) nullable | `NULL` = DRAFT; có giá trị = PUBLISHED |

### Cột bổ sung bằng migration mới

| Field | Type | Rule |
|---|---|---|
| `created_by` | BIGINT nullable ở DB | Application bắt buộc cho row mới; FK `user_accounts` |
| `created_at` | TIMESTAMP(6) | NOT NULL, default thời điểm migration/insert |
| `updated_at` | TIMESTAMP(6) | NOT NULL, cập nhật khi sửa draft |
| `version` | BIGINT | NOT NULL, default 0, `>= 0`; optimistic concurrency |

`created_by` được để nullable ở DB để migration không bịa người tạo cho dữ liệu legacy. Row published
cũ được backfill `created_by = published_by` khi có thể; draft legacy không xác định người tạo được
giữ nguyên, chỉ người có scope quản lý mới thấy và có thể xử lý theo bằng chứng dữ liệu thực tế.

### Trạng thái

```text
DRAFT (published_at = NULL)
  ├─ edit -> DRAFT (version + 1)
  └─ publish -> PUBLISHED (published_by, published_at, version + 1)

PUBLISHED -> terminal trong v1
```

### Validation

- Trim title/body; reject blank.
- Title 1–200 ký tự; body 1–5.000 ký tự ở application.
- ADMIN: `faculty_id = NULL`, `source_name` server đặt là nguồn trường.
- DEAN: `faculty_id` lấy từ actor, `source_name` lấy từ `faculties.name`.
- Publish chỉ khi DRAFT và tập recipient không rỗng.
- Edit/publish dùng expected version để chống hai tab ghi đè nhau.

## 2. `notification_events` — sự kiện bất biến

| Field | Type | Rule |
|---|---|---|
| `id` | BIGINT | PK, auto increment |
| `event_key` | VARCHAR(191) | NOT NULL, UNIQUE, server tạo |
| `category` | VARCHAR(32) | `ANNOUNCEMENT` hoặc `ACADEMIC_EVENT` |
| `event_type` | VARCHAR(64) | Một giá trị trong enum bên dưới |
| `title` | VARCHAR(200) | NOT NULL, trim khác rỗng |
| `body` | TEXT | NOT NULL, trim khác rỗng, tối đa 5.000 ở application |
| `actor_id` | BIGINT nullable | FK tài khoản gây ra sự kiện; system event có thể null |
| `announcement_id` | BIGINT nullable | FK announcement; chỉ loại `ANNOUNCEMENT_PUBLISHED` |
| `target_type` | VARCHAR(32) | Enum route nội bộ |
| `target_id` | BIGINT nullable | ID nguồn dùng bởi resolver; tùy target |
| `created_at` | TIMESTAMP(6) | NOT NULL |

### Event types v1

| `event_type` | `category` | Key format |
|---|---|---|
| `ANNOUNCEMENT_PUBLISHED` | ANNOUNCEMENT | `ANNOUNCEMENT:{announcementId}:PUBLISHED` |
| `GROUP_INVITATION_CREATED` | ACADEMIC_EVENT | `GROUP_INVITATION:{invitationId}:CREATED` |
| `GROUP_INVITATION_ACCEPTED` | ACADEMIC_EVENT | `GROUP_INVITATION:{invitationId}:ACCEPTED` |
| `GROUP_INVITATION_REJECTED` | ACADEMIC_EVENT | `GROUP_INVITATION:{invitationId}:REJECTED` |
| `TOPIC_PROPOSAL_APPROVED` | ACADEMIC_EVENT | `TOPIC:{topicId}:APPROVED` |
| `TOPIC_PROPOSAL_REJECTED` | ACADEMIC_EVENT | `TOPIC:{topicId}:REJECTED` |
| `REGISTRATION_ACCEPTED` | ACADEMIC_EVENT | `REGISTRATION:{registrationId}:ACCEPTED` |
| `REGISTRATION_REJECTED` | ACADEMIC_EVENT | `REGISTRATION:{registrationId}:REJECTED` |
| `REGISTRATION_AUTO_REJECTED` | ACADEMIC_EVENT | `REGISTRATION:{registrationId}:AUTO_REJECTED` |
| `DIRECT_GRADER_ASSIGNED` | ACADEMIC_EVENT | `DIRECT_GRADING:{assignmentId}:ASSIGNED` |
| `COUNCIL_MEMBER_ASSIGNED` | ACADEMIC_EVENT | `COUNCIL_MEMBER:{membershipId}:ASSIGNED` |
| `RESULT_PUBLISHED` | ACADEMIC_EVENT | `RESULT:{finalResultId}:PUBLISHED` |

Topic publication riêng không tạo event ở v1 nếu cùng quyết định approved/published trong workflow;
nếu code hiện tại tách hai transition, implementation task phải map đúng một thời điểm theo spec đã duyệt.

### Target types v1

`ANNOUNCEMENT`, `GROUP_INVITATION`, `GROUP`, `TOPIC`, `REGISTRATION`, `DIRECT_GRADING`,
`COUNCIL`, `RESULT`, `NONE`.

- `target_type` + `target_id` chỉ là tham chiếu điều hướng, không cấp quyền.
- Resolver có mapping đóng; enum không biết trả 404 an toàn.
- `NONE` yêu cầu `target_id IS NULL`; các loại còn lại yêu cầu ID dương.
- Event không update/delete ở v1. Thay đổi nguồn sau đó không làm nội dung lịch sử đổi.

## 3. `user_notifications` — bản nhận của tài khoản

| Field | Type | Rule |
|---|---|---|
| `id` | BIGINT | PK, auto increment |
| `event_id` | BIGINT | NOT NULL, FK `notification_events` |
| `recipient_id` | BIGINT | NOT NULL, FK `user_accounts` |
| `delivered_at` | TIMESTAMP(6) | NOT NULL; thời điểm row được tạo |
| `read_at` | TIMESTAMP(6) nullable | `NULL` = UNREAD; có giá trị = READ |

### Keys and indexes

- `UNIQUE (event_id, recipient_id)` — một sự kiện chỉ đến một tài khoản một lần.
- `INDEX (recipient_id, read_at, delivered_at DESC, id DESC)` — count/lọc unread.
- `INDEX (recipient_id, delivered_at DESC, id DESC)` — inbox/top 5 all.
- FK dùng `RESTRICT`/mặc định; tài khoản được disable thay vì xóa để giữ audit.

### State transitions

```text
UNREAD (read_at = NULL)
  └─ mark/read/open -> READ (read_at = server now)

READ -> READ (idempotent; giữ read_at đầu tiên)
```

Không có chuyển READ → UNREAD và không có delete trong v1.

## 4. Recipient derivation

| Nguồn | Query/nguồn đáng tin cậy |
|---|---|
| Announcement cấp trường | Mọi `user_accounts.enabled = TRUE` tại thời điểm publish |
| Announcement cấp khoa | Tài khoản enabled có `account.faculty_id = actor.faculty_id` |
| Group invitation created | `invitee_id` của invitation đã persist |
| Invitation response | Leader/inviter đáng tin cậy từ group/invitation |
| Topic decision | `topics.author_id` |
| Registration decision | `registration_members.student_id` snapshot của registration |
| Direct grader assigned | Lecturer ID trên assignment đã persist |
| Council member assigned | Lecturer ID trên council membership đã persist |
| Result published | `registration_members.student_id` snapshot gắn final result |

Mọi query chỉ chọn tài khoản hiện tồn tại; event nghiệp vụ có recipient bị disable vẫn có thể tạo
row để bảo toàn lịch sử, còn announcement chủ động chỉ chọn tài khoản enabled. Quy tắc này cần
được giữ nhất quán trong implementation và test.

## 5. Transaction and concurrency invariants

1. Source transition + event + recipients commit hoặc rollback cùng nhau.
2. `event_key` không được lấy từ title/body/timestamp ngẫu nhiên; phải tái tạo được từ source.
3. Unique violation do cùng source transition đồng thời phải biến thành conflict nghiệp vụ hoặc
   được xem là idempotent theo contract, không tạo event thứ hai.
4. Publish announcement khóa/check version row DRAFT trước khi snapshot recipient.
5. Mark-one update có điều kiện `id AND recipient_id AND read_at IS NULL`.
6. Mark-all có điều kiện `recipient_id AND read_at IS NULL AND delivered_at <= cutoff`.
7. Count và list luôn filter `recipient_id = authenticatedAccountId` ở query, không lọc sau khi load.

## 6. Flyway migration plan

Tên dự kiến: `V12__implement_in_app_notifications.sql`.

Thứ tự additive:

1. Thêm `created_by`, `created_at`, `updated_at`, `version` vào `announcements`.
2. Backfill `created_by` từ `published_by` khi có; giữ null có chủ đích cho legacy không xác định.
3. Thêm FK/index/check mới cho metadata announcement.
4. Tạo `notification_events` với checks, unique và FKs.
5. Tạo `user_notifications` với checks, unique, indexes và FKs.

Không sửa V1–V11, không drop/rename dữ liệu hiện có. MySQL DDL không được giả định rollback toàn bộ
migration; phải chạy thử cả clean schema và schema đã ở V11 trên database integration riêng. Nếu
migration fail, dừng deploy và sửa bằng migration tiếp theo hoặc phục hồi database test/backup theo
quy trình vận hành; không chỉnh tay schema production để “bỏ qua” Flyway.

## 7. Query shapes cần xác minh

- Unread count: `recipient_id = ? AND read_at IS NULL`.
- Header: recipient, order `delivered_at DESC, id DESC`, limit 5, join event một lần.
- Inbox: recipient + optional read/category, page 20, cùng order.
- Manager announcements: scope school/faculty + status derived, order updated/published time.
- Recipient fan-out: `INSERT ... SELECT` hoặc batch insert từ query server-side; không N+1 account load.

`EXPLAIN` trên dữ liệu đại diện phải dùng index recipient và không quét toàn bộ bảng
`user_notifications` cho header/inbox.
