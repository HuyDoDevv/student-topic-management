> Cập nhật theo yêu cầu người dùng tạo DB toàn hệ thống và đơn giản hóa chạy local:
> SQL hiện tại là `database/V1__full_system.sql` (20 bảng MySQL), import thủ công.
> Đã bỏ Flyway, scripts Python và cache .tools; chạy bằng ./run.sh trên macOS hoặc run.cmd trên Windows.
> Database hiện hành là MySQL 8.4 LTS; schema 20 bảng ở `database/V1__full_system.sql`.
> Nội dung bên dưới lưu lại kế hoạch/báo cáo feature 001 trước thay đổi này;
> các lệnh Flyway/scripts cũ không còn là hướng dẫn chạy hiện tại. Xem README.md gốc.

# Data Model — Thiết kế có điều kiện

Áp dụng [spec](spec.md) FR-01–15. MySQL 8.4 LTS; tên/kiểu dưới đây là thiết kế kỹ thuật,
không thêm nghiệp vụ. Q có ảnh hưởng schema được ghi rõ; chưa tạo migration.

## Entities

| Entity / bảng dự kiến | Trường chính | Quan hệ và ghi chú |
|---|---|---|
| Department / departments | id bigint PK, name text | Bộ môn đã nêu; tổ chức khoa/scope cuối cùng chờ Q-18 |
| UserAccount / user_accounts | id bigint PK, username text unique, password_hash text, department_id FK | Mật khẩu không lưu plaintext; quy tắc chuẩn hóa username/tổ hợp role cần ghi khi chốt provisioning |
| UserRole / user_roles | user_id FK, role_code | PK hai trường; STUDENT/LECTURER/DEAN. Bảng hỗ trợ lưu role, không mặc định tổ hợp role nào được phép |
| RegistrationPeriod / registration_periods | id, name, topic_type, lecturer_start/end, student_start/end, reviewer_score_deadline, council_report_date, version | Bốn mốc đăng ký timestamptz; ngày giai đoạn sau kiểu date dự kiến, độ chính xác/bắt buộc còn Q-02 |
| Topic / topics | id, period_id FK, department_id FK, author_id FK, title, description, objectives, student_requirements, expected_results, status, version | Bản đề tài thuộc một đợt cho luồng hiện tại. Nếu Q-05 cho tái sử dụng, tách catalog và offering bằng migration trước triển khai phần đó |
| TopicSupervisor / topic_supervisors | topic_id FK, lecturer_id FK, slot | Một hàng/GVHD; slot 1–2 là chỗ kỹ thuật, không phân thứ bậc GVHD |
| StudentGroup / student_groups | id, period_id FK, leader_student_id FK, version | Trưởng nhóm luôn là thành viên; không thêm tên nhóm bắt buộc |
| GroupMembership / group_memberships | group_id, period_id, student_id FK, slot | Một tư cách SV/đợt; slot 1–3 chỉ bảo vệ sức chứa |
| Registration / registrations | id, period_id, group_id, topic_id, status, submitted_at, decided_at, withdrawn_at, decision_actor_id, rejection_kind, rejection_reason, version | rejected/withdrawn không tái dùng ID khi đăng ký lại; tạo hàng mới; retention còn Q-09 |

Các FK danh tính trỏ cùng UserAccount; service xác minh vai trò GV/SV đúng đối tượng.
Không suy ra department bắt buộc cho trưởng khoa hay quyền sinh viên từ FK bộ môn.
Chưa chốt schema phạm vi khoa: không bỏ trống scope rồi hiểu là toàn quyền.

Dữ liệu nhập form dùng giờ Asia/Ho_Chi_Minh, chuyển sang instant lưu timestamptz.
Hiển thị lại bằng cùng zone; không dùng múi giờ trình duyệt để quyết định hạn.
version bigint là optimistic concurrency token kỹ thuật, không phải trạng thái nghiệp vụ.

## Database invariants

| ID | Cơ chế DB dự kiến | REQ / FR |
|---|---|---|
| DB-01 | NOT NULL tên/loại/bốn mốc; CHECK lecturer_start < lecturer_end AND lecturer_end <= student_start AND student_start < student_end | REQ-04–05; FR-02–03 |
| DB-02 | CHECK reviewer_score_deadline IS NULL nếu không TLCN/KLTN; council_report_date IS NULL nếu không KLTN. Bắt buộc khi áp dụng chưa chốt; chưa hoàn tất DDL | REQ-06–07; FR-04, Q-02 |
| DB-03 | CHECK status thuộc DRAFT/PENDING_APPROVAL/APPROVED/PUBLISHED; khi khác DRAFT, cả 5 trường nội dung trim khác rỗng | REQ-08–09; FR-05,14 |
| DB-04 | PK(topic_id,lecturer_id), UNIQUE(topic_id,slot), CHECK slot BETWEEN 1 AND 2 | REQ-10; FR-06 |
| DB-05 | PK(group_id,student_id); UNIQUE(period_id,student_id); UNIQUE(group_id,slot); CHECK slot BETWEEN 1 AND 3 | REQ-12–13; FR-08–09 |
| DB-06 | leader_student_id NOT NULL; FK (group.id,leader_student_id) → membership(group_id,student_id), DEFERRABLE INITIALLY DEFERRED | REQ-12; FR-08 |
| DB-07 | UNIQUE(id,period_id) trên topic/group; membership FK(group_id,period_id) → group; registration FK(group_id,period_id) và FK(topic_id,period_id) | REQ-13–15; FR-09–10 |
| DB-08 | Partial UNIQUE(period_id,group_id) WHERE status IN ('PENDING','ACCEPTED') | REQ-14; FR-10,13 |
| DB-09 | Partial UNIQUE(period_id,topic_id) WHERE status='ACCEPTED' | Xác nhận U Q-10; FR-11 |
| DB-10 | CHECK registration status trong PENDING/ACCEPTED/REJECTED/WITHDRAWN; automatic rejection có rejection_kind=TOPIC_TAKEN và đúng lý do đã chốt | FR-11–13 |

DB-06 cùng membership FK tạo vòng quan hệ có chủ đích: insert nhóm và thành viên trưởng
nhóm trong cùng transaction, kiểm tra FK deferred ở commit. Điều này bảo đảm tối thiểu
một thành viên và đúng một leader; không được xóa leader mà không chuyển leader hợp lệ.
DB-05 bảo vệ tối đa 3 mà không dùng CHECK đếm hàng.

Không UNIQUE(group_id,topic_id) trên mọi trạng thái: phải cho đăng ký lại sau rút/từ chối.
Không CHECK đọc bảng khác để kiểm tra số GVHD hay department. Service dưới khóa giao dịch
kiểm tra tối thiểu 1 GVHD khi gửi duyệt, cùng bộ môn, quyền và trạng thái. Bản nháp có thể
0 GVHD; giới hạn tối đa vẫn 2. Các đường ghi phải qua cùng service transaction.
Không cấp quyền ghi DB cho người dùng ứng dụng; sửa DB trực tiếp bằng DBeaver không phải
luồng nghiệp vụ được hỗ trợ và không thay thế kiểm thử backend.

## State transitions

### Đợt (trạng thái suy ra, không cần job chuyển trạng thái)

| Khoảng thời gian | Nhãn kỹ thuật |
|---|---|
| now < lecturer_start | BEFORE_LECTURER |
| lecturer_start <= now < lecturer_end | LECTURER_OPEN |
| lecturer_end <= now < student_start | BETWEEN_WINDOWS |
| student_start <= now < student_end | STUDENT_OPEN |
| now >= student_end | REGISTRATION_CLOSED |

BETWEEN_WINDOWS có thể rỗng. Công bố đề tài là điều kiện riêng; STUDENT_OPEN không tự
công bố. REGISTRATION_CLOSED chỉ nói đóng đăng ký, không kết thúc toàn bộ dự án hoặc
tự từ chối pending. Sửa loại/bốn mốc chỉ trước lecturer_start **đang lưu** (FR-15).

### Đề tài

| Từ → đến | Actor | Điều kiện |
|---|---|---|
| DRAFT → DRAFT | GV tác giả | Chỉ sửa nháp của mình; không suy diễn thêm deadline sửa nháp |
| DRAFT → PENDING_APPROVAL | GV tác giả | Trong hạn GV, đủ 5 nội dung, 1–2 GVHD cùng bộ môn |
| PENDING_APPROVAL → APPROVED | Trưởng khoa | Quyền theo scope chờ Q-18 |
| PENDING_APPROVAL → DRAFT | Trưởng khoa | Từ chối; gửi lại vẫn phải trong hạn GV |
| APPROVED → PUBLISHED | Trưởng khoa | Công bố; không nhảy qua duyệt |

Không có sửa GV sau DRAFT, hủy công bố hoặc xóa topic trong phạm vi đã chốt.
Công bố danh sách thể hiện bằng tập đề tài PUBLISHED; granularity công bố cần đối chiếu
AC nếu yêu cầu công bố toàn danh sách một lần. Contract giữ thao tác tập hợp nguyên tử
là đề xuất kỹ thuật, không cho công bố DRAFT/PENDING.

### Đăng ký

| Từ → đến | Actor/trigger | Điều kiện |
|---|---|---|
| Chưa tồn tại → PENDING | Trưởng nhóm | SV window, topic PUBLISHED cùng đợt, chưa có ACCEPTED topic, nhóm 1–3, không active khác |
| PENDING → ACCEPTED | Trưởng khoa | Chưa có accepted khác; đồng thời auto-reject pending khác cùng topic/đợt |
| PENDING → REJECTED | Trưởng khoa | Lý do bắt buộc chưa chốt Q-09 |
| PENDING → REJECTED | Auto khi topic nhận nhóm khác | Lý do chính xác “Đề tài đã nhận nhóm” |
| PENDING → WITHDRAWN | Trưởng nhóm | Trong hạn SV |
| REJECTED/WITHDRAWN → đăng ký mới PENDING | Trưởng nhóm | Tạo ID mới, đầy đủ điều kiện gửi; hàng cũ không đổi |

Không chuyển ACCEPTED sang WITHDRAWN. Không tự chọn FIFO hoặc ưu tiên rút/accept.
Nhóm không cần trạng thái dư thừa: quyền sửa suy ra từ thời gian và không tồn tại
đăng ký PENDING/ACCEPTED. Tạo nhóm/rời nhóm vẫn Q-07, không thêm trạng thái “nhóm nháp”.

## Transaction protocol and concurrency

Thiết kế correctness local: **mọi mutation trong đợt lấy khóa period FOR UPDATE trước**,
isolation READ COMMITTED, một command một đợt. Resolve ID → period chỉ dùng để định tuyến;
sau khóa đọc lại quan hệ, actor, quyền, version, trạng thái. Không khóa child trước period.
Nếu nhiều child cần khóa, dùng thứ tự bảng cố định và ID tăng dần.
Không gọi network hay chờ tương tác người dùng trong transaction.

Lấy thời điểm kiểm tra sau khi đã chờ khóa, qua Clock đáng tin cậy có thể thay trong test;
không dùng thời gian gửi request hoặc thời điểm bắt đầu transaction trước khi chờ khóa.
Điểm xét hợp lệ là lúc service kiểm tra sau khóa, không hứa commit xảy ra trước deadline.
Nếu khách hàng yêu cầu deadline theo commit, phải xác nhận và sửa thiết kế.

| Command/race | Cách bảo vệ |
|---|---|
| Cùng SV vào hai nhóm | Khóa period + DB-05; nhóm ở hai đợt độc lập |
| Thêm 2 người khi nhóm có 2 | Kiểm tra lại sau khóa, cấp slot trống; tối đa 3 ở commit |
| Chuyển leader / gửi đăng ký | Khóa period, đọc lại leader; gửi chỉ với leader còn hiệu lực |
| Sửa nhóm / gửi | Kiểm tra không active sau khóa; không sửa sau khi PENDING hình thành |
| Hai đăng ký của cùng nhóm | Khóa + DB-08; thất bại không tạo hàng dở dang |
| Hai accept cùng topic | Khóa + DB-09; một accepted, pending khác auto-rejected |
| Accept / đăng ký mới | Chung khóa: nếu gửi trước thì nằm trong auto-reject; nếu gửi sau thì bị từ chối |
| Sửa draft / gửi duyệt | Chung khóa; kiểm tra status và version trước ghi |
| Sửa lịch / mở đợt | Kiểm tra giờ và lecturer_start hiện hành sau khóa; không dùng giờ được đề nghị |
| Rút / accept | Trạng thái cuối loại trừ nhau; **chính sách ưu tiên còn Q-09**, chưa phê duyệt hành vi chọn người thắng |

Accept transaction: khóa → xác thực scope/actor → đọc PENDING và topic → kiểm tra chưa
accepted → đổi đăng ký được chọn thành ACCEPTED → đổi tất cả PENDING khác sang REJECTED,
ghi reason cố định → commit. Bất kỳ lỗi nào rollback toàn bộ, không gửi thông báo.
Membership và supervisors không đổi trong transaction này.

Lỗi unique/FK/check/stale version chuyển thành lỗi nghiệp vụ thích hợp sau rollback.
Deadlock/lock timeout trả xung đột có thể thử lại; không tự retry vô hạn hoặc ghi một phần.
Không coi version từ client là bằng chứng quyền.

Cập nhật role/bộ môn chưa có endpoint. Nếu sau này bổ sung, phải khóa các hàng danh tính
liên quan hoặc phối hợp cùng protocol để không đổi quyền trong lúc command ghi dữ liệu.
Không dựa vào role cache session để vượt kiểm tra service; thay đổi qua SQL ngoài ứng dụng
trong khi test không đại diện một luồng quản trị đã chốt.

## Migration and sample data

Dự kiến V1 identity/department; V2 periods/topics/supervisors; V3 groups/membership;
V4 registrations/partial indexes. Đây là cách chia dự kiến, chỉ viết migration sau khi
các Q ảnh hưởng schema được xác nhận. Không gán số V đã tồn tại hoặc sửa migration đã chạy.

Test migration từ rỗng, schema trước → sau với fixture và dữ liệu vi phạm để kiểm tra
thất bại có kiểm soát. Backup trước thay đổi local có dữ liệu cần giữ; ưu tiên migration
sửa tiến tiếp, không giả định rollback schema không mất dữ liệu.

Seed local chủ động, idempotent theo mã fixture; một khoa thử nghiệm (không xác nhận scope),
hai bộ môn, một dean, ba GV, tối thiểu bảy SV, nhóm 1/2/3 người, hai nhóm chờ cùng topic,
topic ở mỗi trạng thái và các loại đợt. Mật khẩu được cấp qua môi trường rồi hash;
không lưu mật khẩu sử dụng được trong SQL/docs. Không tự xóa dữ liệu có sẵn hoặc dời
thời gian đợt mỗi lần restart. Seed là dụng cụ demo, không phải chính sách cấp tài khoản.
Retention lịch sử chưa chốt: không tạo job purge/cascade xóa lịch sử.
