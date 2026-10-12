# Data Model: Quản lý Bộ môn

## Entities

### Department / `departments`

| Field | Type | Rule |
|---|---|---|
| `id` | BIGINT PK | Auto increment |
| `name` | VARCHAR(255) | Trim không rỗng |
| `faculty_id` | BIGINT FK | Bắt buộc, tham chiếu `faculties.id` |

Unique: `(faculty_id, name)`. Department đang được tham chiếu không được xóa.

### UserAccount / `user_accounts`

Thêm quan hệ nullable `department_id` trong giai đoạn chuyển đổi.

- LECTURER/DEAN: phải có Department trước khi dùng luồng đề tài mới.
- STUDENT: chỉ dùng Faculty, Department để NULL.
- ADMIN: Faculty và Department có thể NULL.
- Department của tài khoản phải thuộc cùng Faculty.

### Topic / `topics`

Thêm quan hệ `department_id`. V16 để nullable nhằm bảo toàn Topic cũ; Topic mới do runtime
tạo phải có Department. Sau backfill, migration enforce mới chuyển cột sang NOT NULL.

### TopicSupervisor / `topic_supervisors`

Không thêm cột Department; suy ra qua `lecturer.department_id`. Khi phân công, mọi supervisor
phải cùng Department với Topic. Slot 1 tiếp tục là tác giả.

### Council / `councils`

Không có `department_id`. Council kế thừa Faculty từ RegistrationPeriod.

## Relationships

```text
Faculty 1 ── * Department
Faculty 1 ── * RegistrationPeriod
Department 1 ── * UserAccount (LECTURER/DEAN)
Department 1 ── * Topic
Topic 1 ── * TopicSupervisor ── 1 UserAccount
RegistrationPeriod 1 ── * Council
```

## Migration States

| State | Accounts | Topics | Runtime |
|---|---|---|---|
| V15 | Không có Department | Không có Department | Faculty-only |
| V16 expand | Nullable Department | Nullable Department | Chưa đổi behavior |
| Adopt | Account mới được gán | Topic mới bắt buộc | Dữ liệu cũ có thể NULL |
| Backfill | Đã ánh xạ | Đã ánh xạ | Không còn bản ghi cần thiết NULL |
| Enforce | Rule theo loại account | NOT NULL | Toàn bộ rule Department hoạt động |

## Invariants

1. `department.faculty_id == account.faculty_id` với LECTURER/DEAN.
2. `topic.department_id == topic.author.department_id` khi tạo/gửi duyệt.
3. Mọi supervisor của Topic có cùng `department_id` với Topic.
4. DEAN thao tác trên Topic khi `topic.department.faculty_id == dean.faculty_id`.
5. Council không phụ thuộc Department.
