# Acceptance Matrix: Báo cáo, hội đồng, chấm điểm và kết quả

**Feature**: `002-review-results`
**Cập nhật**: 2026-09-15
**Trạng thái chung**: PASS — năm user story đã được kiểm tra trên MySQL 8.4 test riêng,
bao gồm migration/backfill, integration, concurrency và browser E2E.

| Requirement | Acceptance scenarios | Implementation evidence | Automated evidence hiện có | T008 |
|---|---|---|---|---|
| FR-16–17, FR-24–25 | AS-6.1–6.5 | `ReportService`, `ReportController`, local file repository, report views | Rules/service/controller ở T003; `ReviewWorkflowIT`, upload race và browser upload/download | PASS |
| FR-18–19, FR-24 | AS-7.1–7.6 | `CouncilService`, `CouncilController`, board views | Rules/service/controller ở T004; V8 migration, demo fixture, board concurrency và E2E | PASS |
| FR-20, FR-24–25 | AS-8.1–8.5 | `ScoringService`, `ScoringController`, scoring views | Rules/service/controller ở T005; score/complete race và browser submit có CSRF | PASS |
| FR-21–22, FR-24 | AS-9.1–9.5 | `ResultService`, faculty result controller/views | Rules/service/controller ở T006; atomic complete/publish integration, concurrency và E2E | PASS |
| FR-23–24 | AS-10.1–10.4 | snapshot-filtered result repository/service, student views | Privacy tests ở T007; published snapshot integration và browser outsider 404 | PASS |

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

Các lệnh dùng `topic_registration_test` cô lập; không dùng hoặc reset database phát triển.
