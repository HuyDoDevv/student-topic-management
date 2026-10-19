# Feature Specification: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

**Feature Branch**: `feature/006-feedback-fixes`

**Created**: 2026-10-14

**Status**: Planned — T001 hoàn thành tài liệu nền; T002–T018 cần được duyệt và triển khai riêng theo `AGENTS.md`

**Input**: 18 phản hồi trong `feeback lỗi.docx` và các quyết định đã chốt về vai trò DEAN, COURSE, NCKH, TLCN, KLTN, phản biện, hội đồng và công bố kết quả.

## Mục tiêu và phạm vi

Feature 006 sửa các điểm gây nhầm vai trò, thao tác rời rạc và thiếu căn cứ khi
chấm điểm. Kết quả cần đạt là DEAN chỉ quản lý quy trình, giảng viên thực hiện
đúng nhiệm vụ được phân công, sinh viên theo dõi được tiến trình, và mỗi kết quả
có đủ đầu điểm cùng dấu vết tổng hợp trước khi công bố.

Phạm vi gồm 18 phản hồi đã ghi nhận, bốn loại đợt `COURSE`, `NCKH`, `TLCN`,
`KLTN`, dữ liệu lịch sử liên quan và các kiểm tra quyền qua giao diện, URL trực
tiếp, POST giả mạo, deadline và thao tác đồng thời. Feature không tự tính lại
kết quả đã `PUBLISHED`, không sửa migration V1–V21 và không thay đổi dữ liệu
ứng dụng trong quá trình lập tài liệu.

## Quy tắc nghiệp vụ đã chốt

### Vai trò DEAN

- Mỗi Khoa có tối đa một DEAN đang hoạt động tại một thời điểm. Phải khóa DEAN
  cũ trước khi kích hoạt DEAN mới; tài khoản và lịch sử của người cũ được giữ.
- Tài khoản DEAN mới chỉ có role `DEAN`, không đồng thời có `LECTURER`.
- DEAN quản lý đợt, duyệt/từ chối/công bố đề tài, phân công phản biện/người chấm/
  hội đồng, theo dõi tiến độ, tổng hợp và công bố kết quả theo đúng quy trình.
- DEAN không đề xuất đề tài, làm GVHD/đồng hướng dẫn, phản biện, người chấm trực
  tiếp, thành viên hội đồng hoặc người nhập điểm.
- Lịch sử do DEAN cũ từng hướng dẫn/chấm vẫn đọc được nhưng không cho tạo nhiệm
  vụ mới dựa trên tổ hợp role cũ.

### COURSE và NCKH

- Mỗi đăng ký được một giảng viên do DEAN phân công chấm trực tiếp.
- Người chấm phải đang hoạt động, thuộc cùng Khoa, không phải DEAN và không phải
  GVHD/đồng hướng dẫn của đề tài.

### TLCN

- Không có hội đồng. DEAN phân công một giáo viên phản biện (GVPB).
- GVPB nộp một báo cáo PDF và điểm riêng cho mọi sinh viên trước hoặc đúng
  `reviewer_score_deadline`; hạn kết thúc vào cuối ngày theo múi giờ Việt Nam.
- Chỉ sau khi phản biện nộp đủ và đúng hạn, DEAN mới phân công người chấm thứ hai.
- GVPB và người chấm thứ hai phải khác nhau, đang hoạt động, cùng Khoa, không
  phải DEAN và không phải GVHD/đồng hướng dẫn.
- Điểm cuối của từng sinh viên bằng `(điểm GVPB + điểm người chấm thứ hai) / 2`.

### KLTN

- Có một GVPB và một hội đồng gồm 3–5 thành viên có quyền chấm.
- GVPB không thuộc hội đồng của chính đề tài; GVHD/đồng hướng dẫn và DEAN không
  được làm GVPB hoặc thành viên hội đồng của đề tài đó.
- Điểm cuối của từng sinh viên là trung bình cộng điểm GVPB và điểm của tất cả
  thành viên hội đồng có quyền chấm.
- Chỉ tổng hợp/công bố khi đủ mọi đầu điểm bắt buộc. Làm tròn hai chữ số thập
  phân theo `HALF_UP`.

## Truy vết 18 phản hồi

| ID | Phản hồi cần xử lý | Yêu cầu/Task | Tiêu chí nghiệm thu chính |
|---|---|---|---|
| FB-01 | DEAN xuất hiện trong khu vực đề xuất đề tài của giảng viên | FR-006-01, T002 | DEAN không thấy và không mở được route đề xuất qua URL trực tiếp. |
| FB-02 | Bộ lọc “Tất cả Bộ môn trong Khoa” thừa khi đợt đã thuộc một Bộ môn | FR-006-02, T003 | Trang duyệt chỉ tải đúng Bộ môn của đợt; request giả mạo bị từ chối. |
| FB-03 | Danh sách phân công chấm vẫn có DEAN | FR-006-01, T002 | Mọi query và service loại tài khoản có role DEAN. |
| FB-04 | Nhấn thông báo đi tới link lỗi/không có quyền | FR-006-03, T004 | Người nhận mở được target hợp lệ; nhiệm vụ hết hiệu lực quay về inbox có thông báo, không 404. |
| FB-05 | Chấm nhóm ba sinh viên phải mở từng người | FR-006-04, T008 | Một form chấm toàn nhóm; lưu nháp/nộp nguyên tử, không lưu nửa nhóm. |
| FB-06 | Danh sách kết quả rối khi một đợt có nhiều đề tài | FR-006-05, T017 | Có lọc, sắp xếp, phân trang và gom theo nhóm/đề tài. |
| FB-07 | Đề tài đã duyệt nhưng chưa công bố không thể trả về sửa | FR-006-06, T005 | Tác giả đưa `APPROVED → DRAFT` khi chưa công bố/chưa có đăng ký/còn thời hạn, rồi gửi duyệt lại. |
| FB-08 | Sinh viên không thấy đầy đủ tiến trình sau khi đăng ký | FR-006-07, T006 | Hiển thị chín bước dẫn xuất từ chờ xét đến đã công bố. |
| FB-09 | Khó phân biệt vai trò sinh viên giữa nhiều đợt/nhóm | FR-006-08, T007 | Mỗi thẻ nêu đợt, Bộ môn, nhóm, đề tài, vai trò, trạng thái và hành động. |
| FB-10 | Có thể chọn chính DEAN làm người chấm rồi phát sinh lỗi | FR-006-01, T002 | UI không liệt kê và backend từ chối DEAN dù POST trực tiếp. |
| FB-11 | TLCN bị đưa vào luồng hội đồng | FR-006-09, T011 | TLCN không xuất hiện ở hội đồng; dùng GVPB và người chấm thứ hai. |
| FB-12 | UI quản lý hội đồng khó theo dõi | FR-006-10, T013 | Danh sách thành viên rõ vai trò và thao tác thêm/xóa trực tiếp trong giai đoạn cho phép. |
| FB-13 | Đã chọn giảng viên hội đồng thì không sửa được | FR-006-10, T013 | Sửa được khi `DRAFT`/`ASSIGNED` và chưa có điểm; khóa sau khi có điểm hoặc `ACTIVE`. |
| FB-14 | Người hướng dẫn vẫn có thể được chọn chấm đề tài mình | FR-006-11, T010/T012/T013 | Mọi phân công GVPB/người chấm/hội đồng kiểm tra xung đột GVHD và đồng hướng dẫn. |
| FB-15 | Có thể chọn lịch bảo vệ trong quá khứ | FR-006-12, T014 | Service dùng clock hệ thống từ chối thời điểm không lớn hơn hiện tại; lỗi hiện tại form. |
| FB-16 | Công bố điểm không cho thấy căn cứ và độ chính xác | FR-006-13, T016 | Chi tiết trước công bố hiện người chấm, vai trò, trạng thái, điểm, công thức và audit actor/time. |
| FB-17 | Chủ tịch không thấy tiến độ chấm của thư ký/thành viên | FR-006-14, T015 | Chủ tịch thấy tiến độ từng người trong hội đồng mình; không lộ điểm nháp. |
| FB-18 | Quy trình phản biện/đầu điểm TLCN và KLTN chưa đầy đủ | FR-006-15–18, T009–T012 | Có phân công, báo cáo, điểm từng SV, deadline và công thức riêng cho TLCN/KLTN. |

## User Scenarios and Testing

### User Story 1 — DEAN quản lý mà không tham gia chấm hoặc hướng dẫn (P1)

DEAN quản lý toàn bộ quy trình trong Khoa nhưng không xuất hiện trong bất kỳ
danh sách ứng viên đề xuất, hướng dẫn, phản biện, chấm trực tiếp hoặc hội đồng.

**Independent test**: tạo một DEAN chỉ có role `DEAN`, thử GET/POST route giảng
viên và thử gửi ID DEAN vào mọi form phân công; tất cả bị loại/từ chối trong khi
route quản lý của DEAN vẫn hoạt động.

### User Story 2 — Tác giả và sinh viên hiểu đúng trạng thái công việc (P1)

Tác giả có thể trả đề tài đã duyệt nhưng chưa công bố về bản nháp trong điều
kiện an toàn. Sinh viên nhìn thấy vai trò và tiến trình dẫn xuất trên từng đợt.

**Independent test**: kiểm tra topic `APPROVED` có/không có đăng ký và một sinh
viên có bốn đợt, hai lần làm trưởng nhóm; trạng thái và hành động phải đúng từng
trường hợp.

### User Story 3 — Giảng viên chấm cả nhóm trong một giao dịch (P1)

Người chấm xem mọi thành viên của nhóm trên một màn hình, nhập điểm/nhận xét
riêng và lưu nháp hoặc nộp toàn bộ.

**Independent test**: nhóm 1–3 sinh viên; một điểm ngoài miền làm request thất
bại hoàn toàn, dữ liệu/version cũ giữ nguyên; request hợp lệ lưu đủ cả nhóm.

### User Story 4 — TLCN có phản biện và người chấm thứ hai, không có hội đồng (P1)

GVPB nộp báo cáo PDF và điểm toàn nhóm đúng hạn; sau đó DEAN mới phân công một
người chấm thứ hai hợp lệ và hệ thống tính trung bình hai đầu điểm.

**Independent test**: trước/sau deadline Việt Nam, thiếu báo cáo/thiếu một sinh
viên, trùng GVPB/GVHD/DEAN và đủ hai đầu điểm.

### User Story 5 — KLTN có phản biện độc lập với hội đồng (P1)

KLTN có GVPB và hội đồng 3–5 người. GVPB, GVHD và DEAN không được trùng với
thành viên bị cấm; kết quả chỉ sẵn sàng khi đủ GVPB và mọi thành viên có quyền chấm.

**Independent test**: một GVPB và ba thành viên cùng cho 7.00 phải cho kết quả
7.00; thiếu bất kỳ đầu điểm nào không được tổng hợp/công bố.

### User Story 6 — DEAN và Chủ tịch theo dõi được tiến độ, DEAN công bố có căn cứ (P2)

Chủ tịch xem tiến độ của hội đồng mình; DEAN xem tiến độ trong Khoa và trang
chi tiết căn cứ trước khi công bố. Người không có quyền không thấy điểm nháp.

**Independent test**: hai hội đồng và hai Khoa; Chủ tịch chỉ xem hội đồng mình,
DEAN chỉ xem Khoa mình, sinh viên chỉ thấy điểm sau `PUBLISHED`.

### User Story 7 — Điều hướng và danh sách lớn vẫn rõ ràng (P2)

Thông báo mở đúng nơi người nhận có quyền. Danh sách kết quả hỗ trợ nhiều đợt,
đề tài, nhóm và người chấm mà không mất quan hệ giữa sinh viên và đề tài.

**Independent test**: target còn hiệu lực/hết hiệu lực và dữ liệu nhiều trang;
bộ lọc, sort, pagination, group và link chi tiết đều giữ đúng scope.

## Functional Requirements

- **FR-006-01**: Tài khoản DEAN mới chỉ có role `DEAN`; hệ thống ngăn tổ hợp
  `DEAN + LECTURER` mới, loại DEAN khỏi mọi ứng viên hướng dẫn/chấm/hội đồng và
  từ chối route nhập điểm/đề xuất của giảng viên.
- **FR-006-02**: Duyệt đề xuất theo đúng Bộ môn của đợt, không có bộ lọc Khoa
  rộng hơn và không tin tham số Bộ môn từ client.
- **FR-006-03**: Mỗi thông báo phải resolve tới route người nhận có quyền; target
  hết hiệu lực phải cho kết quả có chủ đích thay vì 404.
- **FR-006-04**: Chấm điểm một nhóm trong một request nguyên tử, validate đủ mọi
  sinh viên và dùng optimistic locking cho lưu nháp/nộp.
- **FR-006-05**: Danh sách kết quả có lọc theo đợt, loại đề tài, đề tài/nhóm,
  người chấm, trạng thái; hỗ trợ sort, pagination và group theo đề tài/nhóm.
- **FR-006-06**: Tác giả chỉ được đưa `APPROVED → DRAFT` khi đề tài chưa công bố,
  chưa có đăng ký và còn trong giai đoạn; sau sửa phải gửi duyệt lại.
- **FR-006-07**: Tiến trình sinh viên được dẫn xuất thành chín bước, không thêm
  trạng thái DB nếu có thể suy ra từ dữ liệu nguồn.
- **FR-006-08**: Danh sách nhóm thể hiện đợt, Bộ môn, mã nhóm, đề tài, vai trò
  Trưởng nhóm/Thành viên, trạng thái và hành động cho từng thẻ.
- **FR-006-09**: TLCN không dùng hội đồng; chỉ có một GVPB và một người chấm thứ hai.
- **FR-006-10**: Thành viên hội đồng sửa được khi `DRAFT`/`ASSIGNED` và chưa có
  điểm; GVPB thay được khi chưa nộp; mọi thay đổi kiểm tra lại xung đột.
- **FR-006-11**: GVHD/đồng hướng dẫn không được phản biện, chấm trực tiếp hoặc
  tham gia hội đồng chấm chính đề tài mình.
- **FR-006-12**: Lịch bảo vệ phải lớn hơn thời điểm hiện tại theo clock server;
  form có `min` chỉ là hỗ trợ giao diện, service là nguồn quyết định.
- **FR-006-13**: Chi tiết kết quả trước công bố hiển thị người chấm/vai trò,
  trạng thái nộp, điểm đã nộp, công thức, số đầu điểm, trung bình và audit tổng
  hợp/công bố; chỉ DEAN được công bố.
- **FR-006-14**: Chủ tịch chỉ xem tiến độ của hội đồng mình; DEAN chỉ xem trong
  Khoa; không lộ điểm nháp cho người không có quyền.
- **FR-006-15**: Một đăng ký TLCN/KLTN có đúng một GVPB đang hoạt động, cùng Khoa,
  không phải DEAN/GVHD và không xung đột với vai trò chấm khác trên cùng đề tài.
- **FR-006-16**: GVPB nộp một báo cáo PDF và điểm riêng cho đủ mọi sinh viên
  trước hoặc đúng cuối ngày deadline theo `Asia/Ho_Chi_Minh`; không nộp từng phần.
- **FR-006-17**: Điểm TLCN bằng trung bình GVPB và người chấm thứ hai; điểm KLTN
  bằng trung bình GVPB và mọi thành viên hội đồng có quyền chấm, theo từng SV.
- **FR-006-18**: Kết quả chỉ chuyển sang sẵn sàng khi đủ đầu điểm, làm tròn hai
  chữ số theo `HALF_UP`; kết quả lịch sử `PUBLISHED` không bị tính lại.
- **FR-006-19**: Mỗi Khoa có tối đa một DEAN hoạt động kể cả khi có hai request
  tạo/kích hoạt đồng thời; đổi DEAN không làm mất lịch sử.
- **FR-006-20**: Migration mới phải nâng cấp từ V21, validate/rerun được và bảo
  toàn toàn bộ business rows; không suy diễn GVPB cho dữ liệu TLCN/KLTN cũ.

## Edge Cases and Stop Gates

- Deadline được xét tại cuối ngày Việt Nam, bao gồm biên đúng hạn và ngay sau hạn.
- Nhóm có 1, 2 hoặc 3 sinh viên; thiếu một sinh viên trong payload bị từ chối.
- GVPB hoặc thành viên bị vô hiệu hóa/đổi Khoa sau phân công phải được kiểm tra lại
  trước các transition nhạy cảm; không tự xóa lịch sử.
- Hai request cùng sửa thành viên, phân công hoặc nộp điểm phải phát hiện version cũ.
- URL trực tiếp và POST giả mạo phải bị chặn tại service/backend, không chỉ ẩn UI.
- Nếu thiết kế migration cần sửa V1–V21, tự backfill người phản biện lịch sử, thay
  công thức đã công bố hoặc đổi public contract ngoài tài liệu này thì phải dừng.

## Success Criteria

- **SC-006-01**: Cả 18 phản hồi có task, tiêu chí nghiệm thu và evidence sau triển khai.
- **SC-006-02**: 0 trường hợp DEAN mới có role giảng viên hoặc được tạo nhiệm vụ
  hướng dẫn/phản biện/chấm/hội đồng trong ma trận quyền.
- **SC-006-03**: 100% nhóm 1–3 sinh viên được chấm nguyên tử trên một màn hình;
  payload lỗi không để lại thay đổi từng phần.
- **SC-006-04**: Mọi kịch bản TLCN/KLTN đủ điểm cho kết quả đúng công thức và
  `HALF_UP`; thiếu một đầu điểm cho 0 kết quả có thể công bố.
- **SC-006-05**: 100% target thông báo trong bộ acceptance mở được hoặc fallback
  có chủ đích; không có 404 do gửi người nhận tới route sai quyền.
- **SC-006-06**: Migration V21→V23 giữ nguyên số lượng và nội dung business rows,
  Flyway validate/rerun đạt trên MySQL 8.4 DB test.
- **SC-006-07**: Unit, web, integration, E2E và `git diff --check` đạt; phần chưa
  chạy hoặc baseline ngoài scope được ghi rõ, không suy diễn thành “đã hoạt động”.

## Assumptions and Boundaries

- “Cùng Khoa” là điều kiện tổ chức cho người được phân công; không yêu cầu cùng
  Bộ môn với đề tài nếu quy tắc feature 005 cho phép phân công chéo Bộ môn.
- Chỉ một GVPB trên mỗi đăng ký TLCN/KLTN trong phạm vi 006.
- `reviewer_assignments` đã tồn tại ở schema V1 nhưng chưa có lifecycle/report/
  score model đủ dùng; V23 mở rộng an toàn thay vì sửa migration cũ.
- `FinalResult` hiện bắt buộc liên hệ hạ tầng hội đồng/direct grading; thiết kế
  triển khai phải bảo toàn dữ liệu cũ và có thể dùng nguồn chấm mới mà không tính
  lại row `PUBLISHED`.
- T001 chỉ tạo tài liệu và commit tài liệu. Mỗi T002–T018 cần một chu trình phê
  duyệt riêng trước khi sửa code, migration, route hoặc cấu hình.
