# Feature Specification: Quản lý đợt, đề tài và đăng ký nhóm

**Feature Branch**: Không tạo nhánh; dự án không có hook tạo nhánh.

**Feature Directory**: `specs/001-topic-registration`

**Created**: 2026-09-08

**Status**: Ready for implementation — các quyết định nghiệp vụ hiện tại đã được xác nhận.

**Input**: Quản lý đợt đăng ký, đề tài và đăng ký đề tài của nhóm sinh viên; nhận diện và
phân quyền cần thiết; duyệt/công bố đề tài; chấp nhận hoặc từ chối đăng ký theo nghiệp vụ
được chốt. Giữ các chức năng còn lại trong phạm vi tổng thể dự án.

Nguồn: [bản phân tích](../../workspace/docs/requirements-analysis.md), đối chiếu
[PDF trang 1](../../workspace/docs/customer-requirements.pdf);
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

- Q: Ai quản lý thành viên, chọn trưởng nhóm và gửi đăng ký đề tài của nhóm? → A: A — SV tạo nhóm trở thành trưởng nhóm; trưởng nhóm mời/xóa thành viên, chuyển quyền và gửi đăng ký; SV được mời quyết định tham gia và thành viên thường được tự rời theo cập nhật ngày 2026-09-11. Xác nhận U, làm rõ quyền trong Q-07, Q-18; liên quan REQ-12–15, REQ-31.

- Q: Nhóm được rút đăng ký và đăng ký lại sau khi bị từ chối theo quy tắc nào? → A: A — Trong hạn SV, trưởng nhóm được rút đăng ký đang chờ xét; sau rút hoặc bị từ chối, được đăng ký lại đề tài cũ hoặc đề tài khác còn nhận đăng ký. Không được rút đăng ký đã chấp nhận. Xác nhận U, làm rõ Q-09; liên quan REQ-03, REQ-14–16, REQ-31.

Phiên làm rõ thứ hai trong ngày 2026-09-08:

- Q: Giảng viên được đăng đề tài thuộc bộ môn nào và được chọn giảng viên hướng dẫn từ những bộ môn nào? → A: A — Giảng viên chỉ đăng đề tài thuộc bộ môn mình; mọi GVHD phải cùng bộ môn đó. Xác nhận U, làm rõ phần quyền bộ môn trong Q-18; liên quan REQ-08–11, REQ-31. Không suy rộng thành giới hạn bộ môn đối với sinh viên hoặc trưởng khoa.

- Q: Trong đợt triển khai đầu, tài khoản người dùng được cung cấp và đăng nhập theo cách nào? → A: A — Tài khoản nội bộ được cấp sẵn cùng vai trò/bộ môn; đăng nhập bằng tên đăng nhập và mật khẩu; không tự đăng ký. Xác nhận U, phiên 2; làm rõ nguồn danh tính trong Q-18, liên quan REQ-28, REQ-31–32. Không tự xác định người/công cụ cấp tài khoản hoặc cơ chế kỹ thuật lưu thông tin xác thực.

- Q: Khi trưởng khoa chấp nhận một nhóm cho đề tài, hệ thống xử lý các đăng ký chờ xét khác và việc nhận đăng ký mới cho đề tài đó như thế nào? → A: A — Tự động từ chối các đăng ký chờ xét còn lại với lý do “Đề tài đã nhận nhóm”; ngừng nhận đăng ký mới cho đề tài đó trong đợt. Xác nhận U, phiên 2; giải quyết phần còn lại của Q-10, liên quan REQ-14–16. Các nhóm bị từ chối được áp dụng quy tắc đăng ký lại đã chốt; không mở rộng sang thông báo tự động.

- Q: Hai giai đoạn đăng ký được mở theo lịch và múi giờ nào? → A: A — Dùng Asia/Ho_Chi_Minh; hai khoảng GV/SV không chồng lấn, tự mở/đóng theo lịch; đăng ký SV còn yêu cầu trưởng khoa đã công bố danh sách. Tính thời điểm bắt đầu, không tính thời điểm kết thúc. Xác nhận U, phiên 2; giải quyết Q-03–04 về lịch và biên, liên quan REQ-02–05, REQ-15.

- Q: Đề tài được phép chỉnh sửa và phải có đủ thông tin ở những trạng thái nào? → A: A — Nháp → Chờ duyệt → Đã duyệt → Đã công bố. GV chỉ sửa bản nháp của mình; gửi duyệt phải có nội dung, bộ môn và 1–2 GVHD hợp lệ. Trưởng khoa từ chối đưa đề tài về Nháp để sửa/gửi lại trong hạn GV. Xác nhận U, phiên 2; làm rõ vòng đời Q-05–06, liên quan REQ-03, REQ-08–11, REQ-15, REQ-31.

Phiên làm rõ thứ ba trong ngày 2026-09-08:

- Q: Trưởng nhóm được thêm/xóa thành viên và chuyển quyền trưởng nhóm vào thời điểm nào? → A: A — Chỉ trong hạn SV và khi nhóm không có đăng ký chờ xét hoặc đã chấp nhận; sau rút/từ chối có thể sửa nếu còn hạn. “Thêm” được thay bằng gửi lời mời có xác nhận ngày 2026-09-11; cùng điều kiện thời gian áp dụng cho gửi/accept/hủy, tự rời và giải tán. Liên quan REQ-03, REQ-12–16, REQ-31.

- Q: Khi trưởng nhóm thêm một sinh viên, sinh viên đó có cần đồng ý trước khi trở thành thành viên chính thức không? → A: Quyết định B “thêm trực tiếp” của phiên 3 đã bị thay thế ngày 2026-09-11. Trưởng nhóm gửi lời mời giữ chỗ; chỉ SV được mời mới có thể chấp nhận để trở thành thành viên. Liên quan REQ-12–13, REQ-31.

- Q: Nhóm cần tối thiểu bao nhiêu sinh viên để được gửi đăng ký đề tài? → A: Quyết định “từ 1 đến 3 sinh viên đều được gửi” của phiên 3 đã bị thay thế ngày 2026-09-12. Nhóm vẫn được tạo và quản lý khi có 1–3 sinh viên, nhưng chỉ được gửi đăng ký đề tài khi có đúng 3 thành viên chính thức và đúng một trưởng nhóm. Liên quan REQ-12–15, FR-08/FR-10.

- Q: Khi gửi duyệt, đề tài bắt buộc phải có những thông tin nội dung nào? → A: A — Tên đề tài, mô tả, mục tiêu, yêu cầu đối với sinh viên và kết quả dự kiến đều bắt buộc, ngoài bộ môn và 1–2 GVHD đã chốt. Xác nhận U, phiên 3; làm rõ nội dung trong Q-05, liên quan REQ-08–10. Không đặt thêm giới hạn độ dài hoặc mẫu trình bày chưa được xác nhận.

- Q: Trưởng khoa được thay đổi loại đợt và thời gian đăng ký sau khi đợt đã bắt đầu không? → A: Quy tắc cuối được cập nhật ngày 2026-09-10 — từ đầu ngày bắt đầu GV, khóa tên, loại và các ngày bắt đầu; trưởng khoa chỉ được kéo dài ngày kết thúc của giai đoạn chưa hết hạn, không được rút ngắn hoặc mở lại giai đoạn đã đóng, và lịch sau sửa vẫn phải đúng thứ tự. Quyết định này thay thế phương án khóa toàn bộ loại/bốn mốc đã ghi ở phiên 3; liên quan REQ-03–05, REQ-31.

### Session 2026-09-09

- Q: Trong phiên bản đầu tiên, hệ thống sẽ cấp tài khoản, kết hợp vai trò và giới hạn phạm vi dữ liệu theo phương án nào? → A: Phương án A và bổ sung ADMIN — Tài khoản được seed/import local; tài khoản nhân sự có thể đồng thời giữ LECTURER và DEAN, STUDENT không kết hợp với vai trò nhân sự. Trưởng khoa quản lý trong khoa mình, giảng viên thao tác đề tài trong bộ môn mình, sinh viên xem đề tài đã công bố trong khoa và chỉ thay đổi nhóm/đăng ký mình có quyền. Thêm role ADMIN để xử lý sự cố website; phạm vi cụ thể được chốt ở các câu tiếp theo trong phiên này. Xác nhận U, làm rõ một phần Q-18; liên quan REQ-28, REQ-31–32.
- Q: ADMIN có được phép can thiệp trực tiếp vào các nghiệp vụ học vụ như tạo đợt, duyệt đề tài và xét đăng ký không? → A: Không. ADMIN không quản lý nghiệp vụ học tập, đăng ký, khoa/bộ môn hoặc chuyển giảng viên; việc duyệt học vụ thuộc trưởng khoa. Quyết định “chỉ xử lý sự cố” ở thời điểm này được mở rộng hẹp ngày 2026-09-16 để ADMIN cấp ba loại tài khoản theo mapping role cố định, nhưng không thay đổi giới hạn học vụ hoặc cho sửa role tùy ý. Xác nhận U, liên quan REQ-28, REQ-31–32.
- Q: Khi trưởng khoa chuyển một giảng viên đang có đề tài hoặc phân công GVHD sang bộ môn/khoa khác, các trách nhiệm đang hoạt động phải được xử lý thế nào? → A: Bỏ qua trong phiên bản đầu vì là chức năng mở rộng, mức độ không quan trọng. Phiên bản đầu không cung cấp thao tác chuyển giảng viên và giả định thông tin bộ môn/khoa ổn định trong các luồng đang hoạt động; nếu có thời gian sẽ đặc tả và triển khai sau. Xác nhận U, đóng phần chuyển bộ môn của Q-18 bằng quyết định ngoài phạm vi hiện tại.
- Q: Khi có kẻ gian đăng nội dung, ADMIN nên “xóa” theo cách nào? → A: A — ADMIN ẩn nội dung khỏi website nhưng giữ dữ liệu để kiểm tra hoặc khôi phục; lưu ADMIN thực hiện, thời gian và lý do, đồng thời khóa tài khoản gây sự cố. Đây là quyền xử lý khẩn cấp, không cấp quyền sửa nội dung hoặc thực hiện nghiệp vụ học vụ/đăng ký. Xác nhận U, giải quyết phần còn lại của Q-18; liên quan REQ-28, REQ-31–32.
- Q: Phiên bản local đầu tiên được nghiệm thu theo mức chất lượng nào? → A: A — Dùng toàn bộ AS/SC hiện tại làm chuẩn nghiệm thu; kiểm tra chức năng, phân quyền backend, CSRF, mật khẩu băm, validation, không lộ secret, responsive trên desktop/tablet/mobile và các xung đột dữ liệu chính. Không đặt SLA định lượng cho thời gian phản hồi, số người dùng đồng thời, uptime hoặc backup ở phiên bản local. Xác nhận U, giải quyết Q-20 và G-Acceptance; các Q nghiệp vụ khi đó còn mở đã được chốt trong các phiên sau.

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

### Session 2026-09-11

- Q: Giảng viên tạo đề tài có phải tự chọn chính mình trong danh sách GVHD không? → A: Không. Tác giả được backend tự động phân công làm GVHD chính ở slot 1; biểu mẫu không cho chọn lại tác giả và chỉ cho chọn thêm tối đa một GVHD phối hợp đang hoạt động, có role LECTURER và cùng bộ môn. Phiên bản đầu không cho giảng viên tạo đề tài thay cho một GVHD chính khác. Xác nhận U ngày 2026-09-11; cập nhật FR-06 và AS-2.1/2.3/2.6–2.7/2.11.
- Q: Trưởng khoa công bố đề tài theo từng mục hay theo danh sách và có hạn xử lý riêng không? → A: Trưởng khoa duyệt hoặc từ chối từng đề tài; một thao tác công bố chuyển toàn bộ đề tài đang ở trạng thái Đã duyệt trong một đợt sang Đã công bố trong cùng giao dịch. Không đặt SLA hoặc khóa cứng thời điểm duyệt/công bố; giao diện phải nhắc trưởng khoa hoàn tất trước ngày bắt đầu đăng ký sinh viên. Nếu xử lý trễ nhưng vẫn trong hạn SV, hệ thống vẫn cho công bố để sinh viên sử dụng phần thời gian còn lại. Xác nhận U ngày 2026-09-11, giải quyết G-Publish; liên quan FR-07/FR-14 và AS-3.1–3.5.
- Q: Trưởng khoa thao tác các đề tài được đề xuất ở đâu? → A: Hiển thị danh sách đề tài của đợt ngay trên trang chi tiết đợt; tại đó trưởng khoa xem nội dung, duyệt/từ chối từng đề tài và công bố toàn bộ các đề tài đã duyệt. Xác nhận U ngày 2026-09-11; đây là bố trí UI, không thay kiểm tra quyền/scope ở backend.
- Q: Sinh viên được phép tạo nhóm vào thời điểm nào trong đợt đăng ký? → A: A — Chỉ trong thời gian sinh viên đăng ký. Ngoài khoảng này, yêu cầu tạo nhóm bị từ chối và không tạo dữ liệu. Xác nhận U ngày 2026-09-11, làm rõ phần thời điểm tạo nhóm của Q-07; liên quan REQ-03, REQ-12–13 và FR-03/FR-08.
- Q: Sinh viên có được tự rời hoặc tự chuyển sang nhóm khác không? → A: Quyết định cũ “không tự rời” đã được thay thế trong cùng ngày 2026-09-11. Thành viên thường được tự rời nhóm; trưởng nhóm có thành viên khác phải chuyển quyền trước, còn trưởng nhóm một người được giải tán nhóm. Trưởng nhóm chỉ gửi lời mời và sinh viên chỉ trở thành thành viên sau khi đồng ý. Liên quan REQ-12–13 và FR-08–09.
- Q: Khi trưởng nhóm muốn rời nhóm thì hệ thống phải xử lý thế nào? → A: A — Thành viên thường được tự rời. Trưởng nhóm có thành viên khác phải chuyển quyền trước khi rời; nếu nhóm chỉ có một người thì được giải tán nhóm. Hệ thống không tự chọn trưởng nhóm thay thế. Xác nhận U ngày 2026-09-11; thay thế phần không cho tự rời của quyết định Q-07 trước đó, liên quan FR-08–09.
- Q: Lời mời đang chờ có giữ chỗ và một SV được nhận bao nhiêu lời mời trong cùng đợt? → A: A — Lời mời PENDING giữ một chỗ trong giới hạn ba người; tổng số thành viên và lời mời PENDING của nhóm không vượt quá ba. Mỗi SV chỉ có tối đa một lời mời PENDING trong một đợt. Khi SV từ chối hoặc trưởng nhóm hủy, chỗ được giải phóng. Xác nhận U ngày 2026-09-11; liên quan FR-08–09 và AS-4.2–4.3/4.12.
- Q: Sinh viên được chấp nhận lời mời hoặc rời nhóm vào thời điểm nào? → A: A — Gửi/chấp nhận/hủy lời mời, rời hoặc giải tán nhóm chỉ thực hiện trong hạn SV và khi nhóm chưa có đăng ký PENDING/ACCEPTED. SV được từ chối lời mời bất cứ lúc nào; lời mời chưa trả lời tự hết hiệu lực khi hết hạn SV. Xác nhận U ngày 2026-09-11; liên quan FR-03/FR-08 và AS-4.4/4.10–4.12.
- Q: Nhóm còn lời mời đang chờ có được đăng ký đề tài không? → A: A — Không. Trước khi gửi đăng ký đề tài, mọi lời mời PENDING của nhóm phải được SV phản hồi hoặc trưởng nhóm hủy. Hệ thống không tự hủy lời mời khi gửi đăng ký. Xác nhận U ngày 2026-09-11; liên quan FR-08/FR-10 và AS-4.15/AS-5.1.
- Q: Khi Trưởng khoa từ chối thủ công một đăng ký đề tài, lý do từ chối có bắt buộc không? → A: A — Bắt buộc nhập lý do có nội dung sau khi loại bỏ khoảng trắng đầu/cuối. Thiếu hoặc chỉ nhập khoảng trắng thì từ chối toàn bộ thao tác và giữ đăng ký ở trạng thái Chờ xét. Xác nhận U ngày 2026-09-11; làm rõ phần lý do thủ công của Q-09, liên quan FR-12 và AS-5.5.
- Q: Khi trưởng nhóm rút đăng ký đồng thời với lúc Trưởng khoa chấp nhận chính đăng ký đó, thao tác nào được ưu tiên? → A: A — Giao dịch lấy khóa đợt trước được hoàn tất; giao dịch sau phải đọc lại trạng thái và bị từ chối vì đăng ký không còn Chờ xét. Nếu rút lấy khóa trước thì kết quả là Đã rút; nếu chấp nhận lấy khóa trước thì kết quả là Đã chấp nhận. Xác nhận U ngày 2026-09-11; làm rõ phần tranh chấp của Q-09, phù hợp protocol khóa period hiện tại và liên quan FR-11/FR-13, AS-5.15.
- Q: Các đăng ký đã bị từ chối hoặc đã rút được lưu trong database bao lâu? → A: A — Giữ toàn bộ lịch sử không thời hạn trong phiên bản đầu. Mỗi lần đăng ký lại tạo một bản ghi mới; bản ghi REJECTED/WITHDRAWN cũ không bị sửa, ghi đè hoặc xóa và không có tác vụ tự động dọn lịch sử. Xác nhận U ngày 2026-09-11; giải quyết phần retention cuối cùng của Q-09, liên quan FR-10–13 và AS-5.10.

### Session 2026-09-12

- Q: Nếu trưởng khoa đồng thời là giảng viên thì có được đề xuất đề tài và ai sẽ duyệt đề tài đó? → A: B — Trưởng khoa không được tạo, sửa hoặc gửi duyệt đề tài, kể cả khi tài khoản đồng thời có role LECTURER. Mỗi khoa chỉ có một trưởng khoa trong phạm vi hiện tại; trong vòng đời đề tài, trưởng khoa thực hiện luồng duyệt/công bố thay vì làm tác giả. Backend phải chặn trực tiếp các route tác giả và giao diện không hiển thị chức năng đề xuất cho tài khoản có role DEAN. Trưởng khoa vẫn có thể mang role LECTURER để phản ánh chức danh giảng viên và được chọn làm GVHD phối hợp cho đề tài do giảng viên khác tạo. Xác nhận U ngày 2026-09-12; làm rõ FR-01/FR-05–07 và AS-2.4.
- Q: Nhóm bao nhiêu người mới được gửi đăng ký và lịch sử thành viên được hiển thị thế nào sau khi rút/từ chối? → A: Chỉ nhóm có đúng 3 thành viên chính thức và đúng một trưởng nhóm tại thời điểm gửi mới được đăng ký. Hệ thống lưu bất biến danh sách ba thành viên, vị trí và người là trưởng nhóm tại thời điểm gửi; lịch sử đăng ký không đọc lại cơ cấu nhóm hiện tại nên không đổi khi nhóm được sửa sau khi rút/từ chối. Xác nhận U ngày 2026-09-12, thay thế quy tắc tối thiểu cũ; liên quan FR-08, FR-10, FR-13 và AS-4.13/AS-5.1/AS-5.10.

### Session 2026-09-13

- Q: Sau khi trưởng khoa chấp nhận đăng ký đề tài của nhóm, mọi thành viên nhận biết kết quả bằng cách nào? → A: Trang chi tiết nhóm phải hiển thị đăng ký có hiệu lực cho mọi thành viên hiện tại, gồm tên đề tài, trạng thái “Chờ xét” hoặc “Đã chấp nhận” và liên kết tới chi tiết đăng ký. Khi trạng thái là ACCEPTED, trang nhóm phải có xác nhận nổi bật rằng đăng ký đề tài đã thành công. Đây là phản hồi trực tiếp trên giao diện khi sinh viên truy cập, không mở rộng thành email, push hoặc mô-đun thông báo tự động. Xác nhận U ngày 2026-09-13; liên quan FR-10–11 và AS-5.4.

### Session 2026-09-14

- Q: Dashboard của giảng viên, trưởng khoa và sinh viên có nên dùng cùng thông số không? → A: Không. Trang tổng quan dùng cùng khung giao diện nhưng số liệu và việc tiếp theo theo vai trò: DEAN thấy đề tài chờ duyệt/chờ công bố, đăng ký chờ xét và đợt trong khoa; LECTURER (không có DEAN) chỉ thấy đề tài do mình đề xuất trong bộ môn/khoa; STUDENT thấy lời mời còn hiệu lực, nhóm hiện tại và đăng ký PENDING/ACCEPTED của chính mình qua snapshot khi gửi. Sinh viên phải nhìn thấy xác nhận ACCEPTED và liên kết đăng ký. Tài khoản DEAN+LECTURER dùng dashboard trưởng khoa, ADMIN không đọc số liệu học vụ. Số liệu 0 phải hiện 0, không dùng số mẫu hoặc dấu gạch; không đưa chấm điểm/kết quả ngoài phạm vi vào dashboard. Các liên kết vẫn phải được backend kiểm tra quyền. Xác nhận U ngày 2026-09-14; làm rõ FR-01/FR-10–11 và NFR-02.

### Session 2026-09-16

- Q: Trưởng khoa cần nhìn các đợt và trạng thái xử lý thế nào? → A: Dashboard và danh sách đợt chỉ tính/hiển thị đợt còn hiệu lực, nghĩa là thời điểm hiện tại chưa qua `student_end`. Danh sách có bộ lọc trạng thái, dùng nhãn rõ đối tượng “Sắp/Đang nhận đề tài GV”, “Chờ/Đang nhận đăng ký SV”, đồng thời hiển thị số đề tài chờ duyệt, nhóm đăng ký chờ xét và đề tài chờ công bố của từng đợt. Đợt đã kết thúc không bị xóa và vẫn có thể được truy cập bằng liên kết trực tiếp đúng quyền. Xác nhận U từ feedback ngày 2026-09-16; liên quan FR-01/FR-03/FR-07/FR-11.
- Q: Giảng viên có được tạo đề tài mới ngoài thời gian đề xuất không? → A: Không. Chỉ mở form và tạo bản nháp đề tài mới trong khoảng GV; backend phải kiểm tra lại dưới khóa đợt để chặn request trực tiếp hoặc thời gian đổi giữa GET và POST. Ngoài hạn, Giảng viên vẫn xem đề tài cũ và sửa bản nháp hiện hữu theo quy tắc DRAFT, nhưng chỉ gửi duyệt trong hạn như trước. Trang chọn đợt chia “Đợt còn hiệu lực” theo `student_end` và “Đợt đã hết hạn” để giữ đường xem lịch sử. Xác nhận U từ feedback ngày 2026-09-16; liên quan FR-03/FR-05/FR-14.
- Q: Sinh viên cần nhìn các đợt đăng ký nhóm thế nào? → A: Trang chọn đợt chia thành “Sắp đăng ký”, “Đang đăng ký” và “Đã kết thúc” theo `student_start`/`student_end`. Chỉ đợt đang đăng ký cho phép tạo nhóm mới; nhóm và lời mời đã có vẫn giữ lối xem/phản hồi phù hợp với quyền và quy tắc thời gian hiện hành. Xác nhận U từ feedback ngày 2026-09-16; liên quan FR-03/FR-08.
- Q: ADMIN cần quản lý tài khoản ở mức nào? → A: ADMIN có một giao diện xem toàn bộ tài khoản và được tạo tài khoản STUDENT, LECTURER hoặc DEAN từng người hoặc import từ file CSV UTF-8 theo file mẫu tải xuống. Loại DEAN được cấp đồng thời DEAN và LECTURER; không cho tạo thêm ADMIN. Toàn bộ file import phải được kiểm tra trước khi ghi và chỉ cần một dòng lỗi thì không tạo tài khoản nào. Phạm vi này không thêm sửa/xóa tài khoản hiện hữu, thay role tùy ý, quản lý danh mục khoa/bộ môn hoặc quyền học vụ. Xác nhận U từ feedback ngày 2026-09-16; cập nhật FR-01, AS-1.17, AT-10 và CL-04.
- Q: Workspace ADMIN cần chia bố cục như thế nào? → A: Toàn bộ thống kê tài khoản chỉ nằm trên Dashboard. Quản lý Sinh viên, Giảng viên và Trưởng khoa là ba trang/menu độc lập, mỗi trang khóa sẵn đúng loại tài khoản thay vì dùng bộ lọc loại chung. Tạo tài khoản và import CSV nằm trong một khu vực cấp tài khoản riêng, không trộn với danh sách quản lý. Xác nhận U từ feedback layout ngày 2026-09-16; không thay đổi quyền hoặc phạm vi dữ liệu của FR-01.

### Session 2026-09-17

- Q: Trưởng khoa có cần xem các đợt đã quá hạn ngay trong danh sách không? → A: Có. Danh sách đợt trả cả đợt còn hiệu lực và đợt đã qua `student_end`, đồng thời bộ lọc trạng thái có lựa chọn “Đã quá hạn”; Dashboard vẫn chỉ đếm đợt chưa quá hạn. Quyết định này thay thế phần “danh sách chỉ hiển thị đợt còn hiệu lực” của feedback ngày 2026-09-16, không thay đổi phạm vi khoa hoặc quyền truy cập chi tiết. Xác nhận U ngày 2026-09-17; liên quan FR-01/FR-03.

### Session 2026-09-21

- Q: Danh sách đợt của Trưởng khoa cần hỗ trợ tìm các đợt đang có việc chờ xử lý như thế nào? → A: Cho phép kết hợp tìm kiếm với bộ lọc loại đợt, trạng thái và loại công việc đang chờ; loại công việc gồm đề tài chờ duyệt, nhóm chờ xét và đề tài chờ công bố. Các số lượng khác 0 phải được thể hiện bằng màu nhấn riêng để Trưởng khoa nhận biết nhanh, còn số 0 giữ kiểu trung tính. Xác nhận U từ feedback giao diện ngày 2026-09-21; liên quan FR-03/FR-07/FR-11.

### Session 2026-09-28

- Q: ADMIN còn được import tài khoản bằng CSV không? → A: Không. ADMIN chỉ tạo từng tài khoản STUDENT, LECTURER hoặc DEAN bằng biểu mẫu; loại bỏ upload CSV, file mẫu và endpoint import. Quyết định này thay thế phần import tài khoản đã chốt ngày 2026-09-16, không thay đổi mapping role, phạm vi khoa/bộ môn hoặc chức năng tạo từng tài khoản. Liên quan FR-01, AS-1.17–1.18, AT-10 và CL-04.
- Q: Phiên bản chỉ triển khai cho Khoa Công nghệ Thông tin có còn cần khái niệm Bộ môn không? → A: Không. Loại bỏ hoàn toàn Department khỏi schema, model, API và UI; giữ bảng Faculty, seed một `Khoa Công nghệ Thông tin` và chưa cung cấp CRUD khoa. STUDENT/LECTURER/DEAN liên kết trực tiếp tới Faculty, ADMIN có thể không có Faculty; Topic và Council kế thừa Faculty từ RegistrationPeriod. Mọi quy tắc “cùng bộ môn” cũ trở thành “cùng khoa”. Khi mở rộng nhiều khoa, mọi đợt, đề tài, đăng ký, hội đồng và chấm điểm tiếp tục bị giới hạn theo `faculty_id`. Quyết định này thay thế mọi mô tả Bộ môn trước đó.

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
   **Then** người dùng được nhận diện theo tài khoản, vai trò và khoa đã cấp;
   không lấy vai trò/khoa từ thông tin tự khai của người đăng nhập (AS-1.5).
6. **Given** tên đăng nhập không tồn tại hoặc mật khẩu không đúng,
   **When** đăng nhập, **Then** không nhận diện người gọi là tài khoản hợp lệ và không
   cho thực hiện thao tác được bảo vệ (AS-1.6).
7. **Given** người chưa đăng nhập, **When** yêu cầu một thao tác được bảo vệ trong luồng,
   **Then** bị từ chối, không có thay đổi nghiệp vụ (AS-1.7).
8. **Given** người chưa được cấp tài khoản, **When** tìm cách tự đăng ký tài khoản,
   **Then** hệ thống không cung cấp khả năng tự đăng ký hoặc tự cấp vai trò/khoa (AS-1.8).

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
14. **Given** tài khoản thuộc khoa hoặc nhóm khác phạm vi được cấp,
    **When** đọc hoặc thay đổi dữ liệu được bảo vệ,
    **Then** backend từ chối dữ liệu ngoài phạm vi đã xác nhận (AS-1.14).
15. **Given** tài khoản chỉ có role ADMIN,
    **When** thực hiện tạo đợt, duyệt/công bố đề tài hoặc xét đăng ký,
    **Then** backend từ chối vì ADMIN không có quyền học vụ hoặc đăng ký (AS-1.15).
16. **Given** nội dung do tài khoản gây sự cố đăng và ADMIN cung cấp lý do,
    **When** ADMIN xử lý khẩn cấp,
    **Then** nội dung bị ẩn khỏi website nhưng còn trong database, tài khoản gây sự cố
    bị khóa và hệ thống lưu ADMIN, thời gian, lý do để kiểm tra/khôi phục (AS-1.16).
17. **Given** hệ thống đã seed Khoa Công nghệ Thông tin,
    **When** ADMIN tạo một tài khoản STUDENT, LECTURER hoặc DEAN,
    **Then** mật khẩu được băm, loại tài khoản được gán đúng role cố định, DEAN nhận thêm
    LECTURER và không có chức năng import tài khoản hàng loạt (AS-1.17).
18. **Given** ADMIN đã đăng nhập,
    **When** mở workspace quản trị tài khoản,
    **Then** Dashboard hiển thị thống kê tài khoản; ba danh sách Sinh viên,
    Giảng viên, Trưởng khoa và khu vực Tạo tài khoản là các điểm đến
    độc lập, không lặp thống kê hoặc form tạo trên trang danh sách (AS-1.18).

### User Story 2 - Giảng viên đăng đề tài có hướng dẫn (Priority: P1)

Là giảng viên, tôi muốn đăng đề tài thuộc khoa của đợt, tự làm GVHD chính và có thể chọn thêm
một GVHD phối hợp
để đề tài có thể được xét duyệt.

**Why this priority**: Tạo nguồn đề tài cho nhóm lựa chọn.
Nguồn: REQ-03, REQ-08–11 (phần GVHD); FR-05–06.

**Independent Test**: Dùng đợt và danh tính GV đã chuẩn bị; thử đề tài có số GVHD khác nhau.

**Acceptance Scenarios**:

1. **Given** thời điểm nằm trong khoảng đăng ký GV và đề tài có đủ nội dung,
   **When** GV gửi bản nháp của mình, **Then** tác giả là GVHD chính ở slot 1, GVHD phối hợp
   nếu có ở slot 2, đề tài chuyển sang Chờ duyệt và GV không được sửa (AS-2.1).
   Nội dung đầy đủ gồm tên đề tài, mô tả, mục tiêu, yêu cầu đối với SV và kết quả dự kiến.
2. **Given** thời điểm chắc chắn ngoài khoảng đăng ký GV,
   **When** gửi duyệt lần đầu hoặc gửi lại, **Then** bị từ chối, bản nháp không chuyển sang Chờ duyệt (AS-2.2).
3. **Given** biểu mẫu tạo hoặc sửa bản nháp, **When** tác giả không chọn GVHD phối hợp,
   **Then** bản nháp vẫn có chính tác giả làm GVHD chính; nếu gửi nhiều hơn một GVHD phối hợp
   hoặc cố chọn lại tác giả thì yêu cầu bị từ chối (AS-2.3).
4. **Given** không chứng minh được quyền đăng đề tài,
   **When** gửi yêu cầu, **Then** bị từ chối, không tạo đề tài (AS-2.4).
   Tài khoản có role DEAN cũng bị từ chối toàn bộ thao tác tạo, xem danh sách của tác giả,
   sửa và gửi duyệt, kể cả khi đồng thời có role LECTURER.
5. **Given** GV thuộc khoa A, **When** đăng đề tài trong đợt thuộc khoa B khác A,
   **Then** bị từ chối và không tạo đề tài, kể cả khi bên gọi tự khai thuộc B (AS-2.5).
6. **Given** đề tài thuộc khoa của GV đăng nhưng GVHD phối hợp thuộc khoa khác,
   **When** gửi đề tài với phân công đó, **Then** bị từ chối và không ghi nhận phân công
   trái quy tắc; áp dụng cho cả trường hợp một và hai GVHD (AS-2.6).

7. **Given** GV là người tạo bản nháp, **When** sửa bản nháp của mình,
   **Then** nội dung và GVHD phối hợp được cập nhật, tác giả vẫn là GVHD chính;
   GV khác không được sửa bản nháp đó (AS-2.7).
8. **Given** đề tài Chờ duyệt, Đã duyệt hoặc Đã công bố, **When** GV đăng yêu cầu sửa,
   **Then** bị từ chối, nội dung và danh sách GVHD không đổi (AS-2.8).
9. **Given** đề tài bị trưởng khoa từ chối và đã về Nháp,
   **When** GV sửa đủ thông tin và gửi lại trong hạn GV,
   **Then** cùng đề tài chuyển trở lại Chờ duyệt (AS-2.9).

10. **Given** bản nháp thiếu hoặc để trống bất kỳ trường nào trong năm trường: tên đề tài,
    mô tả, mục tiêu, yêu cầu đối với SV, kết quả dự kiến,
    **When** gửi duyệt, **Then** bị từ chối và đề tài vẫn ở Nháp (AS-2.10).
11. **Given** bản nháp có đủ năm trường, tác giả hợp lệ và tối đa một GVHD phối hợp cùng khoa,
    **When** tác giả gửi duyệt trong hạn GV, **Then** cả năm giá trị được ghi nhận đúng
    trong đề tài Chờ duyệt (AS-2.11).
12. **Given** thời điểm ngoài khoảng đăng ký GV, **When** Giảng viên mở form hoặc gửi yêu cầu
    tạo đề tài mới, **Then** bị từ chối và không tạo dữ liệu; đề tài/bản nháp đã có vẫn xem
    được và bản nháp hiện hữu vẫn sửa được theo quyền tác giả (AS-2.12).

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
   **Then** toàn bộ đề tài đang Đã duyệt trong đợt thành Đã công bố trong một giao dịch
   và có trong danh sách công bố của đợt (AS-3.1).
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
Trang chi tiết đợt hiển thị đề tài được đề xuất và nhắc hoàn tất duyệt/công bố trước
ngày bắt đầu SV. Lời nhắc không tự duyệt, tự công bố hoặc tạo một SLA/khóa cứng mới.

### User Story 4 - Quản lý nhóm và trưởng nhóm (Priority: P1)

Là sinh viên, tôi muốn tạo nhóm và trở thành trưởng nhóm; với tư cách trưởng nhóm,
tôi muốn mời/xóa thành viên, chuyển quyền và gửi đăng ký đề tài; với tư cách thành viên,
tôi muốn tự quyết định lời mời và có thể rời nhóm theo quy tắc.

**Why this priority**: Nhóm là đơn vị đăng ký.
Nguồn: REQ-12–13; FR-08–09.

**Independent Test**: Chuẩn bị SV chưa có nhóm/lời mời PENDING trong đợt, nhóm có trưởng nhóm
và thành viên; kiểm tra tạo nhóm, lời mời, tự rời/giải tán, quyền quản lý và chuyển quyền.

**Acceptance Scenarios**:

1. **Given** nhóm hợp lệ, **When** xem thành phần nhóm,
   **Then** có từ 1 đến 3 thành viên và đúng 1 trưởng nhóm thuộc nhóm (AS-4.1).
   Nhóm một người đủ điều kiện về số lượng để đăng ký; chỉ được tạo nhóm trong hạn SV.
2. **Given** tổng số thành viên và lời mời đang chờ của nhóm đã là 3,
   **When** trưởng nhóm gửi thêm lời mời, **Then** bị từ chối và sức chứa đã giữ không đổi (AS-4.2).
3. **Given** SV đang thuộc một nhóm hoặc đã có một lời mời đang chờ trong một đợt đăng ký,
   **When** nhóm khác mời SV đó trong cùng đợt, **Then** bị từ chối. Từ chối hoặc hủy lời
   mời sẽ giải phóng ràng buộc lời mời đang chờ; không phát sinh hai tư cách thành viên (AS-4.3).
4. **Given** người không phải trưởng nhóm của nhóm đó,
   **When** thay đổi thành viên/trưởng nhóm, **Then** bị từ chối, trừ thao tác tự rời của
   chính thành viên thường. Trưởng nhóm có thành viên khác phải chuyển quyền trước khi rời;
   trưởng nhóm một người được giải tán nhóm và hệ thống không tự chọn người thay thế (AS-4.4).

5. **Given** SV thuộc nhóm ở đợt A và chưa thuộc nhóm nào ở đợt B,
   **When** tham gia nhóm ở đợt B và thỏa các điều kiện khác đã xác nhận,
   **Then** không bị từ chối vì tư cách thành viên ở đợt A (AS-4.5).

6. **Given** thời điểm nằm trong hạn SV, SV chưa thuộc nhóm nào trong đợt và đủ điều kiện tạo nhóm,
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
    **When** gửi/chấp nhận/hủy lời mời, xóa hoặc tự rời, giải tán nhóm hay chuyển quyền,
    **Then** bị từ chối, thành viên và trưởng nhóm không đổi. SV vẫn được từ chối lời mời
    bất cứ lúc nào; lời mời PENDING tự hết hiệu lực khi hết hạn SV (AS-4.10).
11. **Given** đăng ký đã rút/bị từ chối và nhóm không còn đăng ký có hiệu lực,
    **When** trưởng nhóm sửa thành viên hoặc chuyển quyền trong hạn SV,
    **Then** được thực hiện nếu thỏa các ràng buộc còn lại (AS-4.11).

12. **Given** trưởng nhóm, nhóm còn chỗ và được phép sửa trong hạn SV,
    **When** trưởng nhóm mời một SV phù hợp, **Then** lời mời được ghi nhận nhưng SV chưa
    trở thành thành viên; chỉ sau khi chính SV đó chấp nhận hợp lệ thì tư cách thành viên
    mới được tạo. Lời mời đang chờ giữ một chỗ; tổng thành viên và lời mời đang chờ không
    vượt quá ba, mỗi SV chỉ có một lời mời đang chờ trong một đợt (AS-4.12).

13. **Given** nhóm có đúng 3 SV chính thức, đúng một trưởng nhóm và thỏa các điều kiện đăng ký khác,
    **When** trưởng nhóm gửi đăng ký, **Then** đăng ký được tiếp nhận. Nhóm có 1 hoặc 2 SV
    bị từ chối và phải đủ 3 thành viên chính thức trước khi gửi (AS-4.13).
14. **Given** yêu cầu đăng ký cho nhóm không có thành viên hoặc không có trưởng nhóm,
    **When** gửi đăng ký, **Then** bị từ chối và không tạo đăng ký hợp lệ (AS-4.14).
15. **Given** nhóm còn ít nhất một lời mời PENDING,
    **When** trưởng nhóm gửi đăng ký đề tài, **Then** bị từ chối và không tự hủy lời mời;
    trưởng nhóm phải chờ phản hồi hoặc chủ động hủy trước khi gửi lại (AS-4.15).
16. **Given** sinh viên có các đợt trước, trong và sau cửa sổ đăng ký SV,
    **When** mở trang chọn đợt nhóm, **Then** các đợt được tách thành “Sắp đăng ký”,
    “Đang đăng ký” và “Đã kết thúc”; chỉ đợt đang đăng ký có lối tạo nhóm mới, còn lối
    xem nhóm hoặc phản hồi lời mời đã có vẫn được giữ theo quyền và quy tắc thời gian (AS-4.16).

Đã chốt nhóm 1–3 SV, chỉ tạo và sửa nhóm trong hạn SV; sửa nhóm còn yêu cầu không có đăng ký
chờ xét/đã chấp nhận. Trưởng nhóm gửi lời mời và SV chỉ tham gia sau khi đồng ý. Thành viên
thường được tự rời; trưởng nhóm có thành viên khác phải chuyển quyền trước, còn trưởng nhóm
một người được giải tán nhóm. Lời mời PENDING giữ chỗ, nhóm không vượt tổng sức chứa ba và
mỗi SV chỉ có một lời mời PENDING trong một đợt.
Gửi/chấp nhận/hủy lời mời, rời hoặc giải tán chỉ hợp lệ trong hạn SV và khi nhóm không có
đăng ký PENDING/ACCEPTED; từ chối lời mời được phép bất cứ lúc nào và lời mời chờ tự hết
hiệu lực khi hết hạn SV.

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
   **Then** đăng ký liên kết đúng nhóm, đề tài và đợt, đồng thời lưu bất biến ba thành viên,
   vị trí và trưởng nhóm tại thời điểm gửi (AS-5.1).
2. **Given** ngoài hạn SV hoặc đề tài chưa công bố,
   **When** đăng ký, **Then** bị từ chối và không thêm đăng ký hợp lệ (AS-5.2).
3. **Given** nhóm đã có một đăng ký chờ xét hoặc đã được chấp nhận trong đợt,
   **When** đăng ký thêm đề tài khác, **Then** không hình thành hai đăng ký vi phạm
   quy tắc một đề tài của nhóm (AS-5.3).
4. **Given** đăng ký đủ điều kiện xét và đề tài chưa có nhóm được chấp nhận trong đợt,
   **When** trưởng khoa chấp nhận,
   **Then** kết quả là chấp nhận và nhóm được gắn với đề tài cùng GVHD được phân công;
   mọi thành viên hiện tại của nhóm thấy tên đề tài, trạng thái “Đã chấp nhận”, xác nhận
   đăng ký thành công và liên kết tới chi tiết đăng ký trên trang nhóm (AS-5.4).
5. **Given** đăng ký đủ điều kiện xét, **When** trưởng khoa từ chối,
   **Then** kết quả là từ chối, không coi nhóm đã được chấp thuận thực hiện đề tài (AS-5.5).
   Trưởng khoa phải nhập lý do có nội dung sau khi loại bỏ khoảng trắng đầu/cuối; nếu thiếu
   hoặc chỉ có khoảng trắng thì toàn bộ thao tác bị từ chối và đăng ký vẫn Chờ xét. Nhóm được
   đăng ký lại đề tài cũ hoặc khác còn nhận đăng ký trong hạn SV sau khi bị từ chối hợp lệ.
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
    **Then** tạo được đăng ký chờ xét mới với ID mới nếu thỏa các điều kiện còn lại; đăng ký
    đã rút/từ chối trước đó cùng snapshot thành viên/trưởng nhóm lúc gửi được giữ nguyên
    trong lịch sử, không đổi theo cơ cấu nhóm hiện tại (AS-5.10).
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
Q-07 về điều kiện nhóm đã được chốt lại theo luồng lời mời có xác nhận và tự rời/giải tán.
Q-09 và Q-10 đã được giải quyết cho luồng hiện tại.

### Edge Cases

- GV đăng hoặc một trong hai GVHD khác khoa của đợt: từ chối theo AS-2.5–2.6.
  Phiên bản đầu giả định khoa của giảng viên ổn định khi còn đề tài/phân công hoạt động
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
- Bản nháp có thể chưa đủ nội dung; tác giả luôn là GVHD chính và gửi duyệt chỉ nhận
  thêm tối đa một GVHD phối hợp hợp lệ cùng khoa.
- GV không sửa đề tài ngoài trạng thái Nháp. Từ chối duyệt đưa đề tài về Nháp;
  nếu đã hết hạn GV thì không được gửi lại, không tự gia hạn.
- Người tạo nhóm trở thành trưởng nhóm; chuyển quyền chỉ cho thành viên cùng nhóm và
  không tạo hai trưởng nhóm. Nhóm một SV được đăng ký, SV đó là trưởng nhóm.
- Gửi/chấp nhận/hủy lời mời, xóa/tự rời/giải tán và chuyển trưởng nhóm chỉ trong hạn SV khi
  không có đăng ký có hiệu lực; từ chối lời mời được phép bất cứ lúc nào. Sau rút/từ chối
  được sửa nếu còn hạn. Sửa đồng thời với gửi đăng ký không được làm thay đổi thành phần nhóm
  sau khi đăng ký đã chuyển sang chờ xét; gửi đăng ký bị từ chối nếu còn invitation PENDING.
- Cùng lúc gửi/accept lời mời khi nhóm chỉ còn một chỗ: tổng thành viên và lời mời PENDING
  không vượt 3.
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
  không được bỏ qua quyền/ràng buộc; giao dịch lấy khóa đợt trước được xử lý trước và
  giao dịch sau phải đọc lại trạng thái trước khi quyết định.
- Trong hạn SV, trưởng nhóm được rút đăng ký chờ xét và đăng ký lại đề tài cũ/khác còn
  nhận đăng ký sau rút hoặc từ chối; ngoài hạn không được rút/đăng ký lại.
- Đăng ký đã chấp nhận không được rút. Khi rút đồng thời với chấp nhận, giao dịch lấy khóa
  đợt trước quyết định trạng thái cuối; giao dịch sau đọc lại trạng thái và bị từ chối.
- Từ chối thủ công mà thiếu lý do hoặc lý do chỉ gồm khoảng trắng: không thay đổi trạng thái
  đăng ký và hiển thị lỗi validation cho Trưởng khoa.
- Hai yêu cầu đăng ký lại đồng thời không được tạo hai đăng ký có hiệu lực cho một nhóm.
- Đăng ký lại luôn tạo hàng mới; mọi hàng đã rút hoặc bị từ chối được giữ không thời hạn
  trong phiên bản đầu và không bị tác vụ tự động xóa.
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
| FR-01 | Nhận diện người dùng qua tài khoản nội bộ cấp sẵn bằng tên đăng nhập và mật khẩu với các role ADMIN, STUDENT, LECTURER, DEAN. ADMIN xem toàn bộ tài khoản và tạo STUDENT, LECTURER hoặc DEAN qua ba trang/route riêng, không có form gộp, upload CSV, file mẫu hoặc endpoint import. Form LECTURER/DEAN chỉ nhận họ tên, tên đăng nhập và mật khẩu tạm thời; email để null và việc tự cập nhật email thuộc feature sau. Form STUDENT nhận họ tên, MSSV, lớp, khóa dạng `K2026` và mật khẩu; backend dùng MSSV làm username, sinh email `{mssv}@student.hcmute.edu.vn`, gán Khoa Công nghệ Thông tin đã seed và lưu hồ sơ sinh viên trong cùng transaction. Role luôn được khóa theo route; Tạo DEAN gán đồng thời DEAN và LECTURER. ADMIN có thể không có khoa; không cho tạo ADMIN, sửa/xóa tài khoản hiện hữu, thay role tùy ý hoặc CRUD khoa. STUDENT không kết hợp với ADMIN, LECTURER hoặc DEAN. Trưởng khoa, giảng viên và sinh viên chỉ thao tác dữ liệu trong khoa mình; tài khoản DEAN không được dùng luồng tác giả đề tài dù đồng thời có role LECTURER. ADMIN còn có quyền xử lý sự cố khẩn cấp nhưng không quản lý nghiệp vụ học tập/đăng ký. Không cung cấp tự đăng ký và không chấp nhận quyền/phạm vi do bên gọi tự khai. | U: các phiên 2026-09-08 đến 2026-09-29; quyết định Faculty-only ngày 2026-09-28 và tách form theo vai trò ngày 2026-09-29; constitution III | AS-1.3, 1.5–1.8, 1.14–1.18, 2.4, 3.3, 4.4, 5.6 |
| FR-02 | Trưởng khoa tạo đợt có tên và một trong bốn loại môn học/NCKH/TLCN/KLTN. | E: REQ-01, 04 | AS-1.1, 1.4 |
| FR-03 | Đợt có ngày bắt đầu/kết thúc đăng ký GV và SV theo Asia/Ho_Chi_Minh; form không nhận giờ/phút/giây hoặc ngày trước ngày hiện tại. Trong từng khoảng, ngày bắt đầu không sau ngày kết thúc; ngày kết thúc GV phải trước ngày bắt đầu SV. Tự mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc. Áp dụng khoảng SV cho tạo/sửa nhóm, đăng ký, đăng ký lại và rút chờ xét; đăng ký SV còn phải có danh sách đã công bố. Dashboard Trưởng khoa chỉ tính đợt chưa qua `student_end`; danh sách Trưởng khoa hiển thị cả lịch sử đã quá hạn trong đúng phạm vi khoa, có lựa chọn lọc “Đã quá hạn”, nhãn rõ giai đoạn và số hồ sơ chờ xử lý theo từng đợt. Danh sách cho kết hợp tìm kiếm với bộ lọc loại đợt, trạng thái và loại công việc đang chờ; số chờ khác 0 được nhấn màu theo loại công việc, số 0 giữ kiểu trung tính. | E: REQ-02–05; chi tiết xác nhận U, phiên 2 ngày 2026-09-08, các ngày 2026-09-10–11, feedback 2026-09-16, cập nhật 2026-09-17 và feedback giao diện 2026-09-21, Q-03–04/Q-07 | AS-1.1, 1.4, 1.9–1.10, 2.2, 4.6–4.12, 5.2, 5.11, 5.13, 5.17–5.18 |
| FR-04 | Hạn nộp điểm GVPB chỉ cấu hình và bắt buộc cho TLCN/KLTN; ngày báo cáo hội đồng chỉ cấu hình và bắt buộc cho KLTN. Cả hai là ngày lịch theo Asia/Ho_Chi_Minh, không có giờ/phút/giây và khi tạo không được trước ngày hiện tại. Khi đổi loại trước ngày mở GV, tự xóa ngày không còn áp dụng và chỉ lưu nếu đã nhập đủ ngày bắt buộc của loại mới. Khi sửa trực tiếp một ngày đang áp dụng, chỉ được dời sang ngày muộn hơn nếu ngày hiện tại còn trước ngày đang lưu; từ đầu ngày đang lưu thì khóa và không mở lại. | E: REQ-06–07; Q-02 được người dùng giải quyết ngày 2026-09-10 | AS-1.2, 1.4, 1.11–1.12 |
| FR-05 | GV không có role DEAN chỉ được mở form và tạo đề tài mới trong hạn GV; backend kiểm tra lại thời gian dưới khóa đợt. Ngoài hạn vẫn được xem đề tài cũ và sửa bản nháp hiện hữu, nhưng chỉ gửi duyệt trong hạn. Tài khoản có role DEAN không được dùng luồng tác giả. Khi gửi duyệt bắt buộc đủ năm trường nội dung. Khoa của người đăng phải trùng khoa của đợt theo dữ liệu đáng tin cậy; đề tài kế thừa khoa từ đợt và không có `department_id`. | E: REQ-08–09 và quyết định Faculty-only 2026-09-28 | AS-2.1–2.2, 2.4–2.5, 2.10–2.12 |
| FR-06 | Tác giả đề tài được backend tự động phân công làm GVHD chính ở slot 1. Có thể chọn thêm tối đa một GVHD phối hợp đang hoạt động, có role LECTURER và cùng khoa với đợt. Chỉ tác giả được đổi GVHD phối hợp khi đề tài ở Nháp; từ Chờ duyệt trở đi danh sách bị khóa. | E: REQ-10–11 và quyết định Faculty-only 2026-09-28 | AS-2.1, 2.3, 2.6–2.9 |
| FR-07 | Chỉ trưởng khoa được duyệt/từ chối từng đề tài và công bố danh sách trước khi nhóm chọn đề tài từ danh sách đó. Một thao tác công bố chuyển toàn bộ đề tài đang Đã duyệt của đúng đợt sang Đã công bố trong cùng giao dịch; đề tài Nháp/Chờ duyệt không đổi. Trang chi tiết đợt hiển thị các đề tài và nhắc hoàn tất trước ngày bắt đầu SV. Đây là lời nhắc, không phải SLA hoặc khóa cứng: nếu trễ nhưng còn trong hạn SV, trưởng khoa vẫn có thể công bố. Đến ngày bắt đầu SV không tự thay thế việc công bố; công bố không bỏ qua ngày kết thúc SV. | E: REQ-02, 08, 15; quyền/lịch xác nhận U ngày 2026-09-08, granularity/UI xác nhận U ngày 2026-09-11 | AS-3.1–3.5, 5.17–5.18 |
| FR-08 | SV chỉ được tạo nhóm trong hạn SV và trở thành trưởng nhóm. Trang chọn đợt nhóm tách “Sắp đăng ký”, “Đang đăng ký” và “Đã kết thúc” theo `student_start`/`student_end`; chỉ nhóm “Đang đăng ký” có lối tạo nhóm mới, còn lối xem nhóm hoặc phản hồi lời mời đã có được giữ đúng quyền. Trong hạn SV và khi nhóm không có đăng ký PENDING/ACCEPTED, trưởng nhóm được gửi/hủy lời mời, xóa thành viên và chuyển quyền cho thành viên khác; SV được mời tự chấp nhận và chỉ trở thành thành viên sau khi chấp nhận hợp lệ. SV được từ chối lời mời bất cứ lúc nào; lời mời chưa trả lời tự hết hiệu lực khi hết hạn SV. Lời mời PENDING giữ chỗ: tổng thành viên và lời mời PENDING của nhóm không vượt quá ba, mỗi SV chỉ có một lời mời PENDING trong một đợt; từ chối/hủy giải phóng chỗ. Thành viên thường được tự rời trong cùng điều kiện thời gian/trạng thái. Trưởng nhóm có thành viên khác phải chuyển quyền trước khi rời; trưởng nhóm một người được giải tán nhóm, hệ thống không tự chọn người thay thế. Sau rút/từ chối được sửa nếu còn hạn và không có đăng ký có hiệu lực khác. Nhóm được tồn tại với 1–3 SV và đúng một trưởng nhóm là thành viên, nhưng chỉ nhóm có đúng 3 thành viên chính thức mới được gửi đăng ký đề tài. | E: REQ-12–13; quyền/thời điểm xác nhận U ngày 2026-09-08; luồng lời mời, giữ chỗ, thời gian phản hồi và tự rời thay thế quyết định thêm trực tiếp ngày 2026-09-11; điều kiện đủ 3 xác nhận U ngày 2026-09-12; cách chia danh sách xác nhận U từ feedback ngày 2026-09-16 | AS-4.1–4.2, 4.4, 4.6–4.16 |
| FR-09 | Mỗi SV chỉ thuộc một nhóm trong từng đợt đăng ký. Không từ chối tư cách thành viên ở đợt khác chỉ vì SV đã thuộc nhóm trong một đợt. | E: REQ-13; phạm vi được xác nhận U, Session 2026-09-08, giải quyết Q-08 | AS-4.3, 4.5 |
| FR-10 | Chỉ trưởng nhóm được gửi đăng ký cho nhóm mình; tại thời điểm gửi nhóm phải có đúng 3 thành viên chính thức và đúng một trưởng nhóm. Đăng ký lưu snapshot bất biến gồm ba thành viên, vị trí và trưởng nhóm lúc gửi. Nhóm chỉ có một đăng ký có hiệu lực (chờ xét hoặc đã chấp nhận) cho đề tài đã công bố trong đợt và chỉ gửi đăng ký trong hạn SV. Nhóm không được gửi khi còn lời mời PENDING; hệ thống không tự hủy lời mời, trưởng nhóm phải chờ phản hồi hoặc hủy trước. Nhiều nhóm được đăng ký chờ xét cùng đề tài; một đăng ký chờ xét không giữ chỗ độc quyền. | E: REQ-03, 14–15; Q-07/Q-09/Q-10 được xác nhận U, các phiên ngày 2026-09-08 và 2026-09-11; đủ 3 và snapshot xác nhận U ngày 2026-09-12 | AS-4.13, 4.15, 5.1–5.3 |
| FR-11 | Chỉ trưởng khoa được chấp nhận đăng ký trước khi nhóm thực hiện đề tài với GVHD được phân công. Mỗi đề tài trong một đợt chỉ được chấp nhận tối đa một nhóm, kể cả khi thao tác đồng thời. Khi chấp nhận, tự động từ chối các đăng ký còn chờ xét khác cùng đề tài/đợt với lý do “Đề tài đã nhận nhóm” và ngừng nhận đăng ký mới trong đợt. Khi chấp nhận tranh chấp với rút, giao dịch lấy khóa đợt trước quyết định trạng thái; giao dịch sau đọc lại và bị từ chối. Trang nhóm hiển thị đăng ký PENDING/ACCEPTED, tên đề tài và liên kết chi tiết cho mọi thành viên hiện tại; ACCEPTED có xác nhận đăng ký thành công nổi bật. | E: REQ-16; quyền và giới hạn xác nhận U, các phiên ngày 2026-09-08 (Q-01, Q-10), ưu tiên tranh chấp xác nhận ngày 2026-09-11, phản hồi kết quả xác nhận U ngày 2026-09-13 | AS-5.4, 5.6–5.8, 5.14–5.16 |
| FR-12 | Chỉ trưởng khoa được quyết định từ chối thủ công và phải nhập lý do có nội dung sau khi trim; thiếu lý do thì không đổi trạng thái. Hệ thống tự động từ chối theo FR-11 khi đề tài nhận nhóm khác với lý do cố định. Không coi đăng ký bị từ chối là đã chấp thuận. Sau từ chối, trưởng nhóm được đăng ký lại đề tài cũ hoặc khác còn nhận đăng ký trong hạn SV theo FR-13. | U: phạm vi và các phiên ngày 2026-09-08, lý do thủ công xác nhận ngày 2026-09-11; liên quan REQ-16, Q-09–10; PDF không mô tả nhánh từ chối | AS-5.5–5.6, 5.14–5.16 |
| FR-13 | Trong hạn SV, chỉ trưởng nhóm của nhóm được rút đăng ký đang chờ xét. Sau khi rút hoặc bị từ chối, trưởng nhóm được đăng ký lại đề tài cũ hoặc đề tài khác đã công bố còn nhận đăng ký, nếu không có đăng ký có hiệu lực khác trong đợt và nhóm hiện tại lại đủ đúng 3 thành viên. Mỗi lần đăng ký lại tạo hàng mới; giữ không thời hạn các hàng REJECTED/WITHDRAWN cũ cùng snapshot thành viên/trưởng nhóm tại lần gửi đó và không tự động xóa. Không cho rút đăng ký đã chấp nhận; không cho rút hoặc đăng ký lại ngoài hạn SV. Khi rút tranh chấp với chấp nhận, giao dịch lấy khóa đợt trước quyết định trạng thái; giao dịch sau đọc lại và bị từ chối. | U: Session 2026-09-08; ưu tiên tranh chấp và retention Q-09 xác nhận ngày 2026-09-11; snapshot xác nhận U ngày 2026-09-12; liên quan REQ-03, REQ-14–16, REQ-31 | AS-5.9–5.15 |
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
Nhận diện/phân quyền dùng tài khoản nội bộ cấp sẵn với tên đăng nhập/mật khẩu, vai trò và khoa.
Tài khoản được cấp local và không cung cấp tự đăng ký. ADMIN có giao diện xem toàn bộ tài
khoản, chỉ tạo STUDENT/LECTURER/DEAN từng người bằng biểu mẫu, đồng thời vẫn có
quyền xử lý sự cố bằng cách ẩn nội dung, lưu dấu vết và khóa tài khoản gây sự cố. ADMIN không
có quyền học vụ, đăng ký, sửa/xóa tài khoản hiện hữu, thay role tùy ý hoặc thay đổi cơ cấu tổ
chức. Nguồn danh tính cho đợt đầu không phụ thuộc đăng nhập của trường.

Ngoài đợt này nhưng vẫn trong tổng thể: nộp báo cáo REQ-17; phân công GVPB thuộc
REQ-11; phản biện/hội đồng REQ-18–22; chấm điểm REQ-23–25; kết quả REQ-26–27;
thông báo REQ-29–30; quản trị tài khoản ngoài phạm vi tạo và import đã chốt ở FR-01.
REQ-31–32 áp dụng xuyên suốt. Cấu hình REQ-06–07 không kéo nghiệp vụ chấm điểm/hội đồng vào đợt này.

Phụ thuộc nghiệp vụ: Khoa Công nghệ Thông tin được seed; danh tính, vai trò và dữ liệu GV/SV được cấp đáng tin cậy;
quyền trưởng khoa cho duyệt/công bố, quyết định đăng ký và quy tắc lịch đã xác nhận;
điều kiện nhóm và một số chuyển trạng thái còn cần xác nhận. Danh tính là tài khoản nội bộ
cấp sẵn qua màn hình ADMIN hoặc seed demo local; phiên bản đầu không có màn hình quản lý danh mục.
Không mặc định tích hợp bên ngoài. Không cần các tính năng triển khai sau để đặc tả luồng này.

### Authorization and Automated Verification

Đây là yêu cầu chất lượng từ U và constitution III–X, không phải quy tắc mới suy ra từ PDF.
Không chỉ định giao thức, framework, database hay công cụ kiểm thử.

| ID | Kiểm chứng tự động bắt buộc khi triển khai | Liên kết |
|---|---|---|
| AT-01 | Bộ ca bốn loại đợt kiểm tra trường bắt buộc và ngày áp dụng; dữ liệu sai không tạo đợt hợp lệ. | FR-02–04; AS-1.1–1.4 |
| AT-02 | Ca trước/trong/sau từng ngày biên GV/SV theo Asia/Ho_Chi_Minh: mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc. Kiểm tra ngày đã qua, giá trị có giờ, lịch chồng lấn, sai thứ tự hoặc ngày kết thúc GV trùng ngày bắt đầu SV bị từ chối; ngày SV kế tiếp hợp lệ; chưa công bố không nhận đăng ký SV. Áp dụng biên SV cho đăng ký lại và rút chờ xét. | FR-03, 05, 07, 10, 13; AS-1.4, 1.9–1.10, 5.17–5.18; Q-03–04 đã xác nhận và điều chỉnh ngày 2026-09-10 |
| AT-03 | Tác giả luôn được lưu ở slot 1; ca không có/có một GVHD phối hợp cùng khoa hợp lệ. Từ chối giả mạo tác giả, chọn lại tác giả, chọn quá một người phối hợp hoặc chọn người khác khoa. Lần lượt bỏ hoặc để trống từng trường nội dung: không chuyển khỏi Nháp. Đủ cả năm trường và điều kiện khác: chuyển Chờ duyệt. | FR-05–06; AS-2.1–2.6, 2.10–2.11 |
| AT-04 | Ca tạo nhóm trong hạn SV với người tạo là trưởng nhóm và từ chối tạo ngoài hạn; gửi/chấp nhận/từ chối/hủy lời mời; xóa hoặc tự rời; chuyển quyền cho thành viên cùng nhóm; từ chối người nhận ngoài nhóm; giải tán nhóm một người; giữ từ 1 đến 3 thành viên và đúng một trưởng nhóm. Từ chối SV thuộc hai nhóm cùng đợt, không xung đột với nhóm ở đợt khác. Từ chối sửa nhóm khi chờ xét/đã chấp nhận hoặc ngoài hạn SV; cho phép sau rút/từ chối trong hạn nếu không có đăng ký có hiệu lực. Kiểm tra cả hai biên thời gian SV và xác nhận lời mời không tự tạo membership. Chỉ nhóm đúng 3 người đăng ký hợp lệ; nhóm 1/2 người, không thành viên hoặc không trưởng nhóm bị từ chối. Kiểm tra lịch sử đăng ký vẫn hiển thị đúng ba thành viên, vị trí và trưởng nhóm lúc gửi sau khi đăng ký đã rút/từ chối và nhóm hiện tại thay đổi. | FR-08–10,13; AS-4.1–4.15,5.1,5.10 |
| AT-05 | Ca đề tài công bố/chưa công bố, từ chối gửi khi còn lời mời PENDING mà không tự hủy lời mời, đăng ký đầu/thứ hai của nhóm, nhiều nhóm chờ xét cùng đề tài, trưởng khoa chấp nhận/từ chối; từ chối chấp nhận nhóm thứ hai cho cùng đề tài/đợt. Kiểm tra mọi thành viên hiện tại thấy đúng tên đề tài và trạng thái PENDING/ACCEPTED trên trang nhóm, ACCEPTED có xác nhận thành công và liên kết chi tiết. Kiểm tra rút chờ xét trong hạn; đăng ký lại đề tài cũ/khác sau từ chối/rút bằng ID mới và giữ hàng lịch sử; từ chối rút đã chấp nhận, rút/đăng ký lại ngoài hạn và rút sai quyền. | FR-07, 10–13; AS-4.15, 5.4, 5.7, 5.9–5.13 |
| AT-06 | Mỗi thao tác được bảo vệ có ca quyền hợp lệ và bị từ chối, kể cả gọi trực tiếp backend và giả mạo role/quan hệ; xác nhận không thay đổi trái phép. Luồng tác giả đề tài từ chối tài khoản có role DEAN dù đồng thời có LECTURER. Duyệt/công bố đề tài và chấp nhận/từ chối đăng ký: cho phép trưởng khoa; từ chối tài khoản không có vai trò trưởng khoa, gồm GV đăng đề tài và GVHD. Quản lý thành viên/chuyển quyền/gửi hoặc rút đăng ký: chỉ trưởng nhóm của nhóm đó; từ chối thành viên thường, trưởng nhóm khác và người đã chuyển quyền. | FR-01–13; constitution III, VIII; các phiên 2026-09-08 và 2026-09-12 |
| AT-07 | Ca đồng thời thêm thành viên, cùng SV tham gia hai nhóm trong cùng đợt và nhóm đăng ký đề tài; kết quả không vi phạm REQ-12–14. Kiểm tra nhóm ở hai đợt khác nhau không xung đột vì REQ-13. Hai thao tác chấp nhận nhóm khác nhau cho cùng đề tài/đợt không được tạo hai nhóm được chấp nhận. Không gán ưu tiên nghiệp vụ cho nhóm nào; khóa đợt tuần tự hóa kết quả. | FR-08–11, 13; AS-5.8; constitution VI; Q-08–10 đã xác nhận |
| AT-08 | Ca quyền hoặc dữ liệu liên quan thay đổi trong lúc xử lý quyết định; kết quả vẫn tuân thủ chính sách đã chốt, không ghi trạng thái trái phép. Ca chuyển trưởng nhóm đồng thời với quản lý thành viên/gửi đăng ký phải giữ đúng một trưởng nhóm và không dùng quyền đã mất để thực hiện thao tác trái phép.  Rút và chấp nhận đồng thời không được tạo trạng thái vừa rút vừa chấp nhận; đăng ký lại đồng thời không tạo hai đăng ký có hiệu lực. | FR-01, 07–08, 10–13 |
| AT-09 | Đầu vào sai tại ranh giới hệ thống bị từ chối và không lưu thay đổi dở dang. | constitution IV; các FR liên quan |
| AT-10 | Đăng nhập đúng thông tin tài khoản cấp sẵn; từ chối sai mật khẩu/tài khoản không tồn tại và thao tác được bảo vệ khi chưa đăng nhập. Vai trò/khoa đúng dữ liệu được cấp, không thể tự khai để nâng quyền; kiểm tra từ chối truy cập chéo khoa. Chỉ ADMIN được xem và tạo từng tài khoản qua ba route khóa vai trò. STUDENT lấy username từ MSSV, email đúng domain sinh viên và lưu lớp/khóa; LECTURER/DEAN không nhận email khi ADMIN tạo; DEAN nhận hai role. Mật khẩu được băm, khoa CNTT được backend gán, dữ liệu trùng/sai hoặc lỗi ghi profile không để lại account dở dang. Endpoint import và POST gộp không tồn tại; không có CRUD khoa. | FR-01; AS-1.5–1.8, 1.14–1.15, 1.17, 2.4; REQ-31–32 |
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
  mật khẩu và được ADMIN tạo từng người bằng biểu mẫu. Không tự đăng ký. ADMIN
  quản lý việc cấp tài khoản theo ba loại cố định và xử lý sự cố bằng ẩn nội dung, lưu dấu
  vết và khóa tài khoản gây sự cố, nhưng không có quyền học vụ/đăng ký.
  Khoa của GV là dữ liệu dùng để kiểm tra quyền đăng đề tài và tính hợp lệ của GVHD;
  biểu mẫu chỉ chọn tài khoản LECTURER đang hoạt động cùng khoa với đợt.
- **Đợt đăng ký**: tên, loại, bốn ngày đăng ký GV/SV, hai ngày có điều kiện; hai giai đoạn.
  Tất cả trường lịch chỉ nhận ngày theo Asia/Ho_Chi_Minh, không nhận ngày đã qua hoặc
  giờ/phút/giây; mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc. Khoảng GV trước SV.
  Hạn điểm GVPB và ngày hội đồng chỉ có độ chính xác ngày lịch; từng ngày chỉ được dời
  muộn hơn trước ngày đang lưu. Từ đầu ngày bắt đầu GV hiện hành, tên, loại và các ngày bắt đầu
  bị khóa; ngày kết thúc chỉ được kéo dài trước khi giai đoạn tương ứng hết hạn.
- **Đề tài**: tên đề tài, mô tả, mục tiêu, yêu cầu đối với SV, kết quả dự kiến;
  đợt/khoa, GV đăng đồng thời là GVHD chính và tối đa một GVHD phối hợp, tình trạng duyệt/công bố.
  Mỗi đề tài chỉ thuộc một đợt; không có chức năng dùng lại hoặc sao chép sang đợt khác
  trong phiên bản đầu, nên GV tạo đề tài mới nếu muốn đăng lại nội dung ở đợt mới.
  Trong một đợt có thể có nhiều nhóm đăng ký chờ xét nhưng tối đa một nhóm được chấp nhận.
  Trạng thái: Nháp, Chờ duyệt, Đã duyệt, Đã công bố; từ chối duyệt đưa về Nháp.
  Năm trường nội dung bắt buộc khi gửi duyệt; chỉ tác giả được sửa nội dung và GVHD
  khi Nháp. Từ Chờ duyệt trở đi, nội dung và GVHD bị khóa; phiên bản đầu không có
  chức năng thay GVHD trong quá trình thực hiện.
- **Nhóm và thành viên**: nhóm gắn với đợt đăng ký, từ một đến ba SV, đúng một trưởng nhóm;
  lời mời PENDING giữ slot nhưng chỉ tạo tư cách thành viên khi SV chấp nhận.
  Mỗi SV chỉ thuộc một nhóm và chỉ có một lời mời PENDING trong từng đợt, có thể thuộc nhóm
  khác ở đợt khác. Người tạo là trưởng nhóm ban đầu; trưởng nhóm có thể chuyển quyền cho
  thành viên khác. Thành viên thường được tự rời; leader một người được giải tán nhóm.
- **Đăng ký và quyết định**: liên kết nhóm–đề tài–đợt; các trạng thái trong luồng đã chốt
  gồm chờ xét, chấp nhận, từ chối, đã rút. Trưởng khoa quyết định chấp nhận/từ chối thủ công;
  khi chấp nhận một nhóm, hệ thống tự động từ chối các đăng ký khác còn chờ xét cùng
  đề tài/đợt với lý do “Đề tài đã nhận nhóm”.
  trưởng nhóm rút chờ xét trong hạn. Sau từ chối/rút có thể tạo đăng ký mới trong hạn.
  Từ chối thủ công bắt buộc có lý do. Khi rút/chấp nhận đồng thời, giao dịch lấy khóa đợt
  trước quyết định trạng thái và giao dịch sau bị từ chối. Mọi lần rút/từ chối được giữ
  không thời hạn; đăng ký lại tạo hàng mới và không ghi đè lịch sử. Chỉ nhóm đủ đúng 3
  thành viên được gửi; mỗi lần gửi lưu snapshot bất biến thành viên/vị trí/trưởng nhóm.

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
  thông tin nhận đúng vai trò/khoa; số lần cấp quyền thành công bằng mật khẩu sai,
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
có thể kết hợp DEAN, nhưng tài khoản có DEAN không được dùng luồng tác giả đề tài; STUDENT
không kết hợp với ADMIN, LECTURER hoặc DEAN. ADMIN cấp tài khoản STUDENT/LECTURER/DEAN
qua ba luồng khóa vai trò; sinh viên dùng MSSV làm username và email sinh tự động, còn email
giảng viên/trưởng khoa được để trống chờ feature tự cập nhật. ADMIN xử lý sự cố khẩn cấp,
nhưng không có quyền học vụ hoặc đăng ký.
Trưởng khoa đã được xác nhận có quyền
duyệt/công bố và chấp nhận/từ chối đăng ký. Trưởng nhóm đã được xác nhận quản lý thành viên,
chuyển quyền trong nhóm, gửi đăng ký và rút đăng ký chờ xét trong hạn SV.
Không suy rộng thêm quyền ngoài những vai trò và quan hệ tài nguyên đã được chốt.

### Clarifications

Trạng thái ba nhóm làm rõ ban đầu được cập nhật dưới đây; các Q khác vẫn được giữ nguyên,
không bị thay bằng giả định để giới hạn số câu hỏi.

- **CL-01**: Đã giải quyết thẩm quyền: trưởng khoa duyệt/công bố và chấp nhận/từ chối; trưởng nhóm quản lý thành viên, chuyển quyền cho thành viên cùng nhóm và gửi đăng ký; SV tạo nhóm là trưởng nhóm ban đầu.
- **CL-02**: Đã xác nhận một nhóm/SV theo từng đợt (Q-08); nhiều nhóm chờ xét nhưng tối đa một nhóm được chấp nhận/đề tài/đợt (Q-10). Đã xác nhận trong hạn SV được rút chờ xét, đăng ký lại đề tài cũ/khác còn nhận đăng ký sau rút/từ chối; không rút đăng ký đã chấp nhận. Lý do từ chối thủ công là bắt buộc. Nếu rút/chấp nhận đồng thời, giao dịch lấy khóa đợt trước quyết định kết quả và giao dịch sau bị từ chối sau khi đọc lại trạng thái. Mỗi lần đăng ký lại tạo hàng mới và giữ không thời hạn các hàng REJECTED/WITHDRAWN cũ; không có tác vụ tự động xóa lịch sử trong phiên bản đầu (Q-09).
- **CL-03**: Đã giải quyết Q-02. Dùng ngày lịch Asia/Ho_Chi_Minh cho cả bốn ngày GV/SV và hai ngày phụ; không nhập giờ/phút/giây hoặc ngày đã qua. Mở từ đầu ngày bắt đầu và đóng sau cuối ngày kết thúc; ngày bắt đầu SV phải sau ngày kết thúc GV. SV chỉ đăng ký sau công bố và còn hạn. Từ đầu ngày bắt đầu GV, khóa tên, loại và các ngày bắt đầu; chỉ kéo dài ngày kết thúc trước khi giai đoạn hết hạn, không rút ngắn hoặc mở lại. TLCN/KLTN bắt buộc hạn điểm GVPB, KLTN bắt buộc ngày hội đồng; từng ngày phụ chỉ được dời muộn hơn trước ngày đang lưu. Đổi loại trước ngày mở GV tự xóa ngày không còn áp dụng và yêu cầu đủ ngày của loại mới.
- **CL-04**: Đã chọn tài khoản local; ADMIN xem toàn bộ tài khoản và tạo STUDENT/LECTURER/DEAN qua ba biểu mẫu/route riêng. Ngày 2026-09-28 loại bỏ import CSV và toàn bộ Department. Ngày 2026-09-29 chốt sinh viên dùng MSSV làm username, email `{mssv}@student.hcmute.edu.vn`, có lớp và khóa dạng `K2026`; LECTURER/DEAN chưa nhập email khi ADMIN tạo. Tài khoản học vụ gắn trực tiếp Faculty; phiên bản hiện tại seed đúng một Khoa Công nghệ Thông tin và chưa có CRUD khoa. DEAN nhận đồng thời DEAN và LECTURER; quyền trưởng khoa/giảng viên/sinh viên giới hạn theo khoa và quan hệ dữ liệu. ADMIN có thể không có khoa và không được làm nghiệp vụ học vụ.
- **CL-05**: Đã xác nhận toàn bộ AS/SC hiện tại làm chuẩn nghiệm thu; bảo mật nền tảng, responsive và xung đột dữ liệu chính phải được kiểm tra. Không đặt SLA định lượng về tải, thời gian phản hồi, uptime hoặc backup cho phiên bản local. Q-20 và G-Acceptance đã giải quyết; các Q nghiệp vụ còn lại cũng đã được đóng trong các phiên sau.
- **CL-06**: Mỗi đề tài chỉ thuộc một đợt, kế thừa khoa từ đợt và không có chức năng dùng lại/sao chép sang đợt khác. Biểu mẫu chỉ chọn tài khoản LECTURER đang hoạt động cùng khoa. Không có luồng SV đề xuất đề tài trong phiên bản đầu.
- **CL-07**: Đã giải quyết Q-06 cho phạm vi hiện tại. Chỉ tác giả được thay đổi GVHD khi đề tài ở Nháp; từ Chờ duyệt trở đi danh sách bị khóa. Nếu trưởng khoa từ chối đề tài về Nháp thì tác giả được sửa và gửi lại trong hạn GV. Phiên bản đầu không hỗ trợ thay GVHD trong quá trình thực hiện.
- **CL-08**: Đã giải quyết G-Publish. Trưởng khoa duyệt/từ chối từng đề tài; công bố toàn bộ đề tài Đã duyệt của một đợt trong một giao dịch. Trang chi tiết đợt là nơi hiển thị và thao tác; UI nhắc hoàn tất trước ngày bắt đầu SV nhưng không đặt SLA, tự động công bố hoặc khóa thao tác trễ.
- **CL-09**: Q-07 được mở lại và thay đổi ngày 2026-09-11. Chỉ tạo nhóm trong hạn SV; trưởng nhóm gửi lời mời và SV chỉ thành thành viên sau khi đồng ý. Lời mời PENDING giữ chỗ, tổng thành viên và lời mời PENDING của nhóm không vượt ba; mỗi SV chỉ có một lời mời PENDING trong một đợt. Gửi/chấp nhận/hủy, rời/giải tán chỉ trong hạn SV và khi nhóm không có đăng ký PENDING/ACCEPTED; từ chối được phép bất cứ lúc nào, lời mời chờ tự hết hiệu lực khi hết hạn. Thành viên thường được tự rời. Trưởng nhóm có thành viên khác phải chuyển quyền trước; trưởng nhóm một người được giải tán nhóm.
- **CL-10**: Quy tắc tối thiểu của Q-07 được thay thế ngày 2026-09-12: nhóm vẫn tồn tại với 1–3 SV nhưng chỉ nhóm đủ đúng 3 thành viên chính thức mới được gửi đăng ký. Mỗi đăng ký lưu snapshot bất biến ba thành viên, vị trí và trưởng nhóm lúc gửi; lịch sử không thay đổi theo cơ cấu nhóm sau khi rút/từ chối.
- **CL-11**: Đã xác nhận phản hồi kết quả ngày 2026-09-13. Trang nhóm hiển thị tên đề tài, trạng thái PENDING/ACCEPTED và liên kết chi tiết đăng ký cho mọi thành viên hiện tại; ACCEPTED có xác nhận đăng ký thành công nổi bật. Không bổ sung email, push hoặc mô-đun thông báo tự động trong phạm vi này.

Không còn quyết định nghiệp vụ đang mở trong phạm vi hiện tại. Q-02, Q-05, Q-06, Q-07,
Q-09, Q-18 và Q-20 đã đóng; G-Registration được giải phóng để triển khai US5.
