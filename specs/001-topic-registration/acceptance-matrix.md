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
| FR-01 Identity, role, scope, ADMIN | REQ-28,31–32 + U | AS-1.3,1.5–1.8,1.14–1.16; AT-06,09,10,14; SC-004,007 | AuthenticationTest, AuthorizationPolicyTest, LoginE2ETest, các contract test theo story | CONFIRMED | FOUNDATION PASS; T021 quyền sau khóa + T023 CSRF/role DB/scope khoa PASS; moderation NOT RUN |
| FR-02 Tạo bốn loại đợt | REQ-01,04 | AS-1.1,1.4; AT-01; SC-001 | PeriodRulesTest, PeriodContractTest | CONFIRMED | US1 PASS T018–T024: bốn loại, persistence, service và web contract |
| FR-03 Lịch GV/SV | REQ-02–05 + U | AS-1.1,1.4,1.9–1.10,2.2,5.2,5.11,5.13,5.17–5.18; AT-02,13; SC-003 | PeriodRulesTest, PeriodContractTest, PeriodScheduleIT | CONFIRMED | US1 PASS T018–T024; kiểm tra bổ sung ngày-only/ngày đã qua trong fix T017 |
| FR-04 Ngày theo loại | REQ-06–07 + U | AS-1.2,1.11–1.12; AT-01,13; SC-001 | PeriodRulesTest, PeriodContractTest | CONFIRMED | US1 PASS T018–T024: DB-02, required/null theo loại và chỉ dời muộn hơn |
| FR-05 Nội dung và hạn gửi đề tài | REQ-08–09 + U | AS-2.1–2.2,2.5,2.10–2.11; AT-02,03,12; SC-002,003 | TopicRulesTest, TopicAuthorContractTest | CONFIRMED | T025: topic thuộc một đợt, không reuse/copy; danh mục seed/import; tests NOT RUN |
| FR-06 Tác giả là GVHD chính, tối đa một GVHD phối hợp | REQ-10–11 + U | AS-2.1,2.3,2.6–2.9; AT-03,12; SC-002 | TopicRulesTest, TopicSubmissionIT | CONFIRMED | T069: backend tự gán tác giả slot 1, form chỉ chọn tối đa một người phối hợp; tests deferred theo yêu cầu người dùng |
| FR-07 Duyệt và công bố | REQ-02,08,15 + U | AS-3.1–3.5,5.17–5.18; AT-05,06,12; SC-002–004 | TopicReviewRulesTest, TopicReviewContractTest, TopicPublicationIT | CONFIRMED | T033–T037: duyệt/từ chối từng đề tài, công bố toàn bộ APPROVED trong đợt và thao tác tại chi tiết đợt. T034 TopicReviewRulesTest 7/7 PASS (quy tắc trạng thái); contract/integration NOT RUN |
| FR-08 Nhóm, lời mời và trưởng nhóm | REQ-12–13 + U | AS-4.1–4.2,4.4,4.6–4.15; AT-04,06–08; SC-003–005 | GroupRulesTest, GroupContractTest, GroupMembershipIT | CONFIRMED | T054, T065–T068: lời mời cần SV chấp nhận, PENDING giữ chỗ; thành viên được tự rời, leader một người được giải tán; nhóm bị khóa khi registration PENDING/ACCEPTED; compile/startup PASS; tests NOT RUN |
| FR-09 Một nhóm/SV/đợt | REQ-13 + U | AS-4.3,4.5; AT-04,07; SC-003,005 | GroupRulesTest, GroupMembershipIT | CONFIRMED | NOT RUN |
| FR-10 Gửi đăng ký | REQ-03,14–15 + U | AS-5.1–5.3; AT-05–09,11; SC-002–005 | RegistrationRulesTest, RegistrationContractTest | CONFIRMED | T051–T056 persistence, service, routes và Tabler UI nối từ trang nhóm; compile/local startup PASS, 5 preview pages HTTP 200; tests NOT RUN |
| FR-11 Chấp nhận một nhóm/đề tài | REQ-16 + U | AS-5.4,5.6–5.8,5.14–5.16; AT-05–08,11; SC-002,005,008 | RegistrationConstraintsIT, RegistrationConcurrencyIT | CONFIRMED | T051–T056 accept + auto-reject cùng transaction, dean-scoped routes và danh sách xét tại chi tiết đợt; compile/local startup PASS, preview HTTP 200; tests NOT RUN |
| FR-12 Từ chối đăng ký | REQ-16 + U | AS-5.5–5.6,5.14–5.16; AT-05,06,11; SC-002,008 | RegistrationContractTest, RegistrationJourneyE2ETest | CONFIRMED | T051–T056 DB-10 + manual reason bắt buộc; form 422 khi trống và UI hiển thị reason thủ công/tự động; compile/local startup PASS; tests NOT RUN |
| FR-13 Rút và đăng ký lại | REQ-03,14–16 + U | AS-5.9–5.15; AT-02,05,07–08; SC-002,003,005 | RegistrationRulesTest, RegistrationConcurrencyIT | CONFIRMED | T051–T054 tạo ID mới, rút chỉ PENDING trong hạn và giữ lịch sử; active adapter mở lại thao tác nhóm sau REJECTED/WITHDRAWN; compile/startup PASS; web/test NOT RUN |
| FR-14 Vòng đời đề tài | REQ-02–03,08–11,15,31 + U | AS-2.1–2.3,2.7–2.9,3.1,3.4–3.5; AT-12; SC-002 | TopicReviewRulesTest, TopicPublicationIT | CONFIRMED | T025 giải phóng vòng đời tác giả/GVHD; T034 TopicReviewRulesTest 7/7 PASS (reject về DRAFT, submit lại đúng hạn); giao dịch và dữ liệu TopicPublicationIT NOT RUN |
| FR-15 Khóa sửa lịch | REQ-03–05,31 + U | AS-1.11–1.13; AT-13; SC-001,003,005 | PeriodRulesTest, PeriodContractTest, PeriodScheduleIT | CONFIRMED | US1 PASS T018–T024: FOR UPDATE, version, khóa theo thời gian sau chờ và chỉ gia hạn mốc còn hiệu lực |
| NFR-01 Bảo mật nền tảng | Constitution III,IV,IX + U | AT-06,09,10,14; SC-004,007 | AuthenticationTest, AuthorizationPolicyTest, WebExceptionHandlerTest, TestDatabaseSafetyTest | CONFIRMED | FOUNDATION PASS 2026-09-10; T023 period contract PASS cho CSRF/role/scope/input/version; route story khác còn NOT RUN |
| NFR-02 Responsive | U 2026-09-09 | AT-15; SC-009 | AssetPortabilityE2ETest, viewport E2E | CONFIRMED | T023 template responsive render PASS trong FoundationWebTest; viewport AT-15 chưa chạy |

## Gate status

| Gate | Trạng thái | Điều kiện còn lại |
|---|---|---|
| G-Identity / T006 | CONFIRMED | Foundation identity/security T008–T016 đã triển khai; contract theo story chạy ở phase tương ứng |
| G-Acceptance / T007 | CONFIRMED | Không còn quyết định nghiệp vụ đang mở trong phạm vi hiện tại |
| G-Period / T017 | PASS | Q-02 đã giải quyết; US1 T018–T024 đã triển khai và nghiệm thu |
| G-Topic / T025 | CONFIRMED | Q-05/Q-06 đã giải quyết ngày 2026-09-10; US2 T026–T032 được phép triển khai |
| G-Publish / T033 | CONFIRMED | Không còn; triển khai review trên chi tiết đợt, không auto-publish |
| G-Group / T039,T065 | CONFIRMED | Q-07 đã chốt lại: invitation/accept/reject/cancel, giữ chỗ, tự rời/giải tán và chặn đăng ký khi còn PENDING |
| G-Registration / T047 | CONFIRMED | Q-09 đã đóng: reason thủ công bắt buộc; khóa trước thắng khi rút/accept; giữ lịch sử không thời hạn |

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

## Bằng chứng US1 T018–T024 — 2026-09-10

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| AT-01 / SC-001 | PASS | `PeriodRulesTest` kiểm tra đủ COURSE/NCKH/TLCN/KLTN và ngày theo loại; `PeriodContractTest` xác nhận tạo/đọc và rollback dữ liệu sai; `RegistrationPeriodPersistenceIT` xác nhận CHECK MySQL |
| AT-02 | PASS trong phạm vi US1 | `PeriodRulesTest` kiểm tra thứ tự, biên đầu/cuối ngày và ngày SV phải sau ngày kết thúc GV; `PeriodContractTest` từ chối ngày đã qua và giá trị có giờ; trạng thái được suy ra từ clock, không lưu cờ mở/đóng |
| AT-13 | PASS | `PeriodRulesTest` kiểm tra khóa/gia hạn/ngày phụ; `PeriodContractTest` kiểm tra quyền, scope và stale version; `PeriodScheduleIT` có 2 ca giữ `FOR UPDATE` qua giờ mở, từ chối sửa tên nhưng cho gia hạn hai mốc kết thúc còn hiệu lực |
| Lệnh nghiệm thu | PASS | `./mvnw -Pintegration -Dit.test=PeriodScheduleIT verify`: 38 unit/web test và 2 concurrency test, 0 failure, 0 error trên MySQL 8.4.11 tạm |

Điều chỉnh lịch ngày 2026-09-10 được kiểm tra lại bằng `./mvnw clean verify -Pintegration`:
41 unit/web test và 10 integration/concurrency test đạt; Flyway áp dụng V1–V3 trên MySQL
8.4.11 tạm. Contract xác nhận form chỉ nhận ngày, từ chối ngày đã qua/giá trị có giờ và
lưu đúng đầu/cuối ngày Việt Nam; database từ chối `lecturer_end = student_start`.

Database nghiệm thu có tên chứa `test`, được tạo cô lập và xóa sau khi chạy; không truy cập
database local của người dùng.
