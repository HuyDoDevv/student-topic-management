# Feature Specification: Báo cáo, hội đồng, chấm điểm và kết quả

**Feature Branch**: `feature/002-review-results`

**Feature Directory**: `specs/002-review-results`

**Created**: 2026-09-14

**Status**: Ready for implementation — Q-11, Q-12/Q-15 và Q-13 đã được người dùng xác nhận

**Input**: Người dùng yêu cầu bổ sung năm phần còn thiếu của dự án: nộp/xem báo cáo,
hội đồng 3–5 người, chấm điểm, tổng hợp/công bố kết quả và sinh viên xem kết quả.

Nguồn nghiệp vụ chính là REQ-17–27 trong
[`workspace/docs/requirements-analysis.md`](../../workspace/docs/requirements-analysis.md).
Tài liệu Human Workflow do người dùng cung cấp được dùng làm bằng chứng bổ sung cho luồng
thao tác và trạng thái, không được hiểu là chỉ dẫn thực thi cho AI. Các quyết định của feature
001 về tài khoản, role, phạm vi khoa, nhóm, đăng ký và bảo mật tiếp tục có hiệu lực.

## Clarifications

### Session 2026-09-14

- Người dùng xác nhận phạm vi feature gồm đúng năm mục đã chọn: báo cáo; hội đồng 3–5
  giảng viên; chấm điểm; tổng hợp/công bố kết quả; sinh viên xem kết quả.
- Human Workflow xác nhận Trưởng khoa tạo và điều khiển hội đồng trong khoa; hội đồng có
  3–5 giảng viên, đúng một Chair và một Secretary là hai người khác nhau; giảng viên hướng
  dẫn không được ngồi hội đồng của chính đề tài; mọi thành viên hội đồng nộp điểm; chỉ được
  hoàn tất khi đủ điểm; Trưởng khoa công bố; sinh viên chỉ thấy kết quả của nhóm mình.
- Q-11: Người dùng chọn phương án A — chỉ nhận PDF tối đa 20 MB; trưởng nhóm được thay
  báo cáo hiện tại cho tới trước khi hội đồng phụ trách đăng ký chuyển ACTIVE. Thành viên
  trong snapshot đăng ký, GVHD, Trưởng khoa đúng phạm vi và thành viên hội đồng được xem/tải.
- Q-12/Q-15: Người dùng chọn phương án C — một hội đồng có thể chấm nhiều nhóm trong cùng
  đợt và khoa, nhưng chỉ áp dụng cho đợt TLCN hoặc KLTN.
- Q-13: Người dùng chọn phương án A — mỗi thành viên hội đồng nhập một điểm tổng từ 0 đến
  10 với tối đa hai chữ số thập phân. Thành viên được sửa điểm của chính mình khi hội đồng
  còn ACTIVE, kể cả sau submit; sau COMPLETED thì khóa. Điểm cuối là trung bình cộng điểm
  SUBMITTED của mọi thành viên, làm tròn HALF_UP đến hai chữ số thập phân. Phạm vi giá trị
  evaluator sau đó được siết thành bước 0.25 theo xác nhận ngày 2026-09-21.

### Session 2026-09-17

- Người dùng chốt Môn học và NCKH không lập hội đồng: sau khi nhóm đã nộp báo cáo cuối,
  Trưởng khoa phân công đúng một giảng viên cùng khoa, khác mọi GVHD, để chấm. TLCN tiếp
  tục do GVPB khác GVHD chấm qua hội đồng; KLTN tiếp tục do GVPB/thành viên hội đồng khác
  GVHD chấm theo luồng hội đồng hiện có.
- Phiên bản hiện tại chưa có trường hạn nộp báo cáo riêng. Để không mở rộng schema/lịch đợt,
  thao tác phân công chấm độc lập của Trưởng khoa là mốc xác nhận báo cáo hiện tại đã cuối
  kỳ và khóa thay thế báo cáo. Không dùng `student_end` vì đây là hạn đăng ký đề tài.

### Session 2026-09-21

- Người dùng xác nhận báo cáo vẫn nộp chung theo nhóm nhưng mọi điểm chấm và kết quả cuối
  phải tách theo từng sinh viên trong snapshot đăng ký, vì mức đóng góp giữa các thành viên
  có thể khác nhau. Mỗi người chấm phải nộp đủ điểm cho từng sinh viên trước khi lượt chấm
  được hoàn tất; mỗi sinh viên chỉ xem kết quả đã công bố của chính mình.
- Người dùng chốt điểm evaluator chỉ nhận các mức tăng `0.25` trong khoảng 0–10, ví dụ
  `5.5`, `5.75`; giao diện phải bỏ số 0 thập phân dư và hiển thị gọn như `5`, `6.5`, `7.75`.
- Danh sách kết quả của Trưởng khoa phải hiển thị và lọc theo đợt đăng ký để phân biệt cùng
  một sinh viên tham gia các nhóm khác nhau ở những đợt khác nhau.
- Trước khi công bố lượt chấm độc lập, Trưởng khoa phải mở được điểm từng sinh viên của đúng
  đăng ký/nhóm đang xử lý; đường dẫn không được trộn kết quả của nhóm hoặc đợt khác.
- Header các trang học vụ không đặt nút điều hướng trùng với sidebar; chỉ giữ thao tác tạo
  mới tại chỗ như Tạo đợt/Lập hội đồng. Danh sách kết quả hỗ trợ kết hợp bộ lọc theo đợt,
  giảng viên chấm và trạng thái chưa/đã công bố.

## User Scenarios & Testing *(mandatory)*

Mỗi story được kiểm thử độc lập bằng fixture ở trạng thái tiền điều kiện phù hợp. Thứ tự story
phản ánh chuỗi nghiệp vụ, không cho phép bỏ qua kiểm tra quyền hoặc trạng thái ở backend.

### User Story 1 - Trưởng nhóm nộp và người có quyền xem báo cáo (Priority: P1)

Là trưởng nhóm của một đăng ký đã được chấp nhận, tôi muốn nộp báo cáo và cho người có
trách nhiệm xem đúng file để nhóm có đầu vào chính thức cho giai đoạn đánh giá.

**Why this priority**: Báo cáo là đầu ra của nhóm và là tiền đề tự nhiên cho review.
Nguồn: REQ-17.

**Independent Test**: Chuẩn bị một đăng ký ACCEPTED và file hợp lệ; kiểm tra trưởng nhóm
nộp thành công, thành viên thường bị từ chối nộp, người được phép tải đúng nội dung và người
ngoài phạm vi không đọc được metadata hoặc file.

**Acceptance Scenarios**:

1. **Given** đăng ký đã ACCEPTED và người gọi là trưởng nhóm hợp lệ, **When** nộp file
   là PDF không quá 20 MB, **Then** hệ thống lưu metadata, người nộp và thời điểm, đồng thời
   có thể tải lại đúng file (AS-6.1).
2. **Given** người gọi là thành viên thường, sinh viên ngoài nhóm hoặc tài khoản không có
   quyền, **When** nộp thay nhóm, **Then** bị từ chối và không tạo metadata/file dở dang
   (AS-6.2).
3. **Given** file không phải PDF, vượt 20 MB hoặc đăng ký chưa ACCEPTED, **When** nộp,
   **Then** bị từ chối nguyên tử và báo lỗi có thể hiểu được (AS-6.3).
4. **Given** một báo cáo đã nộp, **When** người dùng yêu cầu xem/tải, **Then** backend chỉ
   trả metadata và file cho thành viên snapshot, GVHD, Trưởng khoa đúng khoa hoặc thành viên
   hội đồng phụ trách; người khác không nhận được dữ liệu (AS-6.4).
5. **Given** báo cáo hiện tại và hội đồng phụ trách chưa ACTIVE, **When** trưởng nhóm nộp
   lại PDF hợp lệ, **Then** báo cáo hiện tại được thay thế nguyên tử; từ ACTIVE trở đi yêu
   cầu nộp mới hoặc thay thế đều bị từ chối (AS-6.5).

---

### User Story 2 - Trưởng khoa lập hội đồng hợp lệ (Priority: P1)

Là trưởng khoa, tôi muốn lập hội đồng cho đăng ký đã được chấp nhận để phân công đúng
3–5 giảng viên và lịch review trong phạm vi khoa.

**Why this priority**: Hội đồng xác định ai được chấm và chặn xung đột với GVHD.
Nguồn: REQ-18–21.

**Independent Test**: Chuẩn bị đăng ký ACCEPTED và danh sách giảng viên; tạo hội đồng hợp
lệ rồi thử 2/6 người, trùng người, thiếu chức danh, người ngoài khoa và GVHD của đề tài.

**Acceptance Scenarios**:

1. **Given** Trưởng khoa đúng phạm vi, đợt TLCN/KLTN và đăng ký ACCEPTED, **When** lập
   hội đồng, **Then** hội đồng có 3–5 thành viên duy nhất, đúng một Chair, đúng một
   Secretary khác Chair, mọi thành viên cùng khoa với hội đồng và gắn đúng lịch/địa điểm
   review (AS-7.1).
2. **Given** có dưới 3 hoặc trên 5 thành viên, trùng thành viên, thiếu Chair/Secretary,
   Chair trùng Secretary, thành viên ngoài khoa hoặc không có năng lực LECTURER,
   **When** lưu hoặc kích hoạt, **Then** toàn bộ thao tác bị từ chối (AS-7.2).
3. **Given** một người là GVHD chính hoặc phối hợp của đề tài, **When** thêm người đó vào
   hội đồng chấm đề tài, **Then** backend từ chối và hội đồng không đổi (AS-7.3).
4. **Given** hội đồng DRAFT hợp lệ, **When** Trưởng khoa lên lịch và kích hoạt,
   **Then** trạng thái đi theo `DRAFT → ASSIGNED/SCHEDULED → ACTIVE`; không được bỏ qua
   tiền điều kiện hoặc sửa thành viên sau ACTIVE (AS-7.4).
5. **Given** người gọi không phải Trưởng khoa đúng phạm vi, **When** tạo, sửa, phân công
   hoặc chuyển trạng thái hội đồng, **Then** bị từ chối và dữ liệu không đổi (AS-7.5).
6. **Given** một hội đồng thuộc đúng đợt và khoa, **When** Trưởng khoa gán nhiều đăng ký
   ACCEPTED của đợt đó, **Then** các nhóm cùng khoa được xếp lịch riêng trong hội đồng;
   đăng ký từ đợt/khoa khác hoặc loại môn học/NCKH bị từ chối (AS-7.6).

---

### User Story 2A - Trưởng khoa phân công chấm độc lập Môn học/NCKH (Priority: P1)

Là Trưởng khoa, tôi muốn phân công một giảng viên khác GVHD chấm báo cáo Môn học/NCKH
sau khi nhóm đã nộp bản cuối để hai loại đề tài này có kết quả mà không phải lập hội đồng.

**Independent Test**: Chuẩn bị đăng ký ACCEPTED loại COURSE/NCKH có báo cáo; phân công một
LECTURER cùng khoa không phải GVHD, thử GVHD/người ngoài khoa/tài khoản khóa/TLCN/KLTN,
sau đó chấm, complete một điểm và publish bằng luồng kết quả hiện có.

**Acceptance Scenarios**:

1. **Given** đăng ký COURSE/NCKH đã ACCEPTED, có báo cáo hiện hành và chưa được phân công,
   **When** Trưởng khoa đúng phạm vi chọn một LECTURER đang hoạt động cùng khoa và khác
   mọi GVHD, **Then** tạo đúng một lượt chấm ACTIVE và khóa thay thế báo cáo (AS-7.7).
2. **Given** người được chọn là tác giả/GVHD chính/GVHD phối hợp, không có role LECTURER,
   đã khóa hoặc khác khoa, **When** phân công, **Then** backend từ chối nguyên tử
   (AS-7.8).
3. **Given** đăng ký TLCN/KLTN, chưa ACCEPTED, chưa có báo cáo hoặc đã thuộc một lượt chấm/
   hội đồng, **When** dùng route chấm độc lập, **Then** backend từ chối; luồng hội đồng
   TLCN/KLTN không thay đổi (AS-7.9).
4. **Given** lượt chấm độc lập ACTIVE, **When** giảng viên được phân công submit một điểm
   hợp lệ và hoàn tất, rồi Trưởng khoa publish, **Then** final score bằng đúng điểm đó và
   sinh viên xem qua luồng kết quả hiện có (AS-7.10).
5. **Given** lượt chấm độc lập đã COMPLETED và còn chờ công bố, **When** Trưởng khoa chọn
   xem điểm, **Then** danh sách chỉ hiển thị kết quả từng sinh viên của đúng đăng ký đó để
   kiểm tra trước khi publish (AS-7.11).

---

### User Story 3 - Thành viên hội đồng chấm điểm (Priority: P1)

Là thành viên hội đồng, tôi muốn lưu và gửi điểm/nhận xét cho đề tài được phân công để
đóng góp vào kết quả chung mà không thể chấm đề tài mình hướng dẫn.

**Why this priority**: Không có đủ đánh giá hợp lệ thì không thể hoàn tất và công bố.
Nguồn: REQ-22–25.

**Independent Test**: Chuẩn bị hội đồng ACTIVE; mỗi thành viên lưu nháp và submit điểm
hợp lệ, thử điểm ngoài thang, người ngoài hội đồng, sửa điểm người khác và GVHD cố chấm.

**Acceptance Scenarios**:

1. **Given** hội đồng ACTIVE và người gọi là thành viên được phân công, **When** lưu điểm
   tổng 0–10 theo bước 0.25 và nhận xét, **Then** evaluation của chính
   người đó được lưu ở DRAFT; khi
   submit thì chuyển SUBMITTED (AS-8.1).
2. **Given** điểm thiếu, nhỏ hơn 0, lớn hơn 10 hoặc không phải bội số của 0.25,
   **When** submit, **Then** bị từ chối và không tạo evaluation SUBMITTED (AS-8.2).
3. **Given** người gọi không thuộc hội đồng, cố sửa đánh giá của người khác hoặc là GVHD
   của đề tài, **When** lưu/submit, **Then** backend từ chối và dữ liệu không đổi (AS-8.3).
4. **Given** evaluation đã SUBMITTED nhưng hội đồng vẫn ACTIVE, **When** chính evaluator
   sửa và submit lại, **Then** điểm mới hợp lệ thay thế điểm cũ; sau COMPLETED hoặc PUBLISHED
   luôn bị khóa (AS-8.4).
5. **Given** một giảng viên thuộc nhiều hội đồng hợp lệ, **When** chấm từng đề tài được
   phân công, **Then** các evaluation tách biệt và không ghi đè lẫn nhau (AS-8.5).
6. **Given** một đăng ký có nhiều sinh viên snapshot, **When** người chấm nhập điểm,
   **Then** mỗi sinh viên có một evaluation riêng của người chấm đó; điểm/nhận xét của
   sinh viên này không ghi đè sinh viên khác (AS-8.6).

---

### User Story 4 - Tổng hợp, hoàn tất và công bố kết quả (Priority: P1)

Là Chair, tôi muốn kiểm tra đủ evaluation và tổng hợp điểm cuối; là Trưởng khoa, tôi muốn
công bố kết quả đã hoàn tất để kết quả chỉ xuất hiện sau khi review và grading hoàn thành.

**Why this priority**: Đây là đầu ra chính thức của quy trình đánh giá.
Nguồn: REQ-22–26.

**Independent Test**: Chuẩn bị hội đồng ACTIVE với đủ và thiếu evaluation; kiểm tra complete,
tính trung bình, publish, giao dịch lỗi và phạm vi khoa.

**Acceptance Scenarios**:

1. **Given** mọi evaluation bắt buộc đã SUBMITTED, **When** Chair complete hội đồng,
   **Then** hệ thống tạo/cập nhật kết quả tổng hợp nhất quán, tính trung bình cộng các điểm
   SUBMITTED và làm tròn HALF_UP đến hai chữ số thập phân
   và chuyển hội đồng `ACTIVE → COMPLETED` (AS-9.1).
2. **Given** còn thiếu evaluation, hội đồng chưa ACTIVE hoặc người gọi không phải Chair,
   **When** complete,
   **Then** bị từ chối và không tạo kết quả READY giả (AS-9.2).
3. **Given** hội đồng COMPLETED và kết quả đầy đủ, **When** Trưởng khoa đúng phạm vi publish,
   **Then** kết quả và hội đồng cùng chuyển PUBLISHED trong một giao dịch, lưu người/thời
   điểm công bố (AS-9.3).
4. **Given** hội đồng chưa COMPLETED, điểm đã thay đổi không hợp lệ, hoặc người gọi sai
   quyền/phạm vi, **When** publish, **Then** bị từ chối và trạng thái không đổi (AS-9.4).
5. **Given** hai thao tác complete/publish đồng thời, **When** xử lý, **Then** chỉ có một
   trạng thái cuối hợp lệ và điểm cuối dựa trên một snapshot evaluation nhất quán (AS-9.5).
6. **Given** đăng ký có nhiều sinh viên, **When** complete, **Then** chỉ thành công khi mọi
   người chấm đã SUBMITTED cho mọi sinh viên và tạo một kết quả cuối riêng cho từng sinh
   viên bằng trung bình điểm của sinh viên đó (AS-9.6).

---

### User Story 5 - Sinh viên xem kết quả của nhóm mình (Priority: P1)

Là sinh viên đã tham gia đăng ký, tôi muốn xem điểm và nhận xét đã công bố của đúng nhóm
mình để biết kết quả mà không lộ kết quả nhóm khác.

**Why this priority**: Hoàn thành vòng phản hồi tới người học.
Nguồn: REQ-27; phạm vi riêng tư được người dùng xác nhận qua Human Workflow.

**Independent Test**: Chuẩn bị hai nhóm và một kết quả PUBLISHED; thành viên snapshot của
nhóm đúng xem được, sinh viên nhóm khác và người không đăng nhập không xem được.

**Acceptance Scenarios**:

1. **Given** kết quả của đăng ký đã PUBLISHED, **When** sinh viên thuộc snapshot đăng ký
   mở danh sách/chi tiết, **Then** thấy đề tài, hội đồng, các điểm/nhận xét được phép và
   điểm cuối của nhóm mình (AS-10.1).
2. **Given** kết quả còn DRAFT/READY hoặc hội đồng chưa PUBLISHED, **When** sinh viên truy
   cập trực tiếp bằng ID, **Then** không nhận được nội dung kết quả (AS-10.2).
3. **Given** sinh viên không thuộc snapshot đăng ký, **When** đoán ID hoặc URL của nhóm
   khác, **Then** backend từ chối và không tiết lộ metadata, điểm hoặc nhận xét (AS-10.3).
4. **Given** Trưởng khoa, **When** xem kết quả, **Then** chỉ thấy dữ liệu trong khoa mình;
   role ADMIN tiếp tục không có quyền đọc học vụ theo quyết định feature 001 (AS-10.4).
5. **Given** hai sinh viên cùng snapshot đăng ký có điểm khác nhau, **When** mỗi người xem
   chi tiết, **Then** chỉ thấy điểm/nhận xét và kết quả cuối của chính mình (AS-10.5).
6. **Given** khoa có kết quả từ nhiều đợt và một sinh viên có thể xuất hiện ở các nhóm khác
   nhau giữa các đợt, **When** Trưởng khoa xem hoặc lọc danh sách kết quả, **Then** mỗi dòng
   hiển thị rõ đợt đăng ký và bộ lọc chỉ giữ kết quả của đợt đã chọn (AS-10.6).
7. **Given** danh sách có nhiều hội đồng/lượt chấm và trạng thái, **When** Trưởng khoa kết hợp
   bộ lọc đợt, giảng viên chấm và trạng thái công bố, **Then** chỉ các kết quả thỏa tất cả
   điều kiện được hiển thị và dữ liệu vẫn nằm trong phạm vi khoa (AS-10.7).

### Edge Cases

- Báo cáo có tên file giả mạo, đường dẫn tương đối, content type sai hoặc upload bị ngắt:
  không được ghi file ngoài vùng lưu trữ hoặc để metadata trỏ tới file không hoàn chỉnh.
- Thành phần hội đồng thay đổi đồng thời với activate/scoring: thao tác lấy quyền sau phải
  đọc lại trạng thái và không được tạo evaluation cho người đã mất phân công.
- GVHD được thay đổi hoặc dữ liệu quan hệ cũ không nhất quán: không cho activate/chấm cho
  đến khi không còn xung đột REQ-25.
- Hai evaluator cùng gửi lại form hoặc một evaluator mở nhiều tab: unique theo evaluator,
  đăng ký và thành phần; không tạo điểm trùng.
- Complete tranh chấp với submit: kết quả chỉ được COMPLETED nếu tập evaluation đã khóa
  và đầy đủ; thao tác còn lại nhận xung đột thay vì thay đổi kết quả sau complete.
- Publish lặp lại không được tạo bản kết quả thứ hai hoặc thay đổi người/thời điểm công bố.
- Thành viên rời nhóm sau khi đăng ký: quyền xem kết quả dùng snapshot bất biến của lần gửi,
  nhất quán với feature 001, không dùng membership hiện tại.
- Hai Trưởng khoa cùng phân công chấm độc lập một đăng ký: unique assignment và khóa
  registration/period phải bảo đảm chỉ một thao tác thành công.
- Một snapshot đăng ký không có sinh viên hoặc evaluation trỏ tới sinh viên ngoài snapshot:
  complete phải bị từ chối và không tạo kết quả dở dang.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-16**: Chỉ trưởng nhóm của đăng ký ACCEPTED được nộp project report dạng PDF tối đa
  20 MB. Trưởng nhóm được thay báo cáo hiện tại trước khi hội đồng phụ trách ACTIVE; từ
  ACTIVE trở đi báo cáo bị khóa. (REQ-17; U Q-11)
- **FR-17**: Backend phải kiểm tra quan hệ trước khi trả metadata hoặc nội dung báo cáo;
  chỉ thành viên snapshot, GVHD, Trưởng khoa đúng khoa và thành viên hội đồng phụ trách
  được xem/tải. (REQ-17, REQ-31–32; U Q-11)
- **FR-18**: Trưởng khoa đúng phạm vi được lập hội đồng cho đăng ký ACCEPTED; hội đồng có
  3–5 LECTURER duy nhất cùng khoa, đúng một Chair và một Secretary khác nhau, không gồm
  GVHD của bất kỳ đề tài được phân công. Một hội đồng có thể chấm nhiều đăng ký cùng
  đợt/khoa và chỉ áp dụng TLCN/KLTN.
  (REQ-18–21,25; U Q-12/Q-15)
- **FR-19**: Vòng đời hội đồng là `DRAFT → ASSIGNED/SCHEDULED → ACTIVE → COMPLETED →
  PUBLISHED`; backend kiểm tra tiền điều kiện, khóa cấu hình từ ACTIVE và lưu version để
  phát hiện thao tác cũ. (REQ-18–22,26; U Human Workflow)
- **FR-20**: Chỉ thành viên hội đồng ACTIVE được lưu DRAFT và SUBMIT evaluation của chính
  mình cho đề tài được phân công; GVHD và người ngoài hội đồng không được chấm. Mỗi evaluator
  nhập một điểm tổng 0–10 theo bước 0.25 và được sửa khi còn ACTIVE. Điểm hiển thị bỏ số 0
  thập phân dư, ví dụ `5`, `6.5`, `7.75`. (REQ-22,24–25; U Q-13, 2026-09-21)
- **FR-21**: Điểm cuối là trung bình cộng của tập điểm hợp lệ, đầy đủ và nhất quán theo
  mọi thành viên hội đồng, làm tròn HALF_UP đến hai chữ số thập phân; không complete khi
  thiếu bất kỳ evaluation SUBMITTED nào. (REQ-23; U Q-13)
- **FR-22**: Chỉ Chair của hội đồng được tổng hợp và complete sau khi mọi evaluation đã
  SUBMITTED; chỉ Trưởng khoa đúng phạm vi được publish sau khi review và grading hoàn tất.
  Publish chuyển result và council sang PUBLISHED trong một giao dịch, lưu actor/time và
  không cho sửa điểm sau complete. (REQ-22,26)
- **FR-23**: Sinh viên trong snapshot đăng ký chỉ xem kết quả PUBLISHED của chính nhóm;
  sinh viên khác không được đọc metadata, evaluation hoặc final score. Trưởng khoa chỉ xem
  trong khoa; ADMIN không đọc học vụ theo feature 001. (REQ-27,31–32; U Human Workflow)
- **FR-24**: Mọi create/update/state transition/upload/download/view phải kiểm tra role,
  khoa, membership/snapshot, supervisor và council assignment từ dữ liệu đáng tin cậy ở
  backend; dữ liệu do client tự khai không cấp quyền. (REQ-17–27,31–32)
- **FR-25**: Mọi validation hoặc lỗi lưu file/database phải rollback phần nghiệp vụ liên quan;
  filename tải xuống phải an toàn, storage key không do client quyết định, và phản hồi lỗi
  không lộ đường dẫn máy, SQL hoặc secret. (REQ-31–32; constitution IV, VII, IX)
- **FR-26**: Với đăng ký ACCEPTED loại COURSE/NCKH đã có báo cáo, Trưởng khoa đúng phạm vi
  được phân công đúng một LECTURER đang hoạt động cùng khoa và khác mọi GVHD để chấm độc
  lập. Phân công chuyển ngay sang ACTIVE và khóa báo cáo; người chấm submit một điểm, tự
  complete; Trưởng khoa publish qua lifecycle kết quả hiện có. TLCN/KLTN không được đi qua
  luồng này và tiếp tục dùng hội đồng 3–5 người. (U 2026-09-17; AS-7.7–7.10)
- **FR-27**: Báo cáo và phân công chấm vẫn theo đăng ký/nhóm, nhưng mỗi evaluator phải lưu
  và SUBMIT một evaluation riêng cho từng sinh viên trong snapshot đăng ký. Complete chỉ
  thành công khi ma trận `registration × snapshot student × evaluator` đầy đủ; hệ thống tạo
  một Final Result cho từng sinh viên bằng trung bình các điểm SUBMITTED của chính sinh viên
  đó. Sinh viên chỉ xem kết quả/nhận xét đã công bố mang đúng student ID của mình; Trưởng khoa
  xem được từng kết quả sinh viên trong phạm vi khoa, kèm đợt đăng ký và bộ lọc theo đợt.
  Danh sách cho phép lọc kết hợp theo giảng viên thuộc lượt chấm và trạng thái READY/PUBLISHED.
  (U 2026-09-21; AS-8.6,9.6,10.5–10.7)

### Key Entities

- **Project Report**: PDF hiện tại tối đa 20 MB gắn với một đăng ký ACCEPTED, người nộp và
  thời điểm; có thể được trưởng nhóm thay trước khi hội đồng ACTIVE.
- **Council**: hội đồng thuộc một period và kế thừa khoa từ period, có tên, trạng thái, version, Chair,
  Secretary và 3–5 thành viên; cấu hình lịch/địa điểm và assignment tới đăng ký được chấp nhận.
- **Direct Grading Assignment**: biến thể `DIRECT` dùng chung hạ tầng council/evaluation/result,
  có đúng một đăng ký và một người chấm; người chấm được lưu ở cả officer fields để giữ
  lifecycle complete/publish nhưng không được hiển thị như hội đồng.
- **Council Assignment**: liên kết hội đồng với một hoặc nhiều đăng ký ACCEPTED cùng đợt/khoa
  TLCN/KLTN và lịch review riêng cho từng nhóm.
- **Evaluation**: điểm/nhận xét của một evaluator cho một đăng ký và thành phần điểm;
  gắn thêm đúng một sinh viên snapshot; trạng thái DRAFT/SUBMITTED, không được sửa sau khi
  hội đồng hoàn tất.
- **Score Component**: thành phần điểm tổng duy nhất của đợt, có khoảng 0–10 và bước 0.25.
- **Final Result**: kết quả duy nhất theo cặp đăng ký–sinh viên snapshot, hội đồng,
  người/thời điểm tổng hợp và công bố, summary, điểm cuối cùng cùng mốc review/grading complete.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-010**: 100% ca nộp báo cáo hợp lệ bởi trưởng nhóm tạo được file tải lại đúng byte;
  100% ca nộp sai quyền/định dạng không để lại metadata hoặc file dở dang.
- **SC-011**: Trong toàn bộ test hội đồng, số hội đồng ACTIVE có dưới 3, trên 5, trùng thành
  viên, thiếu chức danh hoặc chứa GVHD của đề tài bằng 0.
- **SC-012**: 100% evaluator hợp lệ submit được evaluation; số lần người ngoài hội đồng,
  GVHD hoặc evaluator khác sửa điểm không được phép bằng 0.
- **SC-013**: Với mọi bộ điểm hợp lệ đã xác nhận, final score khớp quy tắc trung bình/làm
  tròn; không có kết quả nào được publish khi thiếu evaluation hoặc board chưa COMPLETED.
- **SC-014**: 100% sinh viên trong snapshot xem được kết quả PUBLISHED của nhóm mình;
  số kết quả nhóm khác hoặc chưa publish bị tiết lộ trong test bằng 0.
- **SC-015**: Trong các ca submit/complete/publish đồng thời, không có trạng thái cuối vi
  phạm lifecycle, không có điểm bị thay đổi sau complete và không tạo result trùng.
- **SC-016**: 100% FR-16–27 có ánh xạ REQ/U, acceptance scenario, automated test và bằng
  chứng pass trước khi feature được đánh dấu hoàn thành.
- **SC-017**: 100% ca COURSE/NCKH hợp lệ tạo đúng một lượt chấm ACTIVE và khóa báo cáo;
  số ca TLCN/KLTN, GVHD, người ngoài khoa hoặc thiếu báo cáo được phân công trực tiếp bằng 0.
- **SC-018**: 100% kết quả được tính riêng theo sinh viên; complete bị từ chối nếu thiếu bất
  kỳ ô nào trong ma trận sinh viên–người chấm và không sinh viên nào xem được điểm của thành
  viên khác cùng nhóm.

## Assumptions

- Tái sử dụng đăng nhập, role, khoa, period, topic, supervisor, group, registration
  ACCEPTED và registration-member snapshot của feature 001.
- Không bổ sung quản trị tài khoản, announcements, email/push, export PDF/Excel thống kê
  hoặc thay đổi quy tắc đăng ký trong feature này.
- Q-11, Q-12/Q-15 và Q-13 đã được người dùng xác nhận ngày 2026-09-14 theo các phương án
  A, C, A; các quyết định này là nguồn U bổ sung, không sửa nội dung PDF gốc.
- Giao diện tiếp tục responsive và toàn bộ mutation dùng CSRF như NFR-01–02 của feature 001.
