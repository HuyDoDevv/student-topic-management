# Data Model: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

## Mục đích

Mô hình này mô tả trạng thái đích cho T002 và T009–T016. Tên cột/index cuối cùng
phải được xác nhận khi lập plan chi tiết migration; mọi thay đổi dùng migration
mới trên baseline V21, không sửa V1–V21 và không suy diễn dữ liệu lịch sử.

## Baseline liên quan

- `user_accounts`, `roles`, `user_roles`: tài khoản và role; hiện có thể tồn tại
  tổ hợp `DEAN + LECTURER`.
- `registrations` và `registration_members`: một đăng ký đã chấp nhận và snapshot
  sinh viên cần được chấm.
- `reviewer_assignments`: bảng V1 tối giản với `(registration_id, lecturer_id)`;
  chưa có runtime entity/lifecycle/deadline/version.
- `project_reports`: báo cáo do nhóm nộp, không thay thế báo cáo phản biện.
- `councils`, `council_members`, `council_topics`: hội đồng và đề tài được giao.
- `evaluations`: điểm của một evaluator/component theo từng sinh viên, hiện gắn
  với council/direct grading.
- `final_results`: snapshot kết quả theo `(registration_id, student_id)` cùng
  actor/time tổng hợp/công bố.

## UserAccount và role invariant

### Invariant mới

- Tài khoản có role `DEAN` không được đồng thời có `LECTURER` cho dữ liệu mới.
- Mỗi `faculty_id` có tối đa một account đang hoạt động có role `DEAN`.
- V22 loại role `LECTURER` khỏi account có role `DEAN`, không xóa account và
  không thay foreign key lịch sử tới account đó.
- Query ứng viên phải xác nhận cả role cần thiết, `enabled`, scope Khoa và
  `NOT DEAN`; migration không thay thế kiểm tra service.

## ReviewerAssignment

Đại diện một GVPB cho một registration TLCN/KLTN.

| Thuộc tính logic | Kiểu/quan hệ | Ràng buộc |
|---|---|---|
| registration | FK `registrations` | Một assignment hoạt động cho mỗi registration; registration phải `ACCEPTED`. |
| reviewer | FK `user_accounts` | Enabled, role LECTURER, không DEAN, cùng Khoa, không GVHD/đồng hướng dẫn. |
| deadline | ngày nghiệp vụ | Dùng cuối ngày `Asia/Ho_Chi_Minh`; lấy từ/copy nhất quán với kỳ đăng ký. |
| status | enum | `ASSIGNED`, `SUBMITTED`; hủy/thay chỉ khi chưa submitted và phải có audit nếu được bổ sung. |
| assignedBy/assignedAt | FK + instant | Actor phải là DEAN đúng Khoa. |
| submittedAt | instant nullable | Chỉ có khi report và toàn bộ score đã ghi thành công. |
| version | bigint | Optimistic locking, không âm. |

### Invariants

- Chỉ áp dụng cho `TLCN` và `KLTN`.
- Reviewer không trùng người chấm thứ hai của TLCN hoặc thành viên hội đồng KLTN.
- Không đổi reviewer sau submission. Thay trước submission phải kiểm tra lại mọi
  conflict ở thời điểm ghi.
- Không tự tạo assignment cho dữ liệu lịch sử khi migrate.

## ReviewerReport

Metadata báo cáo PDF của GVPB; nội dung file dùng `ReportFileRepository` hiện có.

| Thuộc tính logic | Kiểu/quan hệ | Ràng buộc |
|---|---|---|
| assignment | FK/unique | Tối đa một report hiện hành cho assignment trong scope 006. |
| fileName | varchar | Không rỗng; tên hiển thị an toàn. |
| storageKey | varchar | Không rỗng, không chứa path traversal; do repository cấp. |
| contentType | varchar | Phải là PDF theo kiểm tra upload được duyệt. |
| sizeBytes | bigint | Không âm và trong giới hạn cấu hình hiện có. |
| submittedBy | FK account | Phải là chính reviewer của assignment. |
| submittedAt | instant | Không sau deadline đã quy đổi. |

Không tái sử dụng `project_reports`, vì bảng đó biểu diễn báo cáo của nhóm và actor
là sinh viên; trộn hai loại làm sai ownership và audit.

## ReviewerEvaluation

Điểm phản biện riêng của từng sinh viên snapshot trong registration.

| Thuộc tính logic | Kiểu/quan hệ | Ràng buộc |
|---|---|---|
| assignment | FK reviewer assignment | Bắt buộc. |
| registration/student | FK snapshot member | Student phải thuộc `registration_members` của assignment. |
| score | decimal | Trong miền điểm hiện hành; scale lưu trữ không làm mất dữ liệu nhập. |
| feedback | text nullable/required theo form | Riêng từng sinh viên. |
| status | enum | Submission phản biện chỉ chấp nhận tập đầy đủ; không để mixed status. |
| submittedAt | instant | Cùng transaction với report và assignment transition. |
| version | bigint | Optimistic locking. |

Khóa duy nhất logic là `(assignment, student)`. Không chấp nhận student thừa,
thiếu hoặc trùng trong payload.

## GroupScoreForm và evaluation hiện hữu

`GroupScoreForm` là DTO, không tạo bảng. Form chứa registration/council hoặc direct
assignment identity, version và danh sách row theo student. Service tải lại
snapshot member, so tập ID và ghi các `EvaluationEntity` hiện hữu trong một
transaction. Một row lỗi làm rollback toàn bộ.

## Council và direct grading

### COURSE/NCKH

- Giữ mô hình direct grading hiện hữu với đúng một grader.
- Grader không phải DEAN/reviewer/supervisor của cùng đề tài.

### TLCN

- Không tạo `CouncilEntity` kiểu hội đồng cho nghiệp vụ mới.
- Có `ReviewerAssignment` đã submitted và một direct grader thứ hai.
- Việc tái sử dụng hạ tầng `CouncilEntity` ở mode `DIRECT` chỉ là chi tiết lưu trữ
  và không được làm UI/contract coi TLCN có hội đồng.

### KLTN

- Có một reviewer assignment và một council 3–5 member.
- Reviewer không được có row trong `council_members` của council gắn registration.
- Supervisor/co-supervisor và DEAN không được là member.
- Membership chỉ sửa khi council `DRAFT`/`ASSIGNED` và chưa có evaluation.

## FinalResult projection và snapshot

Mỗi kết quả vẫn theo `(registration_id, student_id)`.

### Nguồn điểm bắt buộc

- COURSE/NCKH: một submitted direct evaluation.
- TLCN: một reviewer evaluation + một submitted evaluation của grader thứ hai.
- KLTN: một reviewer evaluation + một submitted evaluation của từng council member
  có quyền chấm tại tập membership đã khóa.

### Transition

```text
INCOMPLETE (không có row final hoặc nguồn chưa đủ)
    └─ đủ mọi nguồn + DEAN tổng hợp → READY
READY
    └─ DEAN công bố → PUBLISHED
PUBLISHED
    └─ bất biến trong feature 006
```

Khi tạo `READY`, hệ thống ghi score đã làm tròn hai chữ số `HALF_UP`, summary có
đủ actor/role/source và `consolidated_by/at`. `published_by/at` chỉ xuất hiện ở
transition công bố. Không sửa hoặc tái tính row `PUBLISHED`.

## Derived Student Progress

Không thêm bảng/cột status mới nếu projection có thể suy ra:

1. `PENDING` → Chờ xét đăng ký.
2. `ACCEPTED`/`REJECTED` → Đã chấp nhận/Bị từ chối.
3. Accepted, chưa có project report → Chờ nộp báo cáo.
4. Có project report, chưa có assignment cần thiết → Đã nộp báo cáo.
5. Chưa đủ reviewer/direct/council assignment → Chờ phân công phản biện/chấm.
6. Có reviewer assignment chưa submitted → Phản biện đang thực hiện.
7. Assignment đủ nhưng evaluation chưa đủ → Đang chấm điểm.
8. Nguồn điểm đủ hoặc final `READY` → Đã đủ điểm, chờ công bố.
9. Final `PUBLISHED` → Đã công bố kết quả.

Ưu tiên trạng thái terminal/đặc hiệu hơn khi nhiều điều kiện cùng đúng.

## Migration Strategy

### V22 — separate dean from lecturer

1. Kiểm kê account role kép và quan hệ lịch sử.
2. Bỏ mapping `LECTURER` khỏi account có `DEAN` theo cách bảo toàn account/FK.
3. Bổ sung invariant cần thiết nếu thiết kế DB được duyệt; service vẫn kiểm tra.
4. Chứng minh số account, đề tài, supervisor, council, evaluation, result không mất.

### V23 — support reviewer assessments

1. Mở rộng/chuẩn hóa `reviewer_assignments` hiện hữu mà không tự tạo reviewer.
2. Tạo bảng/constraint cho report và evaluation theo student.
3. Thêm index phục vụ reviewer inbox, completeness và result aggregation.
4. Legacy assignment nếu có phải giữ nguyên và được đánh dấu/đọc an toàn; dữ liệu
   không đủ không được tự chuyển thành `SUBMITTED`.
5. Validate V1→V23, V21→V23, rerun Flyway và snapshot business rows.

## Integrity and Concurrency

- Service validate business conflict; FK/unique/check constraint bảo vệ cấu trúc.
- Lock/ordering nhất quán khi tạo DEAN, đổi reviewer/member, submit nhóm và tổng hợp.
- `@Version` cho assignment/evaluation/council/result nơi có cập nhật cạnh tranh.
- Không dựa vào hidden field để xác định faculty, member set, student set hoặc role.
- Không hard-delete assignment/evaluation đã tham gia audit; lifecycle/historical
  read phải được plan riêng nếu cần vô hiệu hóa.
