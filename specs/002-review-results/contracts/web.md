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
| GET | `/faculty/boards/new?periodId={id}` | Form create; faculty lấy từ period |
| POST | `/faculty/boards` | Create DRAFT với 3–5 member/chức danh |
| GET | `/faculty/boards/{id}` | Detail, members, assignments, progress |
| POST | `/faculty/boards/{id}/assignments` | Gán ACCEPTED registration + lịch/địa điểm |
| POST | `/faculty/boards/{id}/assign` | DRAFT → ASSIGNED |
| POST | `/faculty/boards/{id}/schedule` | ASSIGNED → SCHEDULED |
| POST | `/faculty/boards/{id}/activate` | SCHEDULED → ACTIVE |

Create form: `periodId`, `name`, `chairId`, `secretaryId`,
`memberIds` (1–3 người bổ sung). Assignment: `registrationId`, `scheduledAt`, `location`,
`version`. Service revalidates TLCN/KLTN, faculty, active LECTURER, 3–5 unique,
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
cùng faculty và không thuộc bất kỳ TopicSupervisor nào. Thành công tạo một membership,
một assignment với nhãn `Chấm độc lập`, chuyển ngay ACTIVE và vì vậy khóa report. Không
dùng `student_end` làm hạn báo cáo; Trưởng khoa chỉ thao tác sau khi xác nhận kỳ nộp đã hết.

## Scoring — assigned LECTURER

| Method | Route | Purpose |
|---|---|---|
| GET | `/lecturer/scoring` | List assignment ACTIVE của actor |
| GET | `/lecturer/scoring/{councilId}/{registrationId}` | Danh sách sinh viên và tiến độ điểm |
| GET | `/lecturer/scoring/{councilId}/{registrationId}/students/{studentId}` | Form điểm một sinh viên |
| POST | `/lecturer/scoring/{councilId}/{registrationId}/students/{studentId}/draft` | Lưu DRAFT riêng sinh viên |
| POST | `/lecturer/scoring/{councilId}/{registrationId}/students/{studentId}/submit` | Lưu và SUBMIT riêng sinh viên |

Form: `score` (0..10, bước 0.25), `feedback`, `version` (null cho lần đầu). Backend từ chối
mọi giá trị không phải bội số của 0.25; HTML input dùng `step=0.25`. Điểm render bỏ số 0
thập phân dư (`5`, `6.5`, `7.75`). `studentId`
lấy từ route nhưng service luôn kiểm tra lại membership snapshot. Chỉ chính council member
được ghi khi ACTIVE; supervisor luôn bị từ chối. SUBMITTED được sửa/submit lại khi vẫn
ACTIVE; complete khóa toàn bộ và yêu cầu đủ mọi sinh viên.

## Result completion/publication

| Method | Route | Actor | Purpose |
|---|---|---|---|
| POST | `/faculty/boards/{id}/complete` | Chair | ACTIVE → COMPLETED, tạo READY results |
| GET | `/faculty/results?periodId={periodId}&registrationId={registrationId}&lecturerId={lecturerId}&status={READY|PUBLISHED}` | scoped DEAN | List result trong faculty; lọc kết hợp theo đợt, đăng ký/nhóm, giảng viên và trạng thái công bố |
| GET | `/faculty/results/{registrationId}/{studentId}` | scoped DEAN | Result detail từng sinh viên |
| POST | `/faculty/boards/{id}/publish` | scoped DEAN | COMPLETED → PUBLISHED nguyên tử |

Complete yêu cầu mỗi `(CouncilTopic, RegistrationMember, CouncilMember)` có đúng một
SUBMITTED evaluation.
Với `COUNCIL` ma trận có 3–5 member; với `DIRECT` có đúng một assignment/member/evaluation
nên điểm cuối bằng điểm của grader. Điểm cuối dùng scale 2 HALF_UP. Publish lặp, thiếu READY
result hoặc stale version trả conflict và không đổi một phần.
`periodId` là tùy chọn; bỏ trống trả toàn bộ kết quả trong khoa. Bộ lọc chỉ áp dụng trên tập
kết quả đã được giới hạn theo khoa của DEAN, nên một ID đợt ngoài phạm vi chỉ cho danh sách
rỗng và không làm lộ metadata của đợt đó.
`registrationId` cũng là tùy chọn và được dùng từ màn hình phân công chấm độc lập để Trưởng
khoa xem điểm từng sinh viên của đúng nhóm ở trạng thái COMPLETED trước khi công bố.
`lecturerId` khớp khi giảng viên thuộc lượt chấm/hội đồng của kết quả; `status` nhận `READY`
(Chưa công bố) hoặc `PUBLISHED` (Đã công bố). Các điều kiện tùy chọn được kết hợp bằng AND.

## Student results

| Method | Route | Actor | Purpose |
|---|---|---|---|
| GET | `/student/results` | STUDENT | List PUBLISHED results có actor trong registration snapshot |
| GET | `/student/results/{registrationId}` | snapshot STUDENT | Detail điểm/nhận xét/final score |

Direct ID của result thuộc sinh viên khác, khác nhóm hoặc chưa publish bị từ chối mà không
trả metadata.

## Error mapping

| Condition | HTTP |
|---|---|
| malformed ID/multipart/form | 400 |
| wrong role/scope/relationship | 403 |
| missing visible resource | 404 |
| stale version/concurrent state | 409 |
| PDF/size/score/cardinality/state validation | 422 |

Các lỗi không trả SQL, storage root, storage key, stack trace hoặc tên file tạm.
