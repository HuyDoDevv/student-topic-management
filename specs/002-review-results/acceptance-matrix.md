# Acceptance Matrix: Báo cáo, hội đồng, chấm điểm và kết quả

**Feature**: `002-review-results`
**Cập nhật**: 2026-09-22
**Trạng thái phạm vi T010**: PASS — implementation chấm riêng từng sinh viên, migration,
unit/web, integration/concurrency và Playwright E2E đều đạt trên MySQL test cô lập.

| Requirement | Acceptance scenarios | Implementation evidence | Automated evidence hiện có | Status |
|---|---|---|---|---|
| FR-16–17, FR-24–25 | AS-6.1–6.5 | `ReportService`, `ReportController`, local file repository, report views | Rules/service/controller ở T003; `ReviewWorkflowIT`, upload race và browser upload/download | PASS |
| FR-18–19, FR-24 | AS-7.1–7.6 | `CouncilService`, `CouncilController`, board views | Rules/service/controller ở T004; V8 migration, demo fixture, board concurrency và E2E | PASS |
| FR-20, FR-24–25 | AS-8.1–8.5 | `ScoreRules` bước 0.25, `ScoringService`, `ScoringController`, scoring views định dạng gọn | Rules/service/controller ở T005; follow-up rules/controller 17/17; score/complete race và browser submit có CSRF | PASS |
| FR-21–22, FR-24 | AS-9.1–9.5 | `ResultService`, faculty result controller/views | Rules/service/controller ở T006; atomic complete/publish integration, concurrency và E2E | PASS |
| FR-23–24 | AS-10.1–10.4 | snapshot-filtered result repository/service, student views | Privacy tests ở T007; published snapshot integration và browser outsider 404 | PASS |
| FR-26 | AS-7.7–7.10 | `DirectGradingService`, V10 mode/trigger, direct-grading view, reused scoring/result | Rules/service/controller và regression liên quan 53/53; migration V7→V10 và empty/V6→V10 trên MySQL 8.4 đạt 4/4 | PASS |
| FR-27 | AS-7.11, AS-8.6, AS-9.6, AS-10.5–10.7 | V11, evaluation/result theo student snapshot, scoring UI từng sinh viên, complete theo full matrix, student-scoped query; Trưởng khoa xem đúng đăng ký trước publish và lọc kết hợp đợt/giảng viên/trạng thái | Targeted unit/web 47/47; direct/result group-scoped preview và filter tests 26/26; default suite 200/200; integration/concurrency 18/18; E2E 2/2 gồm privacy giữa hai sinh viên cùng nhóm và outsider 404 | PASS |

## Manual demo evidence

| Journey | Fixture/actor | Expected result | Status |
|---|---|---|---|
| Upload/replace report | `student1.demo`, registration ACCEPTED chưa gắn board | PDF hiện hành được lưu/tải; file cũ được thay trước ACTIVE | AUTOMATED PASS; manual fixture available |
| Report lock and protected download | ACTIVE/PUBLISHED demo registrations | Actor đúng quan hệ tải được; replace bị khóa | AUTOMATED PASS; manual fixture available |
| Board 3–5 members | `dean.demo`, `[DEMO] Hội đồng KLTN - Đang chấm` | Board có Chair, Secretary và member cùng bộ môn | AUTOMATED PASS; manual fixture available |
| Score/resubmit | `lecturer1.demo`, `lecturer4.demo`, `lecturer5.demo` | Chỉ evaluation của actor đổi khi board ACTIVE | AUTOMATED PASS; manual fixture available |
| Complete/publish | Chair `lecturer1.demo`, sau đó `dean.demo` | Toàn board thành COMPLETED rồi PUBLISHED nguyên tử | AUTOMATED PASS; manual fixture available |
| Student visibility | `student1.demo` đến `student3.demo` | Chỉ snapshot group thấy result PUBLISHED của mình | AUTOMATED PASS; manual fixture available |
| Individual result privacy | `student1.demo` và `student2.demo` cùng registration | Mỗi sinh viên nhận điểm/nhận xét riêng, không thấy feedback của người còn lại | AUTOMATED PASS trên MySQL test cô lập và Playwright |

## Verification record

| Command/check | Result | Note |
|---|---|---|
| `env -u JAVA_HOME ./mvnw test` | PASS — 157/157 | Bao gồm 7 `PeriodContractTest`; chạy trên MySQL 8.4.11 test riêng ngày 2026-09-15 |
| `env -u JAVA_HOME ./mvnw verify -Pintegration` | PASS — 157 unit/contract + 18 integration/concurrency | V1–V8, backfill V7, fixture idempotency, workflow thật và ba race scenario |
| `env -u JAVA_HOME ./mvnw verify -Pe2e` | PASS — 157 unit/contract + 2 E2E | Login và upload → score → complete → publish → student privacy; app test dùng cổng ngẫu nhiên và tự dừng |
| `git diff --check` | PASS | Không có whitespace error trong thay đổi T008 và cấu hình port |
| Targeted T009 rules/service/web/schema/result regression | PASS — 53/53 | Bao gồm direct assignment, loại người chấm, council/scoring cũ và final score một người |
| `./mvnw -Pintegration -Dtest=DirectGradingRulesTest -Dit.test=SchemaSeedMigrationIT,ReviewSchemaMigrationIT verify` | PASS — 4 unit/migration + 4/4 integration | MySQL 8.4.11 cô lập; xác nhận V10 từ database rỗng, V6 và V7, trigger DIRECT một thành viên và Hibernate mapping |
| `./run.sh` với database local đã có V9 | PASS | Flyway validate đúng V9 `enforce organization catalog uniqueness`, áp dụng V10 và Spring Boot khởi động ở cổng 8088; không dùng `repair` hoặc xóa dữ liệu |
| `./mvnw test` với MySQL test cô lập | PARTIAL — 192/193 | Một lỗi baseline `PeriodContractTest.dashboardQueriesAreScopedToPersistedRoleAndOrganization`: ADMIN kỳ vọng 0 nhưng nhận 4 dashboard metrics; T009 không sửa dashboard |
| `./mvnw verify -Pintegration` với MySQL test cô lập | PARTIAL — 17/18 | Migration V10 và concurrency đạt; `ReviewWorkflowIT` cũ kỳ vọng 13 tài khoản nhưng fixture hiện seed 14, không thuộc T009 |
| `git diff --check` | PASS | Không có whitespace error trong thay đổi T009 |
| Targeted T010 unit/web | PASS — 47/47 | Rules, service, controller, schema contract, định dạng điểm và student-scoped result query |
| Targeted bước điểm/định dạng | PASS — 18/18 | Từ chối 5.6/8.55, nhận 5.5/5.75; input và result bỏ `.00`/số 0 dư |
| `./mvnw test` không có test DB env | PARTIAL — 190/197 | 7 lỗi duy nhất ở `PeriodContractTest` do thiếu `TEST_DATABASE_URL`; các test còn lại đạt |
| Targeted result period/group/lecturer/status filter | PASS — 26/26 | Service đưa đúng đợt và giảng viên; lượt COMPLETED mở đúng registration; controller kết hợp filter trước publish |
| `./mvnw test` sau filter UI, không có test DB env | PARTIAL — 193/200 | 7 lỗi duy nhất ở `PeriodContractTest` do thiếu `TEST_DATABASE_URL`; 193 test còn lại đạt |
| Flyway local V10→V11 + Hibernate validation | PASS | 11 migration hợp lệ; 30 evaluation và 6 final result có khóa cá nhân duy nhất; không có row ngoài snapshot |
| `./run.sh -Dspring-boot.run.arguments=--server.port=0` | PASS | Ứng dụng khởi động và dừng sạch; lần chạy cổng 8088 thất bại vì cổng đang được dùng, không phải lỗi ứng dụng |
| `./mvnw verify -Pintegration` cho T010 | PASS — 200 unit/web + 18 integration/concurrency | MySQL 8.4 test cô lập; V1→V11, V10→V11, backfill, workflow và race scenarios đều đạt |
| `./mvnw verify -Pe2e` cho T010 | PASS — 200 unit/web + 2 E2E | Login và review/results journey; chấm/công bố điểm riêng, privacy cùng nhóm và outsider 404 |

Các lệnh T009 dùng `topic_registration_direct_test` cô lập. T010 dùng database MySQL 8.4
`topic_registration_t010_test` cô lập cho integration/E2E; không dùng hoặc reset database
phát triển để thay thế.
