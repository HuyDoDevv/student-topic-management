# Feature Specification: Thông báo trong hệ thống

**Feature Branch**: `feature/003-notifications`

**Created**: 2026-09-24

**Status**: Approved — được phép triển khai ngày 2026-09-24

**Input**: Người dùng yêu cầu lập kế hoạch chức năng thông báo để duyệt trước khi làm.

## Phạm vi và nguồn yêu cầu

- **REQ-29**: Cung cấp thông tin cho người dùng hệ thống thông qua thông báo chính thức.
- **REQ-30**: Hỗ trợ đăng thông báo chính thức của trường và khoa kịp thời.
- **Q-22**: Nguồn gốc không quy định người đăng, đối tượng nhận, vòng đời, trạng thái đã đọc,
  cảnh báo tự động hoặc kênh gửi ngoài hệ thống.
- Các quyết định **PN-01–PN-12** trong tài liệu này đã được chủ dự án duyệt ngày 2026-09-24
  bằng chỉ đạo bắt đầu triển khai feature từ `develop`.

## Các quyết định đã duyệt

| ID | Đề xuất |
|---|---|
| PN-01 | Phiên bản đầu chỉ có thông báo trong ứng dụng; không email, SMS, mobile push hoặc WebSocket. |
| PN-02 | `ADMIN` đăng thông báo cấp trường; `DEAN` đăng thông báo của đúng khoa mình quản lý. Dự án hiện phục vụ Khoa Công nghệ thông tin. |
| PN-03 | Thông báo chính thức có bản nháp; chỉ sửa khi chưa công bố. Sau khi công bố không sửa, thu hồi hoặc xóa trong phiên bản đầu. |
| PN-04 | Khi công bố, hệ thống chốt danh sách tài khoản đang hoạt động thuộc đối tượng nhận; tài khoản tạo sau không nhận ngược thông báo cũ. |
| PN-05 | Chuông ở header hiển thị số chưa đọc và tối đa 5 mục gần nhất; trang danh sách có phân trang và lọc. |
| PN-06 | Người nhận có thể đánh dấu từng thông báo hoặc tất cả là đã đọc. Đọc là trạng thái riêng của từng tài khoản. |
| PN-07 | Các sự kiện nghiệp vụ ở bảng bên dưới tự tạo thông báo cho người liên quan. |
| PN-08 | Không gửi cho người thực hiện chính hành động đó, trừ khi họ đồng thời là đối tượng bắt buộc nhận theo nghiệp vụ. |
| PN-09 | Thông báo sự kiện và thay đổi nghiệp vụ được ghi trong cùng transaction; rollback nghiệp vụ thì không để lại thông báo mồ côi. |
| PN-10 | Cùng một sự kiện không tạo trùng thông báo cho cùng người nhận khi request bị retry hoặc có tranh chấp đồng thời. |
| PN-11 | Không tự động xóa thông báo; chính sách lưu trữ/xóa hàng loạt nằm ngoài phiên bản đầu. |
| PN-12 | Link trong thông báo chỉ là đường dẫn nội bộ do server tạo; khi mở, backend kiểm tra lại quyền trên tài nguyên. |

### Ma trận sự kiện tự động đã duyệt

| Sự kiện | Người nhận | Không nhận | Đích mở |
|---|---|---|---|
| Sinh viên được mời vào nhóm | Sinh viên được mời | Người gửi lời mời | Chi tiết nhóm/lời mời |
| Lời mời được chấp nhận hoặc từ chối | Trưởng nhóm đã gửi lời mời | Sinh viên vừa phản hồi | Chi tiết nhóm |
| Đề tài giảng viên được duyệt hoặc từ chối | Giảng viên đề xuất | Trưởng khoa thực hiện | Chi tiết đề tài |
| Đăng ký đề tài được chấp nhận, từ chối hoặc tự động từ chối | Từng sinh viên thuộc snapshot của đăng ký | Trưởng khoa thực hiện | Chi tiết đăng ký |
| Giảng viên được phân công chấm trực tiếp | Giảng viên được phân công | Trưởng khoa thực hiện | Phiếu chấm/đề tài |
| Giảng viên được thêm vào hội đồng | Giảng viên được phân công | Trưởng khoa thực hiện | Chi tiết hội đồng |
| Kết quả được công bố | Từng sinh viên thuộc snapshot kết quả | Trưởng khoa thực hiện | Kết quả cá nhân/đề tài |

Thông báo nhắc hạn nộp, thông báo khi nộp báo cáo, thay đổi lịch hội đồng và thông báo qua
kênh ngoài hệ thống chưa nằm trong phiên bản đầu vì yêu cầu gốc chưa quy định và chúng cần
thêm scheduler hoặc quy tắc thời điểm riêng.

## User Scenarios & Testing

### User Story 1 - Nhận và xem thông báo (Priority: P1)

Là người dùng đã đăng nhập, tôi xem được số thông báo chưa đọc trên chuông, các thông báo
gần nhất và trang danh sách đầy đủ để không bỏ lỡ thông tin liên quan đến mình.

**Why this priority**: Đây là lát cắt tạo giá trị trực tiếp cho mọi loại thông báo và thay thế
placeholder hiện có trong header.

**Independent Test**: Tạo sẵn thông báo cho một tài khoản, đăng nhập bằng tài khoản đó và
kiểm chứng badge, dropdown, danh sách, trạng thái đọc và việc tài khoản khác không xem được.

**Acceptance Scenarios**:

1. **Given** tài khoản có ba thông báo chưa đọc, **When** mở một trang bất kỳ,
   **Then** chuông hiển thị số 3 và dropdown sắp xếp mới nhất trước.
2. **Given** tài khoản có nhiều hơn năm thông báo, **When** mở dropdown,
   **Then** chỉ năm mục gần nhất hiển thị và có liên kết đến trang danh sách đầy đủ.
3. **Given** một thông báo thuộc tài khoản A, **When** tài khoản B đoán ID và truy cập,
   **Then** backend từ chối mà không tiết lộ nội dung hoặc thay đổi trạng thái đọc.
4. **Given** thông báo trỏ tới tài nguyên người nhận không còn quyền xem, **When** mở thông báo,
   **Then** trạng thái đọc được xử lý theo hợp đồng và backend vẫn từ chối tài nguyên đích.

---

### User Story 2 - Đăng thông báo chính thức (Priority: P1)

Là quản trị viên hoặc trưởng khoa, tôi soạn bản nháp và công bố thông báo đúng phạm vi để
người dùng liên quan nhận thông tin chính thức của trường/khoa.

**Why this priority**: Đây là phần được nêu trực tiếp trong REQ-29 và REQ-30.

**Independent Test**: ADMIN công bố một thông báo toàn trường và DEAN công bố một thông báo
của khoa; kiểm chứng đúng tập tài khoản đang hoạt động nhận được và thao tác trái quyền bị chặn.

**Acceptance Scenarios**:

1. **Given** ADMIN có bản nháp hợp lệ, **When** công bố cấp trường,
   **Then** mỗi tài khoản đang hoạt động trong phạm vi nhận đúng một thông báo.
2. **Given** DEAN thuộc Khoa Công nghệ thông tin, **When** công bố cấp khoa,
   **Then** chỉ tài khoản đang hoạt động thuộc khoa đó nhận thông báo.
3. **Given** DEAN cố chọn khoa ngoài phạm vi hoặc người dùng thường gọi endpoint công bố,
   **When** request được xử lý, **Then** backend từ chối và không tạo người nhận nào.
4. **Given** thông báo đã công bố, **When** người đăng cố sửa hoặc công bố lại,
   **Then** hệ thống từ chối và không tạo thông báo trùng.

---

### User Story 3 - Nhận cảnh báo theo sự kiện nghiệp vụ (Priority: P2)

Là sinh viên hoặc giảng viên, tôi tự động nhận thông báo khi một sự kiện quan trọng trong
đăng ký đề tài, phân công chấm hoặc công bố kết quả ảnh hưởng trực tiếp đến mình.

**Why this priority**: Phần này giảm việc người dùng phải tự dò nhiều màn hình, nhưng là mở
rộng do người dùng duyệt chứ không được REQ-29/REQ-30 quy định rõ.

**Independent Test**: Kích hoạt riêng từng sự kiện trong ma trận, kiểm chứng đúng người nhận,
đúng liên kết, không gửi cho người ngoài phạm vi và không trùng khi retry.

**Acceptance Scenarios**:

1. **Given** trưởng khoa chấp nhận một đăng ký, **When** transaction hoàn tất,
   **Then** mỗi sinh viên trong snapshot đăng ký nhận đúng một thông báo.
2. **Given** thao tác nghiệp vụ bị rollback, **When** kiểm tra hộp thông báo,
   **Then** không có thông báo phát sinh từ thao tác thất bại.
3. **Given** hai request đồng thời xử lý cùng một quyết định, **When** chỉ một quyết định hợp lệ,
   **Then** mỗi người nhận có tối đa một thông báo cho sự kiện đó.
4. **Given** kết quả được công bố, **When** sinh viên thuộc snapshot mở thông báo,
   **Then** sinh viên đến đúng trang kết quả được phép xem; sinh viên khác không nhận và không xem được.

---

### User Story 4 - Quản lý trạng thái đã đọc (Priority: P2)

Là người nhận, tôi lọc thông báo chưa đọc, đánh dấu một mục hoặc tất cả là đã đọc để dễ theo dõi.

**Why this priority**: Trạng thái đọc làm badge có ý nghĩa và giúp danh sách không trở nên rối.

**Independent Test**: Với tập thông báo đã đọc/chưa đọc hỗn hợp, dùng từng thao tác và kiểm chứng
chỉ dữ liệu của tài khoản hiện tại thay đổi, badge cập nhật đúng sau redirect.

**Acceptance Scenarios**:

1. **Given** một thông báo chưa đọc, **When** người nhận đánh dấu đã đọc,
   **Then** thời điểm đọc được lưu một lần và số chưa đọc giảm một.
2. **Given** tài khoản có nhiều thông báo chưa đọc, **When** chọn “Đánh dấu tất cả đã đọc”,
   **Then** mọi mục hiện có của tài khoản được đánh dấu và tài khoản khác không đổi.
3. **Given** bộ lọc “Chưa đọc”, **When** tải trang,
   **Then** chỉ thông báo chưa đọc của tài khoản hiện tại được trả về theo phân trang.

### Edge Cases

- Người nhận bị vô hiệu hóa sau khi đã nhận: dữ liệu được giữ, nhưng tài khoản không thể đăng nhập;
  không tự xóa lịch sử.
- Người nhận đổi vai trò/khoa sau khi thông báo đã công bố: tập người nhận đã chốt không thay đổi.
- Tài nguyên đích bị xóa hoặc người nhận mất quyền: vẫn xem được nội dung tóm tắt thông báo,
  nhưng truy cập tài nguyên đích trả 404/403 theo chính sách hiện có.
- Badge lớn hơn 99 hiển thị `99+`, trong khi trang danh sách vẫn dùng số và phân trang thực tế.
- Tiêu đề/nội dung chỉ có khoảng trắng, link ngoài hệ thống, ID người nhận do client gửi lên,
  request thiếu CSRF hoặc IDOR đều bị từ chối trước khi ghi dữ liệu.
- Hai thao tác “đánh dấu đã đọc” đồng thời là idempotent và không làm sai số chưa đọc.
- Công bố tới tập người nhận rỗng bị từ chối với thông báo rõ ràng thay vì tạo bản công bố không ai nhận.

## Requirements

### Functional Requirements

- **FR-001**: Hệ thống PHẢI cung cấp hộp thông báo riêng cho từng tài khoản đã xác thực.
- **FR-002**: Hệ thống PHẢI hiển thị số chưa đọc và tối đa năm mục gần nhất trong chuông header.
- **FR-003**: Hệ thống PHẢI cung cấp trang danh sách phân trang, sắp xếp mới nhất trước và lọc
  theo `ALL`/`UNREAD`, nhóm loại `ANNOUNCEMENT`/`ACADEMIC_EVENT`.
- **FR-004**: Người nhận PHẢI có thể đánh dấu một thông báo hoặc toàn bộ thông báo của chính mình
  là đã đọc bằng thao tác thay đổi trạng thái có CSRF.
- **FR-005**: Chỉ ADMIN được tạo/công bố thông báo cấp trường; chỉ DEAN được tạo/công bố thông báo
  cấp đúng khoa mình quản lý. Client không được quyết định phạm vi quyền.
- **FR-006**: Bản nháp PHẢI có tiêu đề, nội dung, nguồn và phạm vi hợp lệ; chỉ bản nháp được sửa.
- **FR-007**: Khi công bố, hệ thống PHẢI chốt các tài khoản đang hoạt động trong phạm vi và tạo
  đúng một bản nhận cho mỗi tài khoản trong cùng transaction.
- **FR-008**: Hệ thống PHẢI tạo thông báo cho đúng người nhận của từng sự kiện trong ma trận đã duyệt.
- **FR-009**: Tạo thông báo sự kiện PHẢI cùng transaction với thay đổi nguồn; thất bại ở một phần
  PHẢI rollback toàn bộ.
- **FR-010**: Hệ thống PHẢI chống trùng bằng một khóa sự kiện ổn định và ràng buộc duy nhất cho
  cặp sự kiện–người nhận, kể cả khi retry hoặc xử lý đồng thời.
- **FR-011**: Tiêu đề, nội dung hiển thị, loại, người nhận và đường dẫn đích PHẢI do server tạo;
  không nhận người nhận hoặc URL tùy ý từ client.
- **FR-012**: Mọi truy vấn, đánh dấu đọc và điều hướng PHẢI kiểm tra tài khoản hiện tại tại backend;
  URL thông báo không thay thế kiểm tra quyền của tài nguyên đích.
- **FR-013**: Hệ thống PHẢI lưu thời điểm tạo, thời điểm đọc, loại, nguồn sự kiện và người nhận
  đủ để kiểm thử truy vết nhưng không lưu secrets hoặc dữ liệu nhạy cảm không cần thiết.
- **FR-014**: Thông báo đã tạo KHÔNG tự động bị xóa trong phiên bản đầu.
- **FR-015**: Giao diện PHẢI có trạng thái rỗng, badge có chữ/accessible name, thao tác bàn phím,
  thông báo kết quả sau redirect và không biểu đạt trạng thái chỉ bằng màu.

### Authorization Matrix

| Khả năng | ADMIN | DEAN | LECTURER | STUDENT |
|---|---:|---:|---:|---:|
| Xem/đọc thông báo của chính mình | Có | Có | Có | Có |
| Đăng thông báo cấp trường | Có | Không | Không | Không |
| Đăng thông báo cấp khoa thuộc phạm vi | Không | Có | Không | Không |
| Xem bản nháp do mình được phép quản lý | Có | Có, đúng khoa | Không | Không |
| Chọn trực tiếp người nhận/URL tùy ý | Không | Không | Không | Không |

### Key Entities

- **Official Announcement**: Nội dung chính thức của trường/khoa; có bản nháp và thời điểm công bố.
- **Notification Event**: Một lần phát sinh thông tin cần gửi; có loại, nội dung hiển thị,
  khóa chống trùng, đích nội bộ và nguồn nghiệp vụ.
- **User Notification**: Bản nhận của một tài khoản cho một sự kiện; lưu trạng thái/thời điểm đọc.
- **Recipient Scope**: Quy tắc server dùng để suy ra tập tài khoản tại thời điểm công bố hoặc phát sinh.

## Success Criteria

- **SC-001**: 100% thông báo chính thức thử nghiệm đến đúng tài khoản đang hoạt động trong phạm vi,
  không đến tài khoản ngoài phạm vi.
- **SC-002**: 100% sự kiện trong ma trận tạo đúng một thông báo/người nhận khi commit và không tạo
  thông báo khi transaction rollback.
- **SC-003**: Các kiểm thử retry/đồng thời không tạo cặp sự kiện–người nhận trùng.
- **SC-004**: Mọi thử nghiệm truy cập chéo tài khoản hoặc sai khoa bị backend từ chối và không làm
  thay đổi dữ liệu.
- **SC-005**: Dropdown và trang đầu hộp thông báo hiển thị trong không quá 2 giây với dữ liệu kiểm thử
  2.000 tài khoản và 100.000 bản nhận trên môi trường kiểm thử dự án.
- **SC-006**: Người dùng hoàn thành việc xem và đánh dấu một thông báo trong tối đa ba thao tác từ
  bất kỳ trang đã đăng nhập nào.

## Assumptions and Boundaries

- Tái sử dụng xác thực, vai trò, scope khoa, Thymeleaf/Tabler và MySQL hiện có.
- `announcements` hiện có là nền cho nội dung chính thức; thay đổi schema phải dùng migration Flyway mới.
- Phiên bản đầu phục vụ Khoa Công nghệ thông tin, không thêm chức năng quản trị danh mục khoa.
- Không có realtime push: badge mới xuất hiện ở lần tải trang/request kế tiếp.
- Không có email, SMS, mobile push, WebSocket, message broker, scheduler nhắc hạn, đính kèm,
  bình luận, tùy chọn tắt loại thông báo, xóa/khôi phục hoặc thống kê tỷ lệ đọc.
- PN-01–PN-12 đã được duyệt; triển khai phải tuân thủ Git Flow và từng task/commit trong `AGENTS.md`.

## Traceability

| Nguồn | Phạm vi |
|---|---|
| REQ-29, REQ-30 | User Story 1–2, FR-001–007, FR-012–015 |
| Q-22 + PN-01–PN-12 (duyệt 2026-09-24) | Kênh, người đăng, đối tượng nhận, trạng thái đọc, vòng đời |
| Ma trận sự kiện (duyệt 2026-09-24) | User Story 3–4, FR-008–011 |
| Constitution III, IV, VI, VII, VIII, IX, X | Quyền backend, validation, transaction/idempotency, migration, test, bằng chứng |
