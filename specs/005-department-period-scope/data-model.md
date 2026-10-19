# Data Model: Đợt đăng ký theo Bộ môn

**Trạng thái**: mô hình đích của feature 005, không phải schema đã triển khai. Tham chiếu [spec.md](spec.md), [research.md](research.md).

## Entities and relationships

### Faculty / `faculties`

Giữ đơn vị bao trùm Bộ môn, DEAN, STUDENT và dữ liệu học vụ. Không thêm Khoa mới hoặc chuyển Khoa của dữ liệu cũ trong 005.

### Department / `departments`

`id`, `faculty_id`, `name` đã có từ 004. Một Bộ môn thuộc đúng một Khoa. Chỉ Bộ môn hợp lệ thuộc Khoa của DEAN mới được gắn vào đợt mới.

### RegistrationPeriod / `registration_periods`

| Field | State/rule |
|---|---|
| `id`, `faculty_id` | Giữ quan hệ và ID hiện có. |
| `department_id` | Quan hệ mới tới `departments.id`; nullable ở giai đoạn expand, bắt buộc sau backfill/enforce. `department.faculty_id == period.faculty_id`. |
| `topic_type` | Giữ COURSE/NCKH/TLCN/KLTN; không dùng làm ID môn học. |
| Tên, lịch, `version` | Giữ validation thời gian và optimistic concurrency hiện có. |

Bộ môn không đổi sau khi mở cho GV hoặc khi đã có đề tài/nhóm/đăng ký/hội đồng/kết quả phụ thuộc. Thay đổi trước mốc đó vẫn phải hợp lệ cùng Khoa và không gây sai phạm vi.

### Topic / `topics` và TopicSupervisor / `topic_supervisors`

`topic.period_id` và `topic.department_id` đã có. Với đề tài mới, `topic.department_id == topic.period.department_id == author.department_id`; mỗi đề tài có 1–2 GVHD đang hợp lệ cùng Bộ môn. Không sao chép Bộ môn xuống `topic_supervisors`; suy ra từ account. Dữ liệu legacy sai lệch phải được xử lý tại data gate, không tự chuyển tác giả/GVHD.

### UserAccount / `user_accounts` và role

LECTURER có Bộ môn trong Khoa. DEAN có thể có Bộ môn công tác nhưng quyền quản lý dựa trên Khoa. STUDENT không bị gán Bộ môn để giới hạn đợt. Tối đa một account có role DEAN và `enabled=true` trong mỗi Khoa; account DEAN cũ có thể được vô hiệu hóa thay vì xóa.

### Council, CouncilMember và nhiệm vụ chấm

`Council` thuộc một `RegistrationPeriod`; Bộ môn được suy ra qua `council.period.department`, không thêm cột trên hội đồng. Đề tài/đăng ký được gán phải cùng đợt với hội đồng. Thành viên/người chấm có thể thuộc Bộ môn khác trong cùng Khoa; bản ghi phân công hợp lệ là nguồn quyền xem đúng nhiệm vụ, không phải toàn danh sách. GVHD không được chấm đề tài của mình.

## Relationship view

```text
Faculty 1 ── * Department 1 ── * RegistrationPeriod
   │                            │
   ├── * UserAccount            ├── * Topic ── * TopicSupervisor
   └── * Student                └── * Council ── * CouncilMember
```

`RegistrationPeriod.faculty_id` vẫn tồn tại; bất biến cùng Khoa với Department tránh hai nguồn phạm vi mâu thuẫn.

## Migration states

| State | Period Department | Runtime and gate |
|---|---|---|
| V18 baseline | Chưa có cột | Đợt/đề tài/hội đồng vẫn Khoa; đề tài đã có Bộ môn. |
| Expand | Nullable | Đọc được legacy; chưa ép dữ liệu cũ. |
| Adopt | Đợt mới bắt buộc, cũ có thể NULL | Service kiểm tra mọi luồng mới; không tự suy đoán legacy. |
| Confirmed backfill | Đợt cũ được ánh xạ có xác nhận | Kiểm chứng cùng Khoa, đề tài/tác giả/GVHD và số lượng bản ghi trước/sau. |
| Enforce | NOT NULL/constraint phù hợp | Chỉ chạy khi không còn NULL/mâu thuẫn; Flyway validate và nâng cấp lặp an toàn. |

## Invariants

1. `period.department.faculty_id == period.faculty_id`.
2. `topic.department_id == period.department_id`; tác giả và 1–2 GVHD cùng Bộ môn đó.
3. DEAN quản lý theo `faculty_id`, không theo Bộ môn công tác.
4. LECTURER thường chỉ truy cập đợt/đề tài cùng Bộ môn; ngoại lệ phân công không lan sang list.
5. STUDENT trong Khoa thấy nhiều Bộ môn nhưng chỉ đề tài đã công bố.
6. Council/đề tài được gán cùng period; người chấm không là GVHD của đề tài được chấm.
7. Tối đa một DEAN hoạt động/Khoa, kể cả dưới thao tác đồng thời.
