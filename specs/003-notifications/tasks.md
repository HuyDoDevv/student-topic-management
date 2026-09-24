# Tasks: Thông báo trong hệ thống

**Input**: Design documents from `/specs/003-notifications/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/web.md`,
`quickstart.md`

**Tests**: Mỗi task implementation phải viết/chạy test có ý nghĩa trong cùng task. Test mới được
viết trước phần implementation tương ứng. Mỗi checkbox Txxx tương ứng đúng một commit hoàn chỉnh
theo `AGENTS.md`; chỉ đánh dấu `[X]` sau khi test và bằng chứng của task đạt.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Có thể làm song song sau khi dependency đã hoàn tất vì chủ yếu sửa file khác nhau.
- **[Story]**: Ánh xạ tới user story trong `spec.md`.

## Phase 1: Setup and approved design

**Purpose**: Chốt nguồn sự thật và phạm vi đã được người dùng duyệt trước khi code.

- [X] T001 Ghi nhận phê duyệt PN-01–PN-12, hoàn thiện thiết kế và task plan tại `specs/003-notifications/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/web.md`, `quickstart.md`, `tasks.md`

**Checkpoint**: Thiết kế được duyệt, branch đúng `feature/003-notifications`, không có code ngoài phạm vi.

---

## Phase 2: Foundational notification infrastructure

**Purpose**: Schema, persistence và domain primitives dùng chung cho mọi user story.

**⚠️ CRITICAL**: T002–T003 phải hoàn tất trước mọi user story.

- [X] T002 Tạo Flyway `src/main/resources/db/migration/V12__implement_in_app_notifications.sql`, entities/repositories notification tại `src/main/java/vn/edu/topicregistration/entity/` và `repository/`, kèm migration/persistence tests tại `src/test/java/vn/edu/topicregistration/integration/NotificationPersistenceIT.java`
- [X] T003 Viết test trước rồi triển khai event-key, recipient fan-out, inbox query, target resolver và transaction-safe emission tại `src/test/java/vn/edu/topicregistration/unit/NotificationServiceTest.java`, `src/main/java/vn/edu/topicregistration/service/NotificationService.java`, `NotificationEventKey.java`, `NotificationTargetResolver.java`

**Checkpoint**: Có thể tạo một event bất biến, fan-out duy nhất theo recipient, truy vấn inbox của
một account và resolve target đóng; rollback/unique được schema bảo vệ.

---

## Phase 3: User Story 1 — Nhận và xem thông báo (Priority: P1) 🎯 MVP

**Goal**: Người dùng xem unread badge, top 5 ở chuông và inbox phân trang của chính mình.

**Independent Test**: Seed notification cho hai account; account A thấy đúng badge/top 5/inbox,
account B không thể xem row của A; empty state và `99+` render đúng.

- [X] T004 [US1] Viết MockMvc/UI test trước rồi triển khai inbox read-only, header model advice và Tabler UI tại `src/test/java/vn/edu/topicregistration/web/NotificationInboxWebTest.java`, `src/main/java/vn/edu/topicregistration/controller/NotificationController.java`, `NotificationModelAdvice.java`, `src/main/resources/templates/notifications/list.html`, `fragments/header.html`

**Checkpoint**: US1 hoạt động độc lập với fixture notification, chưa cần publisher hoặc event integrations.

---

## Phase 4: User Story 2 — Đăng thông báo chính thức (Priority: P1)

**Goal**: ADMIN quản lý announcement cấp trường; DEAN quản lý đúng khoa, draft một chiều tới published
và snapshot tài khoản enabled.

**Independent Test**: ADMIN/DEAN tạo–sửa–publish; đúng tập recipient nhận một lần; wrong role/scope,
stale version và duplicate publish bị backend từ chối không ghi dở.

- [X] T005 [P] [US2] Viết unit/integration test trước rồi triển khai announcement validation, scope, optimistic version và atomic publish/fan-out tại `src/test/java/vn/edu/topicregistration/unit/AnnouncementServiceTest.java`, `src/test/java/vn/edu/topicregistration/integration/AnnouncementPublicationIT.java`, `src/main/java/vn/edu/topicregistration/service/AnnouncementService.java`, `entity/Announcement.java`, `repository/AnnouncementRepository.java`
- [X] T006 [US2] Viết web contract test trước rồi triển khai create/edit/publish/detail/list endpoints và UI tại `src/test/java/vn/edu/topicregistration/web/AnnouncementWebTest.java`, `src/main/java/vn/edu/topicregistration/controller/AnnouncementController.java`, `src/main/java/vn/edu/topicregistration/dto/AnnouncementForm.java`, `src/main/resources/templates/announcements/form.html`, `list.html`, `detail.html`, cùng navigation trong `fragments/sidebar.html`

**Checkpoint**: REQ-29–30 hoàn thành độc lập; announcement chính thức xuất hiện trong inbox US1.

---

## Phase 5: User Story 3 — Cảnh báo theo sự kiện nghiệp vụ (Priority: P2)

**Goal**: Các transition đã duyệt tự tạo đúng một thông báo/người liên quan trong cùng transaction.

**Independent Test**: Kích hoạt từng transition, kiểm chứng recipient snapshot, target, actor exclusion,
rollback và retry; người ngoài scope không nhận.

- [X] T007 [P] [US3] Viết regression/integration test trước rồi tích hợp group invitation, topic moderation và registration decisions vào notification emitter tại `src/test/java/vn/edu/topicregistration/integration/RegistrationNotificationIT.java`, `src/main/java/vn/edu/topicregistration/service/GroupService.java`, `TopicReviewService.java`, `RegistrationService.java`
- [X] T008 [US3] Viết regression/integration test trước rồi tích hợp direct-grader assignment, council membership và result publication vào notification emitter tại `src/test/java/vn/edu/topicregistration/integration/ReviewResultNotificationIT.java`, `src/main/java/vn/edu/topicregistration/service/DirectGradingService.java`, `CouncilService.java`, `ResultService.java`

**Checkpoint**: Toàn bộ ma trận sự kiện trong spec có test recipient/rollback/dedup và target hợp lệ.

---

## Phase 6: User Story 4 — Quản lý trạng thái đã đọc (Priority: P2)

**Goal**: Người nhận lọc unread/category, mark-one, mark-all có cutoff và open bằng POST an toàn.

**Independent Test**: Hai account có read-state độc lập; thao tác lặp idempotent; notification đến sau
cutoff chưa đọc; open không nhận redirect URL và target vẫn kiểm tra quyền.

- [X] T009 [US4] Viết web/integration test trước rồi triển khai filter, mark-one, mark-all cutoff và open/redirect tại `src/test/java/vn/edu/topicregistration/web/NotificationReadWebTest.java`, `src/test/java/vn/edu/topicregistration/integration/NotificationReadIT.java`, `src/main/java/vn/edu/topicregistration/service/NotificationService.java`, `controller/NotificationController.java`, `src/main/resources/templates/notifications/list.html`, `fragments/header.html`

**Checkpoint**: US4 hoàn chỉnh, CSRF/IDOR/idempotency/read timestamp được kiểm chứng.

---

## Phase 7: Polish and cross-cutting acceptance

**Purpose**: Chứng minh concurrency, security, UX và end-to-end cho toàn feature.

- [X] T010 Viết MySQL concurrency/security tests cho duplicate event, publish race, rollback và cross-account access tại `src/test/java/vn/edu/topicregistration/concurrency/NotificationConcurrencyIT.java` và cập nhật profile fixture liên quan tại `src/test/resources/`
- [ ] T011 Viết Playwright E2E cho ADMIN publish → STUDENT bell/open/read, DEAN scope, keyboard/mobile/empty state tại `src/test/java/vn/edu/topicregistration/e2e/NotificationFlowE2ETest.java` và hoàn thiện CSS cần thiết tại `src/main/resources/static/css/app.css`
- [ ] T012 Chạy quickstart/full suites, kiểm tra query/index và cập nhật bằng chứng/truy vết tại `specs/003-notifications/quickstart.md`, `specs/003-notifications/acceptance-matrix.md`, `database/DATA_FLOW.md`, `database/class-diagram.mmd`, `database/README.md`

---

## Dependencies & Execution Order

### Phase dependencies

```text
T001
  └─ T002
      └─ T003
          ├─ T004 (US1)
          ├─ T005 → T006 (US2)
          ├─ T007 → T008 (US3)
          └─ T009 (US4; dùng controller/UI của T004)

T004 + T006 + T008 + T009
  └─ T010
      └─ T011
          └─ T012
```

- T004 và T005 có thể bắt đầu song song sau T003.
- T007 có thể bắt đầu sau T003 nhưng acceptance của US3 cần T004 để quan sát trên UI.
- T009 cần T004 vì sửa cùng controller/templates và hoàn thiện semantics đọc.
- T010–T012 chỉ bắt đầu khi mọi story đã đạt checkpoint.

### User story dependencies

- **US1**: Chỉ phụ thuộc foundation, là MVP inbox/read-only.
- **US2**: Chỉ phụ thuộc foundation; tích hợp tự nhiên với inbox qua event/recipient chung.
- **US3**: Phụ thuộc foundation và các source feature 001/002 hiện có; không phụ thuộc publisher.
- **US4**: Phụ thuộc US1 controller/UI, không phụ thuộc US2/US3 về mặt domain.

## Parallel Opportunities

```text
Sau T003:
- T004: inbox/header UI
- T005: announcement domain/publish
- T007: event integration nhóm/đề tài/đăng ký

Sau T007:
- T008: review/results events
- T009: read-state endpoints (nếu T004 đã xong)
```

Các task song song không được commit chung; mỗi Txxx vẫn là một commit riêng.

## Implementation Strategy

1. Hoàn thành T001–T003 để có nền dữ liệu và service an toàn.
2. Hoàn thành T004 và xác minh MVP inbox độc lập.
3. Hoàn thành T005–T006 để đáp ứng trực tiếp REQ-29–30.
4. Hoàn thành T007–T008 cho cảnh báo sự kiện đã duyệt.
5. Hoàn thành T009, sau đó hardening T010–T012.
6. Sau mỗi task: test phù hợp, `git diff --check`, review diff, đánh dấu `[X]`, commit đúng task ID.
7. Không push/merge/xóa branch nếu người dùng chưa yêu cầu riêng.
