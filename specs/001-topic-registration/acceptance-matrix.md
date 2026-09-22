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
| FR-01 Identity, role, scope, ADMIN | REQ-28,31–32 + U | AS-1.3,1.5–1.8,1.14–1.18,2.4; AT-06,09,10,14; SC-004,007 | AuthenticationTest, AuthorizationPolicyTest, AdminAccountServiceTest, AdminAccountControllerTest, DashboardServiceTest, NavigationModelAdviceTest, FoundationWebTest, PeriodContractTest, LoginE2ETest, các contract test theo story | CONFIRMED | FOUNDATION PASS; T021 quyền sau khóa + T023 CSRF/role DB/scope khoa PASS; T070 chặn DEAN khỏi luồng tác giả ở backend/UI, test riêng deferred; T075 dashboard actor-scoped PASS; T080 cấp tài khoản từng người/import nguyên tử/file mẫu và chặn non-ADMIN PASS 32 unit/web/preview tests; T081 tách Dashboard, ba danh sách khóa loại và trang tạo/import riêng PASS 26 test mục tiêu + visual QA desktop/mobile; full suite 172/179 đạt, 7 lỗi môi trường MySQL đã biết ở PeriodContractTest; moderation NOT RUN |
| FR-02 Tạo bốn loại đợt | REQ-01,04 | AS-1.1,1.4; AT-01; SC-001 | PeriodRulesTest, PeriodContractTest | CONFIRMED | US1 PASS T018–T024: bốn loại, persistence, service và web contract |
| FR-03 Lịch GV/SV | REQ-02–05 + U | AS-1.1,1.4,1.9–1.10,2.2,5.2,5.11,5.13,5.17–5.18; AT-02,13; SC-003 | PeriodRulesTest, PeriodContractTest, PeriodScheduleIT, DashboardServiceTest, FoundationWebTest | CONFIRMED | US1 PASS T018–T024; kiểm tra bổ sung ngày-only/ngày đã qua trong fix T017; T077 giữ Dashboard đếm active, nhãn giai đoạn và ba số hồ sơ; T082 đưa lịch sử quá hạn vào danh sách/bộ lọc, unit + preview 15 test PASS; follow-up 2026-09-21 thêm bộ lọc kết hợp theo loại/công việc chờ và badge nhấn màu số khác 0, `FoundationWebTest` PASS |
| FR-04 Ngày theo loại | REQ-06–07 + U | AS-1.2,1.11–1.12; AT-01,13; SC-001 | PeriodRulesTest, PeriodContractTest | CONFIRMED | US1 PASS T018–T024: DB-02, required/null theo loại và chỉ dời muộn hơn |
| FR-05 Nội dung và hạn gửi đề tài | REQ-08–09 + U | AS-2.1–2.2,2.4–2.5,2.10–2.12; AT-02,03,06,12; SC-002–004 | TopicRulesTest, TopicAuthorServiceTest, TopicAuthorContractTest, FoundationWebTest | CONFIRMED | T025: topic thuộc một đợt, không reuse/copy; T070 chặn tài khoản có DEAN tạo/xem/sửa/gửi topic và ẩn menu; T078 chặn mở form/tạo mới ngoài hạn ở service dưới khóa, giữ xem/sửa nháp cũ và tách active/expired trên UI; unit/preview PASS |
| FR-06 Tác giả là GVHD chính, tối đa một GVHD phối hợp | REQ-10–11 + U | AS-2.1,2.3,2.6–2.9; AT-03,12; SC-002 | TopicRulesTest, TopicSubmissionIT | CONFIRMED | T069: backend tự gán tác giả slot 1, form chỉ chọn tối đa một người phối hợp; tests deferred theo yêu cầu người dùng |
| FR-07 Duyệt và công bố | REQ-02,08,15 + U | AS-3.1–3.5,5.17–5.18; AT-05,06,12; SC-002–004 | TopicReviewContractTest, TopicPublicationIT | CONFIRMED | T033–T037: duyệt/từ chối từng đề tài và công bố toàn bộ APPROVED trong đợt; T071 thêm câu chữ phân biệt; T072 tách route/màn hình đề xuất giảng viên khỏi xét đăng ký sinh viên; automated tests NOT RUN |
| FR-08 Nhóm, lời mời và trưởng nhóm | REQ-12–13 + U | AS-4.1–4.2,4.4,4.6–4.16; AT-04,06–08; SC-003–005 | GroupRulesTest, GroupContractTest, GroupMembershipIT, RegistrationFeedbackRegressionTest, GroupPeriodPhaseTest, FoundationWebTest | CONFIRMED | T054, T065–T068: lời mời cần SV chấp nhận, PENDING giữ chỗ; thành viên được tự rời, leader một người được giải tán; nhóm bị khóa khi registration PENDING/ACCEPTED. T071 tái hiện endpoint reject trên local: UI và MySQL đều ghi REJECTED; bổ sung xác nhận, thông báo kết quả và thời gian phản hồi. T072 chỉ cho mở nhóm từ lịch sử khi SV còn là thành viên hiện tại. T073 chặn nhóm 1/2 người và cho nhóm đúng 3 người qua rules; test hồi quy PASS, contract/concurrency còn thuộc T041/T049/T057–T058. T079 tách trang chọn đợt thành sắp/đang/đã kết thúc, kiểm tra hai biên thời gian và giữ lối xem nhóm/lời mời cũ; unit/preview PASS. |
| FR-09 Một nhóm/SV/đợt | REQ-13 + U | AS-4.3,4.5; AT-04,07; SC-003,005 | GroupRulesTest, GroupMembershipIT | CONFIRMED | NOT RUN |
| FR-10 Gửi đăng ký | REQ-03,14–15 + U | AS-4.13,5.1–5.3; AT-04–09,11; SC-002–005 | RegistrationRulesTest, RegistrationContractTest, RegistrationFeedbackRegressionTest, GroupRegistrationFeedbackTest, SchemaSeedMigrationIT | CONFIRMED | T051–T056 persistence, service, routes và Tabler UI nối từ trang nhóm. T073 yêu cầu đúng 3 người ở rules/backend/UI, V7 tạo snapshot bằng trigger nguyên tử và preview regression PASS. T074 hiển thị đăng ký PENDING/ACCEPTED, tên đề tài và liên kết chi tiết cho mọi thành viên hiện tại; unit + preview regression PASS. T060 migration V7 và snapshot trigger trên MySQL 8.4.11 PASS; contract/concurrency vẫn chờ task riêng. |
| FR-11 Chấp nhận một nhóm/đề tài | REQ-16 + U | AS-5.4,5.6–5.8,5.14–5.16; AT-05–08,11; SC-002,005,008 | RegistrationConstraintsIT, RegistrationConcurrencyIT, GroupRegistrationFeedbackTest, FoundationWebTest | CONFIRMED | T051–T056 accept + auto-reject cùng transaction, dean-scoped routes và danh sách xét tại chi tiết đợt. T074 cung cấp phản hồi ACCEPTED nổi bật trên trang nhóm cho cả trưởng nhóm và thành viên thường, lấy từ registration thật qua port; 8 unit/preview tests PASS ngày 2026-09-13. Contract/concurrency MySQL vẫn thuộc T049/T050/T057. |
| FR-12 Từ chối đăng ký | REQ-16 + U | AS-5.5–5.6,5.14–5.16; AT-05,06,11; SC-002,008 | RegistrationContractTest, RegistrationJourneyE2ETest | CONFIRMED | T051–T056 DB-10 + manual reason bắt buộc; form 422 khi trống và UI hiển thị reason thủ công/tự động; compile/local startup PASS; tests NOT RUN |
| FR-13 Rút và đăng ký lại | REQ-03,14–16 + U | AS-5.9–5.15; AT-02,04–05,07–08; SC-002,003,005 | RegistrationRulesTest, RegistrationConcurrencyIT, RegistrationFeedbackRegressionTest, SchemaSeedMigrationIT | CONFIRMED | T051–T054 tạo ID mới, rút chỉ PENDING trong hạn và giữ lịch sử; active adapter mở lại thao tác nhóm sau REJECTED/WITHDRAWN. T072 chuyển route quyết định của đăng ký đã xử lý về trang lịch sử. T073 dùng snapshot bất biến cho quyền xem/chi tiết/danh sách xét. T060 nâng V6 có dữ liệu lịch sử lên V7 và backfill snapshot trên MySQL 8.4.11 PASS; race đăng ký vẫn chờ T057. |
| FR-14 Vòng đời đề tài | REQ-02–03,08–11,15,31 + U | AS-2.1–2.3,2.7–2.9,3.1,3.4–3.5; AT-12; SC-002 | TopicReviewRulesTest, TopicPublicationIT | CONFIRMED | T025 giải phóng vòng đời tác giả/GVHD; tests NOT RUN |
| FR-15 Khóa sửa lịch | REQ-03–05,31 + U | AS-1.11–1.13; AT-13; SC-001,003,005 | PeriodRulesTest, PeriodContractTest, PeriodScheduleIT | CONFIRMED | US1 PASS T018–T024: FOR UPDATE, version, khóa theo thời gian sau chờ và chỉ gia hạn mốc còn hiệu lực |
| NFR-01 Bảo mật nền tảng | Constitution III,IV,IX + U | AT-06,09,10,14; SC-004,007 | AuthenticationTest, AuthorizationPolicyTest, WebExceptionHandlerTest, TestDatabaseSafetyTest | CONFIRMED | FOUNDATION PASS 2026-09-10; T023 period contract PASS cho CSRF/role/scope/input/version; route story khác còn NOT RUN |
| NFR-02 Responsive | U 2026-09-09 | AT-15; SC-009 | AssetPortabilityE2ETest, viewport E2E | CONFIRMED | T023 template responsive render PASS trong FoundationWebTest; T075 sửa flex-grow nhóm sidebar, preview tại 750px và E2E desktop 1280px đo khoảng cách hai nhóm menu PASS; T081 visual QA trang tạo/import tại desktop 1440px và mobile 390px PASS; T062 chạy 26 route ở 360/768/1280 px, không tràn ngang toàn trang và bảng rộng cuộn trong vùng chứa: PASS |

## Gate status

| Gate | Trạng thái | Điều kiện còn lại |
|---|---|---|
| G-Identity / T006,T080–T081 | PASS trong phạm vi cấp tài khoản | Foundation identity/security T008–T016 và provisioning/layout ADMIN T080–T081 đã triển khai; contract học vụ theo story chạy ở phase tương ứng |
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
| Role và scope | PASS | `AuthorizationPolicyTest`: 5 test methods đọc lại role/khoa/bộ môn/tác giả/trưởng nhóm từ dữ liệu; foundation kiểm tra ADMIN qua policy khẩn cấp, T080 bổ sung policy cấp tài khoản |
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

## Bằng chứng feedback đăng ký T073 — 2026-09-13

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| Nhóm đủ đúng 3 | PASS | `RegistrationFeedbackRegressionTest` cho nhóm đúng 3 qua rules, nhóm 1/2 người nhận `GROUP_INCOMPLETE` |
| Snapshot lịch sử | PASS | Test service dùng ba snapshot và leader lúc gửi dù nhóm hiện tại rỗng/đổi leader; `FoundationWebTest` kiểm tra câu chữ điều kiện đủ 3 và “Thành viên tại thời điểm gửi” |
| Unit/web không cần DB | PASS | `./mvnw test`: 39/45 test đạt; 6 lỗi còn lại đều do `PeriodContractTest` yêu cầu `TEST_DATABASE_URL`, không phải assertion/code regression |
| Migration V7 trên MySQL 8.4 | PASS tại T060 | T073 chưa chạy do thiếu DB test ở thời điểm đó; T060 đã chạy trên MySQL 8.4.11 cô lập, bằng chứng ở bảng dưới. |

## Bằng chứng schema/seed T060 — 2026-09-14

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| DB rỗng → V7 | PASS | `SchemaSeedMigrationIT`: 7 migration hợp lệ, 22 bảng nghiệp vụ, 44 CHECK, 49 FK, 19 UNIQUE, 23 PK tính cả Flyway và đủ 9 trigger; không tự tạo tài khoản demo. |
| Seed chủ động | PASS | Thiếu secret không ghi dữ liệu; nhập secret sinh tại runtime tạo 11 tài khoản, 4 đợt, 3 đăng ký với 9 snapshot; hash khớp, seed lại không nhân đôi hoặc ghi đè trạng thái/ngày đã sửa. |
| V6 có fixture → V7 | PASS | Giữ 3 đăng ký lịch sử; backfill 7 snapshot, gồm fallback một leader cho nhóm đã giải tán; checksum V1–V6 không đổi, chạy lại Flyway không áp thêm migration. Snapshot V7 chặn UPDATE/DELETE và trigger từ chối gửi từ nhóm một thành viên. |
| Kiểm thử | PASS | MySQL 8.4.11 ở database `topic_registration_t060_test` cô lập; `./mvnw test` và `./mvnw verify -Pintegration` đạt, gồm 3/3 ca T060 và 13/13 ca integration/concurrency. Không dùng DB local cổng 3306 hay sửa migration V1–V7. |

## Bằng chứng tái cấu trúc MVC T076 — 2026-09-14

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| Cấu trúc production | PASS | 74 lớp production được gom theo các layer `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security`, `seed`, `service`, `util`; chỉ còn một lớp `TopicRegistrationApplication` mang `@SpringBootApplication`. |
| Hành vi và hợp đồng | PASS | Không đổi route, template, migration hoặc logic nghiệp vụ; production và test compile sạch. Bộ regression không cần MySQL đạt 77/77 test, gồm 2/2 ca `LayeredArchitectureTest`. |
| Full unit/web suite | BLOCKED do môi trường | `env -u JAVA_HOME ./mvnw test` chạy 84 test: 77 đạt, 7 lỗi đều thuộc `PeriodContractTest` vì chưa cấu hình `TEST_DATABASE_URL`; baseline trước refactor có cùng 7 lỗi. Không dùng database local có dữ liệu thật và không bỏ qua test. |

## Bằng chứng feedback ADMIN T080 — 2026-09-16

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| Tạo từng tài khoản | PASS | `AdminAccountServiceTest` kiểm tra username/họ tên chuẩn hóa, BCrypt không chứa plaintext và DEAN nhận cả DEAN+LECTURER; view trả đúng loại vừa tạo. |
| Import CSV nguyên tử | PASS | Test kiểm tra ba loại hợp lệ chỉ ghi sau khi toàn file đạt; một dòng ADMIN/sai loại/sai mã bộ môn làm importedCount=0 và không ghi account/role; giới hạn/header/UTF-8 được kiểm tra trong service. |
| Phân quyền và input | PASS | Mọi entry service gồm danh sách, tạo, import và tải file mẫu đều gọi `requireRole(ADMIN)`; form whitelist không nhận trường `roles`; controller trả 422 cho validation/import lỗi. |
| UI và điều hướng | PASS | `DashboardServiceTest`, `NavigationModelAdviceTest` và `FoundationWebTest` kiểm tra lối quản lý tài khoản chỉ dành ADMIN, trang danh sách/bộ lọc/import/file mẫu và form tạo render trong preview. |
| Kiểm thử mục tiêu | PASS | `AdminAccountServiceTest,AdminAccountControllerTest,NavigationModelAdviceTest,DashboardServiceTest,FoundationWebTest`: 32 test, 0 failure/error. |
| Full unit/web suite | BLOCKED do môi trường | `env -u JAVA_HOME ./mvnw test`: 177 test, 170 đạt; 7 lỗi đều thuộc `PeriodContractTest` vì thiếu `TEST_DATABASE_URL`, không liên quan T080. Không dùng database local có dữ liệu thật. |

## Bằng chứng layout ADMIN T081 — 2026-09-16

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| Dashboard tài khoản | PASS | `DashboardServiceTest` kiểm tra ADMIN nhận bốn thống kê thật: tổng tài khoản, Sinh viên, Giảng viên và Trưởng khoa; không gọi clock hoặc truy vấn học vụ. `JdbcDashboardQuery` đếm DEAN riêng khỏi LECTURER dù DEAN đồng thời mang hai role. |
| Danh sách khóa loại | PASS | `AdminAccountControllerTest` kiểm tra `/admin/accounts/students`, `/lecturers`, `/deans` luôn dựng `AccountFilter` với loại cố định phía backend; `/admin/accounts` chuyển hướng về Sinh viên và tạo thành công chuyển về danh sách đúng loại. |
| Tạo và import riêng | PASS | `FoundationWebTest` kiểm tra ba trang danh sách không chứa import/thống kê, còn `/admin/accounts/new` chứa form đơn lẻ, import CSV và file mẫu; sidebar có bốn mục ADMIN riêng. Preview chỉ cho GET, từ chối mọi request mutation và không tạo session CSRF khi render form. |
| Visual QA | PASS | Preview desktop 1440px cho Dashboard, ba danh sách và trang tạo/import tách biệt; Playwright viewport 390px xác nhận form, nút, file input và bảng mã bộ môn xếp dọc, không tràn ngang. |
| Kiểm thử mục tiêu | PASS | `env -u JAVA_HOME ./mvnw -Dtest=DashboardServiceTest,AdminAccountControllerTest,FoundationWebTest test`: 26 test, 0 failure/error. |
| Full unit/web suite | BLOCKED do môi trường | `env -u JAVA_HOME ./mvnw test`: 179 test, 172 đạt; 7 lỗi đều thuộc `PeriodContractTest` vì thiếu `TEST_DATABASE_URL`, không liên quan T081. Không dùng database local có dữ liệu thật. |

## Bằng chứng ma trận phân quyền T061 — 2026-09-22

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| Xác thực và chống CSRF | PASS | Request chưa đăng nhập bị chuyển tới `/login`; POST thiếu CSRF nhận 403 và không làm thay đổi đăng ký. |
| Ranh giới ADMIN/học vụ | PASS | ADMIN được mở dashboard, quản lý tài khoản và moderation nhưng nhận 403 ở các GET/POST học vụ; dashboard ADMIN không lộ dữ liệu đợt hoặc đề tài. |
| Role, scope và quan hệ | PASS | Request trực tiếp xác nhận DEAN/LECTURER/STUDENT không đi qua ranh giới role; role giả trong request không vượt qua role persisted. Thay đổi khoa, tác giả hoặc trưởng nhóm trong DB có hiệu lực ngay; tài nguyên ngoài scope trả 404 chống dò, quan hệ không hợp lệ trả 403. |
| Secret | PASS | Mật khẩu tạo tài khoản không xuất hiện trong response/log; DB chỉ lưu BCrypt hash và hash cũng không bị ghi log. |
| Kiểm thử mục tiêu | PASS | `AuthorizationMatrixTest`: 5 test, 0 failure/error trên MySQL 8.4.11 cô lập với Flyway V1–V11. |

## Bằng chứng JAR/assets/responsive T062 — 2026-09-22

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| Portability và asset | PASS | Executable `topic-registration.jar` khởi động bằng profile preview với working directory tạm nằm ngoài repository; browser chỉ request CSS/JS/image cùng origin, không dùng CDN hay file từ source tree. |
| Đóng gói notices | PASS | JAR chứa `THIRD_PARTY_NOTICES.md`, license Tabler/Bootstrap/Popper và bundle Tabler 1.4.0 dưới `META-INF`/`BOOT-INF`. |
| Responsive AT-15 / SC-009 | PASS | 26 route chính được render ở 360, 768 và 1280 px mà không tràn ngang toàn trang; bảng đợt 9 cột tại 768 px cuộn trong `.table-responsive`, vùng bảng mobile không làm rộng document. |
| Accessibility và trạng thái | PASS | Skip link, menu mobile, thông báo rỗng và thao tác chính dùng được bằng bàn phím; form control có label, action có accessible name; badge có chữ và trang lỗi 404 hiển thị mã/tiêu đề, không chỉ biểu đạt bằng màu. |
| Kiểm thử mục tiêu | PASS | `AssetPortabilityE2ETest`: 4 test, 0 failure/error trên executable JAR và Chromium; đồng thời phát hiện/sửa view-model preview thiếu `canViewGroup` ở trang lời mời. |
