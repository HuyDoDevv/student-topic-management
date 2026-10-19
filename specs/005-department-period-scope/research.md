# Research: Đợt đăng ký theo Bộ môn

**Nguồn**: [spec.md](spec.md), U-005-01–07 và REQ được truy vết trong đặc tả. Đây là quyết định thiết kế cho feature 005, không phải bằng chứng đã triển khai.

## R1 — Đợt có đúng một Bộ môn

**Decision**: Mỗi `RegistrationPeriod` mới thuộc một `Department` trong Khoa của Trưởng khoa, áp dụng cho COURSE, NCKH, TLCN và KLTN. Giữ Khoa trên đợt để tương thích các quan hệ và quyền cấp Khoa; Bộ môn là phạm vi cụ thể của đợt.

**Rationale**: U-005-01 làm rõ cách tổ chức danh sách đề tài theo Bộ môn. PDF chỉ nêu danh sách đề tài theo Bộ môn, không trực tiếp yêu cầu chọn Bộ môn khi tạo đợt.

**Alternatives considered**: Chỉ gắn Bộ môn trên đề tài khiến cùng một đợt trộn nhiều Bộ môn; tự tạo đợt theo mỗi môn học cụ thể vượt phạm vi U-005-07.

## R2 — Trưởng khoa vẫn quản lý toàn Khoa

**Decision**: DEAN tạo/xem/sửa đợt thuộc bất kỳ Bộ môn nào trong Khoa mình; `department_id` của tài khoản DEAN không hạn chế quyền này. Bộ môn gửi từ form phải được tải lại và kiểm tra thuộc Khoa của actor.

**Rationale**: U-005-02 giữ quyền quản lý cấp Khoa của 004; không tạo role Trưởng Bộ môn.

**Alternatives considered**: Khóa DEAN vào Bộ môn công tác hoặc thêm role mới đều trái quyết định đã xác nhận.

## R3 — Phạm vi GV và ngoại lệ phân công

**Decision**: LECTURER thường chỉ liệt kê/xem/đề xuất đề tài trong đợt thuộc Bộ môn mình. Ngoại lệ chấm/hội đồng chéo Bộ môn cùng Khoa chỉ cấp quyền xem **nhiệm vụ cụ thể còn hiệu lực**, không mở danh sách đợt/đề tài chung; GVHD không chấm đề tài mình hướng dẫn.

**Rationale**: U-005-03, U-005-05, U-005-06 và REQ-25/31. Chặn ở service/backend, kể cả URL trực tiếp.

**Alternatives considered**: Chỉ ẩn liên kết trên UI không bảo vệ dữ liệu; mở toàn đợt cho người được phân công làm lộ đề tài khác.

## R4 — Sinh viên và hội đồng

**Decision**: STUDENT giữ phạm vi Khoa, có thể thấy đợt nhiều Bộ môn và chỉ thấy/chọn đề tài đã công bố. Hội đồng tiếp tục thuộc đợt, suy ra Bộ môn qua đợt; không thêm `councils.department_id`.

**Rationale**: U-005-04/06, tránh nhân đôi phạm vi và không áp quy tắc GV sang SV.

**Alternatives considered**: Khóa SV theo Bộ môn; thêm cột Bộ môn độc lập cho hội đồng, dễ tạo trạng thái mâu thuẫn.

## R5 — Chuyển đổi dữ liệu đợt cũ

**Decision**: Migration mới mở rộng nullable; kiểm kê và lập bảng ánh xạ đợt→Bộ môn được chủ dự án xác nhận; phát hiện đợt có đề tài từ nhiều Bộ môn thì dừng, không tự gán. Chỉ enforce sau khi chứng minh mọi đợt cần thiết đã được ánh xạ và quan hệ đề tài/nhóm/hội đồng còn nhất quán.

**Rationale**: FR-005-09 và nguyên tắc bảo toàn dữ liệu. Bộ môn của đề tài bất kỳ hoặc tên đợt không phải chứng cứ đủ tin cậy.

**Alternatives considered**: Gán tất cả đợt vào `Công nghệ phần mềm`, lấy Bộ môn theo đề tài đầu tiên, hoặc sửa migration V1–V18: đều có nguy cơ sai nghiệp vụ/lịch sử Flyway.

## R6 — Một DEAN đang hoạt động

**Decision**: Tối đa một tài khoản có role DEAN và trạng thái hoạt động trong mỗi Khoa; thay người phụ trách không làm đổi sở hữu đợt. Cần kiểm tra cả tạo/kích hoạt và đồng thời, không chỉ lọc UI.

**Rationale**: U-005-02 và User Story 5. Đây là quy tắc bổ sung của chủ dự án, không gán cho PDF.

**Alternatives considered**: Xóa tài khoản DEAN cũ hoặc gắn đợt vào cá nhân DEAN; cả hai không cần thiết và có thể mất lịch sử.

## Boundaries

- Chưa quyết định lại quy tắc một SV tham gia nhóm xuyên các đợt đồng thời.
- Không tạo luồng GVPB mới; chỉ bảo vệ nhiệm vụ chấm/hội đồng đang có.
- T010 của feature 004 chưa nghiệm thu full integration/E2E; phải kiểm tra lại baseline trước khi tuyên bố 005 hoàn thành.
