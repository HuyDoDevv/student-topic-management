# Web Contract: Báo cáo, hội đồng, chấm điểm và kết quả

Tất cả route trừ download trả HTML. Mọi POST dùng CSRF, không nhận actor/role/status từ
client và dùng `version` khi cập nhật resource đã tồn tại.

## Reports

| Method | Route | Actor | Result |
|---|---|---|---|
| GET | `/reports/{registrationId}` | snapshot member, supervisor, scoped DEAN, assigned council member | Trang metadata và download hiện hành |
| POST multipart | `/reports/{registrationId}` | snapshot leader | Upload/replace PDF, 303 về detail |
| GET | `/reports/{registrationId}/download` | cùng quyền GET detail | `application/pdf`, attachment filename an toàn |

Validation: ACCEPTED; leader-at-submission; PDF signature và content type; `1..20 MiB`;
không có council assignment ACTIVE/COMPLETED/PUBLISHED khi upload. Không tìm thấy và ngoài
scope không làm lộ đường dẫn/storage key.

## Councils — DEAN

| Method | Route | Purpose |
|---|---|---|
| GET | `/faculty/boards` | List council trong faculty |
| GET | `/faculty/boards/new?periodId={id}&departmentId={id}` | Form create |
| POST | `/faculty/boards` | Create DRAFT với 3–5 member/chức danh |
| GET | `/faculty/boards/{id}` | Detail, members, assignments, progress |
| POST | `/faculty/boards/{id}/assignments` | Gán ACCEPTED registration + lịch/địa điểm |
| POST | `/faculty/boards/{id}/assign` | DRAFT → ASSIGNED |
| POST | `/faculty/boards/{id}/schedule` | ASSIGNED → SCHEDULED |
| POST | `/faculty/boards/{id}/activate` | SCHEDULED → ACTIVE |

Create form: `periodId`, `departmentId`, `name`, `chairId`, `secretaryId`,
`memberIds` (1–3 người bổ sung). Assignment: `registrationId`, `scheduledAt`, `location`,
`version`. Service revalidates TLCN/KLTN, faculty/department, active LECTURER, 3–5 unique,
chair != secretary, accepted registration và supervisor exclusion.

## Direct grading — DEAN / assigned LECTURER

| Method | Route | Actor | Purpose |
|---|---|---|---|
| GET | `/faculty/direct-grading` | scoped DEAN | List COURSE/NCKH có report chờ phân công và các lượt đã tạo |
| POST | `/faculty/direct-grading` | scoped DEAN | Chọn một grader và tạo lượt DIRECT ACTIVE |
| POST | `/lecturer/direct-grading/{id}/complete` | assigned grader | ACTIVE → COMPLETED sau khi đã submit điểm |
| POST | `/faculty/direct-grading/{id}/publish` | scoped DEAN | COMPLETED → PUBLISHED |

Form phân công chỉ nhận `registrationId`, `graderId`. Service khóa period rồi registration,
revalidate COURSE/NCKH, ACCEPTED, current report, chưa có assignment, grader active LECTURER
cùng department và không thuộc bất kỳ TopicSupervisor nào. Thành công tạo một membership,
một assignment với nhãn `Chấm độc lập`, chuyển ngay ACTIVE và vì vậy khóa report. Không
dùng `student_end` làm hạn báo cáo; Trưởng khoa chỉ thao tác sau khi xác nhận kỳ nộp đã hết.

## Scoring — assigned LECTURER

| Method | Route | Purpose |
|---|---|---|
| GET | `/lecturer/scoring` | List assignment ACTIVE của actor |
| GET | `/lecturer/scoring/{councilId}/{registrationId}` | Form score/report link |
| POST | `/lecturer/scoring/{councilId}/{registrationId}/draft` | Lưu DRAFT |
| POST | `/lecturer/scoring/{councilId}/{registrationId}/submit` | Lưu và SUBMIT |

Form: `score` (0..10, max 2 decimal), `feedback`, `version` (null cho lần đầu). Chỉ chính
council member được ghi khi ACTIVE; supervisor luôn bị từ chối. SUBMITTED được sửa/submit
lại khi vẫn ACTIVE; complete khóa toàn bộ.

## Result completion/publication

| Method | Route | Actor | Purpose |
|---|---|---|---|
| POST | `/faculty/boards/{id}/complete` | Chair | ACTIVE → COMPLETED, tạo READY results |
| GET | `/faculty/results` | scoped DEAN | List result trong faculty |
| GET | `/faculty/results/{registrationId}` | scoped DEAN | Result detail |
| POST | `/faculty/boards/{id}/publish` | scoped DEAN | COMPLETED → PUBLISHED nguyên tử |

Complete yêu cầu mỗi `(CouncilTopic, CouncilMember)` có đúng một SUBMITTED evaluation.
Với `COUNCIL` ma trận có 3–5 member; với `DIRECT` có đúng một assignment/member/evaluation
nên điểm cuối bằng điểm của grader. Điểm cuối dùng scale 2 HALF_UP. Publish lặp, thiếu READY
result hoặc stale version trả conflict và không đổi một phần.

## Student results

| Method | Route | Actor | Purpose |
|---|---|---|---|
| GET | `/student/results` | STUDENT | List PUBLISHED results có actor trong registration snapshot |
| GET | `/student/results/{registrationId}` | snapshot STUDENT | Detail điểm/nhận xét/final score |

Direct ID của result khác nhóm/chưa publish bị từ chối mà không trả metadata.

## Error mapping

| Condition | HTTP |
|---|---|
| malformed ID/multipart/form | 400 |
| wrong role/scope/relationship | 403 |
| missing visible resource | 404 |
| stale version/concurrent state | 409 |
| PDF/size/score/cardinality/state validation | 422 |

Các lỗi không trả SQL, storage root, storage key, stack trace hoặc tên file tạm.
