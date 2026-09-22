# Data Model: Báo cáo, hội đồng, chấm điểm và kết quả

## Existing dependencies

- `registration_periods`: `COUNCIL` chỉ dùng cho TLCN/KLTN; `DIRECT` chỉ dùng cho
  COURSE/NCKH.
- `registrations`: chỉ row `ACCEPTED` được có report/council/result.
- `registration_members`: snapshot sinh viên dùng cho upload ownership và result visibility.
- `topics` + `topic_supervisors`: department scope và danh sách cấm chấm.
- `user_accounts` + `user_roles`: account active, LECTURER/DEAN và organization scope.

## ProjectReport

| Field | Rule |
|---|---|
| id | Primary key |
| registration | ACCEPTED registration |
| submittedBy | Snapshot leader tại lần đăng ký |
| submittedAt | UTC instant |
| fileName | Tên hiển thị đã chuẩn hóa, không dùng làm path |
| storageKey | UUID do server sinh dưới storage root |
| contentType | `application/pdf` |
| sizeBytes | `1..20 MiB` |
| replacedAt | Null cho bản hiện hành; có giá trị cho bản đã được thay |
| version | Optimistic version |

Generated `current_registration_guard = registration_id` khi `replaced_at IS NULL`, ngược
lại null; unique guard đảm bảo tối đa một báo cáo hiện hành/registration.

## Council

| Field | Rule |
|---|---|
| id | Primary key |
| period | TLCN hoặc KLTN |
| department | Thuộc faculty của period |
| name | Bắt buộc, trim, tối đa 255 |
| chair | Active LECTURER trong department, là member slot 1 |
| secretary | Active LECTURER khác chair, là member slot 2 |
| gradingMode | `COUNCIL` hoặc `DIRECT` |
| status | DRAFT, ASSIGNED, SCHEDULED, ACTIVE, COMPLETED, PUBLISHED |
| createdAt | UTC instant |
| version | Optimistic version |

Transitions:

```text
DRAFT --có 3..5 member + >=1 assignment--> ASSIGNED
ASSIGNED --mọi assignment có lịch/địa điểm--> SCHEDULED
SCHEDULED --revalidate type/scope/member/supervisor/report--> ACTIVE
ACTIVE --Chair + đủ SUBMITTED evaluation--> COMPLETED
COMPLETED --DEAN publish tất cả READY result--> PUBLISHED
```

Member và assignment chỉ sửa trước ACTIVE. Không có transition lùi trong feature này.

Với `COUNCIL`, Chair và Secretary khác nhau và có 3–5 member. Với `DIRECT`, Chair và
Secretary cùng trỏ tới người chấm để tái sử dụng quyền complete, trigger chỉ tạo một member
slot 1, có đúng một CouncilTopic và vòng đời được đưa thẳng tới ACTIVE trong transaction
phân công. Quy tắc service chỉ cho `DIRECT` với COURSE/NCKH, có báo cáo hiện hành và người
chấm khác mọi TopicSupervisor.

## CouncilMember

Composite key `(council_id, lecturer_id)`, unique `(council_id, slot)`, slot 1..5. Một
council phải có 3..5 member trước ASSIGNED; chair/secretary khác nhau và đều thuộc member.
Mọi member active LECTURER cùng department; không member nào là supervisor của một topic
đã gán vào council.

Ngoại lệ có chủ đích cho `DIRECT`: đúng một member slot 1 là người chấm. Đây không phải hội
đồng và không xuất hiện trong danh sách hội đồng TLCN/KLTN.

## CouncilTopic

Composite key `(council_id, registration_id)`, period copy phục vụ FK cùng period,
`scheduled_at`, `location`. Unique `registration_id` đảm bảo một registration chỉ thuộc một
council. Registration phải ACCEPTED và topic department phải trùng council department.

## ScoreComponent

Một row tên `Điểm hội đồng` cho mỗi period, min 0.00, max 10.00. Unique hiện có
`(period_id, name)` bảo vệ tạo lặp.

## Evaluation

| Field | Rule |
|---|---|
| id | Primary key |
| registration/period/council | Phải khớp CouncilTopic ACCEPTED |
| student | Phải thuộc `registration_members` snapshot của registration |
| evaluator | Phải là CouncilMember và không là TopicSupervisor |
| component | `Điểm hội đồng` cùng period |
| score | 0.00..10.00, bội số của 0.25; lưu DECIMAL, hiển thị bỏ số 0 dư |
| feedback | Tùy chọn |
| status | DRAFT hoặc SUBMITTED |
| evaluatedAt/submittedAt | UTC instant; submittedAt bắt buộc khi SUBMITTED |
| version | Optimistic version |

Unique `(registration_id, student_id, evaluator_id, component_id)` giữ một điểm cho mỗi
sinh viên/evaluator. Chỉ ghi khi council ACTIVE; khóa sau COMPLETED.

## FinalResult

| Field | Rule |
|---|---|
| registration + student | Composite primary key; student thuộc snapshot registration ACCEPTED |
| council | Council đã complete |
| status | READY hoặc PUBLISHED |
| consolidatedBy/At | Chair và thời điểm complete |
| evaluationSummary | Tóm tắt số evaluation và mean |
| finalScore | Mean SUBMITTED score, scale 2 HALF_UP |
| reviewCompletedAt/gradingCompletedAt | Thời điểm complete |
| publishedBy/At | DEAN và thời điểm publish; null khi READY |
| version | Optimistic version |

READY được tạo cho mọi sinh viên snapshot của mọi CouncilTopic trong cùng transaction
complete. Publish đổi mọi READY của council và council sang PUBLISHED trong cùng transaction.

## Migration V8

V8 chỉ `ALTER` các bảng placeholder, thêm index/constraint/column; không sửa V1–V7.
Migration backfill trạng thái bảo thủ cho row cũ: report mới nhất/registration là current;
council hiện có bắt đầu DRAFT; evaluation hiện có được coi SUBMITTED; final result có
`published_at` thì PUBLISHED, ngược lại READY. Dữ liệu legacy vi phạm invariant mới phải làm
migration fail rõ ràng thay vì tự xóa.

## Migration V9/V10

V9 giữ nguyên migration tên khoa/bộ môn duy nhất đã từng được áp dụng trên database local.
V10 thêm `councils.grading_mode`, backfill mặc định `COUNCIL`, thay check officer cũ bằng
check kết hợp mode/officer và tạo lại trigger officer. `COUNCIL` vẫn bắt buộc Chair khác
Secretary; `DIRECT` bắt buộc hai FK officer cùng trỏ một người và trigger chỉ tạo một row
membership. Không sửa V1–V9 và không thêm bảng nghiệp vụ mới.

## Migration V11

V11 thêm `student_id` vào `evaluations` và `final_results`, đổi unique/primary key sang phạm
vi từng sinh viên và thêm composite foreign key tới `registration_members`. Dữ liệu điểm
nhóm từ V10 trở về trước được sao chép cho từng thành viên snapshot để bảo toàn kết quả lịch
sử; từ V11 mọi điểm mới bắt buộc gắn đúng một sinh viên snapshot. Không sửa V1–V10.

## Lock order

1. `registration_periods` FOR UPDATE.
2. `councils` FOR UPDATE.
3. `council_topics`, `council_members`, `evaluations`, `final_results` theo ID.
4. Filesystem write hoàn tất trước metadata insert; cleanup qua transaction callback.
