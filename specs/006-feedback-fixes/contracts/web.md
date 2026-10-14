# Web Contracts: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

## Quy ước chung

- Đây là hợp đồng hành vi cho Spring MVC/Thymeleaf, không phải REST API công khai.
- Controller chỉ bind/validate request, chọn view/redirect; service tải lại dữ liệu
  tin cậy, kiểm tra role/scope/state/version và thực hiện transaction.
- POST cần CSRF. ID, Khoa, role, student/member set và trạng thái từ client không
  được coi là nguồn quyết định.
- Validation nghiệp vụ quay lại form với lỗi cụ thể khi người dùng có thể sửa;
  request không có quyền không được lộ dữ liệu ngoài scope.

## Authorization matrix

| Capability | DEAN | LECTURER | REVIEWER | SUPERVISOR | STUDENT |
|---|---:|---:|---:|---:|---:|
| Quản lý đợt/duyệt/công bố đề tài | Có, trong Khoa | Không | Không | Không | Không |
| Đề xuất/sửa nội dung đề tài | Không | Có, theo tác giả/Bộ môn | Theo role lecturer nếu không DEAN | Có theo ownership | Không |
| Phân công GVPB/người chấm/hội đồng | Có, trong Khoa | Không | Không | Không | Không |
| Nộp phản biện | Không | Chỉ khi được giao | Có, đúng assignment | Không nếu là GVHD đề tài | Không |
| Nhập điểm | Không | Chỉ nhiệm vụ hợp lệ | Điểm phản biện được giao | Không cho đề tài mình hướng dẫn | Không |
| Xem tiến độ hội đồng | Có, trong Khoa | Chủ tịch hội đồng mình | Theo nhiệm vụ mình | Theo quyền nhiệm vụ | Không |
| Tổng hợp/công bố | Có, khi đủ điều kiện | Không | Không | Không | Không |
| Xem điểm | Căn cứ trong Khoa | Theo quyền nhiệm vụ | Điểm mình nộp | Theo quyền được cấp | Chỉ sau PUBLISHED |

Một account DEAN không được sử dụng cột LECTURER/REVIEWER/SUPERVISOR trong
ma trận kể cả khi dữ liệu lịch sử từng có role kép.

## Topic review and authoring

### `GET /periods/{periodId}/topics`

- DEAN: danh sách duyệt chỉ gồm topic thuộc đúng period và Department của period;
  không hiển thị filter “Tất cả Bộ môn trong Khoa”.
- Lecturer: giữ scope authoring hiện hành, DEAN không được vào bằng role kép.
- Query/request thêm `departmentId` không được mở rộng kết quả.

### `POST /topics/{id}/return-to-draft` (proposed)

**Actor**: tác giả topic, không phải DEAN.

**Preconditions**: topic `APPROVED`, chưa `PUBLISHED`, không có registration và
còn giai đoạn cho phép. Service dùng topic/period/registration từ DB.

**Success**: topic thành `DRAFT`, redirect về form edit với message phải gửi duyệt lại.

**Failure**: giữ nguyên topic; trả lỗi có thể hiểu được cho topic đã công bố, có
đăng ký, hết giai đoạn, version cũ hoặc actor không phải tác giả.

Các route hiện hữu `GET /topics/{id}/edit`, `POST /topics/{id}/draft` và
`POST /topics/{id}/submit` tiếp tục áp dụng ownership/state check.

## Notification open

### `POST /notifications/{id}/open`

- Đánh dấu đọc theo owner hiện tại và resolve target theo category, target ID,
  role cùng trạng thái nhiệm vụ tại thời điểm mở.
- Direct grader đi tới nhiệm vụ chấm lecturer, không tới trang phân công của DEAN.
- Reviewer đi tới trang submission assignment của chính mình.
- DEAN đi tới màn hình quản lý phù hợp trong Khoa.
- Target không còn hiệu lực redirect `/notifications?target=unavailable` (hoặc
  flash tương đương đã thống nhất), không trả 404 do route sai quyền.

## Group scoring

### `GET /lecturer/scoring/{assignmentId}` (target contract)

Trả một trang có thông tin đợt/đề tài/nhóm và toàn bộ student snapshot mà actor
có quyền chấm. Có score, feedback, row version và trạng thái hiện tại riêng từng
student; không mở endpoint từng student cho workflow mới.

### `POST /lecturer/scoring/{assignmentId}/draft`

### `POST /lecturer/scoring/{assignmentId}/submit`

Payload `GroupScoreForm` chứa version nhiệm vụ và danh sách row `{studentId,
score, feedback, version}`. Service yêu cầu tập student ID đúng bằng snapshot,
không lặp, mọi score hợp lệ. Một lỗi/version cũ làm toàn request thất bại và
không có row nào được ghi. `submit` yêu cầu đầy đủ hơn `draft` theo rule hiện hành.

Nếu implementation cần giữ route có `{councilId}/{registrationId}`, semantics
vẫn phải như trên và notification resolver phải tạo đúng URL actor có quyền.

## Reviewer assignment and submission

### `GET /faculty/reviewers`

**Actor**: DEAN. Liệt kê registration TLCN/KLTN trong Khoa cùng trạng thái phân
công/submission. Candidate không gồm DEAN, GVHD/đồng hướng dẫn, người chấm trực
tiếp hoặc member xung đột.

### `POST /faculty/reviewers`

Payload tối thiểu `{registrationId, reviewerId, version}`. Service kiểm tra
registration accepted, type, Khoa, account enabled/role, conflict và assignment
hiện tại. Thành công redirect danh sách/chi tiết; conflict/version cũ không ghi.

### `GET /lecturer/reviewers/{assignmentId}`

**Actor**: chính reviewer đang được giao. Hiển thị đề tài, nhóm, mọi sinh viên,
deadline theo giờ Việt Nam, upload PDF và score/feedback từng sinh viên.

### `POST /lecturer/reviewers/{assignmentId}/submit`

Multipart payload gồm PDF, version và đủ row của mọi student. Chấp nhận khi
actor còn hợp lệ và server time `<=` cuối ngày deadline. File, metadata, toàn bộ
score và transition `SUBMITTED` cùng transaction/compensation an toàn; lỗi không
để assignment ở trạng thái nửa hoàn thành.

### `POST /faculty/reviewers/{assignmentId}/replace`

Chỉ DEAN đúng Khoa; chỉ trước khi có report/score/submission. Reviewer mới phải
qua lại toàn bộ conflict check. Route cụ thể có thể gộp vào update assignment
nhưng contract không cho phép thay sau submission.

## Direct grading

### `GET /faculty/direct-grading`

- COURSE/NCKH: liệt kê registration đủ điều kiện chưa có grader.
- TLCN: chỉ liệt kê sau reviewer submission đầy đủ và đúng hạn.
- KLTN: không dùng direct grading.

### `POST /faculty/direct-grading`

DEAN gửi registration/grader/version. Backend loại DEAN, reviewer cùng đề tài,
GVHD/đồng hướng dẫn, account disabled/khác Khoa và assignment trùng. Với TLCN,
precondition reviewer submitted là bắt buộc.

Route lecturer completion/publication hiện hữu phải được điều chỉnh để chỉ DEAN
tổng hợp/công bố theo contract kết quả; grader không tự công bố.

## Council

### `GET /faculty/boards` và `POST /faculty/boards`

Chỉ KLTN được tạo/gắn hội đồng. TLCN không xuất hiện. Candidate/member không gồm
DEAN, GVPB hoặc GVHD/đồng hướng dẫn của topic được giao; member enabled cùng Khoa.

### `POST /faculty/boards/{id}/members` (target contract)

Thêm/xóa/đổi vai trò thành viên khi council `DRAFT`/`ASSIGNED` và chưa có điểm.
Payload có council version; service kiểm tra 3–5 member, chair/secretary, conflict
cho toàn bộ assignment. Sau evaluation hoặc `ACTIVE`, request bị từ chối.

### `POST /faculty/boards/{id}/schedule`

Thời gian bảo vệ phải lớn hơn `clock.instant()` tại service. Create/update cùng
rule và optimistic lock. Invalid/past time render lại `templates/councils/detail`
hoặc form tương ứng cùng field error; không chuyển trang lỗi 422 chung.

### `GET /faculty/boards/{id}`

- DEAN đúng Khoa: xem assignment và tiến độ mọi member.
- Chair: xem council mình, trạng thái `NOT_STARTED`, `DRAFT`, `SUBMITTED`, số SV
  đã chấm/tổng số và thời điểm nộp; không xem điểm draft của người khác.
- Member khác chỉ có dữ liệu cần cho nhiệm vụ của mình.

## Results

### `GET /faculty/results`

Query params allow-list: `period`, `topicType`, `topicOrGroup`, `grader`, `status`,
`sort`, `page`, `size`. Backend áp scope Khoa, pagination ổn định và group view
theo topic/registration; param không hợp lệ không được dùng làm query tùy ý.

### `GET /faculty/results/{registrationId}/{studentId}`

Trước publication, chỉ actor được phép thấy chi tiết căn cứ gồm người chấm, vai
trò, trạng thái nộp, điểm submitted, công thức, số đầu điểm, average, consolidated
actor/time và published actor/time. Không trả điểm draft của người khác.

### `POST /faculty/results/{registrationId}/consolidate` (target contract)

Chỉ DEAN đúng Khoa; tạo/update `READY` cho từng student khi đủ toàn bộ nguồn theo
loại đợt. Dùng version và transaction; không thay row `PUBLISHED`.

### `POST /faculty/results/{registrationId}/publish` (target contract)

Chỉ DEAN, chỉ `READY` và đủ mọi student trong registration. Ghi publisher/time;
student sau đó xem qua `/student/results/{registrationId}`. Thiếu bất kỳ đầu
điểm hoặc student result nào làm toàn request bị từ chối.

## HTTP and validation expectations

- `200`: render trang hợp lệ.
- `302`: POST thành công hoặc fallback notification có chủ đích.
- `400`/field error: payload malformed/invalid có thể sửa.
- `403` hoặc `404` theo policy hiện hữu: actor ngoài scope; phải nhất quán và
  không lộ sự tồn tại record.
- `409` hoặc form conflict theo handler hiện hữu: optimistic lock/stale state;
  không ghi một phần.

Test web phải bao phủ GET trực tiếp, POST giả mạo, CSRF, Khoa khác, DEAN cố dùng
route giảng viên, account disabled, stale version và target hết hiệu lực.
