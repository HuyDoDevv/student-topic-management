# Implementation Plan: Thông báo trong hệ thống

**Branch**: `feature/003-notifications` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/.project-kit/features/003-notifications/spec.md`

**Approval gate**: PN-01–PN-12 và ma trận sự kiện đã được chủ dự án duyệt ngày 2026-09-24;
implementation gate được mở và công việc phải đi theo `tasks.md`/mỗi task một commit.

## Summary

Xây dựng hộp thông báo trong ứng dụng dựa trên chuông hiện có, đồng thời triển khai khả năng
đăng thông báo chính thức của trường/khoa theo REQ-29–30. Thiết kế dùng `announcements` làm
nội dung chính thức, thêm sự kiện thông báo bất biến và bản nhận riêng cho từng tài khoản để
có trạng thái đã đọc, chống trùng và phân quyền chắc chắn. Các service nghiệp vụ gọi bộ phát
thông báo đồng bộ trong cùng transaction; giao diện Thymeleaf lấy badge và năm mục gần nhất
bằng truy vấn có index. Không thêm email, push, WebSocket, scheduler hoặc message broker.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA,
Thymeleaf, Bean Validation, Flyway, Tabler

**Storage**: MySQL 8.4 LTS/InnoDB; Flyway migration mới sau `V11`

**Testing**: JUnit 5/Spring Boot Test, Mockito, MockMvc/Spring Security Test, MySQL integration
profile, Playwright E2E

**Target Platform**: Ứng dụng web server-rendered chạy JVM trên macOS/Linux/Windows; trình
duyệt desktop/mobile hiện đại

**Project Type**: Spring MVC monolith với giao diện Thymeleaf

**Performance Goals**: Trang đầu hộp thông báo và dữ liệu header tải trong 2 giây ở bộ dữ liệu
2.000 tài khoản/100.000 bản nhận; truy vấn danh sách và count dùng index, không N+1

**Constraints**: Dùng request/response thông thường; thông báo mới xuất hiện ở lần tải trang kế;
mọi POST có CSRF; đường dẫn đích do server kiểm soát; write cùng transaction nguồn; không sửa
migration V1–V11; chỉ phục vụ Khoa Công nghệ thông tin trong phạm vi hiện tại

**Scale/Scope**: Bốn role hiện có; một khoa vận hành; 4 user stories; 7 loại sự kiện nghiệp vụ;
chuông header, một trang inbox, một nhóm trang quản lý announcement

## Constitution Check

*GATE: kiểm tra trước Phase 0 và kiểm tra lại sau Phase 1.*

| Nguyên tắc | Kết quả trước thiết kế | Cách đáp ứng |
|---|---|---|
| I. Nguồn nghiệp vụ | PASS | REQ-29–30 được tách khỏi phần mở rộng; Q-22 được giải quyết bằng PN-01–PN-12 đã duyệt ngày 2026-09-24. |
| II. Truy vết | PASS | Spec và contract ánh xạ REQ-29–30; thông báo tự động truy vết tới quyết định người dùng, không gán nhầm vào PDF. |
| III. Phân quyền backend | PASS | ADMIN/DEAN và quyền sở hữu bản nhận được kiểm tra trong service; tài nguyên đích kiểm tra lại quyền. |
| IV. Kiểm tra đầu vào | PASS | Server suy ra nguồn, scope, recipient và target; validate tiêu đề/nội dung/filter/page tại biên. |
| V. Tách nghiệp vụ | PASS | Controller chỉ map request; `AnnouncementService` và `NotificationService` giữ quy tắc. |
| VI. Đồng thời | PASS | Chuyển trạng thái nguồn, unique event key và unique event–recipient bảo vệ retry/concurrency. |
| VII. Migration | PASS | Dùng migration Flyway mới, không sửa V1–V11; nêu rollback và dữ liệu cũ trong data model. |
| VIII. Kiểm thử | PASS | Có unit, web/contract, MySQL integration/concurrency và E2E cho luồng chính. |
| IX. Secrets | PASS | Không có dịch vụ ngoài hoặc secrets mới; fixture chỉ dùng dữ liệu giả. |
| X. Bằng chứng | PASS có điều kiện | Chỉ được đánh dấu hoàn thành sau khi PN được duyệt, task riêng được test và acceptance matrix cập nhật. |

**Gate result trước Phase 0**: PASS. Phạm vi đề xuất đã được duyệt ngày 2026-09-24.

## Project Structure

### Documentation (this feature)

```text
.project-kit/features/003-notifications/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── web.md
```

`tasks.md` được tạo sau khi plan được duyệt và là nguồn thứ tự triển khai.

### Source Code (repository root, dự kiến sau khi duyệt)

```text
src/main/java/vn/edu/topicregistration/
├── controller/
│   ├── AnnouncementController.java
│   ├── NotificationController.java
│   └── NotificationModelAdvice.java
├── dto/
├── entity/
│   ├── Announcement.java
│   ├── NotificationEvent.java
│   └── UserNotification.java
├── repository/
│   ├── AnnouncementRepository.java
│   ├── NotificationEventRepository.java
│   └── UserNotificationRepository.java
└── service/
    ├── AnnouncementService.java
    ├── NotificationService.java
    ├── NotificationEventKey.java
    └── NotificationTargetResolver.java

src/main/resources/
├── db/migration/V12__implement_in_app_notifications.sql
└── templates/
    ├── announcements/
    │   ├── form.html
    │   ├── list.html
    │   └── detail.html
    ├── notifications/list.html
    └── fragments/header.html

src/test/java/vn/edu/topicregistration/
├── unit/
├── web/
├── integration/
├── concurrency/
└── e2e/
```

**Structure Decision**: Giữ monolith/package hiện có. Không tạo module, service hoặc frontend
riêng. Model advice chỉ cung cấp dữ liệu header cho request đã xác thực; nghiệp vụ và quyền nằm
trong service. Các service Group/Topic/Registration/DirectGrading/Council/Result hiện có chỉ được
chạm tới tại điểm phát sinh sự kiện đã duyệt.

## Data and Transaction Strategy

1. Mở rộng `announcements` bằng metadata thời gian/version an toàn qua Flyway; giữ nguyên dữ
   liệu và khóa ngoại hiện có.
2. `notification_events` lưu nội dung bất biến, loại sự kiện, actor, target có kiểu và
   `event_key` duy nhất.
3. `user_notifications` lưu một bản nhận cho mỗi tài khoản, `delivered_at`, `read_at`; unique
   `(event_id, recipient_id)` và index theo recipient/read/time.
4. Sự kiện tự động được tạo sau khi toàn bộ validation nghiệp vụ đã qua nhưng trước khi method
   `@Transactional` kết thúc. Bất kỳ lỗi nào rollback cả nguồn và thông báo.
5. Idempotency gồm hai lớp: trạng thái/lock hiện có của nguồn ngăn xử lý lại, unique keys ở DB
   là hàng rào cuối chống hai request đồng thời.
6. “Đánh dấu tất cả” chỉ cập nhật các bản nhận tồn tại tại thời điểm request bắt đầu để thông báo
   đến đồng thời không vô tình bị đánh dấu đã đọc.

## Interface and Security Strategy

- Inbox dùng `GET`; các thao tác thay đổi trạng thái dùng `POST` + CSRF + redirect 303.
- Click mở dùng POST do có side effect đánh dấu đọc, rồi server ánh xạ `target_type/target_id`
  sang route nội bộ. Client không gửi URL chuyển tiếp.
- Truy cập ID bản nhận của tài khoản khác trả 404 để không lộ tồn tại.
- Nội dung do người đăng nhập được Thymeleaf escape; phiên bản đầu không hỗ trợ HTML/rich text.
- ADMIN có scope trường; DEAN lấy faculty trực tiếp từ identity đáng tin cậy, không tin
  `facultyId` từ form.
- Thông báo không cấp quyền: controller đích vẫn gọi policy hiện tại.

## Delivery Sequence After Approval

### Phase A — Data foundation and domain service

- Migration, entities/repositories, enum/key factory, recipient resolver.
- Unit test validation, scope, target mapping; MySQL test migration/index/unique/rollback.

### Phase B — Official announcements (REQ-29–30)

- Draft/create/edit/publish cho ADMIN và DEAN theo scope.
- Snapshot recipient khi publish; list/detail management; audit metadata.
- Contract/integration test quyền, input và duplicate publish.

### Phase C — Recipient inbox and header

- Badge, năm mục gần nhất, trang danh sách/filter/pagination.
- Mark-one, mark-all và open/redirect an toàn.
- Web/E2E test IDOR, CSRF, accessible empty/unread states.

### Phase D — Automatic business-event notifications

- Tích hợp lần lượt group invitation, topic review, registration decision, direct/council
  assignment và result publication.
- Mỗi integration point có test recipient, rollback và event-key uniqueness riêng.

### Phase E — Acceptance and hardening

- Concurrency tests, representative data query check, full unit/integration/E2E suite,
  acceptance evidence và traceability update.
- Mỗi checkbox task sau này phải là một commit riêng theo `AGENTS.md`.

## Post-Design Constitution Re-check

| Gate | Result after Phase 1 |
|---|---|
| Không biến suy luận thành yêu cầu | PASS: mọi mở rộng nằm trong PN và approval gate. |
| Quyền và scope ở backend | PASS: contract không nhận recipient/scope/target tùy ý. |
| Transaction và concurrency | PASS: cùng transaction + hai unique constraints + test MySQL. |
| Migration có thể tái lập | PASS: data-model quy định V12 additive, không sửa lịch sử. |
| Kiểm thử và bằng chứng | PASS có điều kiện: quickstart nêu test, nhưng chưa thể chạy trước implementation. |

**Final design gate**: PASS. Người dùng đã xác nhận và yêu cầu bắt đầu triển khai ngày 2026-09-24.

## Complexity Tracking

Không có vi phạm constitution cần biện minh. Phương án cố ý không dùng outbox, broker,
WebSocket hoặc microservice vì phạm vi nhỏ và yêu cầu transaction đồng bộ.
