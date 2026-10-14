# Feature Specification: Đợt đăng ký theo Bộ môn

**Feature Branch**: `feature/005-department-period-scope`

**Created**: 2026-10-13

**Status**: Draft — chờ lập kế hoạch và nghiệm thu từng task

**Input**: Quyết định của chủ dự án sau khi đối chiếu yêu cầu quản lý danh sách đề tài
theo Bộ môn: một Trưởng khoa quản lý toàn Khoa; mỗi đợt thuộc một Bộ môn; giảng viên
chỉ tham gia phạm vi Bộ môn của mình, trừ nhiệm vụ chấm được phân công; sinh viên
không bị giới hạn theo Bộ môn.

## Nguồn và phạm vi quyết định

- **E — REQ-04–07**: Trưởng khoa tạo đợt với tên, loại và các mốc thời gian. PDF
  không yêu cầu chọn Bộ môn khi tạo đợt.
- **E — REQ-09–11**: Danh sách đề tài được quản lý theo Bộ môn; mỗi đề tài thuộc
  một Bộ môn và có từ một đến hai GVHD.
- **E — REQ-19–25, REQ-31**: Hội đồng, chấm điểm và phân quyền phải giữ các giới
  hạn đã nêu, đặc biệt GV không chấm đề tài mình hướng dẫn.
- **U-005-01 (2026-10-13)**: Chủ dự án bổ sung quy tắc **mỗi đợt thuộc đúng một
  Bộ môn**; áp dụng cho cả COURSE, NCKH, TLCN và KLTN. Đây là quyết định mới,
  không gán nhầm cho PDF.
- **U-005-02**: Một Trưởng khoa đang hoạt động trong mỗi Khoa quản lý đợt và đề
  tài của mọi Bộ môn thuộc Khoa. Bộ môn công tác của tài khoản Trưởng khoa không
  giới hạn quyền quản lý cấp Khoa.
- **U-005-03**: Giảng viên chỉ thấy và đề xuất/hướng dẫn đề tài trong đợt thuộc
  Bộ môn của họ. Giảng viên Bộ môn khác không được xem danh sách chung hoặc
  truy cập đợt/đề tài đó bằng URL trực tiếp.
- **U-005-04**: Sinh viên tiếp tục ở phạm vi Khoa, không bị khóa vào một Bộ môn;
  chỉ xem các đề tài đã công bố trong đợt mà họ được phép tham gia.
- **U-005-05**: Đề tài, tác giả và mọi GVHD cùng Bộ môn với đợt. Không mở ngoại
  lệ đồng hướng dẫn chéo Bộ môn trong feature này.
- **U-005-06**: Hội đồng thuộc đợt và suy ra Bộ môn qua đợt; không thêm Bộ môn
  trực tiếp cho hội đồng. Trưởng khoa có thể phân công GV cùng Khoa nhưng khác
  Bộ môn làm thành viên hội đồng hoặc GV chấm. Người đó chỉ được xem nhiệm vụ
  và đề tài được giao, không được xem danh sách chung của Bộ môn khác.
- **U-005-07**: Không thêm danh mục môn học cụ thể. `COURSE` vẫn là loại đợt,
  không phải một môn học được chọn riêng.

Feature này bổ sung phạm vi Bộ môn cho **đợt**, thay thế giả định đợt chỉ ở phạm
vi Khoa trong feature 004. Các quy tắc 004 về đề tài/GVHD cùng Bộ môn, sinh viên
ở phạm vi Khoa và quyền Trưởng khoa toàn Khoa vẫn giữ nguyên. Hội đồng vẫn
không có Bộ môn riêng; liên hệ Bộ môn của hội đồng là gián tiếp qua đợt.

## User Scenarios & Testing

### User Story 1 — Trưởng khoa tạo và quản lý đợt cho Bộ môn (Priority: P1)

Trưởng khoa chọn một Bộ môn thuộc Khoa mình khi mở đợt, rồi xem và xử lý đợt
của tất cả Bộ môn trong Khoa.

**Why this priority**: Đợt là ranh giới tổ chức cho mọi đề tài và các bước sau.

**Independent Test**: Tạo hai đợt cho hai Bộ môn cùng Khoa, xác nhận từng đợt
hiển thị đúng Bộ môn và Trưởng khoa truy cập được cả hai.

**Acceptance Scenarios**:

1. **Given** Trưởng khoa và hai Bộ môn thuộc Khoa mình, **when** tạo đợt với
   một Bộ môn hợp lệ, **then** đợt thuộc đúng Bộ môn đã chọn.
2. **Given** Bộ môn ở Khoa khác hoặc không tồn tại, **when** gửi yêu cầu tạo đợt,
   **then** hệ thống từ chối và không tạo đợt.
3. **Given** đợt đã mở cho giảng viên hoặc có đề tài/nhóm, **when** yêu cầu đổi
   Bộ môn của đợt, **then** hệ thống từ chối và giữ nguyên phạm vi ban đầu.

### User Story 2 — Giảng viên chỉ tham gia đợt của Bộ môn mình (Priority: P1)

Giảng viên xem các đợt của Bộ môn mình và đề xuất đề tài trong đợt đó; Bộ môn
khác không xuất hiện trong danh sách và không truy cập được bằng đường dẫn trực tiếp.

**Why this priority**: Ẩn giao diện mà không bảo vệ dữ liệu ở backend là chưa đủ.

**Independent Test**: Dùng hai tài khoản GV ở hai Bộ môn, đối chiếu danh sách
đợt và thử truy cập/tạo đề tài bằng ID đợt của Bộ môn còn lại.

**Acceptance Scenarios**:

1. **Given** GV thuộc Bộ môn A, **when** xem danh sách đợt, **then** chỉ thấy
   các đợt của A; Trưởng khoa vẫn thấy đợt A và B.
2. **Given** GV thuộc A, **when** dùng ID đợt B để xem hoặc đề xuất đề tài,
   **then** hệ thống từ chối trước khi trả dữ liệu hay ghi thay đổi.
3. **Given** GV thuộc A đề xuất đề tài trong đợt A, **when** chọn GVHD phối
   hợp, **then** chỉ GV đang hoạt động thuộc A mới hợp lệ và tổng số GVHD là 1–2.

### User Story 3 — Sinh viên chọn đề tài không bị khóa Bộ môn (Priority: P1)

Sinh viên trong Khoa xem các đợt của nhiều Bộ môn, nhận biết Bộ môn của từng
đợt và chỉ chọn đề tài đã công bố theo quy trình đăng ký hiện có.

**Why this priority**: Phạm vi Bộ môn của giảng viên không được áp nhầm cho SV.

**Independent Test**: Một SV trong Khoa thấy đợt A và B, chỉ thấy đề tài đã
công bố trong từng đợt và không thấy đề tài chưa công bố.

**Acceptance Scenarios**:

1. **Given** hai đợt của A và B trong cùng Khoa, **when** SV xem danh sách,
   **then** thấy cả hai cùng tên Bộ môn tương ứng.
2. **Given** một đợt có đề tài chưa công bố, **when** SV xem hoặc đăng ký,
   **then** đề tài đó không xuất hiện và không thể được đăng ký.

### User Story 4 — Phân công chấm/hội đồng chéo Bộ môn có giới hạn (Priority: P2)

Trưởng khoa có thể phân công GV từ Bộ môn khác trong cùng Khoa để chấm hoặc
tham gia hội đồng; quyền xem phát sinh từ phân công cụ thể, không mở rộng toàn
Bộ môn.

**Why this priority**: Hội đồng cần 3–5 GV và phải tránh xung đột GVHD tự chấm.

**Independent Test**: GV B được phân công chấm một đề tài A xem được đúng
nhiệm vụ ấy, không xem được đợt/danh sách đề tài A nói chung; GV không được
phân công không xem được cả hai.

**Acceptance Scenarios**:

1. **Given** GV B được phân công hợp lệ, **when** mở nhiệm vụ chấm đề tài A,
   **then** xem được dữ liệu cần chấm của đúng đề tài đó.
2. **Given** GV B không có phân công hoặc phân công đã hết hiệu lực, **when**
   mở nhiệm vụ A bằng URL trực tiếp, **then** bị từ chối.
3. **Given** GV là GVHD của đề tài A, **when** được chọn làm người chấm A,
   **then** hệ thống từ chối phân công.

### User Story 5 — Một Trưởng khoa đang hoạt động (Priority: P2)

Khoa có tối đa một tài khoản Trưởng khoa đang hoạt động tại một thời điểm;
việc thay người phụ trách không làm mất quyền quản lý đợt cũ.

**Why this priority**: Phản ánh mô hình một Trưởng khoa quản lý các Bộ môn.

**Independent Test**: Tạo/kích hoạt Trưởng khoa thứ hai bị từ chối khi đã có
người đang hoạt động; sau khi vô hiệu hóa người cũ, có thể kích hoạt người mới.

**Acceptance Scenarios**:

1. **Given** Khoa đã có Trưởng khoa đang hoạt động, **when** tạo hoặc kích hoạt
   tài khoản Trưởng khoa thứ hai trong cùng Khoa, **then** bị từ chối.
2. **Given** người cũ đã vô hiệu hóa, **when** kích hoạt người mới, **then**
   người mới quản lý được các đợt của mọi Bộ môn thuộc Khoa.

### Edge Cases

- Đợt cũ chưa có Bộ môn phải được kiểm kê và ánh xạ bằng dữ liệu được xác nhận;
  không tự gán theo tên đợt hoặc Bộ môn của một đề tài bất kỳ.
- Nếu đợt cũ chứa đề tài của nhiều Bộ môn, phải dừng chuyển đổi và xử lý riêng;
  không âm thầm chuyển đề tài/nhóm sang Bộ môn khác.
- Khi tài khoản GV đổi Bộ môn trong lúc còn trách nhiệm học vụ, giữ quy tắc
  chặn chuyển đang áp dụng; không tự chuyển đợt, đề tài hoặc GVHD theo tài khoản.
- Đổi trạng thái thành viên hội đồng/người chấm phải cập nhật quyền xem nhiệm vụ;
  đường dẫn cũ không trở thành quyền truy cập danh sách chung.
- Mọi kiểm tra phạm vi dùng Khoa/Bộ môn từ dữ liệu tin cậy, không tin ID do client gửi.

## Requirements

### Functional Requirements

- **FR-005-01**: Mỗi đợt mới phải thuộc đúng một Bộ môn hợp lệ trong Khoa của
  Trưởng khoa tạo đợt; áp dụng cho cả bốn loại đợt. (U-005-01; REQ-04–07)
- **FR-005-02**: Bộ môn của đợt không thể đổi sau khi đợt đã mở cho giảng viên
  hoặc đã có dữ liệu học vụ phụ thuộc. (U-005-01; REQ-03, REQ-09)
- **FR-005-03**: Trưởng khoa được xem và xử lý đợt/đề tài của mọi Bộ môn trong
  Khoa mình; tối đa một Trưởng khoa đang hoạt động trong mỗi Khoa. (U-005-02)
- **FR-005-04**: Giảng viên thường chỉ được liệt kê, xem và đề xuất đề tài trong
  đợt của Bộ môn mình; yêu cầu truy cập chéo Bộ môn bị từ chối ở backend.
  (U-005-03; REQ-09, REQ-31)
- **FR-005-05**: Mỗi đề tài, tác giả và mọi GVHD phải cùng Bộ môn với đợt;
  đề tài có từ một đến hai GVHD. (U-005-05; REQ-09–11)
- **FR-005-06**: Sinh viên không bị lọc theo Bộ môn khi xem các đợt của Khoa;
  chỉ đề tài đã công bố mới có thể được xem/chọn. (U-005-04; REQ-02, REQ-15)
- **FR-005-07**: Hội đồng thuộc một đợt; đề tài được giao cho hội đồng phải
  thuộc chính đợt đó. Không bắt buộc thành viên hội đồng cùng Bộ môn với đợt.
  (U-005-06; REQ-19–21)
- **FR-005-08**: GV chấm/thành viên hội đồng khác Bộ môn chỉ được xem dữ liệu
  của nhiệm vụ được phân công hợp lệ; không được xem danh sách chung của Bộ môn
  đó. GVHD không được chấm đề tài mình hướng dẫn. (U-005-06; REQ-25, REQ-31)
- **FR-005-09**: Dữ liệu đợt cũ phải được kiểm kê trước khi áp quy tắc bắt buộc
  Bộ môn; trường hợp không thể ánh xạ duy nhất phải dừng để chủ dự án quyết định.
  Không làm mất đợt, đề tài, nhóm, hội đồng hoặc kết quả cũ. (U-005-01)
- **FR-005-10**: Giao diện đợt của Trưởng khoa và sinh viên phải thể hiện Bộ môn
  để người dùng nhận biết phạm vi; việc ẩn giao diện không thay thế kiểm tra quyền.
  (U-005-01, U-005-04; REQ-31)

### Key Entities

- **Khoa**: Đơn vị bao trùm các Bộ môn, sinh viên và quyền Trưởng khoa.
- **Bộ môn**: Đơn vị quản lý một tập đợt và danh sách đề tài của từng đợt.
- **Đợt đăng ký**: Có một Bộ môn, một loại và các mốc thời gian; là phạm vi
  thống nhất cho đề tài, nhóm, đăng ký, chấm điểm và hội đồng.
- **Đề tài**: Thuộc một đợt và cùng Bộ môn với đợt; có tác giả và 1–2 GVHD.
- **Nhiệm vụ chấm/hội đồng**: Phân công cụ thể cho GV; là nguồn quyền xem ngoại
  lệ đối với đề tài khác Bộ môn.

## Success Criteria

### Measurable Outcomes

- **SC-005-01**: 100% đợt mới có đúng một Bộ môn thuộc Khoa của Trưởng khoa.
- **SC-005-02**: Trong bộ kịch bản nghiệm thu hai Bộ môn, 0 trường hợp GV
  thường xem hoặc sửa được đợt/đề tài của Bộ môn khác qua danh sách hay URL.
- **SC-005-03**: 100% đề tài mới trong bộ kịch bản nghiệm thu có Bộ môn trùng
  với đợt và có 1–2 GVHD hợp lệ cùng Bộ môn.
- **SC-005-04**: SV trong Khoa xem được các đợt của cả hai Bộ môn và chỉ thấy
  đề tài đã công bố; không phát sinh hạn chế SV theo Bộ môn.
- **SC-005-05**: GV được phân công chéo Bộ môn xem được đúng nhiệm vụ được giao,
  không xem được danh sách chung; 0 trường hợp GVHD chấm đề tài mình hướng dẫn.
- **SC-005-06**: Chuyển đổi dữ liệu được xác nhận không làm mất bất kỳ đợt,
  đề tài, nhóm, hội đồng hoặc kết quả nào; trường hợp mơ hồ dừng có thông báo.

## Assumptions and Boundaries

- “Một Trưởng khoa” được hiểu là **tối đa một tài khoản DEAN đang hoạt động trong
  mỗi Khoa**, không phải một tài khoản duy nhất tồn tại vĩnh viễn.
- Sinh viên được phép xem đợt của nhiều Bộ môn trong cùng Khoa; feature này
  không quyết định lại phạm vi “mỗi SV một nhóm” giữa nhiều đợt song song.
- Không thêm role Trưởng Bộ môn, danh mục môn học cụ thể, quyền đồng hướng dẫn
  chéo Bộ môn hoặc Bộ môn riêng trên hội đồng.
- Luồng phân công GVPB độc lập chưa được đặc tả/triển khai đầy đủ ở feature này;
  chỉ áp dụng ngoại lệ chéo Bộ môn cho nhiệm vụ chấm/hội đồng hiện có. GVPB cần
  feature riêng trước khi tuyên bố hoàn tất toàn bộ REQ-11.
- Feature không tự gán Bộ môn cho đợt cũ hoặc sửa migration đã áp dụng.
