# Acceptance Matrix: Báo cáo, hội đồng, chấm điểm và kết quả

**Feature**: `002-review-results`
**Cập nhật**: 2026-09-17
**Trạng thái phạm vi T009**: PASS — cơ chế chấm trực tiếp COURSE/NCKH đã được kiểm tra trên
MySQL 8.4 test riêng; hai regression baseline ngoài phạm vi được ghi rõ ở bảng bên dưới.

| Requirement | Acceptance scenarios | Implementation evidence | Automated evidence hiện có | Status |
|---|---|---|---|---|
| FR-16–17, FR-24–25 | AS-6.1–6.5 | `ReportService`, `ReportController`, local file repository, report views | Rules/service/controller ở T003; `ReviewWorkflowIT`, upload race và browser upload/download | PASS |
| FR-18–19, FR-24 | AS-7.1–7.6 | `CouncilService`, `CouncilController`, board views | Rules/service/controller ở T004; V8 migration, demo fixture, board concurrency và E2E | PASS |
| FR-20, FR-24–25 | AS-8.1–8.5 | `ScoringService`, `ScoringController`, scoring views | Rules/service/controller ở T005; score/complete race và browser submit có CSRF | PASS |
| FR-21–22, FR-24 | AS-9.1–9.5 | `ResultService`, faculty result controller/views | Rules/service/controller ở T006; atomic complete/publish integration, concurrency và E2E | PASS |
| FR-23–24 | AS-10.1–10.4 | snapshot-filtered result repository/service, student views | Privacy tests ở T007; published snapshot integration và browser outsider 404 | PASS |
| FR-26 | AS-7.7–7.10 | `DirectGradingService`, V10 mode/trigger, direct-grading view, reused scoring/result | Rules/service/controller và regression liên quan 53/53; migration V7→V10 và empty/V6→V10 trên MySQL 8.4 đạt 4/4 | PASS |

## Manual demo evidence

| Journey | Fixture/actor | Expected result | Status |
|---|---|---|---|
| Upload/replace report | `student1.demo`, registration ACCEPTED chưa gắn board | PDF hiện hành được lưu/tải; file cũ được thay trước ACTIVE | AUTOMATED PASS; manual fixture available |
| Report lock and protected download | ACTIVE/PUBLISHED demo registrations | Actor đúng quan hệ tải được; replace bị khóa | AUTOMATED PASS; manual fixture available |
| Board 3–5 members | `dean.demo`, `[DEMO] Hội đồng KLTN - Đang chấm` | Board có Chair, Secretary và member cùng bộ môn | AUTOMATED PASS; manual fixture available |
| Score/resubmit | `lecturer1.demo`, `lecturer4.demo`, `lecturer5.demo` | Chỉ evaluation của actor đổi khi board ACTIVE | AUTOMATED PASS; manual fixture available |
| Complete/publish | Chair `lecturer1.demo`, sau đó `dean.demo` | Toàn board thành COMPLETED rồi PUBLISHED nguyên tử | AUTOMATED PASS; manual fixture available |
| Student visibility | `student1.demo` đến `student3.demo` | Chỉ snapshot group thấy result PUBLISHED của mình | AUTOMATED PASS; manual fixture available |

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

Các lệnh T009 dùng `topic_registration_direct_test` cô lập; không dùng hoặc reset database
phát triển.
