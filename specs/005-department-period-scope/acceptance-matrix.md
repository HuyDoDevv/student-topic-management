# Acceptance Matrix: Đợt đăng ký theo Bộ môn

**Status (2026-10-14, Asia/Ho_Chi_Minh):** T014 chưa đạt. T001–T010 và T012–T013 có bằng chứng kiểm thử mục tiêu được ghi trong `tasks.md`; T011 chưa nghiệm thu. Phiên T014 thử `./mvnw -q test` nhưng dừng ở `testCompile` trước khi chạy test. Full integration và E2E không chạy vì cổng đầu chưa đạt. Không có migration/reset nào được chạy trên DB ứng dụng trong phiên T014.

| Requirement | Bằng chứng hiện có | Trạng thái nghiệm thu T014 |
|---|---|---|
| FR-005-01 — Đợt mới thuộc Bộ môn đúng Khoa | T004: `PeriodServiceTest` 5/5, `PeriodTransactionServiceTest` 10/10, `PeriodContractTest` 10/10; T005: `PeriodContractTest` 12/12. T013: `PeriodDepartmentMigrationIT` 11/11, FK kép chặn sai Khoa ở DB test. | Có bằng chứng mục tiêu; chưa chạy bộ nghiệm thu bốn loại COURSE/NCKH/TLCN/KLTN trong T014. |
| FR-005-02 — Không đổi Bộ môn khi đợt đã mở/có dữ liệu | T004–T005 ghi nhận unit/web test cho khóa đổi phạm vi. | Có bằng chứng mục tiêu; full regression/concurrency chưa đạt cổng biên dịch. |
| FR-005-03 — DEAN toàn Khoa và tối đa một DEAN hoạt động | T004–T005 ghi nhận kiểm tra phạm vi DEAN. T011 vẫn `[ ]`, dù code và test liên quan đang sửa trong working tree. | **Blocked:** chưa có nghiệm thu T011/race/chuyển giao DEAN. |
| FR-005-04 — GV không đọc/ghi ngoài Bộ môn | T006: `TopicDepartmentIT` 16/16; T007: 18/18; các task ghi nhận GET/POST ID chéo Bộ môn bị từ chối. | Có bằng chứng mục tiêu; chưa chạy lại full suite trong T014. |
| FR-005-05 — Đề tài/tác giả/1–2 GVHD cùng Bộ môn đợt | T007: `TopicDepartmentIT` 18/18 và test quy tắc đề tài; task ghi nhận từ chối GVHD khác Bộ môn. | Có bằng chứng mục tiêu; chưa nghiệm thu dataset A/B đầy đủ trong T014. |
| FR-005-06 — SV thấy nhiều Bộ môn, chỉ đề tài công bố | T008: `TopicDepartmentIT` 20/20 và `./mvnw -q test` 317/317 tại thời điểm task. | Có bằng chứng mục tiêu; chưa chạy lại full suite/E2E trong T014. |
| FR-005-07 — Hội đồng/đề tài cùng đợt, không có Bộ môn riêng | T009: `CouncilServiceTest` + `DirectGradingServiceTest` 17/17; T013 migration test 11/11. | Có bằng chứng mục tiêu; chưa chạy full integration/E2E trong T014. |
| FR-005-08 — Quyền người chấm theo nhiệm vụ, GVHD không tự chấm | T010: bốn lớp test mục tiêu 42/42, bao gồm service và URL; T009 kiểm tra phân công. | Có bằng chứng mục tiêu; full regression chưa đạt. Quickstart còn kịch bản **hủy phân công**, trong khi quyết định T010 không triển khai thao tác hủy; cần thống nhất kịch bản nghiệm thu riêng, không tự đổi yêu cầu. |
| FR-005-09 — Legacy được duyệt và chuyển đổi không mất dữ liệu | T012: xác nhận đợt ID 1 → `Lập Trình Web`, migration test 8/8; T013: `PeriodDepartmentMigrationIT` 11/11 trên MySQL 8.4 DB test `127.0.0.1:3307`, gồm nâng cấp, validate/rerun và dữ liệu sai dừng trước DDL. | Data gate mục tiêu đạt trên DB test; DB ứng dụng chưa áp V19–V21; full suite chưa đạt. |
| FR-005-10 — Giao diện DEAN/SV hiển thị Bộ môn, backend kiểm quyền | T005: `PeriodContractTest` 12/12; T008: `TopicDepartmentIT` 20/20 tại thời điểm task. | Có bằng chứng web mục tiêu; E2E T014 chưa chạy. |

## Success criteria gate

| Criterion | Bằng chứng/giới hạn hiện tại | T014 |
|---|---|---|
| SC-005-01 | Period service/web và V21 đã được kiểm thử mục tiêu; chưa có kết quả T014 cho đủ bốn loại đợt. | Chưa đạt cổng toàn feature |
| SC-005-02 | T006–T007 có `TopicDepartmentIT` cho quyền GV và URL trực tiếp; full regression bị chặn. | Chưa đạt cổng toàn feature |
| SC-005-03 | T007 có test quy tắc đề tài/GVHD; dataset nghiệm thu đầy đủ chưa chạy trong T014. | Chưa đạt cổng toàn feature |
| SC-005-04 | T008 có test SV thấy hai Bộ môn và từ chối đề tài ẩn; E2E chưa chạy. | Chưa đạt cổng toàn feature |
| SC-005-05 | T009–T010 có test phân công/quyền xem; full integration/E2E và kịch bản hủy phân công chưa được chốt. | Chưa đạt cổng toàn feature |
| SC-005-06 | T012–T013 migration test trên DB test chứng minh bảo toàn business rows và dừng khi dữ liệu mâu thuẫn; 11/11 ở T013. | Đạt kiểm chứng mục tiêu, chưa thay thế full suite |

## T014 acceptance attempt — 2026-10-14

- Môi trường dự kiến: JDK 21, MySQL 8.4, `topic_registration_test` tại `127.0.0.1:3307`. URL test được kiểm tra trước khi chạy; mọi datasource trong tiến trình test được ép về DB test. Không ghi credential vào tài liệu hoặc log.
- `./mvnw -q test`: **exit 1 ở `testCompile`**, chưa chạy test. `PeriodScheduleIT.java:181` gọi `PeriodService.update(long,long,PeriodConfiguration)` trong khi service hiện yêu cầu thêm `Long departmentId`. Đây là test chưa theo kịp chữ ký API hiện tại; T014 không sửa source/test ngoài plan.
- `./mvnw -q verify -Pintegration` và `./mvnw -q verify -Pe2e`: **không chạy**, vì cổng unit/web chưa đạt. Bằng chứng T013 11/11 là từ phiên T013 trước, không được tính là full suite T014.
- T011 vẫn mở; không suy ra hoàn thành từ code chưa nghiệm thu. Feature 004 T010 đã ghi nhận full integration 60 test với 5 failure/11 error ở lần chạy 2026-10-13; chưa có bằng chứng baseline đó đã được xử lý.
- T014 giữ `[ ]`. Sau plan/approval riêng để xử lý lỗi biên dịch và nghiệm thu T011, chạy lại ba suite theo `quickstart.md`, đối chiếu SC-005-01–06 và chỉ tick khi tất cả cổng bắt buộc đạt.
