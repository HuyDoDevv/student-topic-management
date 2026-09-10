> Cập nhật ngày 2026-09-09: database dùng MySQL 8.4 LTS. Schema 20 bảng nằm tại
> `src/main/resources/db/migration/V1__full_system.sql`; V2 bắt buộc ngày theo loại đợt,
> V3 bắt buộc khoảng GV kết thúc trước khi khoảng SV bắt đầu.

# Data Model — Thiết kế có điều kiện

Áp dụng [spec](spec.md) FR-01–15. MySQL 8.4 LTS; tên/kiểu dưới đây là thiết kế kỹ thuật,
không thêm nghiệp vụ. Q có ảnh hưởng schema được ghi rõ; thay đổi sau khi chốt phải được
thực hiện bằng migration mới.

## Entities

| Entity / bảng dự kiến | Trường chính | Quan hệ và ghi chú |
|---|---|---|
| Faculty / faculties | id bigint PK, name varchar(255) | Khoa là scope của DEAN và của từng đợt đăng ký |
| Department / departments | id bigint PK, name varchar(255), faculty_id FK | Bộ môn thuộc khoa, được seed/import sẵn; DEAN giới hạn theo khoa, LECTURER theo bộ môn hiện tại |
| UserAccount / user_accounts | id bigint PK, username text unique, password_hash text, department_id FK, enabled boolean | Tài khoản được seed/import; mật khẩu không lưu plaintext; `enabled=false` khóa đăng nhập tài khoản gây sự cố |
| Role / roles | code PK, name | Danh mục role ADMIN/STUDENT/LECTURER/DEAN |
| UserRole / user_roles | user_id FK, role_code FK | PK hai trường; LECTURER có thể kết hợp DEAN; STUDENT không kết hợp ADMIN/LECTURER/DEAN. ADMIN chỉ xử lý sự cố khẩn cấp bằng ẩn mềm nội dung, khóa tài khoản vi phạm và lưu dấu vết; không có quyền học vụ/đăng ký/cơ cấu tổ chức |
| RegistrationPeriod / registration_periods | id, faculty_id FK, name, topic_type, lecturer_start/end, student_start/end, reviewer_score_deadline, council_report_date, version | Form nhận bốn ngày đăng ký; nội bộ lưu `TIMESTAMP(6)` UTC để suy ra trạng thái. Hai ngày phụ dùng `DATE`: TLCN/KLTN bắt buộc hạn điểm GVPB, KLTN bắt buộc thêm ngày hội đồng; loại khác phải để null |
| Topic / topics | id, period_id FK, department_id FK, author_id FK, title, description, objectives, student_requirements, expected_results, status, version | Mỗi đề tài thuộc đúng một đợt; phiên bản đầu không tái sử dụng/sao chép đề tài giữa các đợt và không tách catalog/offering |
| TopicSupervisor / topic_supervisors | topic_id FK, lecturer_id FK, slot | Một hàng/GVHD; chỉ chọn tài khoản LECTURER đang hoạt động cùng bộ môn; slot 1–2 là chỗ kỹ thuật, không phân thứ bậc; chỉ thay đổi khi topic DRAFT |
| StudentGroup / student_groups | id, period_id FK, leader_student_id FK, version | Trưởng nhóm luôn là thành viên; không thêm tên nhóm bắt buộc |
| GroupMembership / group_memberships | group_id, period_id, student_id FK, slot | Một tư cách SV/đợt; slot 1–3 chỉ bảo vệ sức chứa |
| Registration / registrations | id, period_id, group_id, topic_id, status, submitted_at, decided_at, withdrawn_at, decision_actor_id, rejection_kind, rejection_reason, version | rejected/withdrawn không tái dùng ID khi đăng ký lại; tạo hàng mới; retention còn Q-09 |

Các FK danh tính trỏ cùng UserAccount; service xác minh vai trò GV/SV đúng đối tượng.
Không suy ra department bắt buộc cho trưởng khoa hay quyền sinh viên từ FK bộ môn.
Chưa chốt schema phạm vi khoa: không bỏ trống scope rồi hiểu là toàn quyền.

Quy tắc moderation của ADMIN không xóa cứng nội dung. Dữ liệu nội dung cần cờ/trạng thái
ẩn và metadata `hidden_by`, `hidden_at`, `hidden_reason`; mọi thay đổi schema tương ứng phải
được tạo bằng migration trước khi cài quyền này. Đây là luồng sự cố riêng, không làm thay
đổi trạng thái duyệt học vụ của đề tài và không cho ADMIN sửa nội dung.

Dữ liệu nhập form chỉ nhận ngày `yyyy-MM-dd` theo Asia/Ho_Chi_Minh, không nhận giờ/phút/giây.
Ngày bắt đầu quy đổi thành `00:00:00`; ngày kết thúc thành `23:59:59.999999`, rồi lưu
`TIMESTAMP(6)` UTC. Khi đọc, quy đổi lại thành ngày bằng cùng zone; không dùng múi giờ
trình duyệt để quyết định hạn. Tạo mới và đổi loại không nhận ngày trước ngày hiện tại.
version bigint là optimistic concurrency token kỹ thuật, không phải trạng thái nghiệp vụ.

## Database invariants

| ID | Cơ chế DB dự kiến | REQ / FR |
|---|---|---|
| DB-01 | NOT NULL tên/loại/bốn mốc; CHECK lecturer_start < lecturer_end AND lecturer_end < student_start AND student_start < student_end | REQ-04–05; FR-02–03 |
| DB-02 | CHECK theo loại: COURSE/NCKH có cả hai ngày null; TLCN có reviewer_score_deadline NOT NULL và council_report_date null; KLTN có cả hai ngày NOT NULL | REQ-06–07; FR-04 |
| DB-03 | CHECK status thuộc DRAFT/PENDING_APPROVAL/APPROVED/PUBLISHED; khi khác DRAFT, cả 5 trường nội dung trim khác rỗng | REQ-08–09; FR-05,14 |
| DB-04 | PK(topic_id,lecturer_id), UNIQUE(topic_id,slot), CHECK slot BETWEEN 1 AND 2 | REQ-10; FR-06 |
| DB-05 | PK(group_id,student_id); UNIQUE(period_id,student_id); UNIQUE(group_id,slot); CHECK slot BETWEEN 1 AND 3 | REQ-12–13; FR-08–09 |
| DB-06 | leader_student_id NOT NULL; trigger sau insert tự tạo membership slot 1; trigger cập nhật/xóa bảo đảm leader luôn là thành viên | REQ-12; FR-08 |
| DB-07 | UNIQUE(id,period_id) trên topic/group; membership FK(group_id,period_id) → group; registration FK(group_id,period_id) và FK(topic_id,period_id) | REQ-13–15; FR-09–10 |
| DB-08 | Generated column nullable `active_group_guard` kết hợp UNIQUE(period_id,active_group_guard) khi status PENDING/ACCEPTED | REQ-14; FR-10,13 |
| DB-09 | Generated column nullable `accepted_topic_guard` kết hợp UNIQUE(period_id,accepted_topic_guard) khi status ACCEPTED | Xác nhận U Q-10; FR-11 |
| DB-10 | CHECK registration status trong PENDING/ACCEPTED/REJECTED/WITHDRAWN; automatic rejection có rejection_kind=TOPIC_TAKEN và đúng lý do đã chốt | FR-11–13 |

DB-06 dùng trigger vì MySQL không hỗ trợ deferred FK: insert nhóm tự tạo membership của
trưởng nhóm; đổi leader chỉ nhận thành viên hiện hữu và không được xóa leader khi chưa
chuyển quyền. Điều này bảo đảm tối thiểu một thành viên và đúng một leader.
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
| lecturer_start <= now <= lecturer_end | LECTURER_OPEN |
| lecturer_end < now < student_start | BETWEEN_WINDOWS |
| student_start <= now <= student_end | STUDENT_OPEN |
| now > student_end | REGISTRATION_CLOSED |

BETWEEN_WINDOWS là khoảng sau cuối ngày GV đến trước đầu ngày SV. Công bố đề tài là điều kiện riêng; STUDENT_OPEN không tự
công bố. REGISTRATION_CLOSED chỉ nói đóng đăng ký, không kết thúc toàn bộ dự án hoặc
tự từ chối pending. Trước lecturer_start **đang lưu**, DEAN được sửa tên, loại và bốn ngày
nếu cấu hình mới hợp lệ. Từ lecturer_start, tên/loại/mốc bắt đầu bị khóa; chỉ được tăng
mốc kết thúc khi giai đoạn tương ứng chưa hết hạn và toàn bộ thứ tự vẫn hợp lệ. Với từng
ngày phụ đang áp dụng, chỉ được tăng giá trị khi ngày hiện tại tại Asia/Ho_Chi_Minh còn
trước giá trị đang lưu; từ đầu ngày đang lưu thì khóa, không cho mở lại (FR-04, FR-15).

### Đề tài

| Từ → đến | Actor | Điều kiện |
|---|---|---|
| DRAFT → DRAFT | GV tác giả | Chỉ sửa nội dung/GVHD của nháp mình tạo; danh mục GVHD lấy từ tài khoản LECTURER đang hoạt động cùng bộ môn |
| DRAFT → PENDING_APPROVAL | GV tác giả | Trong hạn GV, đủ 5 nội dung, 1–2 GVHD cùng bộ môn |
| PENDING_APPROVAL → APPROVED | Trưởng khoa | Chỉ đề tài thuộc khoa của trưởng khoa |
| PENDING_APPROVAL → DRAFT | Trưởng khoa | Từ chối; gửi lại vẫn phải trong hạn GV |
| APPROVED → PUBLISHED | Trưởng khoa | Công bố; không nhảy qua duyệt |

Không sửa nội dung hoặc GVHD từ PENDING_APPROVAL trở đi; khi bị từ chối về DRAFT,
tác giả có thể sửa và gửi lại trong hạn GV. Không có thay GVHD trong quá trình thực hiện,
hủy công bố hoặc xóa topic trong phạm vi đã chốt.
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

V1 hiện tạo toàn bộ 20 bảng, khóa ngoại, chỉ mục và trigger đã được thiết kế. Q-05/Q-06
đã chốt giữ mô hình topic theo đợt và supervisors hiện tại, không cần tách catalog/offering
hoặc bảng lịch sử thay GVHD. V2 thay hai
CHECK ngày đợt bằng ràng buộc DB-02 đã xác nhận. V3 siết hai giai đoạn đăng ký không được
trùng mốc kết thúc GV/bắt đầu SV. Các quyết định nghiệp vụ còn mở làm thay đổi schema phải
được đưa vào migration mới sau V3 khi được xác nhận.
Không gán lại số phiên bản đã tồn tại hoặc sửa migration đã chạy trên database chung.

Test migration từ rỗng, schema trước → sau với fixture và dữ liệu vi phạm để kiểm tra
thất bại có kiểm soát. Backup trước thay đổi local có dữ liệu cần giữ; ưu tiên migration
sửa tiến tiếp, không giả định rollback schema không mất dữ liệu.

Seed local chủ động, idempotent theo mã fixture; một khoa thử nghiệm (không xác nhận scope),
hai bộ môn, một dean, ba GV, tối thiểu bảy SV, nhóm 1/2/3 người, hai nhóm chờ cùng topic,
topic ở mỗi trạng thái và các loại đợt. Mật khẩu được cấp qua môi trường rồi hash;
không lưu mật khẩu sử dụng được trong SQL/docs. Không tự xóa dữ liệu có sẵn hoặc dời
thời gian đợt mỗi lần restart. Seed là dụng cụ demo, không phải chính sách cấp tài khoản.
Retention lịch sử chưa chốt: không tạo job purge/cascade xóa lịch sử.
