# Implementation Plan: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

**Branch**: `feature/006-feedback-fixes` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

## Summary

Feature 006 tách hoàn toàn DEAN khỏi vai trò giảng viên, sửa các luồng/UI trong
18 phản hồi và bổ sung mô hình phản biện để hoàn thiện cách chấm COURSE, NCKH,
TLCN và KLTN. Triển khai theo thứ tự quyền và dữ liệu nền trước, sau đó workflow
phản biện/chấm, cuối cùng tiến độ và kết quả. Không sửa V1–V21, không tính lại
kết quả đã công bố và không chạy migration trên DB ứng dụng.

## Behavior hiện tại

- `AdminAccountService` tạo DEAN với cả `DEAN` và `LECTURER`; các query chọn
  người chấm/hội đồng dựa trên role giảng viên nên DEAN vẫn xuất hiện.
- `DirectGradingRules` chỉ hỗ trợ `COURSE` và `NCKH`; `CouncilRules` lại hỗ trợ
  cả `TLCN` và `KLTN`.
- Schema V1 có bảng `reviewer_assignments` tối giản nhưng runtime chưa có entity,
  lifecycle, deadline, báo cáo phản biện và điểm phản biện theo từng sinh viên.
- `evaluations` và `final_results` hiện dựa trên hạ tầng council/direct grading;
  chưa biểu diễn công thức riêng TLCN/KLTN có đầu điểm GVPB.
- Topic chỉ sửa ở `DRAFT`; form chấm tách theo từng sinh viên; danh sách kết quả
  phẳng; tiến trình sinh viên và tiến độ hội đồng chưa đủ chi tiết.
- Một số notification target trỏ tới route quản lý của DEAN dù người nhận là
  giảng viên; lịch bảo vệ chưa chặn thời điểm quá khứ tại service.

## Lý do cần thay đổi

Các vấn đề không chỉ là hiển thị: role kép làm sai authorization và danh sách ứng
viên; mô hình dữ liệu thiếu khiến không thể chứng minh đủ đầu điểm; route thông
báo và trạng thái UI gây lỗi thao tác; việc lưu điểm từng sinh viên làm tăng nguy
cơ nhóm được lưu một phần. Do đó phải xử lý cả invariant backend, migration,
transaction, authorization và UI, không chỉ ẩn control trên template.

## Technical Context

- Java 21, Spring Boot MVC, Spring Data JPA, Thymeleaf, Spring Security.
- MySQL 8.4 LTS; Flyway là source of truth; baseline là V21.
- Kiến trúc bắt buộc: `Controller → Service → Repository → Database`.
- Thời gian lưu dạng `Instant`; deadline nghiệp vụ được diễn giải theo
  `Asia/Ho_Chi_Minh` qua clock/config tin cậy của server.
- Test: JUnit 5, Mockito, MockMvc; integration/E2E dùng DB test riêng cổng 3307.

## Thiết kế và thứ tự triển khai

### T001 — Tài liệu nền

Tạo tám artifact trong thư mục 006, ánh xạ đủ FB-01–FB-18 với task và acceptance.
Task này chỉ thay đổi tài liệu và Git branch/commit được duyệt.

### T002 — Tách DEAN khỏi LECTURER

**File dự kiến**: `AdminAccountService.java`, `AuthorizationPolicy.java`,
`UserAccountRepository.java`, `NavigationModelAdvice.java`,
`DirectGradingService.java`, `CouncilService.java`, migration
`V22__separate_dean_from_lecturer.sql` và test tương ứng.

Tài khoản DEAN mới chỉ có `DEAN`; migration bỏ `LECTURER` khỏi tài khoản có
`DEAN` nhưng giữ tài khoản và lịch sử. Repository lẫn service loại DEAN khỏi ứng
viên. Bổ sung kiểm thử một DEAN hoạt động/Khoa và race create/unlock.

### T003–T008 — Sửa các luồng độc lập

| Task | File chính | Thay đổi |
|---|---|---|
| T003 | `TopicReviewService`, `TopicRepository`, `TopicModerationController`, `templates/topics/review.html`, test | Duyệt đúng Bộ môn của đợt; bỏ bộ lọc thừa; chặn tham số giả mạo. |
| T004 | `NotificationTargetResolver`, `NotificationService`, nơi phát event, test inbox/E2E | Resolve target theo quyền người nhận; fallback inbox khi nhiệm vụ hết hiệu lực. |
| T005 | `TopicRules`, `TopicReviewRules`, author/review service/controller/template/test | Cho `APPROVED → DRAFT` trong điều kiện an toàn và bắt buộc duyệt lại. |
| T006 | registration/report/result service, registration controller, detail template/test | Dẫn xuất chín bước tiến trình sinh viên. |
| T007 | group service/controller/templates/test | Hiển thị đợt, Bộ môn, nhóm, đề tài, vai trò và trạng thái từng thẻ. |
| T008 | `GroupScoreForm`, scoring controller/service/templates/test | Chấm cả nhóm trong một transaction, validate toàn payload và version. |

### T009–T010 — Nền và workflow phản biện

Migration `V23__support_reviewer_assessments.sql` mở rộng bảng phân công phản
biện hiện hữu và tạo cấu trúc báo cáo/điểm theo từng sinh viên. Runtime bổ sung
entity/repository, service/controller/form/template. Tái sử dụng
`ReportFileRepository` để lưu PDF; service bắt buộc cùng Khoa, đang hoạt động,
không phải DEAN/GVHD và không xung đột vai trò khác. Một submission phải gồm
PDF cùng điểm đủ mọi sinh viên và đến không muộn hơn cuối ngày deadline.

### T011 — Hoàn thiện TLCN

Loại TLCN khỏi hội đồng. Chỉ cho phân công người chấm thứ hai sau phản biện đủ
và đúng hạn. `ResultService` tổng hợp đúng hai đầu điểm theo từng sinh viên;
thiếu GVPB hoặc người chấm thì chưa sẵn sàng/công bố.

### T012–T014 — Hoàn thiện KLTN và vòng đời hội đồng

- Hội đồng chỉ nhận KLTN, có 3–5 thành viên có quyền chấm và một GVPB độc lập.
- Sửa thành viên khi `DRAFT`/`ASSIGNED` chưa có điểm; thay GVPB khi chưa nộp.
  Mọi lần sửa kiểm tra lại GVHD, GVPB, DEAN và xung đột đề tài.
- Lịch bảo vệ phải ở tương lai theo clock server; form hiển thị lỗi tại chỗ và
  optimistic locking vẫn áp dụng khi sửa lịch.

### T015–T017 — Tiến độ, căn cứ và danh sách kết quả

- Chủ tịch thấy trạng thái từng người trong hội đồng mình; DEAN thấy phạm vi Khoa.
- Chi tiết trước công bố thể hiện nguồn điểm, vai trò, submission, công thức,
  trung bình và audit tổng hợp/công bố; sinh viên chỉ thấy sau `PUBLISHED`.
- Danh sách kết quả hỗ trợ filter, sort, pagination, group theo đề tài/nhóm và
  liên kết chi tiết căn cứ.

### T018 — Nghiệm thu

Tạo `acceptance-matrix.md` sau implementation, điền evidence cho 18 phản hồi,
ma trận role và bốn loại đợt; chạy unit/web/integration/E2E, migration V21→V23,
MySQL 8.4 DB test và `git diff --check`.

## Project Structure

```text
specs/006-feedback-fixes/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/web.md
├── quickstart.md
├── tasks.md
└── checklists/requirements.md
```

Các vùng code dự kiến cho T002–T017: `src/main/java/.../{security,entity,dto,
repository,service,controller}/`, `src/main/resources/{db/migration,templates}/`
và test unit/web/integration/E2E tương ứng. T001 không cấp quyền sửa các vùng này.

## Dependency Graph

```text
T001
 ├─ T002
 ├─ T003
 ├─ T004
 ├─ T005
 ├─ T006
 ├─ T007
 └─ T008
       ↓
      T009 → T010
               ├─ T011 TLCN
               └─ T012 KLTN → T013 → T014
                                 ↓
                              T015 → T016 → T017
                                             ↓
                                            T018
```

T002 là nền quyền cho mọi danh sách ứng viên. T009–T010 là nền dữ liệu và
workflow bắt buộc trước công thức TLCN/KLTN. T015–T017 chỉ hoàn thiện sau khi
nguồn điểm và lifecycle đã ổn định.

## Protected Areas đã có trong plan

- Authentication/authorization và tổ hợp role tại T002.
- Database schema/Flyway tại V22 và V23; không sửa migration cũ.
- Route/controller phản biện và thay đổi contract web nêu trong
  [contracts/web.md](contracts/web.md).
- Shared grading/result contract và công thức TLCN/KLTN tại T011–T016.
- Không có thay đổi dependency, environment, build hoặc deployment config.

## Rủi ro và kiểm soát

| Rủi ro | Kiểm soát |
|---|---|
| Bỏ role LECTURER làm mất khả năng đọc lịch sử DEAN cũ | Phân biệt quyền tạo nhiệm vụ mới với quyền đọc record lịch sử; test dữ liệu trước/sau V22. |
| Race tạo/kích hoạt hai DEAN | Dùng transaction/lock hoặc invariant DB phù hợp được duyệt; có integration test hai request đồng thời. |
| V23 xung đột bảng `reviewer_assignments` V1 | Migration mở rộng có chủ đích, test V1→V23 và V21→V23; không tạo trùng/đổi migration cũ. |
| Submission phản biện hoặc chấm nhóm lưu một phần | Validate toàn payload trước ghi và transaction; rollback khi một dòng lỗi/version cũ. |
| Deadline lệch timezone | Dùng clock server và `Asia/Ho_Chi_Minh`; test đúng cuối ngày và ngay sau hạn. |
| Kết quả lịch sử bị đổi | Bỏ qua `PUBLISHED`; so sánh snapshot business rows trước/sau migration. |
| Lộ điểm nháp | DTO/view theo quyền và trạng thái; test Chủ tịch/DEAN/GV/SV/Khoa khác. |
| Scope lớn hơn năm file ở các task | Danh sách task là scope cấp cao đã duyệt; mỗi task vẫn cần plan chi tiết và approval riêng trước implementation. |

## Expected Behavior sau feature

- DEAN quản trị nhưng không còn bất kỳ capability giảng viên nào.
- COURSE/NCKH dùng một người chấm; TLCN dùng GVPB + người chấm thứ hai; KLTN
  dùng GVPB + toàn bộ thành viên hội đồng có quyền chấm.
- Mọi role conflict được chặn cả ở query, service và request giả mạo.
- Người dùng thao tác nhóm, đề tài, thông báo, hội đồng và kết quả bằng giao diện
  thể hiện đủ ngữ cảnh; công bố có căn cứ và audit rõ ràng.
- Dữ liệu lịch sử và kết quả đã công bố được giữ nguyên.

## Verification Strategy

1. Mỗi task chạy unit/web test mục tiêu và `git diff --check`.
2. T002, T009 và T018 chạy migration/integration trên MySQL 8.4 DB test cổng
   3307 sau khi xác minh biến `TEST_DATABASE_*` không trỏ dev/shared.
3. Test ma trận role `DEAN/LECTURER/REVIEWER/SUPERVISOR/STUDENT`, GET URL trực
   tiếp, POST giả mạo, scope Khoa, role conflict và account disabled.
4. Test nhóm 1–3 sinh viên, lưu nháp/nộp đủ/thiếu, version cũ và hai request đồng thời.
5. Test `COURSE/NCKH/TLCN/KLTN`, deadline Việt Nam, công thức và `HALF_UP`.
6. Test kết quả lịch sử `PUBLISHED` không đổi và migration V21→V23 validate/rerun.
7. Cuối feature chạy `./mvnw test`, integration, E2E và điền evidence thật vào
   `acceptance-matrix.md`; không coi tài liệu quickstart là bằng chứng đã chạy.

## Stop Gates

Dừng và xin plan bổ sung nếu cần đổi architecture, dependency/config, public API
ngoài contract đã nêu, sửa hơn phạm vi từng task, sửa V1–V21, backfill GVPB lịch
sử bằng suy đoán, tính lại `PUBLISHED`, chạy destructive operation hoặc không
thể xác minh DB test an toàn.
