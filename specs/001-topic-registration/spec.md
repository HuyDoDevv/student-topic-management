# Feature Specification: Quản lý đợt, đề tài và đăng ký nhóm

**Feature Branch**: Không tạo nhánh; dự án không có hook tạo nhánh.

**Feature Directory**: `specs/001-topic-registration`

**Created**: 2026-09-08

**Status**: Draft — chờ xác nhận nghiệp vụ, chưa sẵn sàng lập kế hoạch triển khai.

**Input**: Quản lý đợt đăng ký, đề tài và đăng ký đề tài của nhóm sinh viên; nhận diện và
phân quyền cần thiết; duyệt/công bố đề tài; chấp nhận hoặc từ chối đăng ký theo nghiệp vụ
được chốt. Giữ các chức năng còn lại trong phạm vi tổng thể dự án.

Nguồn: [bản phân tích](../../docs/customer/requirements-analysis.md), đối chiếu
[PDF trang 1](../../docs/customer/customer-requirements.pdf);
[constitution 1.0.0](../../.specify/memory/constitution.md).
E = nêu rõ trong nguồn; U = phạm vi do người dùng bổ sung ở yêu cầu hiện tại;
I = diễn giải thành hành vi kiểm chứng được, chưa phải xác nhận khách hàng;
Q = quyết định còn mở. FR là mã trong đặc tả này, không thay thế REQ nguồn.
Các tiêu chí nghiệm thu bên dưới là đề xuất I; phần phụ thuộc Q phải được xác nhận
trước khi trở thành chuẩn nghiệm thu. Không có quyết định công nghệ trong đặc tả.

## Clarifications

### Session 2026-09-08

- Q: Ai có quyền duyệt/công bố đề tài và chấp nhận hoặc từ chối đăng ký của nhóm? → A: B — Trưởng khoa thực hiện cả duyệt/công bố đề tài và chấp nhận/từ chối đăng ký nhóm. Xác nhận U, làm rõ Q-01 cho REQ-02, REQ-08, REQ-16; không thay đổi nguồn PDF.
- Q: Quy tắc một sinh viên chỉ thuộc một nhóm áp dụng trong phạm vi nào? → A: A — Theo từng đợt đăng ký: một sinh viên chỉ thuộc một nhóm trong đợt đó. Việc thuộc nhóm ở đợt khác không vi phạm ràng buộc này. Xác nhận U, làm rõ Q-08 cho REQ-13.
- Q: Trong một đợt, một đề tài được bao nhiêu nhóm đăng ký và được chấp nhận thực hiện? → A: B — Nhiều nhóm có thể đăng ký chờ xét; tối đa một nhóm được chấp nhận thực hiện mỗi đề tài trong một đợt. Xác nhận U, làm rõ Q-10, liên quan REQ-14–16; đây là bổ sung của khách hàng, không phải giới hạn nêu rõ trong PDF.

- Q: Ai quản lý thành viên, chọn trưởng nhóm và gửi đăng ký đề tài của nhóm? → A: A — SV tạo nhóm trở thành trưởng nhóm; trưởng nhóm thêm/xóa thành viên, chuyển quyền trưởng nhóm cho thành viên khác và gửi đăng ký. Xác nhận U, làm rõ quyền trong Q-07, Q-18; liên quan REQ-12–15, REQ-31.

- Q: Nhóm được rút đăng ký và đăng ký lại sau khi bị từ chối theo quy tắc nào? → A: A — Trong hạn SV, trưởng nhóm được rút đăng ký đang chờ xét; sau rút hoặc bị từ chối, được đăng ký lại đề tài cũ hoặc đề tài khác còn nhận đăng ký. Không được rút đăng ký đã chấp nhận. Xác nhận U, làm rõ Q-09; liên quan REQ-03, REQ-14–16, REQ-31.

Phiên làm rõ thứ hai trong ngày 2026-09-08:

- Q: Giảng viên được đăng đề tài thuộc bộ môn nào và được chọn giảng viên hướng dẫn từ những bộ môn nào? → A: A — Giảng viên chỉ đăng đề tài thuộc bộ môn mình; mọi GVHD phải cùng bộ môn đó. Xác nhận U, làm rõ phần quyền bộ môn trong Q-18; liên quan REQ-08–11, REQ-31. Không suy rộng thành giới hạn bộ môn đối với sinh viên hoặc trưởng khoa.

- Q: Trong đợt triển khai đầu, tài khoản người dùng được cung cấp và đăng nhập theo cách nào? → A: A — Tài khoản nội bộ được cấp sẵn cùng vai trò/bộ môn; đăng nhập bằng tên đăng nhập và mật khẩu; không tự đăng ký. Xác nhận U, phiên 2; làm rõ nguồn danh tính trong Q-18, liên quan REQ-28, REQ-31–32. Không tự xác định người/công cụ cấp tài khoản hoặc cơ chế kỹ thuật lưu thông tin xác thực.

- Q: Khi trưởng khoa chấp nhận một nhóm cho đề tài, hệ thống xử lý các đăng ký chờ xét khác và việc nhận đăng ký mới cho đề tài đó như thế nào? → A: A — Tự động từ chối các đăng ký chờ xét còn lại với lý do “Đề tài đã nhận nhóm”; ngừng nhận đăng ký mới cho đề tài đó trong đợt. Xác nhận U, phiên 2; giải quyết phần còn lại của Q-10, liên quan REQ-14–16. Các nhóm bị từ chối được áp dụng quy tắc đăng ký lại đã chốt; không mở rộng sang thông báo tự động.

- Q: Hai giai đoạn đăng ký được mở theo lịch và múi giờ nào? → A: A — Dùng Asia/Ho_Chi_Minh; hai khoảng GV/SV không chồng lấn, tự mở/đóng theo lịch; đăng ký SV còn yêu cầu trưởng khoa đã công bố danh sách. Tính thời điểm bắt đầu, không tính thời điểm kết thúc. Xác nhận U, phiên 2; giải quyết Q-03–04 về lịch và biên, liên quan REQ-02–05, REQ-15.

- Q: Đề tài được phép chỉnh sửa và phải có đủ thông tin ở những trạng thái nào? → A: A — Nháp → Chờ duyệt → Đã duyệt → Đã công bố. GV chỉ sửa bản nháp của mình; gửi duyệt phải có nội dung, bộ môn và 1–2 GVHD hợp lệ. Trưởng khoa từ chối đưa đề tài về Nháp để sửa/gửi lại trong hạn GV. Xác nhận U, phiên 2; làm rõ vòng đời Q-05–06, liên quan REQ-03, REQ-08–11, REQ-15, REQ-31.

Phiên làm rõ thứ ba trong ngày 2026-09-08:

- Q: Trưởng nhóm được thêm/xóa thành viên và chuyển quyền trưởng nhóm vào thời điểm nào? → A: A — Chỉ trong hạn SV và khi nhóm không có đăng ký chờ xét hoặc đã chấp nhận; sau rút/từ chối có thể sửa nếu còn hạn. Xác nhận U, phiên 3; làm rõ thời điểm Q-07, liên quan REQ-03, REQ-12–16, REQ-31. Nhóm đang chờ xét phải rút đăng ký trước khi sửa.

- Q: Khi trưởng nhóm thêm một sinh viên, sinh viên đó có cần đồng ý trước khi trở thành thành viên chính thức không? → A: B — Trưởng nhóm thêm trực tiếp; SV trở thành thành viên ngay nếu nhóm còn chỗ và SV chưa thuộc nhóm khác trong đợt. Không có bước lời mời/xác nhận. Các điều kiện quyền và thời điểm sửa nhóm đã chốt vẫn áp dụng. Xác nhận U, phiên 3; làm rõ Q-07, liên quan REQ-12–13, REQ-31.

- Q: Nhóm cần tối thiểu bao nhiêu sinh viên để được gửi đăng ký đề tài? → A: A — Từ 1 đến 3 sinh viên, có đúng một trưởng nhóm. Xác nhận U, phiên 3; giải quyết số thành viên tối thiểu trong Q-07, liên quan REQ-12–15. Nhóm chỉ có trưởng nhóm vẫn được đăng ký nếu thỏa các điều kiện khác.

- Q: Khi gửi duyệt, đề tài bắt buộc phải có những thông tin nội dung nào? → A: A — Tên đề tài, mô tả, mục tiêu, yêu cầu đối với sinh viên và kết quả dự kiến đều bắt buộc, ngoài bộ môn và 1–2 GVHD đã chốt. Xác nhận U, phiên 3; làm rõ nội dung trong Q-05, liên quan REQ-08–10. Không đặt thêm giới hạn độ dài hoặc mẫu trình bày chưa được xác nhận.

- Q: Trưởng khoa được thay đổi loại đợt và thời gian đăng ký sau khi đợt đã bắt đầu không? → A: Quy tắc cuối được cập nhật ngày 2026-09-10 — từ đầu ngày bắt đầu GV, khóa tên, loại và các ngày bắt đầu; trưởng khoa chỉ được kéo dài ngày kết thúc của giai đoạn chưa hết hạn, không được rút ngắn hoặc mở lại giai đoạn đã đóng, và lịch sau sửa vẫn phải đúng thứ tự. Quyết định này thay thế phương án khóa toàn bộ loại/bốn mốc đã ghi ở phiên 3; liên quan REQ-03–05, REQ-31.

### Session 2026-09-09

- Q: Trong phiên bản đầu tiên, hệ thống sẽ cấp tài khoản, kết hợp vai trò và giới hạn phạm vi dữ liệu theo phương án nào? → A: Phương án A và bổ sung ADMIN — Tài khoản được seed/import local; tài khoản nhân sự có thể đồng thời giữ LECTURER và DEAN, STUDENT không kết hợp với vai trò nhân sự. Trưởng khoa quản lý trong khoa mình, giảng viên thao tác đề tài trong bộ môn mình, sinh viên xem đề tài đã công bố trong khoa và chỉ thay đổi nhóm/đăng ký mình có quyền. Thêm role ADMIN để xử lý sự cố website; phạm vi cụ thể được chốt ở các câu tiếp theo trong phiên này. Xác nhận U, làm rõ một phần Q-18; liên quan REQ-28, REQ-31–32.
- Q: ADMIN có được phép can thiệp trực tiếp vào các nghiệp vụ học vụ như tạo đợt, duyệt đề tài và xét đăng ký không? → A: ADMIN chỉ xử lý sự cố khẩn cấp như nội dung do kẻ gian đăng; không quản lý nghiệp vụ học tập, đăng ký, role, khoa/bộ môn hoặc chuyển giảng viên. Việc duyệt học vụ thuộc trưởng khoa; chuyển giảng viên chưa có trong phiên bản đầu và nếu bổ sung sau sẽ thuộc trưởng khoa. Xác nhận U, thay thế phạm vi ADMIN đề xuất trước đó và làm rõ một phần Q-18; liên quan REQ-28, REQ-31–32.
- Q: Khi trưởng khoa chuyển một giảng viên đang có đề tài hoặc phân công GVHD sang bộ môn/khoa khác, các trách nhiệm đang hoạt động phải được xử lý thế nào? → A: Bỏ qua trong phiên bản đầu vì là chức năng mở rộng, mức độ không quan trọng. Phiên bản đầu không cung cấp thao tác chuyển giảng viên và giả định thông tin bộ môn/khoa ổn định trong các luồng đang hoạt động; nếu có thời gian sẽ đặc tả và triển khai sau. Xác nhận U, đóng phần chuyển bộ môn của Q-18 bằng quyết định ngoài phạm vi hiện tại.
- Q: Khi có kẻ gian đăng nội dung, ADMIN nên “xóa” theo cách nào? → A: A — ADMIN ẩn nội dung khỏi website nhưng giữ dữ liệu để kiểm tra hoặc khôi phục; lưu ADMIN thực hiện, thời gian và lý do, đồng thời khóa tài khoản gây sự cố. Đây là quyền xử lý khẩn cấp, không cấp quyền sửa nội dung hoặc thực hiện nghiệp vụ học vụ/đăng ký. Xác nhận U, giải quyết phần còn lại của Q-18; liên quan REQ-28, REQ-31–32.
- Q: Phiên bản local đầu tiên được nghiệm thu theo mức chất lượng nào? → A: A — Dùng toàn bộ AS/SC hiện tại làm chuẩn nghiệm thu; kiểm tra chức năng, phân quyền backend, CSRF, mật khẩu băm, validation, không lộ secret, responsive trên desktop/tablet/mobile và các xung đột dữ liệu chính. Không đặt SLA định lượng cho thời gian phản hồi, số người dùng đồng thời, uptime hoặc backup ở phiên bản local. Xác nhận U, giải quyết Q-20 và G-Acceptance; các kết quả phụ thuộc Q nghiệp vụ còn lại tiếp tục ở trạng thái blocked cho tới khi từng Q được chốt.

### Session 2026-09-10

- Q: Khi tạo đợt TLCN hoặc KLTN, các ngày phụ có bắt buộc phải nhập không? → A: TLCN và KLTN bắt buộc có hạn nộp điểm GVPB; riêng KLTN bắt buộc thêm ngày báo cáo hội đồng. Xác nhận U, làm rõ phần bắt buộc của Q-02; liên quan REQ-06–07 và FR-04.
- Q: Khi trưởng khoa đổi loại đợt trước giờ bắt đầu đăng ký giảng viên, hệ thống phải xử lý các ngày không còn phù hợp như thế nào? → A: Tự xóa ngày không còn áp dụng; yêu cầu nhập đủ ngày bắt buộc của loại mới trước khi lưu. Nếu thiếu ngày mới bắt buộc thì từ chối toàn bộ thay đổi. Xác nhận U, làm rõ xử lý đổi loại của Q-02; liên quan REQ-03–07 và FR-04/FR-15.
- Q: Hạn nộp điểm GVPB và ngày báo cáo hội đồng cần lưu chính xác đến ngày hay đến cả giờ và phút? → A: Chỉ lưu ngày lịch theo Asia/Ho_Chi_Minh, không có giờ và phút. Xác nhận U, làm rõ độ chính xác của Q-02; liên quan REQ-06–07 và FR-04.
- Q: Sau khi đợt bắt đầu, trưởng khoa được thay đổi lịch đăng ký theo quy tắc nào? → A: Từ đầu ngày bắt đầu GV, khóa tên, loại và các ngày bắt đầu. Chỉ được kéo dài ngày kết thúc của giai đoạn chưa hết hạn; không rút ngắn thời gian, không mở lại giai đoạn đã đóng và ngày bắt đầu SV vẫn phải sau ngày kết thúc GV. Xác nhận U; ngày 2026-09-10 người dùng yêu cầu khoảng GV phải hoàn thành trước khi khoảng SV bắt đầu; liên quan REQ-03–05 và FR-15.
- Q: Trưởng khoa được sửa hạn nộp điểm GVPB và ngày báo cáo hội đồng theo quy tắc nào? → A: Với từng ngày đang áp dụng, chỉ được dời sang ngày muộn hơn khi ngày hiện hành theo Asia/Ho_Chi_Minh vẫn chưa tới ngày đang lưu; không được dời sớm, sửa từ đầu ngày đang lưu hoặc mở lại mốc đã qua. Quy tắc này áp dụng cho sửa trực tiếp; tạo đợt và đổi loại trước giờ mở GV vẫn tuân theo quy tắc bắt buộc/xóa ngày đã chốt. Xác nhận U, giải quyết phần còn lại của Q-02; liên quan REQ-06–07 và FR-04/FR-15.
- Q: Bốn mốc đăng ký GV/SV có cần chọn giờ, phút, giây và có được chọn ngày đã qua không? → A: Chỉ chọn ngày lịch theo Asia/Ho_Chi_Minh, không nhập giờ/phút/giây và không chọn ngày trước ngày hiện tại. Ngày bắt đầu có hiệu lực từ đầu ngày; ngày kết thúc có hiệu lực đến cuối ngày. Ngày bắt đầu SV phải sau ngày kết thúc GV, ví dụ GV 13–15 thì SV sớm nhất bắt đầu ngày 16. Xác nhận U ngày 2026-09-10; cập nhật FR-03/FR-15 và AS-1.1,1.4,1.9–1.12.
- Q: Khi một đề tài đã được tạo cho một đợt, hệ thống phải xử lý việc dùng lại đề tài đó ở đợt sau như thế nào? → A: A — Mỗi đề tài chỉ thuộc một đợt; phiên bản đầu không có chức năng dùng lại hoặc sao chép đề tài. Muốn đăng ở đợt mới, giảng viên tự tạo một đề tài mới. Xác nhận U ngày 2026-09-10, làm rõ phần tái sử dụng của Q-05; liên quan REQ-08–09 và FR-05.
- Q: Danh sách bộ môn và giảng viên hướng dẫn trên biểu mẫu đề tài sẽ được lấy và quản lý theo cách nào? → A: A — Bộ môn và tài khoản giảng viên được seed/import sẵn vào database. Biểu mẫu chỉ hiển thị tài khoản đang hoạt động, có role LECTURER và cùng bộ môn với người đăng; phiên bản đầu không có màn hình quản lý danh mục. Xác nhận U ngày 2026-09-10, giải quyết phần còn lại của Q-05; liên quan REQ-08–11, REQ-28 và FR-01/FR-05–06.
- Q: Sau khi giảng viên đã gửi đề tài chờ duyệt, hệ thống có cho phép thay đổi danh sách GVHD không? → A: A — Chỉ tác giả được thay đổi GVHD khi đề tài ở Nháp. Từ Chờ duyệt trở đi danh sách GVHD bị khóa; nếu trưởng khoa từ chối đưa đề tài về Nháp thì tác giả được sửa và gửi lại trong hạn GV. Phiên bản đầu không hỗ trợ thay GVHD trong quá trình thực hiện. Xác nhận U ngày 2026-09-10, giải quyết phần còn lại của Q-06; liên quan REQ-10–11 và FR-06/FR-14.

## User Scenarios & Testing *(mandatory)*

Các story P1 tạo thành luồng bàn giao đầu tiên; thứ tự trình bày phản ánh phụ thuộc
nghiệp vụ, không có nghĩa story phía sau bị loại khỏi phạm vi.
Mỗi story có thể kiểm thử riêng bằng dữ liệu tiền điều kiện đã chuẩn bị;
điều này không khẳng định chúng triển khai độc lập hoàn toàn.

### User Story 1 - Tạo đợt đăng ký đúng loại (Priority: P1)

Là trưởng khoa, tôi muốn tạo đợt với các mốc thời gian phù hợp để giảng viên và nhóm
sinh viên đăng ký theo đúng đợt.

**Why this priority**: Đợt và thời gian là tiền đề cho đăng đề tài và đăng ký nhóm.
Nguồn: REQ-01–07; FR-01–04.

**Independent Test**: Với người dùng được nhận diện là trưởng khoa, tạo các đợt cho
môn học, NCKH, TLCN, KLTN và đối chiếu giá trị đã ghi nhận.

**Acceptance Scenarios**:

1. **Given** trưởng khoa và bộ giá trị hợp lệ theo quy tắc đã xác nhận,
   **When** tạo đợt, **Then** tên, loại, ngày bắt đầu/kết thúc đăng ký GV và SV
   được ghi nhận đúng giá trị (AS-1.1).
2. **Given** từng loại trong bốn loại, **When** cấu hình ngày,
   **Then** hạn nộp điểm GVPB chỉ áp dụng TLCN/KLTN và ngày báo cáo hội đồng
   chỉ áp dụng KLTN; giá trị không áp dụng không được chấp nhận (AS-1.2).
   TLCN/KLTN bắt buộc có hạn nộp điểm GVPB; KLTN bắt buộc thêm ngày báo cáo hội đồng.
   Hai trường này là ngày lịch theo Asia/Ho_Chi_Minh, không có giờ và phút.
3. **Given** người gọi không chứng minh được quyền tạo đợt,
   **When** yêu cầu tạo đợt, **Then** bị từ chối và số đợt không đổi (AS-1.3).
   Không tự xác định quyền được ủy nhiệm ngoài trưởng khoa.
4. **Given** dữ liệu thiếu tên, thiếu loại, thiếu một mốc đăng ký bắt buộc, chứa giờ/phút/giây
   hoặc chọn ngày trước ngày hiện tại theo Asia/Ho_Chi_Minh,
   **When** tạo đợt, **Then** không tạo đợt hợp lệ (AS-1.4).
5. **Given** tài khoản nội bộ đã được cấp sẵn,
   **When** nhập đúng tên đăng nhập và mật khẩu,
   **Then** người dùng được nhận diện theo tài khoản và vai trò/bộ môn đã cấp;
   không lấy vai trò/bộ môn từ thông tin tự khai của người đăng nhập (AS-1.5).
6. **Given** tên đăng nhập không tồn tại hoặc mật khẩu không đúng,
   **When** đăng nhập, **Then** không nhận diện người gọi là tài khoản hợp lệ và không
   cho thực hiện thao tác được bảo vệ (AS-1.6).
7. **Given** người chưa đăng nhập, **When** yêu cầu một thao tác được bảo vệ trong luồng,
   **Then** bị từ chối, không có thay đổi nghiệp vụ (AS-1.7).
8. **Given** người chưa được cấp tài khoản, **When** tìm cách tự đăng ký tài khoản,
   **Then** hệ thống không cung cấp khả năng tự đăng ký hoặc tự cấp vai trò/bộ môn (AS-1.8).

AS-1.5–1.8 áp dụng cho mọi vai trò trong tính năng, không chỉ trưởng khoa.

9. **Given** cấu hình hai khoảng GV/SV chồng lấn hoặc khoảng SV nằm trước khoảng GV,
   **When** tạo đợt, **Then** không chấp nhận cấu hình lịch đó (AS-1.9).
10. **Given** lịch ngày hợp lệ theo Asia/Ho_Chi_Minh,
    **When** bước sang đầu ngày bắt đầu hoặc qua hết ngày kết thúc của một khoảng,
    **Then** tự mở từ đầu ngày bắt đầu và tự đóng sau cuối ngày kết thúc, không cần thao tác bật/tắt (AS-1.10).

Lịch đăng ký chỉ nhập ngày, không nhập giờ/phút/giây. Trong từng khoảng, ngày bắt đầu
không sau ngày kết thúc. Ngày kết thúc GV phải trước ngày bắt đầu SV; hai ngày này không
được trùng nhau.

11. **Given** chưa đến đầu ngày bắt đầu GV của lịch đang có hiệu lực,
    **When** trưởng khoa sửa tên, loại hoặc bốn mốc bắt đầu/kết thúc GV/SV thành cấu hình hợp lệ,
    **Then** thay đổi được ghi nhận; khi đổi loại, hệ thống tự xóa ngày không còn áp dụng
    và chỉ lưu nếu đã nhập đủ ngày bắt buộc của loại mới (AS-1.11).
12. **Given** đã đến hoặc qua đầu ngày bắt đầu GV của lịch đang có hiệu lực,
    **When** trưởng khoa sửa tên, loại, một mốc bắt đầu, rút ngắn mốc kết thúc hoặc
    mở lại một giai đoạn đã đóng, **Then** bị từ chối và cấu hình không đổi. Nếu giai đoạn
    chưa hết hạn, trưởng khoa được kéo dài mốc kết thúc khi lịch mới vẫn đúng thứ tự.
    Với từng hạn điểm GVPB/ngày hội đồng đang áp dụng, chỉ được dời sang ngày muộn hơn
    khi ngày hiện tại theo Asia/Ho_Chi_Minh còn trước ngày đang lưu; từ đầu ngày đang lưu
    thì mốc đó bị khóa và không được mở lại (AS-1.12).
13. **Given** người không có vai trò trưởng khoa,
    **When** sửa loại/lịch kể cả trước giờ mở GV,
    **Then** bị từ chối và dữ liệu không đổi (AS-1.13).
14. **Given** tài khoản thuộc khoa, bộ môn hoặc nhóm khác phạm vi được cấp,
    **When** đọc hoặc thay đổi dữ liệu được bảo vệ,
    **Then** backend từ chối dữ liệu ngoài phạm vi đã xác nhận (AS-1.14).
15. **Given** tài khoản chỉ có role ADMIN,
    **When** thực hiện tạo đợt, duyệt/công bố đề tài hoặc xét đăng ký,
    **Then** backend từ chối vì ADMIN không có quyền học vụ hoặc đăng ký (AS-1.15).
16. **Given** nội dung do tài khoản gây sự cố đăng và ADMIN cung cấp lý do,
    **When** ADMIN xử lý khẩn cấp,
    **Then** nội dung bị ẩn khỏi website nhưng còn trong database, tài khoản gây sự cố
    bị khóa và hệ thống lưu ADMIN, thời gian, lý do để kiểm tra/khôi phục (AS-1.16).

### User Story 2 - Giảng viên đăng đề tài có hướng dẫn (Priority: P1)

Là giảng viên, tôi muốn đăng đề tài thuộc bộ môn với nội dung và một đến hai GVHD
để đề tài có thể được xét duyệt.

**Why this priority**: Tạo nguồn đề tài cho nhóm lựa chọn.
Nguồn: REQ-03, REQ-08–11 (phần GVHD); FR-05–06.

**Independent Test**: Dùng đợt và danh tính GV đã chuẩn bị; thử đề tài có số GVHD khác nhau.

**Acceptance Scenarios**:

1. **Given** thời điểm nằm trong khoảng đăng ký GV, đề tài thuộc bộ môn của GV đăng,
   có nội dung và 1 hoặc 2 GVHD cùng bộ môn đó, **When** GV gửi bản nháp của mình,
   **Then** đề tài chuyển sang Chờ duyệt với thông tin đã gửi và GV không được sửa (AS-2.1).
   Nội dung đầy đủ gồm tên đề tài, mô tả, mục tiêu, yêu cầu đối với SV và kết quả dự kiến.
2. **Given** thời điểm chắc chắn ngoài khoảng đăng ký GV,
   **When** gửi duyệt lần đầu hoặc gửi lại, **Then** bị từ chối, bản nháp không chuyển sang Chờ duyệt (AS-2.2).
3. **Given** bản nháp có 0 hoặc 3 GVHD, **When** gửi duyệt,
   **Then** bị từ chối, bản nháp không chuyển sang Chờ duyệt (AS-2.3).
   Bản nháp có thể chưa đủ thông tin; gửi duyệt bắt buộc có nội dung, bộ môn và 1–2 GVHD hợp lệ.
4. **Given** không chứng minh được quyền đăng đề tài,
   **When** gửi yêu cầu, **Then** bị từ chối, không tạo đề tài (AS-2.4).
5. **Given** GV thuộc bộ môn A, **When** đăng đề tài thuộc bộ môn B khác A,
   **Then** bị từ chối và không tạo đề tài, kể cả khi bên gọi tự khai thuộc B (AS-2.5).
6. **Given** đề tài thuộc bộ môn của GV đăng nhưng có ít nhất một GVHD khác bộ môn,
   **When** gửi đề tài với phân công đó, **Then** bị từ chối và không ghi nhận phân công
   trái quy tắc; áp dụng cho cả trường hợp một và hai GVHD (AS-2.6).

7. **Given** GV là người tạo bản nháp, **When** sửa bản nháp của mình,
   **Then** nội dung và danh sách GVHD được cập nhật; GV khác không được sửa bản nháp đó (AS-2.7).
8. **Given** đề tài Chờ duyệt, Đã duyệt hoặc Đã công bố, **When** GV đăng yêu cầu sửa,
   **Then** bị từ chối, nội dung và danh sách GVHD không đổi (AS-2.8).
9. **Given** đề tài bị trưởng khoa từ chối và đã về Nháp,
   **When** GV sửa đủ thông tin và gửi lại trong hạn GV,
   **Then** cùng đề tài chuyển trở lại Chờ duyệt (AS-2.9).

10. **Given** bản nháp thiếu hoặc để trống bất kỳ trường nào trong năm trường: tên đề tài,
    mô tả, mục tiêu, yêu cầu đối với SV, kết quả dự kiến,
    **When** gửi duyệt, **Then** bị từ chối và đề tài vẫn ở Nháp (AS-2.10).
11. **Given** bản nháp có đủ năm trường cùng bộ môn và 1–2 GVHD hợp lệ,
    **When** tác giả gửi duyệt trong hạn GV, **Then** cả năm giá trị được ghi nhận đúng
    trong đề tài Chờ duyệt (AS-2.11).

### User Story 3 - Duyệt và công bố danh sách (Priority: P1)

Là trưởng khoa, tôi muốn duyệt đề tài và công bố danh sách
để nhóm sinh viên có danh sách chính thức để lựa chọn.

**Why this priority**: Nhóm chỉ được chọn đề tài đã công bố.
Nguồn: REQ-02, REQ-08, REQ-15; FR-07.

**Independent Test**: Chuẩn bị danh sách đề tài, tài khoản trưởng khoa và tài khoản không có vai trò trưởng khoa;
thực hiện duyệt/công bố, không cần chạy lại thao tác tạo đợt.

**Acceptance Scenarios**:

1. **Given** đề tài Chờ duyệt đủ thông tin, **When** trưởng khoa duyệt,
   **Then** đề tài thành Đã duyệt; **When** trưởng khoa công bố đề tài đã duyệt,
   **Then** đề tài thành Đã công bố và có trong danh sách công bố của đợt (AS-3.1).
2. **Given** đề tài chưa công bố, **When** nhóm cố đăng ký đề tài đó,
   **Then** không tạo đăng ký hợp lệ (AS-3.2).
3. **Given** người không có vai trò trưởng khoa, kể cả GV đăng đề tài hoặc GVHD,
   **When** duyệt hoặc công bố, **Then** bị từ chối và trạng thái đề tài không đổi (AS-3.3).

4. **Given** đề tài Chờ duyệt, **When** trưởng khoa từ chối,
   **Then** đề tài trở về Nháp để GV sửa/gửi lại trong hạn GV (AS-3.4).
5. **Given** đề tài Nháp hoặc Chờ duyệt, **When** yêu cầu công bố trực tiếp,
   **Then** bị từ chối vì chưa Đã duyệt (AS-3.5).

Quyền, thứ tự duyệt/công bố và năm trường nội dung bắt buộc đã chốt.
Điều kiện lịch và công bố đã chốt:
đăng ký SV chỉ mở khi vừa trong hạn SV vừa có danh sách do trưởng khoa công bố.

### User Story 4 - Quản lý nhóm và trưởng nhóm (Priority: P1)

Là sinh viên, tôi muốn tạo nhóm và trở thành trưởng nhóm; với tư cách trưởng nhóm,
tôi muốn thêm/xóa thành viên, chuyển quyền cho thành viên khác và gửi đăng ký đề tài.

**Why this priority**: Nhóm là đơn vị đăng ký.
Nguồn: REQ-12–13; FR-08–09.

**Independent Test**: Chuẩn bị SV chưa có nhóm trong đợt, nhóm có trưởng nhóm và thành viên;
kiểm tra tạo nhóm, quyền quản lý, chuyển quyền và phạm vi tham gia.

**Acceptance Scenarios**:

1. **Given** nhóm hợp lệ, **When** xem thành phần nhóm,
   **Then** có từ 1 đến 3 thành viên và đúng 1 trưởng nhóm thuộc nhóm (AS-4.1).
   Nhóm một người đủ điều kiện về số lượng để đăng ký; thời điểm tạo nhóm vẫn còn mở.
2. **Given** nhóm có 3 thành viên, **When** thêm thành viên thứ tư,
   **Then** bị từ chối và nhóm vẫn có 3 thành viên (AS-4.2).
3. **Given** SV đang thuộc một nhóm trong một đợt đăng ký,
   **When** tham gia nhóm thứ hai trong cùng đợt,
   **Then** bị từ chối và không phát sinh hai tư cách thành viên trái quy tắc (AS-4.3).
4. **Given** người không phải trưởng nhóm của nhóm đó, kể cả thành viên thường,
   **When** thay đổi thành viên/trưởng nhóm, **Then** bị từ chối, nhóm không đổi (AS-4.4).

5. **Given** SV thuộc nhóm ở đợt A và chưa thuộc nhóm nào ở đợt B,
   **When** tham gia nhóm ở đợt B và thỏa các điều kiện khác đã xác nhận,
   **Then** không bị từ chối vì tư cách thành viên ở đợt A (AS-4.5).

6. **Given** SV chưa thuộc nhóm nào trong đợt và đủ điều kiện tạo nhóm,
   **When** SV tạo nhóm, **Then** SV đó là thành viên và trưởng nhóm duy nhất (AS-4.6).
7. **Given** trưởng nhóm và thao tác thêm/xóa thành viên thỏa ràng buộc nhóm,
   **When** thực hiện thao tác trong hạn SV và nhóm không có đăng ký chờ xét/đã chấp nhận,
   **Then** thành viên được cập nhật và nhóm vẫn có tối đa 3 SV, đúng một trưởng nhóm (AS-4.7).
8. **Given** trưởng nhóm và một thành viên khác của cùng nhóm,
   **When** chuyển quyền trong hạn SV và nhóm không có đăng ký chờ xét/đã chấp nhận,
   **Then** người nhận là trưởng nhóm duy nhất; người chuyển không còn quyền trưởng nhóm (AS-4.8).
9. **Given** người nhận không thuộc nhóm, **When** chuyển quyền trưởng nhóm cho người đó,
   **Then** bị từ chối và trưởng nhóm hiện tại không đổi (AS-4.9).

10. **Given** nhóm có đăng ký chờ xét/đã chấp nhận hoặc đã ngoài hạn SV,
    **When** trưởng nhóm thêm/xóa thành viên hoặc chuyển quyền,
    **Then** bị từ chối, thành viên và trưởng nhóm không đổi (AS-4.10).
11. **Given** đăng ký đã rút/bị từ chối và nhóm không còn đăng ký có hiệu lực,
    **When** trưởng nhóm sửa thành viên hoặc chuyển quyền trong hạn SV,
    **Then** được thực hiện nếu thỏa các ràng buộc còn lại (AS-4.11).

12. **Given** trưởng nhóm, nhóm còn chỗ và được phép sửa trong hạn SV,
    SV được thêm chưa thuộc nhóm khác trong đợt,
    **When** trưởng nhóm thêm SV, **Then** SV trở thành thành viên ngay và được tính
    vào giới hạn ba người/một nhóm trong đợt, không cần lời mời hoặc xác nhận (AS-4.12).

13. **Given** nhóm có 1, 2 hoặc 3 SV, đúng một trưởng nhóm và thỏa các điều kiện đăng ký khác,
    **When** trưởng nhóm gửi đăng ký, **Then** đăng ký được tiếp nhận; không yêu cầu
    nhóm một người phải thêm thành viên (AS-4.13).
14. **Given** yêu cầu đăng ký cho nhóm không có thành viên hoặc không có trưởng nhóm,
    **When** gửi đăng ký, **Then** bị từ chối và không tạo đăng ký hợp lệ (AS-4.14).

Đã chốt nhóm 1–3 SV, thêm trực tiếp, quyền và thời điểm sửa nhóm: trong hạn SV, không có đăng ký
chờ xét/đã chấp nhận. Q-07 còn về nhóm nháp/thời điểm tạo
và việc rời/chuyển nhóm. Không suy ra quyền tự sửa nhóm đã được chấp nhận.

### User Story 5 - Đăng ký và xử lý quyết định (Priority: P1)

Là nhóm sinh viên, chúng tôi muốn đăng ký một đề tài đã công bố trong hạn và có
quyết định chấp nhận hoặc từ chối. Trong hạn SV, trưởng nhóm muốn rút đăng ký chờ xét
và đăng ký lại đề tài cũ hoặc khác còn nhận đăng ký sau khi rút hoặc bị từ chối.

**Why this priority**: Hoàn tất kết quả nghiệp vụ của đợt triển khai đầu.
Nguồn: REQ-03, REQ-14–16; yêu cầu hiện tại bổ sung rõ nhánh từ chối; FR-10–12.

**Independent Test**: Chuẩn bị nhóm hợp lệ, đề tài đã công bố và khoảng đăng ký;
thử gửi đăng ký bằng trưởng nhóm và thử từng quyết định bằng trưởng khoa.

**Acceptance Scenarios**:

1. **Given** nhóm chưa có đăng ký xung đột, đề tài đã công bố chưa có nhóm được chấp nhận,
   thời điểm nằm trong hạn SV, kể cả khi đã có nhóm khác đăng ký chờ xét,
   **When** trưởng nhóm gửi đăng ký cho nhóm mình,
   **Then** đăng ký liên kết đúng nhóm, đề tài và đợt (AS-5.1).
2. **Given** ngoài hạn SV hoặc đề tài chưa công bố,
   **When** đăng ký, **Then** bị từ chối và không thêm đăng ký hợp lệ (AS-5.2).
3. **Given** nhóm đã có một đăng ký chờ xét hoặc đã được chấp nhận trong đợt,
   **When** đăng ký thêm đề tài khác, **Then** không hình thành hai đăng ký vi phạm
   quy tắc một đề tài của nhóm (AS-5.3).
4. **Given** đăng ký đủ điều kiện xét và đề tài chưa có nhóm được chấp nhận trong đợt,
   **When** trưởng khoa chấp nhận,
   **Then** kết quả là chấp nhận và nhóm được gắn với đề tài cùng GVHD được phân công (AS-5.4).
5. **Given** đăng ký đủ điều kiện xét, **When** trưởng khoa từ chối,
   **Then** kết quả là từ chối, không coi nhóm đã được chấp thuận thực hiện đề tài (AS-5.5).
   Nhóm được đăng ký lại đề tài cũ hoặc khác còn nhận đăng ký trong hạn SV;
   tính bắt buộc của lý do từ chối vẫn chưa xác nhận.
6. **Given** người không có quyền gửi hoặc quyết định đăng ký,
   **When** thực hiện thao tác tương ứng, **Then** bị từ chối, dữ liệu nghiệp vụ không đổi (AS-5.6).
   Với quyết định chấp nhận/từ chối, người không có vai trò trưởng khoa phải bị từ chối,
   kể cả GVHD và GV đăng đề tài. Với gửi đăng ký, chỉ trưởng nhóm của chính nhóm đó được phép;
   thành viên thường và trưởng nhóm của nhóm khác phải bị từ chối.

7. **Given** đề tài đã có một nhóm được chấp nhận trong đợt,
   **When** trưởng khoa chấp nhận thêm nhóm khác cho cùng đề tài và đợt,
   **Then** thao tác bị từ chối, số nhóm được chấp nhận vẫn là một (AS-5.7).
8. **Given** hai nhóm chờ xét cho cùng đề tài trong cùng đợt, chưa nhóm nào được chấp nhận,
   **When** hai thao tác chấp nhận được gửi đồng thời,
   **Then** không có kết quả nào chứa hai nhóm được chấp nhận cho đề tài đó trong đợt (AS-5.8).

9. **Given** đăng ký đang chờ xét và thời điểm trong hạn SV,
   **When** trưởng nhóm của nhóm đó rút đăng ký,
   **Then** đăng ký trở thành đã rút và không còn là đăng ký có hiệu lực của nhóm (AS-5.9).
10. **Given** đăng ký trước đã bị từ chối hoặc đã rút, nhóm không có đăng ký chờ xét/được
    chấp nhận khác trong đợt, thời điểm trong hạn SV,
    **When** trưởng nhóm đăng ký đề tài cũ hoặc đề tài khác đã công bố còn nhận đăng ký,
    **Then** tạo được đăng ký chờ xét mới nếu thỏa các điều kiện còn lại (AS-5.10).
11. **Given** đăng ký đã được chấp nhận, hoặc đã ngoài hạn SV,
    **When** trưởng nhóm yêu cầu rút, **Then** bị từ chối và đăng ký không đổi (AS-5.11).
12. **Given** đăng ký đang chờ xét trong hạn SV và người gọi không phải trưởng nhóm của nhóm,
    **When** yêu cầu rút, **Then** bị từ chối và đăng ký không đổi (AS-5.12).
13. **Given** đăng ký trước đã bị từ chối hoặc rút nhưng đã ngoài hạn SV,
    **When** trưởng nhóm đăng ký lại, **Then** bị từ chối, không tạo đăng ký mới (AS-5.13).
14. **Given** nhiều nhóm chờ xét cùng đề tài trong một đợt,
    **When** trưởng khoa chấp nhận một nhóm,
    **Then** mọi đăng ký khác còn chờ xét cho đề tài trong đợt chuyển thành từ chối
    với lý do “Đề tài đã nhận nhóm”; đề tài ngừng nhận đăng ký mới trong đợt (AS-5.14).
15. **Given** đề tài đã nhận nhóm trong đợt, dù vẫn còn hạn SV,
    **When** trưởng nhóm khác gửi đăng ký mới hoặc đăng ký lại cho đề tài đó,
    **Then** bị từ chối, không tạo đăng ký chờ xét mới (AS-5.15).
16. **Given** nhóm bị tự động từ chối do đề tài đã nhận nhóm khác,
    **When** trưởng nhóm đăng ký đề tài khác đã công bố còn nhận đăng ký trong hạn SV,
    **Then** được đăng ký nếu thỏa các điều kiện còn lại; đăng ký bị từ chối không giữ
    quyền đăng ký duy nhất của nhóm (AS-5.16).
17. **Given** đã đến ngày bắt đầu SV nhưng danh sách chưa được trưởng khoa công bố,
    **When** nhóm gửi đăng ký, **Then** bị từ chối; sau khi công bố trong hạn SV,
    đăng ký được tiếp nhận nếu thỏa các điều kiện còn lại (AS-5.17).
18. **Given** danh sách đã công bố và các điều kiện khác hợp lệ,
    **When** gửi đăng ký từ đầu ngày bắt đầu SV đến hết ngày kết thúc SV, **Then** trong hạn;
    **When** gửi sau ngày kết thúc SV, **Then** ngoài hạn và bị từ chối (AS-5.18).

Quy tắc biên ngày tương tự áp dụng cho đăng đề tài của GV, đăng ký lại và rút
đăng ký chờ xét của trưởng nhóm. Ngày hiển thị/nhập lịch dùng Asia/Ho_Chi_Minh.

Đã chốt quyền gửi/rút, giới hạn số nhóm được chấp nhận và chính sách đăng ký lại.
Đăng ký có hiệu lực đối với giới hạn một đề tài/nhóm là đăng ký chờ xét hoặc được chấp nhận;
đăng ký đã rút/bị từ chối không ngăn đăng ký mới hợp lệ. Không cho đổi đề tài trực tiếp
trên đăng ký đã được chấp nhận bằng luồng rút/đăng ký lại này.
Còn Q-07 về điều kiện nhóm, Q-09 về lý do từ chối thủ công
và ưu tiên quyết định đồng thời. Q-10 đã được giải quyết cho luồng hiện tại.

### Edge Cases

- GV đăng hoặc một trong hai GVHD khác bộ môn đề tài: từ chối theo AS-2.5–2.6.
  Phiên bản đầu giả định bộ môn của giảng viên ổn định khi còn đề tài/phân công hoạt động
  và không cung cấp thao tác chuyển giảng viên; đây là chức năng mở rộng nếu làm sau.
- Đầu ngày bắt đầu được tính trong hạn và toàn bộ ngày kết thúc vẫn trong hạn;
  dùng Asia/Ho_Chi_Minh. Hai khoảng GV/SV không chồng lấn, GV trước SV.
- Đến hạn SV nhưng chưa công bố danh sách: chưa nhận đăng ký; công bố sau ngày kết thúc
  SV không tự gia hạn hoặc mở lại đợt. Tên, loại và các ngày bắt đầu bị khóa từ đầu ngày bắt đầu GV;
  chỉ kéo dài ngày kết thúc trước khi giai đoạn tương ứng hết hạn.
- Trước ngày bắt đầu GV, đổi loại vẫn phải tuân thủ điều kiện ngày áp dụng: tự xóa ngày
  không còn áp dụng và từ chối toàn bộ thay đổi nếu thiếu ngày bắt buộc của loại mới.
- Kiểm tra quyền sửa lịch theo đầu ngày bắt đầu GV đang có hiệu lực trước thay đổi,
  không dùng ngày bắt đầu mới để vượt qua trạng thái đã khóa.
- Bản nháp có thể chưa đủ thông tin; gửi duyệt phải đủ nội dung, bộ môn và 1–2 GVHD hợp lệ.
- GV không sửa đề tài ngoài trạng thái Nháp. Từ chối duyệt đưa đề tài về Nháp;
  nếu đã hết hạn GV thì không được gửi lại, không tự gia hạn.
- Người tạo nhóm trở thành trưởng nhóm; chuyển quyền chỉ cho thành viên cùng nhóm và
  không tạo hai trưởng nhóm. Nhóm một SV được đăng ký, SV đó là trưởng nhóm.
- Thêm/xóa thành viên, chuyển trưởng nhóm chỉ trong hạn SV khi không có đăng ký có hiệu lực;
  sau rút/từ chối được sửa nếu còn hạn. Sửa đồng thời với gửi đăng ký không được làm thay đổi
  thành phần nhóm sau khi đăng ký đã chuyển sang chờ xét.
- Cùng lúc thêm hai SV vào nhóm đang có hai người: kết quả không vượt 3 thành viên.
- Cùng SV tham gia hai nhóm đồng thời trong cùng đợt: kết quả không được có hai tư cách
  thành viên trong đợt đó. Tham gia nhóm ở hai đợt khác nhau không vi phạm REQ-13 đã làm rõ.
- Cùng nhóm gửi hai đăng ký đồng thời: không tạo hai đăng ký vi phạm REQ-14.
- Nhiều nhóm có thể chờ xét cùng đề tài; tối đa một nhóm được chấp nhận trong mỗi đợt,
  kể cả khi quyết định đồng thời. Không mặc định nhóm đăng ký trước được ưu tiên.
- Khi một nhóm được chấp nhận, mọi đăng ký còn chờ xét khác cùng đề tài/đợt tự động
  bị từ chối với lý do “Đề tài đã nhận nhóm”; ngừng nhận đăng ký mới cho đề tài trong đợt.
  Không chuyển lại trạng thái các đăng ký đã rút hoặc đã từ chối trước đó.
- Đăng ký mới đồng thời với chấp nhận nhóm khác không được để lại đăng ký chờ xét
  cho đề tài đã nhận nhóm: nếu đăng ký được ghi nhận trước thì thuộc diện tự động từ chối,
  nếu đề tài đã nhận nhóm trước thì từ chối tạo đăng ký mới.
- Duyệt/công bố hoặc quyết định đăng ký đồng thời với thay đổi dữ liệu liên quan:
  không được bỏ qua quyền/ràng buộc; quy tắc sửa và ưu tiên quyết định còn mở.
- Trong hạn SV, trưởng nhóm được rút đăng ký chờ xét và đăng ký lại đề tài cũ/khác còn
  nhận đăng ký sau rút hoặc từ chối; ngoài hạn không được rút/đăng ký lại.
- Đăng ký đã chấp nhận không được rút. Rút đồng thời với chấp nhận phải bảo toàn trạng thái:
  không chấp nhận đăng ký đã rút, không rút đăng ký đã chấp nhận; thứ tự ưu tiên chưa chốt.
- Hai yêu cầu đăng ký lại đồng thời không được tạo hai đăng ký có hiệu lực cho một nhóm.
- Gửi lại thao tác hoặc giả mạo danh tính/nhóm/role: không được tạo dữ liệu trái ràng buộc
  hoặc cấp quyền chỉ vì thông tin bên gọi tự khai.
- Tài khoản STUDENT đồng thời có vai trò LECTURER hoặc DEAN là cấu hình không hợp lệ.
  LECTURER có thể đồng thời giữ DEAN. ADMIN là vai trò xử lý sự cố khẩn cấp và không
  mang quyền học vụ; STUDENT không kết hợp với ADMIN, LECTURER hoặc DEAN.
- Xử lý khẩn cấp không xóa cứng nội dung: nội dung bị ẩn, tài khoản gây sự cố bị khóa,
  và lưu người xử lý/thời gian/lý do. Việc khôi phục phải dựa trên dấu vết này và không
  tự thay đổi trạng thái nghiệp vụ khác.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Yêu cầu | Căn cứ / trạng thái | Nghiệm thu |
|---|---|---|---|
| FR-01 | Nhận diện người dùng qua tài khoản nội bộ cấp sẵn bằng tên đăng nhập và mật khẩu, được seed/import local với các role ADMIN, STUDENT, LECTURER, DEAN. LECTURER có thể kết hợp DEAN; STUDENT không kết hợp với ADMIN, LECTURER hoặc DEAN. Trưởng khoa chỉ quản lý trong khoa mình, giảng viên thao tác đề tài trong bộ môn mình, sinh viên xem đề tài đã công bố trong khoa và chỉ thay đổi nhóm/đăng ký mình có quyền. ADMIN chỉ xử lý sự cố khẩn cấp: ẩn nội dung vi phạm, lưu người xử lý/thời gian/lý do và khóa tài khoản gây sự cố; không xóa cứng dữ liệu, không sửa nội dung và không quản lý nghiệp vụ học tập, đăng ký, role, khoa/bộ môn hoặc chuyển giảng viên. Việc duyệt học vụ thuộc trưởng khoa; chuyển giảng viên là chức năng mở rộng chưa có trong phiên bản đầu. Không cung cấp tự đăng ký và không chấp nhận quyền/phạm vi do bên gọi tự khai. | U: các phiên 2026-09-08 và 2026-09-09, Q-18 đã giải quyết; REQ-28, REQ-31–32, INF-06; constitution III | AS-1.3, 1.5–1.8, 1.14–1.16, 2.4, 3.3, 4.4, 5.6 |
| FR-02 | Trưởng khoa tạo đợt có tên và một trong bốn loại môn học/NCKH/TLCN/KLTN. | E: REQ-01, 04 | AS-1.1, 1.4 |
| FR-03 | Đợt có ngày bắt đầu/kết thúc đăng ký GV và SV theo Asia/Ho_Chi_Minh; form không nhận giờ/phút/giây hoặc ngày trước ngày hiện tại. Trong từng khoảng, ngày bắt đầu không sau ngày kết thúc; ngày kết thúc GV phải trước ngày bắt đầu SV. Tự mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc. Áp dụng khoảng SV cho đăng ký, đăng ký lại và rút chờ xét; đăng ký SV còn phải có danh sách đã công bố. | E: REQ-02–05; chi tiết xác nhận U, phiên 2 ngày 2026-09-08 và điều chỉnh ngày 2026-09-10, Q-03–04 | AS-1.1, 1.4, 1.9–1.10, 2.2, 5.2, 5.11, 5.13, 5.17–5.18 |
| FR-04 | Hạn nộp điểm GVPB chỉ cấu hình và bắt buộc cho TLCN/KLTN; ngày báo cáo hội đồng chỉ cấu hình và bắt buộc cho KLTN. Cả hai là ngày lịch theo Asia/Ho_Chi_Minh, không có giờ/phút/giây và khi tạo không được trước ngày hiện tại. Khi đổi loại trước ngày mở GV, tự xóa ngày không còn áp dụng và chỉ lưu nếu đã nhập đủ ngày bắt buộc của loại mới. Khi sửa trực tiếp một ngày đang áp dụng, chỉ được dời sang ngày muộn hơn nếu ngày hiện tại còn trước ngày đang lưu; từ đầu ngày đang lưu thì khóa và không mở lại. | E: REQ-06–07; Q-02 được người dùng giải quyết ngày 2026-09-10 | AS-1.2, 1.4, 1.11–1.12 |
| FR-05 | GV chỉ được gửi duyệt đề tài thuộc bộ môn mình trong hạn GV. Khi gửi duyệt bắt buộc có tên đề tài, mô tả, mục tiêu, yêu cầu đối với SV và kết quả dự kiến; không chấp nhận trường thiếu hoặc trống. Bộ môn người đăng phải được kiểm chứng từ dữ liệu đáng tin cậy. Mỗi đề tài chỉ thuộc một đợt; phiên bản đầu không có chức năng dùng lại hoặc sao chép đề tài sang đợt khác, nên GV phải tạo đề tài mới trong đợt mới. | E: REQ-08–09; giới hạn bộ môn và năm trường xác nhận U, phiên 2–3 ngày 2026-09-08; quan hệ theo đợt xác nhận U ngày 2026-09-10, Q-05/Q-18 | AS-2.1–2.2, 2.5, 2.10–2.11 |
| FR-06 | Đề tài được phân công 1–2 GVHD, tất cả cùng bộ môn của đề tài và người đăng. Gửi duyệt bắt buộc có nội dung, bộ môn và 1–2 GVHD hợp lệ; bản nháp có thể chưa đủ. GV đăng lựa chọn GVHD khi sửa bản nháp của mình. Danh sách lựa chọn chỉ lấy từ tài khoản seed/import đang hoạt động, có role LECTURER và cùng bộ môn; phiên bản đầu không có màn hình quản lý danh mục. Chỉ tác giả được thay đổi GVHD khi đề tài ở Nháp; từ Chờ duyệt trở đi danh sách bị khóa. Nếu đề tài bị từ chối về Nháp, tác giả được sửa GVHD và gửi lại trong hạn; không hỗ trợ thay GVHD trong quá trình thực hiện. | E: REQ-10; REQ-11 phần GVHD; giới hạn bộ môn xác nhận U, phiên 2 ngày 2026-09-08; nguồn danh mục và phạm vi thay đổi xác nhận U ngày 2026-09-10, giải quyết Q-06 | AS-2.1, 2.3, 2.6–2.9 |
| FR-07 | Chỉ trưởng khoa được duyệt đề tài và công bố danh sách trước khi nhóm chọn đề tài từ danh sách đó. Đến ngày bắt đầu SV không thay thế việc công bố; công bố không bỏ qua ngày kết thúc SV. | E: REQ-02, 08, 15; quyền/lịch xác nhận U, các phiên ngày 2026-09-08 | AS-3.1–3.3, 5.17–5.18 |
| FR-08 | SV tạo nhóm trở thành trưởng nhóm. Chỉ trưởng nhóm được thêm/xóa thành viên và chuyển quyền cho thành viên khác cùng nhóm, trong hạn SV và khi nhóm không có đăng ký chờ xét hoặc đã chấp nhận. Thêm trực tiếp, không qua lời mời/xác nhận; SV là thành viên ngay nếu nhóm còn chỗ và SV chưa thuộc nhóm khác trong đợt. Sau rút/từ chối được sửa nếu còn hạn và không có đăng ký có hiệu lực khác. Nhóm từ 1 đến 3 SV, đúng một trưởng nhóm là thành viên; nhóm một người được gửi đăng ký nếu thỏa điều kiện khác. | E: REQ-12–13; quyền, thời điểm và thêm trực tiếp xác nhận U, các phiên ngày 2026-09-08; còn Q-07 về điều kiện nhóm | AS-4.1–4.2, 4.4, 4.6–4.14 |
| FR-09 | Mỗi SV chỉ thuộc một nhóm trong từng đợt đăng ký. Không từ chối tư cách thành viên ở đợt khác chỉ vì SV đã thuộc nhóm trong một đợt. | E: REQ-13; phạm vi được xác nhận U, Session 2026-09-08, giải quyết Q-08 | AS-4.3, 4.5 |
| FR-10 | Chỉ trưởng nhóm được gửi đăng ký cho nhóm mình; nhóm chỉ có một đăng ký có hiệu lực (chờ xét hoặc đã chấp nhận) cho đề tài đã công bố trong đợt và chỉ gửi đăng ký trong hạn SV. Nhiều nhóm được đăng ký chờ xét cùng đề tài; một đăng ký chờ xét không giữ chỗ độc quyền. | E: REQ-03, 14–15; Q-10 được xác nhận U, Session 2026-09-08; còn Q-07, Q-09 | AS-5.1–5.3 |
| FR-11 | Chỉ trưởng khoa được chấp nhận đăng ký trước khi nhóm thực hiện đề tài với GVHD được phân công. Mỗi đề tài trong một đợt chỉ được chấp nhận tối đa một nhóm, kể cả khi thao tác đồng thời. Khi chấp nhận, tự động từ chối các đăng ký còn chờ xét khác cùng đề tài/đợt với lý do “Đề tài đã nhận nhóm” và ngừng nhận đăng ký mới trong đợt. | E: REQ-16; quyền và giới hạn xác nhận U, các phiên ngày 2026-09-08 (Q-01, Q-10); còn Q-09 về ưu tiên thao tác | AS-5.4, 5.6–5.8, 5.14–5.16 |
| FR-12 | Chỉ trưởng khoa được quyết định từ chối thủ công; hệ thống tự động từ chối theo FR-11 khi đề tài nhận nhóm khác. Không coi đăng ký bị từ chối là đã chấp thuận. Sau từ chối, trưởng nhóm được đăng ký lại đề tài cũ hoặc khác còn nhận đăng ký trong hạn SV theo FR-13. | U: phạm vi và các phiên ngày 2026-09-08; liên quan REQ-16, Q-09–10; PDF không mô tả nhánh từ chối | AS-5.5–5.6, 5.14–5.16 |
| FR-13 | Trong hạn SV, chỉ trưởng nhóm của nhóm được rút đăng ký đang chờ xét. Sau khi rút hoặc bị từ chối, trưởng nhóm được đăng ký lại đề tài cũ hoặc đề tài khác đã công bố còn nhận đăng ký, nếu không có đăng ký có hiệu lực khác trong đợt. Không cho rút đăng ký đã chấp nhận; không cho rút hoặc đăng ký lại ngoài hạn SV. | U: Session 2026-09-08, Q-09; liên quan REQ-03, REQ-14–16, REQ-31 | AS-5.9–5.13 |
| FR-14 | Vòng đời đề tài: Nháp → Chờ duyệt → Đã duyệt → Đã công bố. GV chỉ sửa nội dung và GVHD của bản nháp mình tạo, rồi gửi duyệt trong hạn GV. Trưởng khoa duyệt/từ chối đề tài Chờ duyệt; từ chối đưa về Nháp để tác giả sửa/gửi lại trong hạn GV. Chỉ đề tài Đã duyệt được công bố; nội dung và GVHD bị khóa khi Chờ duyệt, Đã duyệt hoặc Đã công bố. Phiên bản đầu không hỗ trợ thay GVHD trong quá trình thực hiện. | U: các phiên ngày 2026-09-08 và 2026-09-10, Q-05–06 đã giải quyết; liên quan REQ-02–03, REQ-08–11, REQ-15, REQ-31 | AS-2.1–2.3, 2.7–2.9, 3.1, 3.4–3.5 |

| FR-15 | Trước đầu ngày bắt đầu GV, trưởng khoa được sửa tên, loại và bốn ngày đăng ký khi cấu hình vẫn hợp lệ và không đưa ngày bắt đầu GV về trước ngày hiện tại; đổi loại tự xóa ngày không còn áp dụng và yêu cầu đủ ngày của loại mới. Từ đầu ngày bắt đầu GV, khóa tên, loại và các ngày bắt đầu. Chỉ được kéo dài ngày kết thúc của giai đoạn chưa hết hạn; không rút ngắn, không mở lại giai đoạn đã đóng và lịch sau sửa vẫn phải đúng thứ tự. Mỗi ngày phụ đang áp dụng chỉ được dời muộn hơn trước ngày đang lưu. | U: Q-02 được người dùng giải quyết ngày 2026-09-10, thay thế quyết định khóa toàn bộ bốn mốc ở phiên 3; liên quan REQ-03–07, REQ-31 | AS-1.11–1.13 |

### Non-Functional Requirements

- **NFR-01 — Bảo mật nền tảng:** Mọi quyền được kiểm tra ở backend; các POST dùng CSRF;
  mật khẩu được băm; đầu vào được validation; session được bảo vệ; secret không nằm trong
  repository hoặc log. Các ca sai quyền/giả mạo phải không làm thay đổi dữ liệu.
- **NFR-02 — Responsive:** Các trang chính phải sử dụng được ở desktop, tablet và mobile.
  Ở viewport 360 px, 768 px và 1280 px, nội dung không tràn ngang toàn trang; bảng rộng
  được cuộn trong vùng chứa; menu, form và thao tác chính vẫn nhìn thấy, đọc được và dùng
  bằng bàn phím. Không đặt SLA định lượng về tải, uptime hoặc backup cho phiên bản local.

### Scope and Dependencies

Trong phạm vi: FR-01–15, gồm cấu hình hai mốc liên quan giai đoạn sau trong FR-04.
Nhận diện/phân quyền dùng tài khoản nội bộ cấp sẵn với tên đăng nhập/mật khẩu và vai trò/bộ môn.
Tài khoản được seed/import local và không cung cấp tự đăng ký. Role ADMIN chỉ xử lý sự cố
khẩn cấp bằng cách ẩn nội dung, lưu dấu vết và khóa tài khoản gây sự cố; không có quyền học
vụ, đăng ký hoặc thay đổi cơ cấu tổ chức. Nguồn danh tính cho đợt đầu không phụ thuộc đăng
nhập của trường.

Ngoài đợt này nhưng vẫn trong tổng thể: nộp báo cáo REQ-17; phân công GVPB thuộc
REQ-11; phản biện/hội đồng REQ-18–22; chấm điểm REQ-23–25; kết quả REQ-26–27;
thông báo REQ-29–30; phần quản trị tài khoản REQ-28 chưa nằm trong luồng.
REQ-31–32 áp dụng xuyên suốt. Cấu hình REQ-06–07 không kéo nghiệp vụ chấm điểm/hội đồng vào đợt này.

Phụ thuộc nghiệp vụ: danh tính, vai trò và dữ liệu GV/SV/bộ môn được seed/import đáng tin cậy;
quyền trưởng khoa cho duyệt/công bố, quyết định đăng ký và quy tắc lịch đã xác nhận;
điều kiện nhóm và một số chuyển trạng thái còn cần xác nhận. Danh tính là tài khoản nội bộ
cấp sẵn qua seed/import local; phiên bản đầu không có màn hình quản lý danh mục.
Không mặc định tích hợp bên ngoài. Không cần các tính năng triển khai sau để đặc tả luồng này.

### Authorization and Automated Verification

Đây là yêu cầu chất lượng từ U và constitution III–X, không phải quy tắc mới suy ra từ PDF.
Không chỉ định giao thức, framework, database hay công cụ kiểm thử.

| ID | Kiểm chứng tự động bắt buộc khi triển khai | Liên kết |
|---|---|---|
| AT-01 | Bộ ca bốn loại đợt kiểm tra trường bắt buộc và ngày áp dụng; dữ liệu sai không tạo đợt hợp lệ. | FR-02–04; AS-1.1–1.4 |
| AT-02 | Ca trước/trong/sau từng ngày biên GV/SV theo Asia/Ho_Chi_Minh: mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc. Kiểm tra ngày đã qua, giá trị có giờ, lịch chồng lấn, sai thứ tự hoặc ngày kết thúc GV trùng ngày bắt đầu SV bị từ chối; ngày SV kế tiếp hợp lệ; chưa công bố không nhận đăng ký SV. Áp dụng biên SV cho đăng ký lại và rút chờ xét. | FR-03, 05, 07, 10, 13; AS-1.4, 1.9–1.10, 5.17–5.18; Q-03–04 đã xác nhận và điều chỉnh ngày 2026-09-10 |
| AT-03 | Ca 1/2 GVHD cùng bộ môn hợp lệ; từ chối người đăng/GVHD khác bộ môn, kể cả giả mạo. Gửi duyệt với 0/3 GVHD bị từ chối. Lần lượt bỏ hoặc để trống từng trường tên đề tài, mô tả, mục tiêu, yêu cầu đối với SV, kết quả dự kiến: không chuyển khỏi Nháp. Đủ cả năm trường và điều kiện khác: chuyển Chờ duyệt, giữ đúng nội dung. | FR-05–06; AS-2.1–2.6, 2.10–2.11 |
| AT-04 | Ca tạo nhóm với người tạo là trưởng nhóm; thêm/xóa thành viên; chuyển quyền cho thành viên cùng nhóm; từ chối người nhận ngoài nhóm; giữ từ 1 đến 3 thành viên và đúng một trưởng nhóm. Từ chối SV thuộc hai nhóm cùng đợt, không xung đột với nhóm ở đợt khác. Từ chối sửa nhóm khi chờ xét/đã chấp nhận hoặc ngoài hạn SV; cho phép sau rút/từ chối trong hạn nếu không có đăng ký có hiệu lực. Kiểm tra cả hai biên thời gian SV; thêm trực tiếp làm SV thành thành viên ngay, không cần lời mời/xác nhận. Kiểm tra nhóm 1/2/3 người đăng ký hợp lệ, nhóm không thành viên/không trưởng nhóm bị từ chối. | FR-08–09; AS-4.1–4.14 |
| AT-05 | Ca đề tài công bố/chưa công bố, đăng ký đầu/thứ hai của nhóm, nhiều nhóm chờ xét cùng đề tài, trưởng khoa chấp nhận/từ chối; từ chối chấp nhận nhóm thứ hai cho cùng đề tài/đợt. Kiểm tra rút chờ xét trong hạn; đăng ký lại đề tài cũ/khác sau từ chối/rút; từ chối rút đã chấp nhận, rút/đăng ký lại ngoài hạn và rút sai quyền. | FR-07, 10–13; AS-5.7, 5.9–5.13 |
| AT-06 | Mỗi thao tác được bảo vệ có ca quyền hợp lệ và bị từ chối, kể cả gọi trực tiếp backend và giả mạo role/quan hệ; xác nhận không thay đổi trái phép. Duyệt/công bố đề tài và chấp nhận/từ chối đăng ký: cho phép trưởng khoa; từ chối tài khoản không có vai trò trưởng khoa, gồm GV đăng đề tài và GVHD. Quản lý thành viên/chuyển quyền/gửi hoặc rút đăng ký: chỉ trưởng nhóm của nhóm đó; từ chối thành viên thường, trưởng nhóm khác và người đã chuyển quyền. | FR-01–13; constitution III, VIII; Session 2026-09-08 |
| AT-07 | Ca đồng thời thêm thành viên, cùng SV tham gia hai nhóm trong cùng đợt và nhóm đăng ký đề tài; kết quả không vi phạm REQ-12–14. Kiểm tra nhóm ở hai đợt khác nhau không xung đột vì REQ-13. Hai thao tác chấp nhận nhóm khác nhau cho cùng đề tài/đợt không được tạo hai nhóm được chấp nhận. Không mặc định cách chọn người thắng. | FR-08–11, 13; AS-5.8; constitution VI; Q-08, Q-10 đã xác nhận; còn Q-09 |
| AT-08 | Ca quyền hoặc dữ liệu liên quan thay đổi trong lúc xử lý quyết định; kết quả vẫn tuân thủ chính sách đã chốt, không ghi trạng thái trái phép. Ca chuyển trưởng nhóm đồng thời với quản lý thành viên/gửi đăng ký phải giữ đúng một trưởng nhóm và không dùng quyền đã mất để thực hiện thao tác trái phép.  Rút và chấp nhận đồng thời không được tạo trạng thái vừa rút vừa chấp nhận; đăng ký lại đồng thời không tạo hai đăng ký có hiệu lực. | FR-01, 07–08, 10–13 |
| AT-09 | Đầu vào sai tại ranh giới hệ thống bị từ chối và không lưu thay đổi dở dang. | constitution IV; các FR liên quan |
| AT-10 | Đăng nhập đúng thông tin tài khoản cấp sẵn; từ chối sai mật khẩu/tài khoản không tồn tại và thao tác được bảo vệ khi chưa đăng nhập. Cho phép LECTURER kết hợp DEAN; từ chối STUDENT kết hợp ADMIN, LECTURER hoặc DEAN. Vai trò/khoa/bộ môn đúng dữ liệu được cấp, không thể tự khai để nâng quyền; kiểm tra từ chối truy cập chéo phạm vi và không cung cấp tự đăng ký. Tài khoản ADMIN bị từ chối mọi thao tác học vụ, đăng ký, quản lý role/khoa/bộ môn và chuyển giảng viên. | FR-01; AS-1.5–1.8, 1.14–1.15; REQ-31–32 |
| AT-11 | Chấp nhận một nhóm làm mọi đăng ký khác còn chờ xét cùng đề tài/đợt bị từ chối đúng lý do; không đổi đăng ký đã rút/từ chối. Từ chối đăng ký mới vào đề tài đã nhận nhóm, kể cả tranh chấp gửi/chấp nhận. Nhóm bị tự động từ chối được đăng ký đề tài khác hợp lệ trong hạn. | FR-10–13; AS-5.14–5.16; Q-10 đã xác nhận |

Migration khi sử dụng database, tách nghiệp vụ khỏi controllers và bảo vệ secrets
tuân thủ constitution V, VII, IX; lựa chọn kỹ thuật được xử lý sau khi hỏi chủ dự án.
Các kiểm thử chưa thể có kết quả kỳ vọng xác định do Q phải được bổ sung sau xác nhận,
không đánh dấu đã đạt.

Yêu cầu kiểm thử tự động AT-12: kiểm tra Nháp → Chờ duyệt → Đã duyệt → Đã công bố;
chỉ tác giả sửa Nháp, thiếu thông tin không gửi duyệt; khóa sửa từ Chờ duyệt;
trưởng khoa từ chối đưa về Nháp; gửi lại trong hạn thành công và ngoài hạn bị từ chối;
không công bố Nháp/Chờ duyệt. Kiểm tra sửa đồng thời với gửi duyệt không làm nội dung
Chờ duyệt tiếp tục bị thay đổi. Liên kết FR-05–07, FR-14, AS-2.7–2.9, AS-3.4–3.5.

Yêu cầu kiểm thử tự động AT-13: sửa tên/loại/bốn ngày đăng ký trước đầu ngày bắt đầu GV bằng
trưởng khoa được phép nếu cấu hình hợp lệ. Từ đầu ngày bắt đầu GV, kiểm tra khóa tên/loại/ngày
bắt đầu, chỉ kéo dài mốc kết thúc của giai đoạn chưa hết hạn, không rút ngắn hoặc mở lại.
Kiểm tra ngày phụ chỉ được dời muộn hơn khi ngày hiện tại còn trước ngày đang lưu và bị
khóa từ đầu ngày đang lưu; dời ngày bắt đầu về tương lai không được vượt khóa. Người không
phải trưởng khoa bị từ chối. Ca sửa đồng thời với thời điểm khóa không được ghi thay đổi
vi phạm. Liên kết FR-04, FR-15, AS-1.11–1.13; giữ dữ liệu không đổi khi từ chối.

Yêu cầu kiểm thử tự động AT-14: ADMIN có lý do được ẩn nội dung vi phạm, khóa tài khoản
gây sự cố và lưu đúng người xử lý/thời gian/lý do; nội dung không còn hiển thị nhưng không
bị xóa khỏi database. Từ chối ADMIN sửa nội dung hoặc thực hiện học vụ/đăng ký; người không
có ADMIN không được xử lý khẩn cấp. Liên kết FR-01, AS-1.15–1.16.

Yêu cầu kiểm thử tự động AT-15: chạy các trang chính ở viewport 360 px, 768 px và 1280 px;
không có tràn ngang toàn trang, bảng rộng cuộn trong vùng chứa, menu/form/thao tác chính
dùng được bằng bàn phím và trạng thái không chỉ thể hiện bằng màu. Liên kết NFR-02, SC-009.

### Key Entities *(include if feature involves data)*

- **Người dùng và vai trò/ngữ cảnh**: danh tính GV/SV/trưởng khoa; quyền trên thao tác
  và quan hệ với nhóm/đề tài. Tài khoản nội bộ cấp sẵn, đăng nhập bằng tên đăng nhập và
  mật khẩu, được gán vai trò/bộ môn qua seed/import. Không tự đăng ký. ADMIN là vai trò xử lý sự cố:
  ẩn nội dung, lưu dấu vết và khóa tài khoản gây sự cố, không có quyền học vụ/đăng ký.
  Bộ môn của GV là dữ liệu dùng để kiểm tra quyền đăng đề tài và tính hợp lệ của GVHD;
  biểu mẫu chỉ chọn tài khoản LECTURER đang hoạt động cùng bộ môn với người đăng.
- **Đợt đăng ký**: tên, loại, bốn ngày đăng ký GV/SV, hai ngày có điều kiện; hai giai đoạn.
  Tất cả trường lịch chỉ nhận ngày theo Asia/Ho_Chi_Minh, không nhận ngày đã qua hoặc
  giờ/phút/giây; mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc. Khoảng GV trước SV.
  Hạn điểm GVPB và ngày hội đồng chỉ có độ chính xác ngày lịch; từng ngày chỉ được dời
  muộn hơn trước ngày đang lưu. Từ đầu ngày bắt đầu GV hiện hành, tên, loại và các ngày bắt đầu
  bị khóa; ngày kết thúc chỉ được kéo dài trước khi giai đoạn tương ứng hết hạn.
- **Bộ môn**: đơn vị mà đề tài thuộc về; dữ liệu được seed/import sẵn và phiên bản đầu
  không có màn hình quản lý danh mục bộ môn.
- **Đề tài**: tên đề tài, mô tả, mục tiêu, yêu cầu đối với SV, kết quả dự kiến;
  bộ môn, GV đăng, quan hệ 1–2 GVHD, tình trạng duyệt/công bố.
  Mỗi đề tài chỉ thuộc một đợt; không có chức năng dùng lại hoặc sao chép sang đợt khác
  trong phiên bản đầu, nên GV tạo đề tài mới nếu muốn đăng lại nội dung ở đợt mới.
  Trong một đợt có thể có nhiều nhóm đăng ký chờ xét nhưng tối đa một nhóm được chấp nhận.
  Trạng thái: Nháp, Chờ duyệt, Đã duyệt, Đã công bố; từ chối duyệt đưa về Nháp.
  Năm trường nội dung bắt buộc khi gửi duyệt; chỉ tác giả được sửa nội dung và GVHD
  khi Nháp. Từ Chờ duyệt trở đi, nội dung và GVHD bị khóa; phiên bản đầu không có
  chức năng thay GVHD trong quá trình thực hiện.
- **Nhóm và thành viên**: nhóm gắn với đợt đăng ký, từ một đến ba SV, đúng một trưởng nhóm;
  thêm trực tiếp làm phát sinh tư cách thành viên ngay, không có tư cách chờ lời mời.
  mỗi SV chỉ thuộc một nhóm trong từng đợt, có thể thuộc nhóm khác ở đợt khác.
  Người tạo là trưởng nhóm ban đầu; trưởng nhóm có thể chuyển quyền cho thành viên khác.
- **Đăng ký và quyết định**: liên kết nhóm–đề tài–đợt; các trạng thái trong luồng đã chốt
  gồm chờ xét, chấp nhận, từ chối, đã rút. Trưởng khoa quyết định chấp nhận/từ chối thủ công;
  khi chấp nhận một nhóm, hệ thống tự động từ chối các đăng ký khác còn chờ xét cùng
  đề tài/đợt với lý do “Đề tài đã nhận nhóm”.
  trưởng nhóm rút chờ xét trong hạn. Sau từ chối/rút có thể tạo đăng ký mới trong hạn.
  Lịch sử lưu trữ, lý do bắt buộc và thứ tự ưu tiên thao tác đồng thời còn chưa xác định.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Các chỉ số sau được xác nhận làm tiêu chuẩn nghiệm thu cho phiên bản local ngày
2026-09-09. Chúng không tạo cam kết SLA định lượng về tải, uptime hoặc backup.

- **SC-001**: Với cả 4 loại đợt, người có quyền hoàn tất tạo đợt; 100% trường được xác nhận
  cho mỗi loại được ghi nhận đúng và không chấp nhận ngày không áp dụng (US1; AT-01).
- **SC-002**: Luồng GV đăng → trưởng khoa duyệt/công bố → nhóm đăng ký → trưởng khoa quyết định
  hoàn tất được cả nhánh chấp nhận và từ chối bằng dữ liệu hợp lệ, sau khi chốt Q
  liên quan; kiểm chứng thêm nhánh rút chờ xét và đăng ký lại trong hạn sau rút/từ chối;
  kết quả khớp nhóm/đề tài/đợt và quyết định (US2–5; AT-03–05).
- **SC-003**: Trong toàn bộ ca nghiệm thu ngoài hạn, chưa công bố, vượt giới hạn nhóm
  hoặc đăng ký, số thay đổi vi phạm được chấp nhận bằng 0 (AT-02, 04–05).
- **SC-004**: 100% thao tác cần bảo vệ có ca cho phép/từ chối; số thay đổi trái phép
  trong các ca từ chối bằng 0 (AT-06).
- **SC-005**: Trong các ca tranh chấp AT-07–08, số trạng thái kết quả vi phạm ràng buộc
  đã xác nhận bằng 0; không yêu cầu một người gửi cụ thể phải thắng.
- **SC-006**: 100% FR có ánh xạ REQ hoặc yêu cầu U, tiêu chí nghiệm thu được xác nhận,
  và kiểm thử liên quan đạt trước khi công bố tính năng hoàn thành.
- **SC-007**: Trong các ca nghiệm thu nhận diện, tài khoản được cấp sẵn đăng nhập đúng
  thông tin nhận đúng vai trò/bộ môn; số lần cấp quyền thành công bằng mật khẩu sai,
  tài khoản không tồn tại hoặc vai trò tự khai bằng 0 (AT-10).
- **SC-008**: Sau mỗi ca chấp nhận nhóm thành công, số đăng ký còn chờ xét cho cùng
  đề tài/đợt bằng 0; 100% đăng ký thuộc diện tự động từ chối có lý do “Đề tài đã nhận nhóm”;
  không tạo được đăng ký mới cho đề tài đã nhận nhóm trong đợt (AT-11).
- **SC-009**: 100% trang chính được kiểm tra ở viewport 360 px, 768 px và 1280 px không
  tràn ngang toàn trang; bảng rộng cuộn trong vùng chứa; menu, form và thao tác chính vẫn
  nhìn thấy, đọc được và dùng được bằng bàn phím (AT-15).

Không đặt ngưỡng thời gian phản hồi, số người dùng đồng thời, uptime hoặc backup cho
phiên bản local theo quyết định Q-20 ngày 2026-09-09.

## Assumptions

Không đặt giả định nghiệp vụ mặc định. Dùng “đề tài” và “thực hiện đề tài” theo A-01
của bản phân tích để mô tả luồng; không quyết định thiết kế lưu trữ. Đã xác nhận LECTURER
có thể kết hợp DEAN, còn STUDENT không kết hợp với ADMIN, LECTURER hoặc DEAN. ADMIN
chỉ xử lý sự cố khẩn cấp và không có quyền học vụ hoặc đăng ký.
Trưởng khoa đã được xác nhận có quyền
duyệt/công bố và chấp nhận/từ chối đăng ký. Trưởng nhóm đã được xác nhận quản lý thành viên,
chuyển quyền trong nhóm, gửi đăng ký và rút đăng ký chờ xét trong hạn SV.
Các quyền khác chưa chốt không được suy rộng.

### Clarifications

Trạng thái ba nhóm làm rõ ban đầu được cập nhật dưới đây; các Q khác vẫn được giữ nguyên,
không bị thay bằng giả định để giới hạn số câu hỏi.

- **CL-01**: Đã giải quyết thẩm quyền: trưởng khoa duyệt/công bố và chấp nhận/từ chối; trưởng nhóm quản lý thành viên, chuyển quyền cho thành viên cùng nhóm và gửi đăng ký; SV tạo nhóm là trưởng nhóm ban đầu.
- **CL-02**: Đã xác nhận một nhóm/SV theo từng đợt (Q-08); nhiều nhóm chờ xét nhưng tối đa một nhóm được chấp nhận/đề tài/đợt (Q-10). Đã xác nhận trong hạn SV được rút chờ xét, đăng ký lại đề tài cũ/khác còn nhận đăng ký sau rút/từ chối; không rút đăng ký đã chấp nhận (Q-09).
- **CL-03**: Đã giải quyết Q-02. Dùng ngày lịch Asia/Ho_Chi_Minh cho cả bốn ngày GV/SV và hai ngày phụ; không nhập giờ/phút/giây hoặc ngày đã qua. Mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc; ngày bắt đầu SV phải sau ngày kết thúc GV. SV chỉ đăng ký sau công bố và còn hạn. Từ đầu ngày bắt đầu GV, khóa tên, loại và các ngày bắt đầu; chỉ kéo dài ngày kết thúc trước khi giai đoạn hết hạn, không rút ngắn hoặc mở lại. TLCN/KLTN bắt buộc hạn điểm GVPB, KLTN bắt buộc ngày hội đồng; từng ngày phụ chỉ được dời muộn hơn trước ngày đang lưu. Đổi loại trước ngày mở GV tự xóa ngày không còn áp dụng và yêu cầu đủ ngày của loại mới.
- **CL-04**: Đã chọn seed/import local; LECTURER có thể kết hợp DEAN, STUDENT không kết hợp ADMIN/vai trò nhân sự; quyền trưởng khoa/giảng viên/sinh viên giới hạn theo khoa, bộ môn và quan hệ dữ liệu. ADMIN chỉ xử lý sự cố bằng cách ẩn nội dung, lưu người xử lý/thời gian/lý do và khóa tài khoản gây sự cố; không xóa cứng, sửa nội dung hoặc quản lý nghiệp vụ/role/cơ cấu tổ chức. Chuyển giảng viên là chức năng mở rộng chưa có trong phiên bản đầu. Q-18 đã giải quyết cho phạm vi hiện tại.
- **CL-05**: Đã xác nhận toàn bộ AS/SC hiện tại làm chuẩn nghiệm thu; bảo mật nền tảng, responsive và xung đột dữ liệu chính phải được kiểm tra. Không đặt SLA định lượng về tải, thời gian phản hồi, uptime hoặc backup cho phiên bản local. Q-20 và G-Acceptance đã giải quyết; tiêu chí phụ thuộc Q nghiệp vụ khác vẫn blocked theo gate tương ứng.
- **CL-06**: Đã giải quyết Q-05 cho phạm vi hiện tại. Mỗi đề tài chỉ thuộc một đợt và không có chức năng dùng lại hoặc sao chép sang đợt khác; GV tự tạo đề tài mới ở đợt mới. Bộ môn và tài khoản GV được seed/import; biểu mẫu chỉ chọn tài khoản LECTURER đang hoạt động cùng bộ môn. Không có màn hình quản lý danh mục hoặc luồng SV đề xuất đề tài trong phiên bản đầu.
- **CL-07**: Đã giải quyết Q-06 cho phạm vi hiện tại. Chỉ tác giả được thay đổi GVHD khi đề tài ở Nháp; từ Chờ duyệt trở đi danh sách bị khóa. Nếu trưởng khoa từ chối đề tài về Nháp thì tác giả được sửa và gửi lại trong hạn GV. Phiên bản đầu không hỗ trợ thay GVHD trong quá trình thực hiện.

Các quyết định khác vẫn mở:

| Câu hỏi nguồn | Phần cần xác nhận trong đợt này | Ảnh hưởng |
|---|---|---|
| Q-07 (phần còn lại) | Nhóm nháp/thời điểm tạo nhóm và rời/chuyển nhóm. Đã chốt 1–3 SV đủ điều kiện số lượng để đăng ký, thêm trực tiếp không cần xác nhận, người tạo, quyền và thời điểm sửa: trong hạn SV, không có đăng ký chờ xét/đã chấp nhận. | FR-08–10 |
| Q-09 (phần còn lại) | Tính bắt buộc của lý do từ chối, thứ tự ưu tiên khi rút/chấp nhận đồng thời, chính sách lưu lịch sử. Quyền rút và đăng ký lại đã chốt. | FR-10–13 |

Chưa sẵn sàng triển khai toàn bộ vì Q-07 và Q-09 vẫn còn phần
cần xác nhận. Q-02, Q-05, Q-06, Q-18 và Q-20 đã đóng; các câu hỏi còn lại tiếp tục được xử lý tuần tự.
