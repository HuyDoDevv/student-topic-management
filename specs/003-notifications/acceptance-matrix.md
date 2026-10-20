# Acceptance Matrix: Thông báo trong hệ thống

**Feature**: `003-notifications`

**Branch**: `feature/003-notifications`

**Ngày xác minh**: 2026-09-24

**Môi trường**: Java 21, Spring Boot 4.1.1, MySQL 8.4 test riêng, Playwright

## Yêu cầu nguồn

| ID | Tiêu chí nghiệm thu | Bằng chứng chính | Trạng thái |
|---|---|---|---|
| REQ-29 | Người dùng nhận/xem thông tin chính thức trong ứng dụng | `NotificationInboxWebTest`, `NotificationReadWebTest`, `NotificationReadIT`, `NotificationFlowE2ETest` | PASS |
| REQ-30 | ADMIN đăng cấp trường; DEAN đăng đúng khoa; fan-out đúng scope | `AnnouncementServiceTest`, `AnnouncementPublicationIT`, `AnnouncementWebTest`, `NotificationFlowE2ETest` | PASS |

## Quyết định PN-01–PN-12

| ID | Quyết định đã duyệt | Bằng chứng | Trạng thái |
|---|---|---|---|
| PN-01 | Chỉ in-app, không email/SMS/push/WebSocket | Dependency/route review; không có outbound channel trong implementation | PASS |
| PN-02 | ADMIN cấp trường, DEAN đúng khoa CNTT | `AnnouncementServiceTest`, `AnnouncementPublicationIT`, `AnnouncementWebTest`, E2E scope khoa | PASS |
| PN-03 | DRAFT → PUBLISHED; published bất biến | `AnnouncementServiceTest`, `AnnouncementPublicationIT`, `AnnouncementWebTest` | PASS |
| PN-04 | Snapshot tài khoản enabled lúc publish | `AnnouncementPublicationIT.adminAndDeanPublishToEnabledScopeExactlyOnce` | PASS |
| PN-05 | Badge, tối đa 5 mục, inbox phân trang/filter | `NotificationInboxWebTest`, `NotificationFlowE2ETest` | PASS |
| PN-06 | Mark-one/mark-all, read-state theo tài khoản | `NotificationReadWebTest`, `NotificationReadIT`, `NotificationConcurrencyIT` | PASS |
| PN-07 | Phát cảnh báo cho toàn bộ ma trận sự kiện | `RegistrationNotificationIT`, `ReviewResultNotificationIT` | PASS |
| PN-08 | Loại actor khi actor không phải recipient bắt buộc | Recipient assertions trong hai integration class sự kiện | PASS |
| PN-09 | Nguồn và notification cùng transaction | Rollback cases trong `RegistrationNotificationIT`, `ReviewResultNotificationIT`, `NotificationConcurrencyIT` | PASS |
| PN-10 | Retry/concurrency không tạo delivery trùng | `NotificationConcurrencyIT.concurrentDuplicateEmissionCreatesOneEventAndOneDeliveryPerRecipient` và publish race | PASS |
| PN-11 | Không tự xóa trong v1 | Endpoint/schema review: không có delete/retention job | PASS |
| PN-12 | Target nội bộ do server resolve, route đích kiểm tra lại quyền | `NotificationServiceTest`, `NotificationReadIT`, `NotificationReadWebTest` | PASS |

## Ma trận sự kiện tự động

| Sự kiện | Test chứng minh recipient/rollback/dedup | Trạng thái |
|---|---|---|
| Mời sinh viên vào nhóm | `RegistrationNotificationIT.groupInvitationLifecycleNotifiesInviteeAndLeaderOnce` | PASS |
| Chấp nhận/từ chối lời mời | `RegistrationNotificationIT.groupInvitationLifecycleNotifiesInviteeAndLeaderOnce` | PASS |
| Duyệt/từ chối đề tài giảng viên | `RegistrationNotificationIT.topicDecisionsNotifyAuthorAndRollbackWithTheSourceTransition` | PASS |
| Chấp nhận/từ chối/tự từ chối đăng ký | Các registration decision tests trong `RegistrationNotificationIT` | PASS |
| Phân công giảng viên chấm trực tiếp | `ReviewResultNotificationIT.directGraderAssignmentNotifiesOnlyTheAssignedLecturer` | PASS |
| Thêm giảng viên vào hội đồng | `ReviewResultNotificationIT.councilCreationNotifiesEveryPersistedMemberOnce` | PASS |
| Công bố kết quả từng sinh viên | `ReviewResultNotificationIT.resultPublicationNotifiesEachSnapshotStudentAndRollsBackAtomically` | PASS |

## Success Criteria

| ID | Kết quả và bằng chứng | Trạng thái |
|---|---|---|
| SC-001 | School/faculty publication fan-out đúng enabled scope; account ngoài khoa không nhận trong integration và E2E | PASS |
| SC-002 | Mọi event trong ma trận có đúng một delivery/recipient khi commit và không có row khi rollback | PASS |
| SC-003 | Duplicate emit và concurrent publish bị unique/state transition chặn; chỉ một request thắng | PASS |
| SC-004 | Cross-account IDOR, sai role/scope và CSRF bị backend từ chối, read-state không đổi | PASS |
| SC-005 | Fixture 2.000 accounts/100.000 deliveries: unread ~0,016 ms, top 5 ~0,067 ms, inbox category ~2,71 ms; không full scan `user_notifications` | PASS |
| SC-006 | Playwright mở chuông bằng bàn phím, mở item và xác nhận read-state trong không quá ba thao tác; mobile không tràn ngang | PASS |

## Full-suite evidence

| Lệnh | Kết quả |
|---|---|
| `env -u JAVA_HOME ./mvnw test` | PASS — 234 tests, 0 failure/error |
| `env -u JAVA_HOME ... ./mvnw verify -Pintegration` | PASS — 234 unit/web + 35 integration/concurrency tests |
| `env -u JAVA_HOME ... ./mvnw verify -Pe2e` | PASS — 234 unit/web + 7 Playwright tests, gồm `NotificationFlowE2ETest` |
| MySQL `EXPLAIN ANALYZE` trên fixture 2.000/100.000 | PASS — recipient indexes được dùng, không full scan bảng delivery |
| `src/test/resources/database/full-schema-check.sql` | PASS — fixture và constraint rejection checks |
| `git diff --check` | PASS |

Các biến môi trường database/E2E được cấp ngoài repository; không ghi credential vào tài liệu hoặc
mã nguồn. Database test được reset bằng Flyway giữa các test class, không dùng dữ liệu local thật.

## Truy vết file triển khai

| Phạm vi | File chính |
|---|---|
| Schema V12 | `src/main/resources/db/migration/V12__implement_in_app_notifications.sql` |
| Event/fan-out/inbox/read | `NotificationService`, `NotificationEventKey`, `NotificationTargetResolver`, notification repositories/entities |
| Announcement domain/UI | `AnnouncementService`, `AnnouncementController`, `templates/announcements/` |
| Inbox/header/UI | `NotificationController`, `NotificationModelAdvice`, `templates/notifications/list.html`, `fragments/header.html` |
| Sự kiện nguồn | `GroupService`, `TopicReviewService`, `RegistrationService`, `DirectGradingService`, `CouncilService`, `ResultService` |
| Tài liệu dữ liệu (lịch sử) | Bộ tài liệu database đã được gỡ khỏi repository ngày 20/10/2026; schema hiện tại nằm tại [database/V1__baseline.sql](../../database/V1__baseline.sql). |
