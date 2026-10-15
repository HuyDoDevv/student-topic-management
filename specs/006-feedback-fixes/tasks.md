# Tasks: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/web.md](contracts/web.md),
[quickstart.md](quickstart.md), [checklists/requirements.md](checklists/requirements.md).

**Status rule**: T001 đã được người dùng cấp quyền bằng `IMPLEMENT APPROVED PLAN`
ngày 2026-10-14. T002–T018 hiện `[ ]`; mỗi task phải qua
`INSPECT → PLAN → APPROVE → IMPLEMENT → VERIFY → REPORT` riêng theo `AGENTS.md`.
Task file, checkbox hoặc trạng thái “ready” không tự cấp quyền sửa code/DB/Git.

## Phase 1 — Feature foundation

- [x] **T001 Khởi tạo feature 006 và tài liệu nền**
  - Tạo branch `feature/006-feedback-fixes` từ checkout sạch `develop` tại commit
    `6d29a78`; không stash/reset/push/merge.
  - Tạo `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/web.md`,
    `quickstart.md`, `tasks.md`, `checklists/requirements.md`.
  - Ánh xạ FB-01–FB-18 với requirement, task và acceptance; kiểm tra link/path,
    `git diff --check`, rồi commit riêng `docs(006): define feedback remediation scope`.

**Checkpoint**: chỉ tài liệu 006 thay đổi; không có code, schema, config hoặc dữ
liệu ứng dụng bị sửa. Sau report T001 quay lại READ ONLY.

## Phase 2 — Authorization foundation

- [ ] **T002 Tách hoàn toàn DEAN khỏi vai trò giảng viên** `[FB-01, FB-03, FB-10]`
  - File dự kiến:
    - `src/main/java/vn/edu/topicregistration/service/AdminAccountService.java`
    - `src/main/java/vn/edu/topicregistration/security/AuthorizationPolicy.java`
    - `src/main/java/vn/edu/topicregistration/repository/UserAccountRepository.java`
    - `src/main/java/vn/edu/topicregistration/controller/NavigationModelAdvice.java`
    - `src/main/java/vn/edu/topicregistration/service/DirectGradingService.java`
    - `src/main/java/vn/edu/topicregistration/service/CouncilService.java`
    - `src/main/resources/db/migration/V22__separate_dean_from_lecturer.sql`
    - unit/web/integration test tương ứng.
  - DEAN mới chỉ có `DEAN`; V22 bỏ `LECTURER` khỏi DEAN legacy nhưng giữ account,
    lịch sử topic/supervision/assignment/evaluation/result.
  - Cấm tổ hợp role mới và loại DEAN ở repository lẫn service cho mọi ứng viên.
  - Test tối đa một DEAN enabled/Khoa, kể cả concurrent create/unlock.
  - Acceptance: DEAN vẫn quản lý/duyệt/phân công/công bố nhưng không mở route đề
    xuất/chấm và không thể bị gán qua POST giả mạo.

## Phase 3 — Independent feedback flows

- [x] **T003 Bỏ bộ lọc Bộ môn thừa ở trang duyệt đề xuất** `[FB-02]`
  - File: `TopicReviewService.java`, `TopicRepository.java`,
    `TopicModerationController.java`, `templates/topics/review.html`,
    `TopicDepartmentIT.java` và test service/controller liên quan.
  - Mỗi đợt chỉ tải topic thuộc Department của chính đợt; bỏ lựa chọn toàn Khoa.
  - Acceptance: URL/query/body giả mạo Department khác không trả dữ liệu và không
    thay đổi quyết định; DEAN vẫn xử lý mọi Department trong Khoa qua từng đợt.

- [ ] **T004 Sửa link thông báo gây 404** `[FB-04]`
  - File: `NotificationTargetResolver.java`, `NotificationService.java`, nơi phát
    notification trong `DirectGradingService`, `CouncilService`,
    `TopicReviewService`, `NotificationInboxWebTest`, `NotificationFlowE2ETest`.
  - Mỗi category resolve tới route recipient có quyền; direct grading notification
    của lecturer không trỏ vào trang phân công DEAN.
  - Acceptance: target hợp lệ mở đúng; nhiệm vụ hết hiệu lực về inbox có message,
    không 404; Khoa/owner khác vẫn bị chặn.

- [ ] **T005 Cho sửa đề tài đã duyệt nhưng chưa công bố** `[FB-07]`
  - File: `TopicRules.java`, `TopicReviewRules.java`, `TopicAuthorService.java`,
    `TopicReviewService.java`, `TopicAuthorController.java`,
    `TopicReviewController.java`, `templates/topics/detail.html`,
    `templates/topics/review.html` và test rule/service/web.
  - Tác giả được `APPROVED → DRAFT` khi chưa published, chưa có registration và
    còn giai đoạn; sau sửa phải submit lại.
  - Acceptance: topic published/có registration/hết hạn bị chặn; DEAN không sửa
    nội dung thay lecturer; stale version không ghi đè.

- [ ] **T006 Hiển thị đầy đủ tiến trình cho sinh viên** `[FB-08]`
  - File: `RegistrationService.java`, `ReportService.java`, `ResultService.java`,
    `RegistrationController.java`, `templates/registrations/detail.html`,
    `templates/groups/detail.html` và test registration/result.
  - Dẫn xuất chín bước trong spec, không thêm DB status dư thừa nếu nguồn hiện có
    đủ suy ra.
  - Acceptance: fixture ở mỗi mốc trả đúng nhãn/action; Khoa/registration khác
    không lộ; published là trạng thái terminal.

- [ ] **T007 Làm rõ vai trò sinh viên trên danh sách nhóm** `[FB-09]`
  - File: `GroupService.java`, `GroupController.java`, `templates/groups/list.html`,
    `templates/fragments/group-period-cards.html`, `GroupPeriodPhaseTest.java` và
    web test nhóm.
  - Mỗi card có tên/mã đợt, Department, mã nhóm, topic, Leader/Member, status/action.
  - Acceptance: một SV có nhiều đợt và hai vai trò khác nhau vẫn phân biệt đúng,
    không trộn group/action giữa period.

- [ ] **T008 Chấm toàn bộ thành viên nhóm trên một màn hình** `[FB-05]`
  - Tạo `src/main/java/vn/edu/topicregistration/dto/GroupScoreForm.java`.
  - Sửa `ScoringController.java`, `ScoringService.java`,
    `templates/scoring/form.html`, `templates/scoring/student-form.html`,
    `ScoringControllerTest.java`, `ScoringServiceTest.java`.
  - Validate tập student snapshot, score/feedback/version; draft/submit cả nhóm
    trong một transaction.
  - Acceptance: nhóm 1–3 SV; một row lỗi/thừa/thiếu/trùng/stale làm rollback toàn
    request; POST giả mạo không ghi; request hợp lệ lưu đủ.

## Phase 4 — Reviewer persistence and workflow

- [x] **T009 Mở rộng schema cho phản biện** `[FB-18]`
  - Tạo migration `src/main/resources/db/migration/V23__support_reviewer_assessments.sql`.
  - Tạo `ReviewerAssignmentEntity.java`, `ReviewerEvaluationEntity.java`,
    `ReviewerReportEntity.java`, ba repository tương ứng.
  - Mở rộng bảng `reviewer_assignments` V1 để có lifecycle/deadline/version/audit;
    lưu report metadata và điểm reviewer theo từng registration student; dùng
    `ReportFileRepository` cho PDF.
  - Migration test: V1→V23, V21→V23, validate/rerun, snapshot business rows,
    không tự suy diễn reviewer hoặc sửa final result `PUBLISHED`.
  - Acceptance: FK/unique/check bảo vệ registration accepted, một reviewer và một
    evaluation/student; legacy rows đọc an toàn, không mất dữ liệu.

- [x] **T010 Phân công và nộp phản biện** `[FB-14, FB-18]`
  - Tạo/sửa `ReviewerAssignmentService.java`, `ReviewerController.java`,
    `ReviewerAssignmentForm.java`, `ReviewerSubmissionForm.java`,
    `templates/reviewers/assignments.html`, `templates/reviewers/submission.html`,
    repository T009 và unit/web/integration test.
  - DEAN phân công reviewer enabled cùng Khoa, không DEAN, không GVHD/đồng hướng
    dẫn và không trùng grader/member của cùng topic.
  - Reviewer nộp PDF cùng score/feedback đủ mọi SV trước/đúng cuối ngày deadline
    Việt Nam trong một transaction.
  - Acceptance: thiếu PDF/score/student, quá hạn, actor sai, conflict hoặc stale
    version đều không để dữ liệu một phần; thay reviewer chỉ trước submission.

## Phase 5 — TLCN and KLTN workflows

- [ ] **T011 Hoàn thiện quy trình TLCN** `[FB-11, FB-18]`
  - File: `DirectGradingRules.java`, `DirectGradingService.java`,
    `ScoringService.java`, `ResultService.java`, `DirectGradingController.java`,
    `templates/direct-grading/list.html` và test direct grading/scoring/result.
  - TLCN không vào council. Chỉ sau reviewer submitted đủ/đúng hạn mới chọn grader
    thứ hai khác reviewer, DEAN, GVHD/đồng hướng dẫn, enabled cùng Khoa.
  - Acceptance: final từng SV bằng hai nguồn/2; thiếu một nguồn không READY/PUBLISHED;
    direct POST trước reviewer submission bị chặn.

- [ ] **T012 Hoàn thiện quy trình KLTN** `[FB-14, FB-18]`
  - File: `CouncilRules.java`, `CouncilService.java`, `ScoringService.java`,
    `FinalScoreRules.java`, `ResultService.java`, `templates/councils/form.html`,
    `templates/councils/detail.html` và test council/scoring/result.
  - Council chỉ nhận KLTN, có 3–5 member quyền chấm; một reviewer độc lập, không
    trùng member; DEAN/GVHD/đồng hướng dẫn bị loại.
  - Acceptance: final là mean reviewer + mọi member theo SV, `HALF_UP` hai chữ số;
    thiếu một submission không tổng hợp/công bố; ví dụ bốn điểm 7 → 7.00.

- [ ] **T013 Cho sửa thành viên và phân công hội đồng đúng giai đoạn** `[FB-12, FB-13, FB-14]`
  - File: `CouncilForm.java`, `CouncilAssignmentForm.java`, `CouncilService.java`,
    `CouncilController.java`, `templates/councils/form.html`,
    `templates/councils/detail.html` và test council.
  - Sửa member khi `DRAFT`/`ASSIGNED` chưa có score; thay reviewer khi chưa submit;
    khóa sau score hoặc `ACTIVE`; mỗi lần sửa kiểm tra toàn bộ conflict.
  - UI hiển thị member/role rõ và thêm/xóa trực tiếp.
  - Acceptance: thao tác hợp lệ giữ cardinality/role; invalid/stale rollback; không
    đổi tập người chấm sau khi đã có score.

- [ ] **T014 Chặn lịch bảo vệ trong quá khứ** `[FB-15]`
  - File: `CouncilRules.java`, `CouncilService.java`, `CouncilAssignmentForm.java`,
    `CouncilController.java`, `templates/councils/detail.html`,
    `CouncilRulesTest.java`, `CouncilControllerTest.java`.
  - Service dùng clock server yêu cầu schedule `> now`; template đặt `min` nhưng
    backend vẫn kiểm tra; create/update cùng optimistic lock.
  - Acceptance: past/equal-now bị từ chối ngay form, future được nhận; timezone
    client không đổi quyết định; không chuyển lỗi validation sang trang 422 chung.

## Phase 6 — Progress, result evidence and navigation

- [ ] **T015 Hiển thị tiến độ chấm cho Chủ tịch và DEAN** `[FB-17]`
  - File: `ScoringService.java`, `CouncilService.java`, `CouncilController.java`,
    `templates/councils/detail.html`, `templates/scoring/list.html` và test quyền/
    tiến độ.
  - Hiển thị từng topic/person: chưa bắt đầu, draft, submitted đủ, số SV đã chấm/
    tổng số, submitted time và trạng thái reviewer report.
  - Acceptance: chair chỉ hội đồng mình, DEAN chỉ Khoa; không lộ điểm draft cho
    người không có quyền; URL khác council/Khoa bị chặn.

- [ ] **T016 Làm rõ căn cứ tổng hợp và công bố điểm** `[FB-16]`
  - File: `FinalScoreRules.java`, `ResultService.java`, `ResultController.java`,
    `templates/results/faculty-detail.html`, `templates/results/student-detail.html`
    và test result/publication.
  - Trước publish hiển thị grader/role/status/submitted score/formula/source count/
    mean/consolidator/time/publisher/time. Chỉ DEAN publish; student chỉ sau publish.
  - Acceptance: completeness theo đúng loại đợt, HALF_UP, actor/time lưu đúng;
    không update `PUBLISHED`; thiếu một nguồn hoặc một SV làm publish bị từ chối.

- [ ] **T017 Làm lại danh sách kết quả khi có nhiều đề tài** `[FB-06]`
  - File: `FinalResultRepository.java`, `ResultService.java`, `ResultController.java`,
    `templates/results/faculty-list.html`, `ResultControllerTest.java`,
    `ResultServiceTest.java`.
  - Filter period/topic type/topic-group/grader/status, allow-list sort, pagination,
    group student theo topic/group và link chi tiết T016.
  - Acceptance: dữ liệu nhiều period/topic không trộn; filter/sort/page ổn định;
    tampered query không vượt scope Khoa hoặc tạo query không an toàn.

## Phase 7 — Full acceptance

- [ ] **T018 Nghiệm thu toàn bộ 006** `[FB-01–FB-18]`
  - Tạo `specs/006-feedback-fixes/acceptance-matrix.md` và chỉ điền evidence thật.
  - Bao phủ role DEAN/LECTURER/REVIEWER/SUPERVISOR/STUDENT; COURSE/NCKH/TLCN/
    KLTN; nhóm 1–3 SV; nhiều period/topic; direct URL/forged POST; deadline; stale
    version/concurrency; migration V21→V23; history preservation.
  - Chạy `./mvnw test`, integration MySQL 8.4 DB test 3307, E2E và
    `git diff --check`; ghi rõ phần chưa verify/rủi ro còn lại.
  - Acceptance: đủ evidence cho SC-006-01–07; task còn mở nếu bất kỳ gate bắt buộc
    chưa đạt. Không sửa lỗi ngoài scope trong lúc acceptance.

## Dependencies

```text
T001 → T002
  ├──→ T003, T004, T005, T006, T007, T008
  └──→ T009 → T010 → T011
                   └→ T012 → T013 → T014
                                  └→ T015 → T016 → T017 → T018

T008 ───────────────────────────────┘
```

- T003–T008 độc lập về nghiệp vụ nhưng đều dùng invariant DEAN từ T002 khi có
  danh sách/quyền liên quan.
- T009 phải hoàn tất trước T010; T010 trước TLCN/KLTN.
- T012 trước sửa lifecycle/schedule/progress/result hội đồng.
- T016 trước T017 để link danh sách tới căn cứ ổn định.

## Feedback coverage summary

| Feedback | Task | Acceptance evidence dự kiến |
|---|---|---|
| FB-01 | T002 | Authorization/menu/direct URL tests |
| FB-02 | T003 | Topic department web/service tests |
| FB-03 | T002 | Candidate query/service tests |
| FB-04 | T004 | Inbox web + notification E2E |
| FB-05 | T008 | Group form transaction/version tests |
| FB-06 | T017 | Result query/controller tests |
| FB-07 | T005 | Topic state/ownership tests |
| FB-08 | T006 | Progress projection tests |
| FB-09 | T007 | Multi-period role web tests |
| FB-10 | T002 | Forged DEAN grader POST test |
| FB-11 | T011 | TLCN no-council tests |
| FB-12 | T013 | Council UI/contract tests |
| FB-13 | T013 | Lifecycle edit/stale tests |
| FB-14 | T010/T012/T013 | Supervisor conflict tests across roles |
| FB-15 | T014 | Fixed-clock rule/controller tests |
| FB-16 | T016 | Result evidence/publication tests |
| FB-17 | T015 | Chair/DEAN progress authorization tests |
| FB-18 | T009–T012 | Migration, reviewer, formula and completeness tests |

## Delivery discipline

- Mỗi task ưu tiên 1–3 file; task đã nêu nhiều hơn năm file là scope cấp cao được
  duyệt nhưng vẫn cần plan chi tiết trước lượt implementation của chính task.
- Không cleanup/refactor/warning fix ngoài task; phát hiện vấn đề khác chỉ report.
- Git write ngoài branch/commit đã nêu ở T001 cần approval riêng hoặc phải nằm rõ
  trong plan task. Không push/merge/rebase/stash/reset/clean nếu chưa được phép.
