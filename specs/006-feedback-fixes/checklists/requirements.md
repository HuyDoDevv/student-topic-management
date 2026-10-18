# Specification Quality Checklist: Feature 006

**Purpose**: kiểm tra tài liệu nền trước khi lập plan chi tiết và triển khai từng task.

**Created**: 2026-10-14

**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Mục tiêu, phạm vi, ngoài phạm vi và quy tắc bất biến đã được nêu.
- [x] Behavior hiện tại được đối chiếu với code/schema baseline V21.
- [x] Requirement nghiệp vụ tách khỏi chi tiết triển khai trong `spec.md`.
- [x] Quyết định kỹ thuật và alternative được ghi riêng trong `research.md`.
- [x] Protected areas, migration, route, authorization và formula được nêu trong plan.
- [x] Tài liệu không tuyên bố test/implementation đã đạt khi chưa có evidence.

## Requirement Completeness

- [x] Không còn marker `[NEEDS CLARIFICATION]` trong đặc tả.
- [x] Đã chốt định nghĩa một DEAN hoạt động/Khoa và chuyển giao DEAN.
- [x] Đã chốt DEAN không kiêm LECTURER và không tham gia hướng dẫn/chấm.
- [x] Đã chốt ma trận COURSE, NCKH, TLCN, KLTN và công thức từng loại.
- [x] Đã chốt deadline cuối ngày theo `Asia/Ho_Chi_Minh`.
- [x] Đã chốt `HALF_UP` hai chữ số và không tính lại kết quả `PUBLISHED`.
- [x] Đã nêu transaction, optimistic locking, direct URL và forged POST.
- [x] Đã nêu migration V21→V23, history preservation và không sửa V1–V21.
- [x] Đã nêu nhóm 1–3 sinh viên, nhiều đợt/đề tài và scope Khoa.

## Coverage of 18 Feedback Items

- [x] FB-01 DEAN xuất hiện trong phần đề xuất đề tài → T002.
- [x] FB-02 Bộ lọc Bộ môn thừa → T003.
- [x] FB-03 DEAN xuất hiện trong phân công chấm → T002.
- [x] FB-04 Link thông báo lỗi → T004.
- [x] FB-05 Chấm từng sinh viên gây thao tác lặp → T008.
- [x] FB-06 Danh sách kết quả rối khi nhiều đề tài → T017.
- [x] FB-07 Topic approved chưa published không sửa được → T005.
- [x] FB-08 Thiếu tiến trình sinh viên → T006.
- [x] FB-09 Khó phân biệt vai trò nhóm qua nhiều đợt → T007.
- [x] FB-10 Có thể chọn DEAN làm người chấm rồi lỗi → T002.
- [x] FB-11 TLCN bị đưa vào hội đồng → T011.
- [x] FB-12 UI hội đồng khó sử dụng → T013.
- [x] FB-13 Không sửa được giảng viên đã chọn → T013.
- [x] FB-14 Chưa loại GVHD khỏi người chấm đề tài mình → T010/T012/T013.
- [x] FB-15 Chọn được lịch bảo vệ quá khứ → T014.
- [x] FB-16 Công bố thiếu căn cứ xác thực → T016.
- [x] FB-17 Chủ tịch không thấy tiến độ thành viên → T015.
- [x] FB-18 Thiếu workflow GVPB và nguồn điểm TLCN/KLTN → T009–T012.

## Acceptance Quality

- [x] Mỗi feedback có task và tiêu chí nghiệm thu quan sát được.
- [x] User story có independent test, gồm cả luồng thành công và từ chối.
- [x] Success criteria đo được và không đánh đồng tài liệu với evidence.
- [x] Web contract nêu actor, precondition, success, failure và tampering.
- [x] Data model nêu entity, quan hệ, uniqueness, lifecycle và migration strategy.
- [x] Quickstart có data set, command, migration, deadline, formula và role tests.
- [x] Task dependency đặt authorization trước assignment và reviewer trước formula.

## Safety and Scope

- [x] T001 chỉ tạo tám file tài liệu, branch và commit riêng được duyệt.
- [x] T002–T018 được đánh dấu chưa triển khai và cần approval riêng.
- [x] Không có yêu cầu push/merge/rebase/stash/reset/clean.
- [x] Không có dependency, build, environment hoặc deployment config change.
- [x] Migration test chỉ dùng DB test đã xác minh; không chạy trên DB ứng dụng.
- [x] Không backfill reviewer lịch sử bằng suy đoán và không xóa audit data.

## Notes

- `reviewer_assignments` đã có từ V1; câu “mở rộng schema phản biện” trong T009
  được hiểu là mở rộng bảng hiện hữu và bổ sung model còn thiếu, không tạo bảng
  trùng hoặc sửa migration V1.
- URL đề xuất trong `contracts/web.md` là contract hành vi. Nếu plan chi tiết cần
  giữ route convention hiện tại, semantics/authorization/acceptance không được đổi.
- `acceptance-matrix.md` chỉ được tạo ở T018 sau khi có implementation và evidence
  thật; T001 không tạo sớm để tránh nhầm tài liệu với kết quả nghiệm thu.
