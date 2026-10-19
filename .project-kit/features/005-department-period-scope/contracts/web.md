# Web Contract: Đợt đăng ký theo Bộ môn

**Trạng thái**: hợp đồng mong đợi sau 005; các route dưới đây là route hiện hữu cần được điều chỉnh behavior, không phải tính năng đã hoàn thành. Tham chiếu [spec.md](../spec.md).

## DEAN — Đợt và đề tài toàn Khoa

| Method | Route hiện có | Contract sau 005 |
|---|---|---|
| GET | `/periods` | Liệt kê đợt của tất cả Bộ môn trong Khoa DEAN, hiển thị Bộ môn. |
| GET | `/periods/new` | Form chọn Bộ môn thuộc Khoa của DEAN. |
| POST | `/periods` | Bắt buộc `departmentId`; backend tải lại Bộ môn và từ chối ID ngoài Khoa/không tồn tại. |
| GET | `/periods/{id}` | DEAN cùng Khoa xem được đợt của mọi Bộ môn trong Khoa. |
| GET/POST | `/periods/{id}/edit`, `/periods/{id}` | Giữ kiểm tra Khoa/version; không đổi Bộ môn sau khi đợt mở hoặc có dữ liệu phụ thuộc. |
| GET | `/periods/{id}/topics/review` | DEAN duyệt đề tài của Bộ môn thuộc đợt; không lọc theo Bộ môn công tác của DEAN. |

POST cần CSRF và validation. Không tin `facultyId`/`departmentId` do client gửi để cấp quyền. Trường hợp sai phạm vi phải bị từ chối trước khi ghi thay đổi; lỗi validation có thể trở về form theo convention hiện có.

## LECTURER — Danh sách, đề xuất và URL trực tiếp

- `/topics` và các danh sách đợt cho GV chỉ trả đợt/đề tài trong Bộ môn actor. Trưởng khoa vẫn dùng quyền Khoa riêng.
- `GET /periods/{periodId}/topics`, `GET /periods/{periodId}/topics/new` và `POST /periods/{periodId}/topics`: GV thường phải thuộc Bộ môn của đợt; gửi ID đợt khác Bộ môn bị từ chối ở backend, không trả danh sách/ghi đề tài.
- `GET /topics/{id}`, form sửa/submit và các thao tác trên đề tài phải kiểm tra actor/ownership/Bộ môn; không chỉ dựa vào việc ẩn đường dẫn.
- Khi tạo/sửa/gửi duyệt, service xác nhận Bộ môn đợt, đề tài, tác giả và 1–2 GVHD khớp; form chỉ gợi ý GVHD hợp lệ, backend vẫn kiểm tra lại.

## STUDENT — Đợt toàn Khoa, đề tài đã công bố

- Danh sách đợt và `topics/period-selection.html` cho thấy Bộ môn của từng đợt trong Khoa; không lọc SV theo Bộ môn.
- Danh sách/chi tiết đề tài cho SV chỉ có đề tài đã công bố; request đăng ký đề tài chưa công bố bị từ chối ở service.
- Không thay quy tắc một SV/một nhóm giữa nhiều đợt trong 005.

## Chấm điểm và hội đồng

- Các route `/faculty/boards` và `/faculty/boards/{id}` tiếp tục thuộc quyền DEAN cùng Khoa; hội đồng suy ra Bộ môn qua đợt, không nhận `council.departmentId`.
- Thành viên/người chấm khác Bộ môn chỉ dùng route nhiệm vụ hiện hữu để xem đúng đề tài/phần chấm đã phân công hợp lệ. Không vì thế truy cập được `/topics` hoặc danh sách chung của Bộ môn kia.
- Khi phân công/hủy phân công, service xác nhận cùng Khoa, cùng đợt, trạng thái phân công và GVHD không chấm đề tài mình; URL cũ không còn quyền nếu phân công hết hiệu lực.

## ADMIN — Một DEAN hoạt động/Khoa

- `POST /admin/accounts/deans` và `POST /admin/accounts/{accountId}/unlock` từ chối nếu thao tác tạo ra DEAN hoạt động thứ hai trong cùng Khoa.
- Vô hiệu hóa DEAN cũ không xóa account hoặc thay đổi `registration_periods`; kích hoạt người mới quản lý được mọi đợt trong Khoa.
- Kiểm tra phải an toàn dưới request đồng thời; không chỉ hiển thị cảnh báo tại form.

## Response/security conventions

Giữ convention HTTP/lỗi/redirect hiện có của controller; task triển khai phải ghi test chính xác cho `403` hoặc `404` ở từng route, tránh lộ tài nguyên ngoài phạm vi. Controller bind và điều phối; service quyết định quyền, trạng thái, transaction; repository không tự đặt business rule.
