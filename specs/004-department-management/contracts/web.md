# Web Contract: Quản lý Bộ môn

## ADMIN — Danh mục Bộ môn

| Method | Route | Rule |
|---|---|---|
| GET | `/admin/departments` | ADMIN xem danh sách, tìm theo tên và lọc Faculty |
| GET | `/admin/departments/new` | ADMIN mở form tạo |
| POST | `/admin/departments` | ADMIN tạo tên hợp lệ trong Faculty |
| GET | `/admin/departments/{id}/edit` | ADMIN mở form sửa |
| POST | `/admin/departments/{id}` | ADMIN cập nhật với validation/CSRF |

Không có route xóa trong phiên bản đầu. Duplicate `(faculty_id, name)`, ID không tồn tại,
Faculty không hợp lệ hoặc actor không phải ADMIN phải bị từ chối mà không ghi dở.

## ADMIN — Cấp và cập nhật tài khoản

- Form LECTURER/DEAN có `departmentId`; danh sách chỉ gồm Department thuộc Faculty đã chọn.
- Backend tải lại Department, kiểm tra Faculty và role; không tin option từ client.
- Form STUDENT không nhận `departmentId`; ADMIN account không được tạo từ các form này.

## LECTURER — Đề tài

- Form tạo/sửa Topic không cho chọn tùy ý `departmentId`.
- Backend suy ra Department từ actor và chỉ trả về GVHD phối hợp cùng Department.
- Submit thiếu Department hoặc chọn supervisor sai phạm vi trả lỗi nghiệp vụ, không đổi Topic.

## DEAN — Duyệt và danh sách

- DEAN tiếp tục dùng route Faculty hiện có và xem Topic của mọi Department trong Faculty.
- Bộ lọc Department là tùy chọn trình bày; backend vẫn khóa theo `faculty_id` của DEAN.

## Security Contract

- Mọi POST yêu cầu CSRF.
- Controller chỉ bind/validate request và gọi service.
- Service kiểm tra role, Faculty, Department, ownership và state transition.
- Không trả dữ liệu Department/Topic ngoài Faculty cho DEAN/LECTURER.
- Không route nào nhận hoặc ghi `council.departmentId`.
