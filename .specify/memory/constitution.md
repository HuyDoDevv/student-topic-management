<!--
Sync Impact Report
Version: scaffold chưa ban hành -> 1.0.0 (ban hành lần đầu).
Modified principles: thay năm placeholder bằng mười nguyên tắc I-X bên dưới.
Added sections: Nguồn yêu cầu và quyết định nền tảng; Quy trình và điều kiện hoàn thành.
Governance: bổ sung sửa đổi, phiên bản và kiểm tra tuân thủ.
Removed: ví dụ scaffold; không xóa nguyên tắc đã ban hành nào.
Templates/commands: không sửa; các workflow đọc constitution khi chạy.
Deferred placeholders: không có.
Follow-up: xác nhận Q liên quan trước triển khai; hỏi chủ dự án trước khi chốt nền tảng.
-->

# Hệ thống quản lý đề tài sinh viên Constitution

## Core Principles

### I. Nguồn nghiệp vụ và trạng thái xác nhận

Yêu cầu khách hàng PHẢI là nguồn sự thật chính về nghiệp vụ. Tài liệu dự án PHẢI phân biệt
yêu cầu nêu rõ trong nguồn (E), suy luận (I/INF), giả định (A) và câu hỏi chưa giải quyết (Q),
theo `workspace/docs/requirements-analysis.md`. KHÔNG ĐƯỢC tự bổ sung nghiệp vụ hoặc coi
suy luận, giả định là yêu cầu đã xác nhận. Điểm mơ hồ PHẢI được ghi nhận cùng câu hỏi
và phạm vi ảnh hưởng; phần triển khai phụ thuộc PHẢI chờ khách hàng xác nhận.
Khi mâu thuẫn, PHẢI đối chiếu PDF và ghi nhận quyết định khách hàng, không ngầm chọn cách hiểu.
Bản phân tích hỗ trợ truy vết, không thay thế nguồn khách hàng hoặc xác nhận của họ.

### II. Truy vết yêu cầu xuyên suốt

Mỗi yêu cầu chức năng được triển khai PHẢI truy vết tới ID yêu cầu và nguồn tương ứng,
hiện là REQ-01 đến REQ-32. Đặc tả, công việc triển khai, tiêu chí nghiệm thu và kiểm thử
PHẢI duy trì liên kết bằng tham chiếu hoặc bảng ánh xạ có thể kiểm tra.
Không bắt buộc gắn ID vào mọi dòng mã. Yêu cầu mới PHẢI có ID ổn định và bằng chứng
khách hàng xác nhận trước khi triển khai. Quy tắc kỹ thuật do chủ dự án yêu cầu PHẢI
dẫn về constitution này, không gán nhầm thành nội dung của PDF.

### III. Phân quyền và ràng buộc tại backend

Backend PHẢI kiểm tra quyền trên thao tác và tài nguyên trước mọi thao tác nhạy cảm
hoặc trả dữ liệu được bảo vệ, và PHẢI thực thi ràng buộc nghiệp vụ đã xác nhận.
Ẩn nút hoặc kiểm tra tại giao diện không đủ. Vai trò, tư cách nhóm trưởng và quan hệ
hướng dẫn PHẢI được kiểm chứng từ trạng thái đáng tin cậy, không tin giá trị bên gọi gửi lên.
Thao tác không chứng minh được quyền PHẢI bị từ chối mà không tạo thay đổi nghiệp vụ.
Ví dụ: chỉ nhóm trưởng nộp báo cáo (REQ-17); giảng viên không chấm đề tài mình hướng dẫn
(REQ-25). Các quyền chưa rõ trong Q-01, Q-17, Q-18 PHẢI được xác nhận; không tự thêm
vai trò quản trị hoặc chính sách xem điểm đề tài khác.

### IV. Kiểm tra đầu vào tại ranh giới hệ thống

Dữ liệu không đáng tin cậy PHẢI được kiểm tra tại ranh giới tiếp nhận trước khi sử dụng
hoặc lưu: cấu trúc, kiểu, trường bắt buộc và giới hạn đã xác định. Backend PHẢI kiểm tra
lại ngay cả khi giao diện đã kiểm tra. Dữ liệu không hợp lệ PHẢI bị từ chối mà không để lại
thay đổi nghiệp vụ dở dang. Giới hạn nghiệp vụ chưa rõ, như thang điểm hoặc định dạng
báo cáo, PHẢI được ghi nhận để xác nhận thay vì tự đặt như yêu cầu khách hàng.

### V. Tách nghiệp vụ khỏi transport/controllers

Logic nghiệp vụ PHẢI tách khỏi lớp tiếp nhận/gửi yêu cầu và controllers. Controllers
chỉ điều phối đầu vào, gọi nghiệp vụ và chuyển kết quả thành phản hồi. Quy tắc nhóm,
đăng ký, quyền theo quan hệ và tính điểm PHẢI kiểm thử được mà không khởi chạy giao diện
hoặc tầng transport. Nguyên tắc không chỉ định framework hay mẫu kiến trúc cụ thể.

### VI. Bảo toàn ràng buộc khi thao tác đồng thời

Các thao tác đồng thời PHẢI giữ nguyên ràng buộc nhóm, đăng ký và chấm điểm đã xác nhận.
Không được chỉ kiểm tra rồi ghi độc lập nếu thao tác khác có thể làm kiểm tra mất hiệu lực.
Thiết kế PHẢI có cơ chế nhất quán phù hợp với nền tảng được chọn và kiểm thử chứng minh;
constitution không chọn cơ chế cụ thể.

Phải bảo vệ trước việc thêm thành viên vượt giới hạn (REQ-12), tham gia hai nhóm trong
phạm vi đã xác nhận (REQ-13), một nhóm đăng ký nhiều đề tài (REQ-14), hoặc thay đổi quan hệ
hướng dẫn đồng thời với chấm điểm làm vi phạm REQ-25. Kết quả tính điểm PHẢI dựa trên tập
điểm thành phần nhất quán theo quy tắc đã xác nhận (REQ-23).
Q-08, Q-09, Q-13 vẫn cần xác nhận; không suy ra mỗi đề tài chỉ được một nhóm đăng ký (Q-10).

### VII. Migration cho cấu trúc dữ liệu

Khi sử dụng database, tạo cấu trúc ban đầu và mọi thay đổi cấu trúc dữ liệu trong database
PHẢI có migration được quản lý phiên bản cùng mã nguồn. Cách áp dụng PHẢI tái lập được
và được kiểm chứng trên trạng thái dữ liệu liên quan. KHÔNG ĐƯỢC dùng chỉnh sửa thủ công
database để thay thế migration. Ảnh hưởng dữ liệu hiện có và cách xử lý khi áp dụng thất bại
PHẢI được ghi rõ. Nguyên tắc không quyết định loại database hoặc công cụ migration.

### VIII. Kiểm thử tự động cho nghiệp vụ quan trọng

Nghiệp vụ quan trọng PHẢI có kiểm thử tự động cho luồng hợp lệ, vi phạm ràng buộc và
từ chối quyền. Phạm vi gồm thời gian đăng ký, ràng buộc nhóm/đề tài, nộp báo cáo,
xung đột hướng dẫn/chấm điểm, tính điểm và điều kiện công bố khi được triển khai
(REQ-03, REQ-10, REQ-12–17, REQ-20–27).
Thao tác có nguy cơ tranh chấp PHẢI có kiểm thử đồng thời chứng minh ràng buộc được giữ.
Kiểm thử quyền PHẢI kiểm chứng từ chối tại backend và không có thay đổi trái phép,
không chỉ kiểm tra nút giao diện. Kiểm thử PHẢI dựa trên quy tắc đã xác nhận và truy vết
về yêu cầu hoặc nguyên tắc kỹ thuật liên quan.

### IX. Không lưu secrets trong mã nguồn

KHÔNG ĐƯỢC lưu mật khẩu thật, token, khóa API, khóa riêng hoặc thông tin kết nối chứa
thông tin xác thực trong mã nguồn, cấu hình được commit, fixture hoặc tài liệu repository.
Secrets PHẢI được cung cấp ngoài mã nguồn bằng cơ chế được quyết định sau.
Ví dụ cấu hình chỉ dùng giá trị giả không có hiệu lực. Rà soát thay đổi PHẢI kiểm tra
không đưa secrets vào repository.

### X. Hoàn thành theo bằng chứng nghiệm thu

Tính năng chỉ được đánh dấu hoàn thành khi đạt mọi tiêu chí nghiệm thu đã xác nhận
trong phạm vi và các kiểm thử liên quan đều thành công. PHẢI ghi phạm vi kiểm chứng,
kết quả kiểm thử và ánh xạ yêu cầu; mã đã viết hoặc giao diện hiển thị không đủ.
AC-01 đến AC-16 trong bản phân tích là suy luận, PHẢI được xem xét và xác nhận trước
khi dùng làm chuẩn nghiệm thu. Câu hỏi còn mở ảnh hưởng hành vi hoặc tiêu chí trong
phạm vi PHẢI được giải quyết trước khi tuyên bố tính năng đó hoàn thành.

## Nguồn yêu cầu và quyết định nền tảng

- Nguồn nghiệp vụ: `workspace/docs/customer-requirements.pdf`, trang 1, và xác nhận bổ sung
  của khách hàng được ghi nhận rõ ràng. Không sửa PDF để hợp thức hóa quyết định triển khai.
- Bản phân tích: `workspace/docs/requirements-analysis.md`; dùng ID REQ, INF, A, Q, AC đúng
  trạng thái. Đây không phải toàn bộ đặc tả đã được phê duyệt.
- Các nguyên tắc kỹ thuật ở đây xuất phát từ chỉ đạo chủ dự án. Bảo toàn đồng thời cụ thể hóa
  INF-07 về chất lượng triển khai; không xác nhận các chính sách nghiệp vụ còn mở.
- Chưa chọn ngôn ngữ, framework, database, cơ chế xác thực, nền tảng triển khai hoặc
  công cụ kiểm thử. Trước khi cần chốt quyết định nền tảng, PHẢI hỏi chủ dự án và ghi lại
  quyết định; không tự chọn mặc định. PowerShell chỉ phục vụ công cụ Spec Kit.
- Không thêm tích hợp, xuất báo cáo, thông báo tự động hoặc nghiệp vụ mới nếu chưa được
  khách hàng xác nhận. Các Q liên quan vẫn để mở.

## Quy trình và điều kiện hoàn thành

1. Trước đặc tả, xác định REQ liên quan, phân loại suy luận/giả định và ghi Q ảnh hưởng phạm vi.
   Chỉ tiếp tục phần công việc không phụ thuộc quyết định còn thiếu.
2. Trước triển khai, xác nhận hành vi và tiêu chí nghiệm thu; xác định kiểm tra quyền,
   đầu vào, ràng buộc đồng thời, migration nếu có database, và kiểm thử cần thiết.
3. Khi triển khai, duy trì truy vết và tách nghiệp vụ khỏi controllers; không mở rộng nghiệp vụ
   vì một lựa chọn kỹ thuật thuận tiện hơn.
4. Khi rà soát hoàn thành, kiểm tra nguyên tắc I-X, bằng chứng nghiệm thu và kiểm thử.
   Mục chưa đạt PHẢI được báo rõ; không đánh dấu hoàn thành khi còn vi phạm.

## Governance

Constitution quy định cách đặc tả, thiết kế, triển khai và kiểm chứng dự án; không thay thế
nguồn nghiệp vụ khách hàng. Nếu tài liệu kỹ thuật hoặc triển khai xung đột với constitution,
PHẢI sửa phần xung đột hoặc đề xuất sửa constitution được chủ dự án xác nhận.
Không được ngầm bỏ qua nguyên tắc. Mâu thuẫn nghiệp vụ PHẢI đưa lại khách hàng xác nhận.

Mỗi sửa đổi PHẢI ghi lý do, nguyên tắc bị ảnh hưởng, tác động tới công việc hiện có và
xác nhận của chủ dự án. PHẢI cập nhật Sync Impact Report, phiên bản, ngày sửa đổi;
công việc liên quan PHẢI được rà soát lại để tuân thủ.

Phiên bản theo MAJOR.MINOR.PATCH: MAJOR cho bỏ/định nghĩa lại nguyên tắc không tương thích;
MINOR cho thêm nguyên tắc hoặc mở rộng hướng dẫn đáng kể; PATCH cho làm rõ không đổi nghĩa.
1.0.0 là lần ban hành đầu từ scaffold chưa có giá trị dự án. Ngày phê chuẩn ban đầu được giữ
nguyên trong các lần sửa đổi sau.

Mọi lần rà soát đặc tả, kế hoạch và thay đổi triển khai PHẢI kiểm tra tuân thủ constitution.
Không có miễn trừ ngầm cho bản thử nghiệm hoặc áp lực tiến độ.

**Version**: 1.0.0 | **Ratified**: 2026-09-08 | **Last Amended**: 2026-09-08
