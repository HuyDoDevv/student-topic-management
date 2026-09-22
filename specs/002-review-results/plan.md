# Implementation Plan: Báo cáo, hội đồng, chấm điểm và kết quả

**Branch**: `feature/002-review-results` | **Date**: 2026-09-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-review-results/spec.md`

## Summary

Mở rộng monolith Spring MVC hiện tại cho REQ-17–27 và quyết định U ngày 2026-09-17: lưu và
phân phối PDF báo cáo; phân công một người chấm độc lập cho COURSE/NCKH; lập hội đồng
TLCN/KLTN 3–5 giảng viên cùng bộ môn cho nhiều nhóm;
thành viên hội đồng chấm một điểm tổng 0–10 riêng cho từng sinh viên snapshot; Chair tổng
hợp điểm trung bình theo từng sinh viên; Trưởng khoa công bố; sinh viên snapshot chỉ xem
kết quả của chính mình. Dùng các bảng placeholder từ V1 nhưng bổ sung vòng đời, version và
ràng buộc bằng migration V8/V10/V11; giữ nguyên V1–V10.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Thymeleaf, Spring Security,
Bean Validation, Spring Data JPA, Flyway

**Storage**: MySQL 8.4 LTS cho metadata/nghiệp vụ; filesystem local có root cấu hình ngoài
source cho PDF, tên lưu do server sinh

**Testing**: JUnit Jupiter, Mockito, MockMvc; MySQL riêng cho integration/concurrency;
Playwright Java cho E2E khi profile tương ứng có môi trường

**Target Platform**: JVM local trên macOS/Linux/Windows, trình duyệt hiện đại

**Project Type**: một Spring Boot executable JAR render HTML phía server

**Performance Goals**: không thêm SLA tải; upload được giới hạn 20 MB và xử lý streaming
để tránh giữ nhiều bản sao trong bộ nhớ

**Constraints**: backend authorization; CSRF; điểm 0–10 theo bước 0.25; report PDF tối đa
20 MB; không path traversal;
transaction nhất quán; một tiến trình; Asia/Ho_Chi_Minh cho hiển thị thời gian; không secret
hoặc đường dẫn máy cá nhân trong source

**Scale/Scope**: FR-16–27, AS-6.*–10.*; hội đồng chỉ TLCN/KLTN, mỗi hội đồng 3–5 người và
nhiều đăng ký cùng period/department; COURSE/NCKH có một lượt chấm DIRECT/một registration

## Constitution Check

### Pre-design gate

| Principle | Status | Evidence / decision |
|---|---|---|
| I. Nguồn nghiệp vụ | PASS | REQ-17–27 và quyết định U ngày 2026-09-14 được tách rõ; Q-11–15 trong phạm vi đã đóng |
| II. Truy vết | PASS | FR-16–25, AS-6.*–10.*, tasks và tests giữ liên kết REQ |
| III. Backend quyền | PASS | service đọc lại role/scope/snapshot/supervisor/council membership |
| IV. Boundary validation | PASS | PDF, dung lượng, điểm, ID, version và state đều kiểm tra server-side |
| V. Tách controllers | PASS | controller → DTO → service/rules → repository/entity/storage |
| VI. Đồng thời | PASS về thiết kế | mutation khóa period rồi council/child; unique và version là lớp bảo vệ cuối |
| VII. Migration | PASS | Giữ nguyên V1–V10; V11 thêm khóa sinh viên và backfill điểm/kết quả lịch sử theo snapshot |
| VIII. Tests | PASS về kế hoạch | unit/HTTP/storage/DB/concurrency/E2E được ghi trong tasks |
| IX. Secrets | PASS | storage root qua env; không ghi credentials hoặc absolute path |
| X. Nghiệm thu | PASS về kế hoạch | chỉ đánh dấu hoàn thành sau test và acceptance evidence |

Không có vi phạm cần miễn trừ.

## Phase 0: Research decisions

Các quyết định và phương án bị loại được ghi tại [research.md](research.md). Không còn
`NEEDS CLARIFICATION` sau khi người dùng chọn Q1:A, Q2:C, Q3:A.

## Phase 1: Design

- [data-model.md](data-model.md): schema V8/V10/V11, thực thể, quan hệ và state transition.
- V10 thêm `grading_mode` để phân biệt hội đồng và lượt chấm độc lập một người; V11 chuyển
  evaluation/final result sang từng sinh viên snapshot; V9 cũ được
  giữ nguyên để các database đã áp dụng không bị checksum mismatch.
- [contracts/web.md](contracts/web.md): route HTML/multipart, quyền, validation và lỗi.
- [quickstart.md](quickstart.md): cách cấu hình storage/test và luồng nghiệm thu.

### Post-design constitution re-check

PASS. Data model dùng khóa/unique/version cho invariant; contract không nhận actor/status
từ client; report storage có chống traversal và dọn file mới khi transaction metadata lỗi;
Chair và Trưởng khoa có quyền tách biệt theo REQ-22/26; student visibility dùng snapshot.

## Project Structure

### Documentation (this feature)

```text
specs/002-review-results/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/web.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/vn/edu/topicregistration/
├── config/          # report-storage configuration
├── controller/      # Report, Council, Scoring, Result controllers
├── dto/             # upload, council, assignment, score and view forms
├── entity/          # report/council/evaluation/result entities and enums
├── exception/       # business error codes
├── repository/      # JPA repositories and safe local file repository
├── security/        # existing authorization policy
└── service/         # report, council, scoring, result rules and transactions

src/main/resources/
├── db/migration/V8__implement_review_and_results.sql
├── db/migration/V9__enforce_organization_catalog_uniqueness.sql
├── db/migration/V10__support_direct_grading_assignments.sql
├── db/migration/V11__grade_each_registration_student.sql
├── templates/reports/
├── templates/councils/
├── templates/scoring/
├── templates/results/
└── application*.yml

src/test/java/vn/edu/topicregistration/
├── unit/
├── web/
├── integration/
├── concurrency/
└── e2e/
```

**Structure Decision**: Giữ một application và các package ngang theo MVC đã chốt ở T076.
Không tạo feature-level `application/` hoặc executable thứ hai.

## Implementation Sequence

1. Hoàn thiện spec/design và migration/entity/repository nền.
2. TDD report upload/download/replace và quyền người xem.
3. TDD council create/assign/schedule/activate cho TLCN/KLTN.
4. TDD scoring DRAFT/SUBMITTED và chặn supervisor/người ngoài.
5. TDD Chair complete/average và Dean publish.
6. TDD student/dean result list/detail cùng privacy.
7. Navigation, seed fixture, integration/concurrency/E2E và acceptance evidence.
8. TDD phân công chấm độc lập COURSE/NCKH, tái sử dụng scoring/result và giữ nguyên council.
9. TDD chấm và công bố kết quả riêng cho từng sinh viên snapshot, backfill điểm nhóm cũ cho
   từng thành viên để không mất dữ liệu đã có.

## Verification Strategy

- Unit: report validation, council cardinality/type/scope/transitions, score range/bước 0.25,
  direct-grader eligibility, mean and rounding, role/relationship decisions.
- HTTP: multipart + CSRF, forms, stale version, direct ID access, allowed/denied matrix.
- Storage: PDF signature, 20 MB limit, generated key, canonical path, replacement and cleanup.
- MySQL: V8–V11 từ database rỗng và fixture cũ, checks/unique/FK, officer trigger cho DIRECT,
  one current report, one assignment per registration, evaluation/result uniqueness theo
  sinh viên snapshot và result state.
- Concurrency: report replacement, activate vs upload, score vs complete, double complete,
  complete vs publish; period-first lock order.
- Browser: leader upload → Dean board → members score → Chair complete → Dean publish →
  snapshot student view and outsider denial.

## Complexity Tracking

Không có vi phạm constitution. Filesystem và database không thể có một transaction phân tán;
thiết kế ghi file mới bằng tên ngẫu nhiên, chỉ publish metadata sau khi ghi thành công, xóa
file mới khi DB rollback và chỉ dọn bản cũ sau commit. Cách này tránh metadata trỏ tới file
chưa hoàn chỉnh mà không thêm object storage hoặc message broker ngoài phạm vi local.
