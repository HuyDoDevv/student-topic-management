# Tasks: Đợt đăng ký theo Bộ môn

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/web.md](contracts/web.md), [quickstart.md](quickstart.md), [acceptance-matrix.md](acceptance-matrix.md).

**Status rule**: T001–T005 đã kiểm chứng; các task còn lại hiện `[ ]`. Mỗi task cần INSPECT→PLAN→APPROVE→IMPLEMENT→VERIFY→REPORT riêng theo `AGENTS.md`; checklist và tài liệu này không cấp quyền sửa code/DB. Viết test cho behavior quan trọng và chỉ tick sau khi evidence đạt.

## Phase 1: Setup and data inventory

**Goal**: xác định baseline V18 và dữ liệu legacy trước migration; không suy đoán ánh xạ.

- [X] T001 Kiểm kê đợt cũ, Bộ môn của đề tài/tác giả/GVHD và quan hệ nhóm/hội đồng/kết quả; ghi danh sách ánh xạ chờ chủ dự án xác nhận tại `specs/005-department-period-scope/legacy-period-mapping.md` và test inventory tại `src/test/java/vn/edu/topicregistration/integration/PeriodDepartmentMigrationIT.java`. Đã kiểm kê DB ứng dụng V18 bằng truy vấn read-only: 1 đợt chưa có đề tài, không đủ chứng cứ gán Bộ môn; integration test T001 đạt 1/1 trên DB test riêng, `PeriodRulesTest` đạt 8/8. Quyết định ánh xạ đợt ID 1 vẫn mở, không cho phép backfill T012.

**Checkpoint**: đợt trộn Bộ môn hoặc không xác định được ánh xạ bị đánh dấu STOP, không có DB dev/shared bị sửa.

## Phase 2: Foundational — expand and persistence

**Goal**: có quan hệ Bộ môn nullable để nâng cấp an toàn từ V18.

- [X] T002 Tạo migration expand-only mới cho `registration_periods.department_id` cùng FK/index cần thiết tại `src/main/resources/db/migration/V19__add_period_department.sql`; chứng minh nâng cấp giữ business rows trong `src/test/java/vn/edu/topicregistration/integration/PeriodDepartmentMigrationIT.java`. Đã verify V18→V19 trên MySQL 8.4 DB test 3307: 16 migration/inventory test và 8 `PeriodRulesTest` đều đạt; legacy period giữ `department_id = NULL`, FK từ chối ID không tồn tại, Flyway validate/rerun đạt. DB ứng dụng vẫn V18; chưa backfill hay enforce.
- [X] T003 Ánh xạ Department nullable và truy vấn có phạm vi trong `src/main/java/vn/edu/topicregistration/entity/RegistrationPeriodEntity.java`, `src/main/java/vn/edu/topicregistration/repository/RegistrationPeriodRepository.java`; kiểm chứng legacy/new row tại `src/test/java/vn/edu/topicregistration/integration/RegistrationPeriodPersistenceIT.java`. Đã verify trên MySQL 8.4 DB test 3307: `RegistrationPeriodPersistenceIT` đạt 5/5, `PeriodRulesTest` đạt 8/8, Flyway validate V1–V19 và `git diff --check` đạt. Legacy `department_id = NULL` vẫn đọc được; truy vấn Khoa+Bộ môn không trả đợt khác phạm vi. Chưa đổi service/UI/quyền và chưa gán Bộ môn cho đợt legacy ID 1.

**Checkpoint**: V18→V19 và Flyway validate đạt trên DB test; legacy period vẫn đọc được, runtime chưa tự gán.

## Phase 3: US1 — DEAN tạo/quản lý đợt theo Bộ môn (P1)

**Independent test**: DEAN Khoa X tạo đợt A/B, xem cả hai; C ở Khoa Y bị từ chối; đợt đã mở/có dữ liệu không đổi Bộ môn.

- [X] T004 [US1] Bổ sung `departmentId` trên `src/main/java/vn/edu/topicregistration/dto/PeriodForm.java`, kiểm tra cùng Khoa và khóa đổi phạm vi trong `src/main/java/vn/edu/topicregistration/service/PeriodService.java` và `src/main/java/vn/edu/topicregistration/service/PeriodTransactionService.java`; viết test tại `src/test/java/vn/edu/topicregistration/unit/PeriodServiceTest.java`. Phạm vi được duyệt mở rộng thêm entity/repository, controller/form tối thiểu và unit/web test để POST tạo đợt vẫn hoạt động trước T005. Đã verify `./mvnw -q test` trên MySQL 8.4 DB test 3307: 309 test/53 report, 0 lỗi; riêng `PeriodServiceTest` 5/5, `PeriodTransactionServiceTest` 10/10, `PeriodContractTest` 10/10; `git diff --check` đạt. Đợt legacy NULL không được tự gán qua form; DB ứng dụng chưa được migrate.
- [X] T005 [US1] Hoàn thiện hiển thị Bộ môn trên `src/main/java/vn/edu/topicregistration/controller/PeriodController.java`, `src/main/resources/templates/periods/list.html`, `src/main/resources/templates/periods/detail.html`; kiểm tra danh sách/chi tiết, quyền và URL tại `src/test/java/vn/edu/topicregistration/web/PeriodContractTest.java`. Ô chọn Bộ môn và POST form tối thiểu đã thuộc T004, không làm lại. Đã verify trên DB test MySQL 8.4 cổng 3307: `PeriodContractTest` đạt 12/12, `./mvnw -q test` đạt 311 test/53 report, không lỗi; `git diff --check` đạt. DEAN thấy đợt của hai Bộ môn cùng Khoa, không xem được chi tiết/edit đợt Khoa khác; legacy NULL hiển thị “Chưa xác định Bộ môn”. Chưa kiểm thử integration/E2E và DB ứng dụng chưa migrate V19.

## Phase 4: US2 — LECTURER chỉ ở Bộ môn mình (P1)

**Independent test**: GV A chỉ thấy đợt A; ID đợt B qua GET/POST bị chặn; đề tài/tác giả/1–2 GVHD cùng A.

- [X] T006 [US2] Giới hạn truy vấn danh sách/chi tiết đợt cho GV theo Bộ môn trong `src/main/java/vn/edu/topicregistration/repository/RegistrationPeriodRepository.java` và `src/main/java/vn/edu/topicregistration/service/TopicAuthorService.java`; test từ chối URL tại `src/test/java/vn/edu/topicregistration/web/TopicDepartmentIT.java`. Tái sử dụng query Khoa+Bộ môn có sẵn từ T003; GV chỉ thấy đợt Bộ môn mình, URL đợt khác/legacy NULL trả 404 trước khi ghi đề tài, và quyền DEAN toàn Khoa không đổi. Đã verify `TopicAuthorServiceTest` 13/13, `TopicDepartmentIT` 16/16, `AuthorizationMatrixTest` 5/5; toàn bộ `./mvnw -q test` đạt 330 test/54 report, 0 lỗi trên MySQL 8.4 DB test riêng cổng 3307 với tài khoản có quyền đã xác minh; `git diff --check` đạt. DB ứng dụng không được sửa/migrate; kiểm tra invariant đầy đủ đề tài/tác giả/GVHD thuộc T007.
- [X] T007 [US2] Ràng buộc Bộ môn đợt=đề tài=tác giả/GVHD tại tạo/sửa/gửi duyệt trong `src/main/java/vn/edu/topicregistration/service/TopicAuthorService.java`, `src/main/java/vn/edu/topicregistration/service/TopicRules.java`; test giả mạo ID/đổi Bộ môn đồng thời tại `src/test/java/vn/edu/topicregistration/web/TopicDepartmentIT.java`. Service khóa và đọc lại tài khoản tác giả/GVHD theo thứ tự ID trước khi lưu hoặc gửi duyệt; topic lệch Bộ môn đợt hoặc GVHD khác Bộ môn bị từ chối, bản nháp giữ nguyên. Đã verify `./mvnw -q test` đạt 317 test (53 report) và chạy riêng `TopicDepartmentIT` đạt 18/18, đều 0 lỗi trên MySQL 8.4 DB test riêng cổng 3307; `git diff --check` đạt. DB ứng dụng không được sửa/migrate; không chạy E2E hoặc toàn bộ profile integration.

## Phase 5: US3 — STUDENT không bị khóa Bộ môn (P1)

**Independent test**: SV cùng Khoa thấy đợt A/B nhưng chỉ đề tài công bố; đăng ký trực tiếp đề tài ẩn bị chặn.

- [X] T008 [US3] Giữ query SV theo Khoa và trạng thái công bố trong `src/main/java/vn/edu/topicregistration/service/PublishedTopicQueryService.java`, `src/main/java/vn/edu/topicregistration/service/RegistrationService.java`; hiển thị Bộ môn tại màn hình SV thực tế `/groups` qua `src/main/resources/templates/fragments/group-period-cards.html` và test tại `src/test/java/vn/edu/topicregistration/web/TopicDepartmentIT.java`. Đã verify trên MySQL 8.4 DB test riêng cổng 3307: `TopicDepartmentIT` 20/20, `./mvnw -q test` 317/317 (53 report), `git diff --check` đạt. SV thấy đợt hai Bộ môn trong cùng Khoa và tên Bộ môn/legacy, không thấy đợt Khoa khác; form chỉ có đề tài `PUBLISHED` cùng đợt, POST trực tiếp từ chối đề tài ẩn/khác đợt và chấp nhận đề tài hợp lệ. Hai service lọc/trạng thái đã đúng nên không sửa; DB ứng dụng không được sửa/migrate.

## Phase 6: US4 — Phân công chấm/hội đồng chéo Bộ môn có giới hạn (P2)

**Independent test**: GV B được giao đúng một đề tài A chỉ xem nhiệm vụ đó; không xem list A; GVHD không chấm đề tài mình.

- [X] T009 [US4] Kiểm tra hội đồng/đề tài cùng đợt, thành viên cùng Khoa và xung đột GVHD khi phân công trong `src/main/java/vn/edu/topicregistration/service/CouncilService.java`, `src/main/java/vn/edu/topicregistration/service/DirectGradingService.java`; test tại `src/test/java/vn/edu/topicregistration/unit/CouncilServiceTest.java` và `src/test/java/vn/edu/topicregistration/unit/DirectGradingServiceTest.java`. Đã verify: hai lớp unit test 17/17, `./mvnw -q test` 320/320 (53 report) trên DB test MySQL 8.4 cổng 3307, `git diff --check` đạt. Gán/kích hoạt hội đồng và chấm trực tiếp từ chối đề tài khác đợt dù cùng Khoa; không tạo phân công khi bị từ chối. Quy tắc thành viên/người chấm cùng Khoa, khác Bộ môn được phép và GVHD không tự chấm vẫn giữ nguyên; quyền xem URL nhiệm vụ thuộc T010. DB ứng dụng không được sửa/migrate.
- [X] T010 [US4] Giới hạn quyền xem/chấm đúng nhiệm vụ còn hiệu lực trong `src/main/java/vn/edu/topicregistration/service/CouncilService.java` và `src/main/java/vn/edu/topicregistration/service/ScoringService.java`; sau khi Chủ tịch tổng hợp, `src/main/java/vn/edu/topicregistration/controller/ResultController.java` chuyển về `/lecturer/scoring` thay vì URL hội đồng đã hết quyền. Test service và URL tại `CouncilServiceTest`, `ScoringServiceTest`, `CouncilControllerTest`, `ResultControllerTest`. Đã verify: bốn lớp test mục tiêu 42/42, `./mvnw -q test` 329/329 (53 report) trên DB test MySQL 8.4 cổng 3307, `git diff --check` đạt. DEAN cùng Khoa giữ quyền xem hội đồng; giảng viên chỉ xem/chấm nhiệm vụ ACTIVE khi còn thuộc Khoa, còn thành viên và quan hệ hội đồng–đăng ký–đề tài cùng đợt. URL cũ bị từ chối khi mất thành viên/phân công hoặc hội đồng hết ACTIVE; không ghi điểm khi bị từ chối. Theo quyết định của chủ dự án, T010 không thêm thao tác/trạng thái hủy phân công; `DirectGradingService` không phải luồng đọc/chấm của giảng viên nên không sửa. DB ứng dụng không được sửa/migrate.

## Phase 7: US5 — Tối đa một DEAN hoạt động/Khoa (P2)

**Independent test**: tạo/kích hoạt DEAN thứ hai bị từ chối kể cả concurrent; thay DEAN không làm đổi đợt cũ.

- [ ] T011 [US5] Chặn tạo/kích hoạt DEAN hoạt động thứ hai cùng Khoa bằng cơ chế nhất quán được duyệt trong `src/main/java/vn/edu/topicregistration/service/AdminAccountService.java`, `src/main/java/vn/edu/topicregistration/repository/UserAccountRepository.java`; test race và chuyển giao tại `src/test/java/vn/edu/topicregistration/integration/AccountProvisioningIT.java`.

## Phase 8: Backfill, enforce and acceptance

**Goal**: chỉ siết schema sau ánh xạ được xác nhận; nghiệm thu toàn feature có evidence.

- [ ] T012 Đối chiếu và ghi lại quyết định ánh xạ từng đợt legacy ở `specs/005-department-period-scope/legacy-period-mapping.md`; sau xác nhận riêng, tạo migration backfill mới tại `src/main/java/db/migration/V20__backfill_period_department.java` và test bảo toàn/mơ hồ tại `src/test/java/vn/edu/topicregistration/integration/PeriodDepartmentMigrationIT.java`.
- [ ] T013 Chỉ sau T012 đạt data gate, tạo migration enforce mới cho Bộ môn bắt buộc và nhất quán Khoa tại `src/main/resources/db/migration/V21__enforce_period_department.sql`; test nâng cấp/idempotent/failure an toàn tại `src/test/java/vn/edu/topicregistration/integration/PeriodDepartmentMigrationIT.java`.
- [ ] T014 Chạy suite theo `specs/005-department-period-scope/quickstart.md`, ghi evidence thực tế và kết quả SC-005-01–06 vào `specs/005-department-period-scope/acceptance-matrix.md`; giữ task mở nếu baseline 004 hoặc suite bắt buộc chưa đạt.

## Dependencies and delivery strategy

```text
T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008
                  │                    └────────→ T009 → T010
                  └──────────────────────────────→ T011
T001 + confirmed mapping + T007 → T012 → T013
T008 + T010 + T011 + T013 → T014
```

- MVP: T001–T008 (US1–US3), chưa tuyên bố full 005 khi US4/US5/data gate còn mở.
- Sau T003, US5 có thể được lập plan riêng song song với US1; T009–T010 có thể bắt đầu sau invariant US2 đã ổn định. Không chạy song song migration test dùng chung DB test.
- V19/V20/V21 là tên migration **dự kiến** trên baseline V18; trước mỗi task phải kiểm tra version hiện tại và dừng nếu đã bị chiếm.
- T012 cần quyết định ánh xạ dữ liệu cụ thể của chủ dự án, không được coi việc duyệt tài liệu 005 là duyệt backfill dữ liệu thật.
