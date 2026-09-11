> Cập nhật ngày 2026-09-09: database dùng MySQL 8.4 LTS. Schema 20 bảng nằm tại
> `src/main/resources/db/migration/V1__full_system.sql`; V2 bắt buộc ngày theo loại đợt,
> V3 bắt buộc khoảng GV kết thúc trước khi khoảng SV bắt đầu. Thay đổi Q-07 ngày
> 2026-09-11 sẽ thêm bảng lời mời bằng migration mới, không sửa V1 đã dùng.

# Data Model — Thiết kế triển khai

Áp dụng [spec](spec.md) FR-01–15. MySQL 8.4 LTS; tên/kiểu dưới đây là thiết kế kỹ thuật,
không thêm nghiệp vụ. Các quyết định Q ảnh hưởng schema đã được chốt; thay đổi sau này phải
được thực hiện bằng migration mới.

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
| TopicSupervisor / topic_supervisors | topic_id FK, lecturer_id FK, slot | Một hàng/GVHD; slot 1 luôn là tác giả/GVHD chính do backend tự gán, slot 2 là GVHD phối hợp không bắt buộc; người phối hợp phải là tài khoản LECTURER đang hoạt động cùng bộ môn; chỉ thay đổi khi topic DRAFT |
| StudentGroup / student_groups | id, period_id FK, leader_student_id FK, version, dissolved_at | Tạo trong hạn SV; nhóm đang hoạt động có trưởng nhóm là thành viên; giải tán bằng `dissolved_at` để giữ lịch sử đăng ký, không thêm tên nhóm bắt buộc hoặc trạng thái nhóm nháp |
| GroupMembership / group_memberships | group_id, period_id, student_id FK, slot | Thành viên đã chấp nhận; một tư cách SV/đợt, slot 1–3 bảo vệ sức chứa; thành viên thường được tự rời theo FR-08 |
| GroupInvitation / group_invitations | id, group_id, period_id, student_id FK, invited_by FK, slot, status, created_at, responded_at, version, pending guards | Lời mời PENDING giữ slot nhưng chưa tạo membership; ACCEPTED/REJECTED/CANCELLED giữ lịch sử phản hồi; hết hiệu lực được suy ra từ student_end |
| Registration / registrations | id, period_id, group_id, topic_id, status, submitted_at, decided_at, withdrawn_at, decision_actor_id, rejection_kind, rejection_reason, version | REJECTED/WITHDRAWN không tái dùng ID khi đăng ký lại; tạo hàng mới; từ chối thủ công bắt buộc có reason sau khi trim; giữ toàn bộ lịch sử không thời hạn trong phiên bản đầu |

Các FK danh tính trỏ cùng UserAccount; service xác minh vai trò GV/SV đúng đối tượng.
Trưởng khoa thao tác dữ liệu trong khoa mình; sinh viên xem đề tài công bố trong khoa và
chỉ thay đổi nhóm/đăng ký mà quan hệ dữ liệu cho phép. Không bỏ trống scope rồi hiểu là toàn quyền.

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
| DB-06 | leader_student_id NOT NULL; trigger sau insert tự tạo membership slot 1; trigger cập nhật/xóa bảo đảm leader luôn là thành viên khi `dissolved_at IS NULL`; nhóm giải tán giữ hàng lịch sử nhưng không còn membership | REQ-12; FR-08 |
| DB-07 | UNIQUE(id,period_id) trên topic/group; membership FK(group_id,period_id) → group; registration FK(group_id,period_id) và FK(topic_id,period_id) | REQ-13–15; FR-09–10 |
| DB-08 | Generated column nullable `active_group_guard` kết hợp UNIQUE(period_id,active_group_guard) khi status PENDING/ACCEPTED | REQ-14; FR-10,13 |
| DB-09 | Generated column nullable `accepted_topic_guard` kết hợp UNIQUE(period_id,accepted_topic_guard) khi status ACCEPTED | Xác nhận U Q-10; FR-11 |
| DB-10 | CHECK registration status trong PENDING/ACCEPTED/REJECTED/WITHDRAWN; manual rejection có rejection_kind=MANUAL và reason không rỗng sau khi trim; automatic rejection có rejection_kind=TOPIC_TAKEN và đúng lý do đã chốt | FR-11–13 |
| DB-11 | `group_invitations` có CHECK status PENDING/ACCEPTED/REJECTED/CANCELLED và slot 1–3; generated guard + UNIQUE bảo đảm một PENDING/SV/đợt và một PENDING/slot/nhóm; FK(group_id,period_id) giữ cùng đợt | U Q-07 ngày 2026-09-11; FR-08–10 |

DB-06 dùng trigger vì MySQL không hỗ trợ deferred FK: insert nhóm tự tạo membership của
trưởng nhóm; đổi leader chỉ nhận thành viên hiện hữu và không được xóa leader khi chưa
chuyển quyền. Điều này bảo đảm tối thiểu một thành viên và đúng một leader.
DB-05 bảo vệ tối đa 3 mà không dùng CHECK đếm hàng.
Tổng membership và lời mời PENDING nằm ở hai bảng nên service kiểm tra lại dưới khóa period,
cấp slot chưa dùng ở cả hai bảng và giữ tổng không quá ba. DB-05/DB-11 là hàng rào cuối
cho từng loại hàng; không dựa riêng vào kiểm tra trước transaction.

Không UNIQUE(group_id,topic_id) trên mọi trạng thái: phải cho đăng ký lại sau rút/từ chối.
Mỗi lần đăng ký lại tạo một hàng mới. Không cascade hoặc job purge xóa registration
REJECTED/WITHDRAWN; lịch sử được giữ không thời hạn trong phiên bản đầu.
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
| DRAFT → DRAFT | GV tác giả | Chỉ sửa nội dung/GVHD phối hợp của nháp mình tạo; tác giả luôn là GVHD chính, danh mục phối hợp lấy từ tài khoản LECTURER đang hoạt động cùng bộ môn |
| DRAFT → PENDING_APPROVAL | GV tác giả | Trong hạn GV, đủ 5 nội dung, tác giả ở slot 1 và tối đa một GVHD phối hợp cùng bộ môn |
| PENDING_APPROVAL → APPROVED | Trưởng khoa | Chỉ đề tài thuộc khoa của trưởng khoa |
| PENDING_APPROVAL → DRAFT | Trưởng khoa | Từ chối; gửi lại vẫn phải trong hạn GV |
| APPROVED → PUBLISHED | Trưởng khoa | Công bố; không nhảy qua duyệt |

Không sửa nội dung hoặc GVHD từ PENDING_APPROVAL trở đi; khi bị từ chối về DRAFT,
tác giả có thể sửa và gửi lại trong hạn GV. Không có thay GVHD trong quá trình thực hiện,
hủy công bố hoặc xóa topic trong phạm vi đã chốt.
Công bố danh sách thể hiện bằng tập đề tài PUBLISHED. Một lệnh công bố khóa đợt rồi
chuyển toàn bộ topic APPROVED của đúng đợt sang PUBLISHED trong cùng giao dịch; topic
DRAFT/PENDING_APPROVAL không đổi. Nếu không còn topic APPROVED thì lệnh không tạo thay đổi.
Không lưu cờ công bố riêng ở registration_periods và không tự động công bố theo đồng hồ.
Ngày bắt đầu SV là mốc UI nhắc trưởng khoa hoàn tất, không phải khóa cứng thao tác.

### Đăng ký

| Từ → đến | Actor/trigger | Điều kiện |
|---|---|---|
| Chưa tồn tại → PENDING | Trưởng nhóm | SV window, topic PUBLISHED cùng đợt, chưa có ACCEPTED topic, nhóm 1–3 thành viên đã chấp nhận, không active khác và không còn lời mời PENDING |
| PENDING → ACCEPTED | Trưởng khoa | Chưa có accepted khác; đồng thời auto-reject pending khác cùng topic/đợt |
| PENDING → REJECTED | Trưởng khoa | Lý do bắt buộc, không rỗng sau khi trim; validation lỗi giữ nguyên PENDING |
| PENDING → REJECTED | Auto khi topic nhận nhóm khác | Lý do chính xác “Đề tài đã nhận nhóm” |
| PENDING → WITHDRAWN | Trưởng nhóm | Trong hạn SV |
| REJECTED/WITHDRAWN → đăng ký mới PENDING | Trưởng nhóm | Tạo ID mới, đầy đủ điều kiện gửi; hàng cũ không đổi |

Không chuyển ACCEPTED sang WITHDRAWN. Khi rút và accept đồng thời, giao dịch lấy khóa period
trước quyết định trạng thái cuối; giao dịch sau đọc lại trạng thái và bị từ chối.
### Lời mời và thành viên nhóm

| Từ → đến | Actor | Điều kiện |
|---|---|---|
| Chưa tồn tại → PENDING | Trưởng nhóm | Trong hạn SV, không active registration, SV cùng khoa chưa có nhóm/lời mời PENDING trong đợt, tổng membership + PENDING dưới 3 |
| PENDING → ACCEPTED + membership | SV được mời | Trong hạn SV, không active registration; khóa period, kiểm tra lại slot/sức chứa rồi tạo membership nguyên tử |
| PENDING → REJECTED | SV được mời | Được phép bất cứ lúc nào; không tạo membership, giải phóng guard/slot |
| PENDING → CANCELLED | Trưởng nhóm | Trong hạn SV, không active registration; giải phóng guard/slot |
| membership → không còn | Chính thành viên thường hoặc trưởng nhóm | Trong hạn SV, không active registration; chính thành viên được tự rời, trưởng nhóm được xóa thành viên thường |
| nhóm một người → DISSOLVED | Trưởng nhóm | Trong hạn SV, không active registration; đặt `dissolved_at`, hủy các lời mời PENDING và xóa membership trưởng nhóm trong cùng transaction |

Lời mời còn PENDING sau `student_end` được hiển thị là hết hiệu lực và không thể chấp nhận;
không cần job đổi trạng thái. Từ chối vẫn cập nhật thành REJECTED để giải phóng generated
guard. Nhóm không có trạng thái nháp; `dissolved_at` chỉ là dấu lưu lịch sử và nhóm này bị
loại khỏi mọi truy vấn nhóm đang hoạt động. Trưởng nhóm có thành viên khác phải chuyển quyền
trước khi tự rời. Không tự chọn trưởng nhóm mới. Đăng ký đề tài bị chặn khi còn lời mời PENDING.

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
| Hai nhóm mời cùng SV | Khóa period + DB-11; chỉ một lời mời PENDING/SV/đợt |
| Mời/accept đồng thời khi còn một chỗ | Khóa period, đọc membership + invitation PENDING, cấp slot trống; DB-05/DB-11 chặn vi phạm cuối |
| SV accept trong khi được thêm vào nhóm khác | Khóa period + DB-05; đọc lại membership/lời mời trước khi tạo membership nguyên tử |
| Chuyển leader / gửi đăng ký | Khóa period, đọc lại leader; gửi chỉ với leader còn hiệu lực |
| Rời/giải tán / gửi đăng ký | Chung khóa period; gửi kiểm tra không còn lời mời PENDING, rời/giải tán kiểm tra không active |
| Hai đăng ký của cùng nhóm | Khóa + DB-08; thất bại không tạo hàng dở dang |
| Hai accept cùng topic | Khóa + DB-09; một accepted, pending khác auto-rejected |
| Accept / đăng ký mới | Chung khóa: nếu gửi trước thì nằm trong auto-reject; nếu gửi sau thì bị từ chối |
| Sửa draft / gửi duyệt | Chung khóa; kiểm tra status và version trước ghi |
| Sửa lịch / mở đợt | Kiểm tra giờ và lecturer_start hiện hành sau khóa; không dùng giờ được đề nghị |
| Rút / accept | Chung khóa period; giao dịch lấy khóa trước hoàn tất, giao dịch sau đọc lại trạng thái không còn PENDING và bị từ chối; không để lại trạng thái kép |

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

V1 hiện tạo 20 bảng nền, khóa ngoại, chỉ mục và trigger đã được thiết kế. Q-05/Q-06
đã chốt giữ mô hình topic theo đợt và supervisors hiện tại, không cần tách catalog/offering
hoặc bảng lịch sử thay GVHD. V2 thay hai
CHECK ngày đợt bằng ràng buộc DB-02 đã xác nhận. V3 siết hai giai đoạn đăng ký không được
trùng mốc kết thúc GV/bắt đầu SV. Luồng lời mời đã xác nhận sẽ tạo `group_invitations` và
thêm `student_groups.dissolved_at` bằng migration mới sau V4, gồm trạng thái, slot giữ chỗ,
audit thời gian và generated guards; migration cũng điều chỉnh trigger giữ leader để chỉ áp
dụng cho nhóm đang hoạt động, không sửa lịch sử V1. Mọi thay đổi nghiệp vụ sau này phải đi
qua migration tiếp theo.
Không gán lại số phiên bản đã tồn tại hoặc sửa migration đã chạy trên database chung.

Test migration từ rỗng, schema trước → sau với fixture và dữ liệu vi phạm để kiểm tra
thất bại có kiểm soát. Backup trước thay đổi local có dữ liệu cần giữ; ưu tiên migration
sửa tiến tiếp, không giả định rollback schema không mất dữ liệu.

Seed local chủ động, idempotent theo mã fixture; một khoa thử nghiệm (không xác nhận scope),
hai bộ môn, một dean, ba GV, tối thiểu bảy SV, nhóm 1/2/3 người, hai nhóm chờ cùng topic,
topic ở mỗi trạng thái và các loại đợt. Mật khẩu được cấp qua môi trường rồi hash;
không lưu mật khẩu sử dụng được trong SQL/docs. Không tự xóa dữ liệu có sẵn hoặc dời
thời gian đợt mỗi lần restart. Seed là dụng cụ demo, không phải chính sách cấp tài khoản.
Giữ lịch sử registration REJECTED/WITHDRAWN không thời hạn; không tạo job purge hoặc
cascade xóa lịch sử trong phiên bản đầu.
