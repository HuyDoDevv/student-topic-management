# Acceptance Matrix — 001 Topic Registration

**Xác nhận ngày 2026-09-09:** toàn bộ Acceptance Scenarios và Success Criteria hiện tại
được dùng làm chuẩn nghiệm thu cho phiên bản local. Bắt buộc kiểm tra chức năng, phân quyền
backend, bảo mật nền tảng, responsive và các xung đột dữ liệu chính. Không có SLA định
lượng về tải, thời gian phản hồi, uptime hoặc backup.

`CONFIRMED` nghĩa là tiêu chí đã được người dùng chấp nhận. `BLOCKED` nghĩa là kết quả
chi tiết còn phụ thuộc một quyết định nghiệp vụ chưa chốt. `FOUNDATION PASS` chỉ xác nhận
phần nền tảng đã chạy, không thay thế test của user story chưa triển khai. `RED` ghi nhận
test đã chạy và thất bại vì hành vi chưa cài theo TDD. `NOT RUN` nghĩa là chưa có bằng
chứng test; không được hiểu là đạt.

| Yêu cầu | Nguồn | AS / AT / SC | Test dự kiến | Trạng thái đặc tả | Bằng chứng |
|---|---|---|---|---|---|
| FR-01 Identity, role, scope, ADMIN | REQ-28,31–32 + U | AS-1.3,1.5–1.8,1.14–1.16; AT-06,09,10,14; SC-004,007 | AuthenticationTest, AuthorizationPolicyTest, LoginE2ETest, các contract test theo story | CONFIRMED | FOUNDATION PASS 2026-09-10: login/role/scope/session/cookie; T019 RED: period endpoints chưa cài; moderation còn NOT RUN |
| FR-02 Tạo bốn loại đợt | REQ-01,04 | AS-1.1,1.4; AT-01; SC-001 | PeriodRulesTest, PeriodContractTest | CONFIRMED | RED T018–T019 2026-09-10: 6 unit + 5 contract tests chạy, chờ T020–T023 |
| FR-03 Lịch GV/SV | REQ-02–05 + U | AS-1.1,1.9–1.10,2.2,5.2,5.11,5.13,5.17–5.18; AT-02,13; SC-003 | PeriodRulesTest, PeriodScheduleIT | CONFIRMED | RED T018–T019 2026-09-10: 6 unit + 5 contract tests chạy, chờ T020–T023 |
| FR-04 Ngày theo loại | REQ-06–07 + U | AS-1.2,1.11–1.12; AT-01,13; SC-001 | PeriodRulesTest, PeriodContractTest | CONFIRMED | RED T018–T019 2026-09-10: 6 unit + 5 contract tests chạy, chờ T020–T023 |
| FR-05 Nội dung và hạn gửi đề tài | REQ-08–09 + U | AS-2.1–2.2,2.5,2.10–2.11; AT-02,03,12; SC-002,003 | TopicRulesTest, TopicAuthorContractTest | BLOCKED Q-05 | NOT RUN |
| FR-06 1–2 GVHD cùng bộ môn | REQ-10–11 + U | AS-2.1,2.3,2.6–2.9; AT-03,12; SC-002 | TopicRulesTest, TopicSubmissionIT | BLOCKED Q-06 | NOT RUN |
| FR-07 Duyệt và công bố | REQ-02,08,15 + U | AS-3.1–3.3,5.17–5.18; AT-05,06,12; SC-002–004 | TopicReviewContractTest, TopicPublicationIT | BLOCKED G-Publish | NOT RUN |
| FR-08 Nhóm và trưởng nhóm | REQ-12–13 + U | AS-4.1–4.2,4.4,4.6–4.14; AT-04,06–08; SC-003–005 | GroupRulesTest, GroupContractTest, GroupMembershipIT | BLOCKED Q-07 | NOT RUN |
| FR-09 Một nhóm/SV/đợt | REQ-13 + U | AS-4.3,4.5; AT-04,07; SC-003,005 | GroupRulesTest, GroupMembershipIT | CONFIRMED | NOT RUN |
| FR-10 Gửi đăng ký | REQ-03,14–15 + U | AS-5.1–5.3; AT-05–09,11; SC-002–005 | RegistrationRulesTest, RegistrationContractTest | BLOCKED Q-09 | NOT RUN |
| FR-11 Chấp nhận một nhóm/đề tài | REQ-16 + U | AS-5.4,5.6–5.8,5.14–5.16; AT-05–08,11; SC-002,005,008 | RegistrationConstraintsIT, RegistrationConcurrencyIT | BLOCKED Q-09 | NOT RUN |
| FR-12 Từ chối đăng ký | REQ-16 + U | AS-5.5–5.6,5.14–5.16; AT-05,06,11; SC-002,008 | RegistrationContractTest, RegistrationJourneyE2ETest | BLOCKED Q-09 | NOT RUN |
| FR-13 Rút và đăng ký lại | REQ-03,14–16 + U | AS-5.9–5.13; AT-02,05,07–08; SC-002,003,005 | RegistrationRulesTest, RegistrationConcurrencyIT | BLOCKED Q-09 | NOT RUN |
| FR-14 Vòng đời đề tài | REQ-02–03,08–11,15,31 + U | AS-2.1–2.3,2.7–2.9,3.1,3.4–3.5; AT-12; SC-002 | TopicReviewRulesTest, TopicPublicationIT | BLOCKED Q-05/Q-06 | NOT RUN |
| FR-15 Khóa sửa lịch | REQ-03–05,31 + U | AS-1.11–1.13; AT-13; SC-001,003,005 | PeriodRulesTest, PeriodContractTest, PeriodScheduleIT | CONFIRMED | RED T018–T019 2026-09-10: 6 unit + 5 contract tests chạy, chờ T020–T023 |
| NFR-01 Bảo mật nền tảng | Constitution III,IV,IX + U | AT-06,09,10,14; SC-004,007 | AuthenticationTest, AuthorizationPolicyTest, WebExceptionHandlerTest, TestDatabaseSafetyTest | CONFIRMED | FOUNDATION PASS 2026-09-10; T019 RED cho quyền/scope period; route story khác còn NOT RUN |
| NFR-02 Responsive | U 2026-09-09 | AT-15; SC-009 | AssetPortabilityE2ETest, viewport E2E | CONFIRMED | NOT RUN |

## Gate status

| Gate | Trạng thái | Điều kiện còn lại |
|---|---|---|
| G-Identity / T006 | CONFIRMED | Foundation identity/security T008–T016 đã triển khai; contract theo story chạy ở phase tương ứng |
| G-Acceptance / T007 | CONFIRMED | Không còn; các hàng phụ thuộc Q khác vẫn BLOCKED |
| G-Period / T017 | CONFIRMED | Q-02 đã giải quyết ngày 2026-09-10; chờ triển khai T018–T024 |
| G-Topic / T025 | BLOCKED | Q-05, Q-06 |
| G-Publish / T033 | BLOCKED | Granularity và thời điểm xử lý |
| G-Group / T039 | BLOCKED | Q-07 |
| G-Registration / T047 | BLOCKED | Q-09 |

## Bằng chứng foundation T008–T016 — 2026-09-10

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| AT-09 nền tảng | PASS | `WebExceptionHandlerTest`: 400/403/404/409/422, 303 và phản hồi 500 an toàn; `FoundationDatabaseIT`: rollback không đổi số hàng |
| AT-10 nền tảng | PASS | `AuthenticationTest`: 5 test methods cho đúng/sai/khóa/không tồn tại, role giả, mọi tổ hợp STUDENT sai, LECTURER+DEAN hợp lệ, session fixation, CSRF/logout và không self-registration |
| Role và scope | PASS | `AuthorizationPolicyTest`: 5 test methods đọc lại role/khoa/bộ môn/tác giả/trưởng nhóm từ dữ liệu; ADMIN chỉ qua policy khẩn cấp |
| MySQL cô lập | PASS | `TestDatabaseSafetyTest` từ chối URL local/PostgreSQL; `FoundationDatabaseIT`: 4 test methods trên MySQL 8.4.11 tạm, 20 bảng, 6 trigger, 4 role, fixture và hai connection |
| Browser/JPA | PASS | `LoginE2ETest`: Spring local khởi động với Hibernate validate và READ COMMITTED; Chromium login thành công; JSESSIONID HttpOnly/SameSite=Lax |

Lệnh đã đạt: `./mvnw test` (19 tests), `./mvnw verify -Pintegration`
(19 unit/web + 4 integration) và `./mvnw verify -Pe2e` (19 unit/web + 1 E2E).
MySQL test tạm dùng database có tên chứa `test`; không truy cập hoặc reset database local.
Các hàng user story và responsive vẫn giữ `NOT RUN` cho tới khi code/test tương ứng tồn tại.
