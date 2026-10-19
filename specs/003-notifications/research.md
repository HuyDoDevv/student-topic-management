# Research: Thông báo trong hệ thống

## 1. Diễn giải REQ-29 và REQ-30

**Decision**: Xem REQ-29 và REQ-30 là cùng một năng lực “thông báo chính thức”, không coi đó
là bằng chứng cho email/push hoặc cảnh báo sự kiện tự động. Phần tự động là đề xuất riêng chờ duyệt.

**Rationale**: `workspace/docs/customer/requirements-analysis.md` ghi rõ hai REQ cùng xuất phát từ đoạn
cuối trang 1 và Q-22 còn mở về kênh, đối tượng nhận, lịch gửi và read receipt.

**Alternatives considered**:

- Chỉ làm bảng tin, không có bell/read state: sát nghĩa tối thiểu nhưng không dùng được placeholder
  chuông hiện có và không giải quyết nhu cầu thông báo theo luồng.
- Mặc định mọi thay đổi nghiệp vụ đều gửi: dễ gây spam và biến suy luận thành yêu cầu.

## 2. Kênh phân phối phiên bản đầu

**Decision**: Chỉ dùng in-app notification, tải bằng request server-rendered ở lần mở/tải trang;
không realtime.

**Rationale**: Phù hợp Spring MVC + Thymeleaf hiện có, không có hạ tầng email/push, giảm secrets,
vận hành và lỗi delivery. Yêu cầu “kịp thời” vẫn được đáp ứng khi người dùng sử dụng hệ thống.

**Alternatives considered**:

- WebSocket/SSE: badge cập nhật tức thời nhưng thêm connection lifecycle, security và test phức tạp.
- Email/SMS/mobile push: cần provider, consent, địa chỉ liên hệ, retry và secrets chưa được yêu cầu.
- Polling JavaScript: gần realtime hơn nhưng thêm tải định kỳ; chưa cần cho quy mô và UX hiện tại.

## 3. Mô hình fan-out theo người nhận

**Decision**: Mỗi sự kiện có một `notification_events`; mỗi người nhận có một
`user_notifications` riêng. Tập người nhận được snapshot tại lúc publish/commit.

**Rationale**: Read state, phân trang, IDOR và count chưa đọc trở nên đơn giản; thay đổi khoa/role
sau này không làm lịch sử biến mất hoặc xuất hiện ngoài dự kiến. Unique `(event_id, recipient_id)`
chống trùng ở tầng dữ liệu.

**Alternatives considered**:

- Tính audience động mỗi lần đọc: ít row hơn nhưng kết quả lịch sử thay đổi theo role/khoa và
  read receipt cần thêm logic đa hình.
- Một row thông báo cho từng người, lặp toàn bộ title/body: truy vấn dễ nhưng tốn dữ liệu và khó
  truy vết một lần phát sinh tới nhiều người nhận.
- Outbox + worker: phù hợp quy mô rất lớn, nhưng thông báo có độ trễ và thêm vận hành; không cần
  cho 2.000 tài khoản dự kiến.

## 4. Tính nhất quán transaction

**Decision**: Các service nguồn gọi `NotificationService.emit(...)` đồng bộ sau khi validation
thành công và trước khi transaction hiện tại commit. Không dùng callback sau commit.

**Rationale**: PN-09 yêu cầu không có thông báo mồ côi và không bỏ sót khi nghiệp vụ đã commit.
InnoDB và các service hiện có đã dùng `@Transactional(READ_COMMITTED)`.

**Alternatives considered**:

- `@TransactionalEventListener(AFTER_COMMIT)`: không rollback được nguồn nếu ghi thông báo lỗi,
  cần retry/outbox để không mất sự kiện.
- Gửi từ controller: vi phạm tách nghiệp vụ và có thể bỏ sót các entry point khác.
- Database trigger: khó tạo nội dung/recipient theo luật Java và khó test/duy trì.

## 5. Chống trùng và tranh chấp

**Decision**: Dùng `event_key` ổn định theo loại + ID nguồn + transition, unique ở DB; bản nhận
unique theo event + recipient. Service nguồn vẫn kiểm tra trạng thái/version/lock như hiện tại.

**Rationale**: Kiểm tra “đã tồn tại” ở application không đủ khi hai request chạy đồng thời. Unique
constraint là hàng rào cuối, còn state transition giúp trả lỗi nghiệp vụ rõ ràng.

**Alternatives considered**:

- UUID mới mỗi lần gọi: không nhận diện được retry của cùng sự kiện.
- Chỉ query-before-insert: có race window.
- Distributed lock: nặng và không cần trong một MySQL monolith.

## 6. Mô hình target an toàn

**Decision**: Lưu `target_type` enum và `target_id`; server map sang route nội bộ. Không nhận hoặc
lưu redirect URL tùy ý từ request người dùng.

**Rationale**: Loại open redirect, giữ route trong quyền kiểm soát ứng dụng và buộc controller
đích kiểm tra lại authorization. Notification không phải capability token.

**Alternatives considered**:

- Lưu URL string: linh hoạt nhưng dễ tạo link ngoài/route sai và khó refactor.
- Không có link: an toàn nhất nhưng giảm giá trị sử dụng và buộc người dùng tự tìm màn hình.

## 7. Vòng đời announcement

**Decision**: DRAFT → PUBLISHED một chiều. Draft được sửa trong scope được phép; publish snapshot
người nhận và nội dung event. Published không sửa, unpublish hay delete ở v1.

**Rationale**: Schema hiện có đã dùng `published_at IS NULL` cho draft. Bất biến sau publish giữ
nội dung mà người dùng đã nhận nhất quán, tránh yêu cầu audit phức tạp.

**Alternatives considered**:

- Cho sửa sau publish: cần version notification, nhãn “đã cập nhật” và quy tắc reset read state.
- Cho thu hồi/xóa: cần xác định người nhận còn xem lịch sử không; Q-22 chưa trả lời.
- Không có draft: ít code hơn nhưng khó soạn/duyệt nội dung chính thức.

## 8. Người đăng và scope

**Decision**: ADMIN quản lý announcement cấp trường; DEAN quản lý announcement đúng faculty lấy
từ identity. `source_name` được server suy ra, không cho form tự khai.

**Rationale**: Đây là ánh xạ ít thay đổi nhất với roles đã có. Backend scope nhất quán với các
feature hiện tại; dự án chỉ vận hành Khoa Công nghệ thông tin.

**Alternatives considered**:

- Chỉ ADMIN đăng mọi loại: đơn giản nhưng không phản ánh nguồn “khoa”.
- Tạo role PUBLISHER mới: linh hoạt hơn nhưng mở rộng quản trị tài khoản/quyền ngoài yêu cầu.
- Cho LECTURER đăng: quá rộng và không có căn cứ yêu cầu.

## 9. Header query và phân trang

**Decision**: Model advice dành cho request đã xác thực gọi query count chưa đọc và top 5; inbox
dùng page size cố định 20, keyset/order `delivered_at DESC, id DESC` hoặc Spring page tương đương
với index bao phủ điều kiện recipient/read/time.

**Rationale**: Hai query nhỏ, dễ render với Thymeleaf và phù hợp cấu trúc header hiện tại. Index
tránh full scan khi dữ liệu tăng.

**Alternatives considered**:

- Render header qua Ajax: tách request nhưng tăng JavaScript, loading state và E2E complexity.
- Cache count theo session: dễ stale và cần invalidation trên mọi event/mark-read.
- Nạp toàn bộ notification rồi đếm: không chấp nhận ở dữ liệu lớn.

## 10. Mark-read semantics

**Decision**: GET không thay đổi trạng thái. `POST /notifications/{id}/read` chỉ đánh dấu;
`POST /notifications/{id}/open` đánh dấu rồi redirect server-side. `read-all` dùng cutoff thời
gian/request để không nuốt thông báo đến đồng thời.

**Rationale**: Giữ HTTP semantics/CSRF rõ ràng và thao tác idempotent. Một thông báo được xem là
đã đọc khi người dùng chủ động đánh dấu hoặc mở nó, không chỉ khi dropdown render.

**Alternatives considered**:

- Mark-read bằng GET khi click: dễ làm nhưng crawler/prefetch có thể thay đổi dữ liệu.
- Mark tất cả khi mở dropdown: giảm badge ngoài ý muốn, người dùng chưa thực sự đọc.
- Reset về unread: chưa cần và không được yêu cầu.

## 11. Nội dung và an toàn hiển thị

**Decision**: Plain text, giới hạn title 200 ký tự và body 5.000 ký tự ở application; Thymeleaf
escape đầu ra. Event tự động dùng template server-side ngắn gọn, không nhúng dữ liệu nhạy cảm.

**Rationale**: Tránh XSS/rich-text sanitization, vừa đủ cho announcement học vụ và giữ dropdown gọn.

**Alternatives considered**:

- HTML/WYSIWYG: UX phong phú nhưng cần sanitizer, upload và policy nội dung.
- Markdown: vẫn cần parser/sanitization và không có yêu cầu.

## 12. Retention

**Decision**: Không tự xóa trong v1. Không cung cấp nút xóa cá nhân hoặc admin.

**Rationale**: Không có thời hạn lưu trữ trong yêu cầu; giữ dữ liệu hỗ trợ audit và tránh mất thông
tin. Dung lượng dự kiến vẫn phù hợp MySQL với index/pagination.

**Alternatives considered**:

- Xóa sau 90/180 ngày: cần quyết định nghiệp vụ và job định kỳ.
- Cho người dùng xóa: làm mất lịch sử và tăng trạng thái/endpoint không cần thiết.

## 13. Chiến lược kiểm thử

**Decision**: Unit test cho policy/key/recipient/target; MockMvc contract cho auth/CSRF/IDOR;
MySQL integration và concurrency cho Flyway, FK, unique, transaction; Playwright E2E cho bell,
publish và inbox.

**Rationale**: Các rủi ro chính nằm ở phân quyền, transaction và concurrency, không chỉ render UI.

**Alternatives considered**:

- Chỉ unit/MockMvc: không chứng minh unique/transaction đúng trên MySQL.
- Chỉ E2E: chậm và khó cô lập mọi nhánh lỗi/race.
