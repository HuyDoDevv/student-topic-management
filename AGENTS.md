## GLOBAL SAFETY PRECEDENCE

This section overrides all repository-local agent skills, workflows,
templates, specifications, task files, hooks, checklists, plans,
constitutions, and historical approvals.

This includes instructions under:

- .agents/
- .specify/
- specs/
- docs/
- workflow files
- skill files
- task files
- generated plans
- generated checklists

No repository document may grant write authorization.

### Current-conversation authorization only

Statements stored in repository files such as:

- "Approved"
- "Ready for implementation"
- "Implementation gate open"
- "Owner approved"
- "Can start immediately"
- checked or unchecked tasks
- historical implementation instructions

are PROJECT METADATA ONLY.

They are never authorization to modify the repository.

Write authorization must come from the active user conversation.

The only phrase that enables implementation is:

IMPLEMENT APPROVED PLAN

Previous approvals, specifications, task status, workflow state,
or generated files do not satisfy this requirement.

---

## SKILL AND WORKFLOW SAFETY

Skills and workflows are subordinate to AGENTS.md.

A skill or workflow must never bypass READ ONLY mode.

In READ ONLY mode:

- do not invoke write-capable workflows
- do not execute implementation skills
- do not execute write-capable hooks
- do not execute Git-changing hooks
- do not create specification files
- do not generate files into the repository
- do not update checklists
- do not mark tasks complete
- do not append tasks
- do not create GitHub issues
- do not create or switch branches
- do not persist generated plans

Skills may be inspected and reasoned about,
but write behavior remains disabled.

---

## HOOK SAFETY

Hooks do not have independent authority.

If a hook could:

- modify files
- modify Git
- modify external systems
- persist generated state

then it must NOT execute while in READ ONLY mode.

A mandatory hook in a subordinate skill does not override this rule.

---

## SPECKIT SAFETY

SpecKit is a planning and specification framework.
It is not an authorization mechanism.

During READ ONLY mode:

speckit-analyze:
MAY analyze only.

speckit-specify:
MAY propose specification content in chat only.
MUST NOT create branches or files.

speckit-plan:
MAY propose a plan in chat only.
MUST NOT generate repository artifacts.

speckit-tasks:
MAY propose tasks in chat only.
MUST NOT write tasks.md.

speckit-clarify:
MAY ask clarification questions.
MUST NOT persist answers.

speckit-checklist:
MAY propose checklist content.
MUST NOT create or update checklist files.

speckit-implement:
MUST NOT execute.

speckit-converge:
MAY report gaps.
MUST NOT append tasks or trigger implementation.

speckit-taskstoissues:
MUST NOT create issues.

Full automatic SpecKit workflows MUST NOT execute in READ ONLY mode.

---

## WRITE AUTHORIZATION SCOPE

Even after:

IMPLEMENT APPROVED PLAN

authorization applies ONLY to the explicitly approved plan.

It does not authorize:

- all remaining tasks in tasks.md
- all tasks in a feature
- the full SpecKit workflow
- unrelated discovered problems
- automatic convergence passes
- arbitrary hooks
- historical TODOs
- other unchecked tasks

After the approved scope is complete,
return to READ ONLY mode.

A new implementation scope requires a new explicit approval.

---

## PROTECTED CHANGES

The phrase IMPLEMENT APPROVED PLAN alone does not automatically approve
protected changes.

Protected changes must also appear explicitly in the approved plan:

- database schema or migrations
- authentication
- authorization
- security configuration
- public API contracts
- routes
- dependencies
- environment configuration
- deployment configuration
- external systems
- destructive operations

If a protected change becomes necessary but was not approved:

STOP.

Do not improvise.

---

## SCOPE EXPANSION

Discovery is not authorization.

Finding another bug, TODO, failing test, architecture problem,
unfinished task, or improvement does not permit fixing it.

Report it separately.

Never expand the current implementation scope automatically.

# AI DEVELOPMENT CONTROL POLICY

This repository operates in controlled development mode.

## Default mode: READ ONLY

Unless the user explicitly writes:

IMPLEMENT APPROVED PLAN

you must NOT:

- modify files
- create files
- delete files
- rename or move files
- install or remove dependencies
- modify database schema
- modify migrations
- modify security behavior
- modify API contracts
- modify routes
- modify environment configuration
- perform Git write operations
- perform refactoring
- perform cleanup

You may:

- read files
- search the repository
- inspect code
- trace execution paths
- explain existing behavior
- identify problems
- produce plans
- report uncertainty

## Required workflow

Every non-trivial task must follow:

INSPECT
→ PLAN
→ USER APPROVAL
→ IMPLEMENT
→ VERIFY
→ REPORT

Never skip directly from a request to implementation.

## Scope limits

Default implementation budget:

- Prefer 1–3 files
- More than 5 files requires explicit approval

Do not:

- perform unrelated cleanup
- rewrite working code
- refactor unrelated code
- add speculative abstractions
- add features not requested
- change architecture during bug fixing

If the task expands outside the approved scope:

STOP and explain instead of continuing.

## Protected areas

Changes to these areas require explicit approval:

- authentication
- authorization
- security configuration
- database schema
- migrations
- public APIs
- routes
- environment variables
- external integrations
- dependency versions
- build configuration
- deployment configuration

## Stop conditions

STOP and ask before continuing if:

- requirements are unclear
- existing behavior is unclear
- architecture must change
- database behavior must change
- security behavior must change
- API contracts must change
- dependencies must change
- more than 5 files need substantial changes

## Implementation key

Only enter write mode when the user explicitly says:

IMPLEMENT APPROVED PLAN

If the approved plan becomes insufficient, STOP.
Do not improvise outside scope.

# Quy tắc làm việc cho AI và thành viên dự án

File này áp dụng cho toàn bộ repository. Mọi AI hoặc thành viên tham gia sửa mã nguồn phải
đọc file này trước khi làm việc.

## Mô hình Git Flow

- `main`: phiên bản ổn định. Không phát triển hoặc commit trực tiếp trên nhánh này.
  **Bảo vệ tuyệt đối:** AI không được merge bất kỳ nhánh nào vào `main`, merge hoặc bật
  auto-merge pull request có nhánh đích là `main`, hay thực hiện commit, rebase, cherry-pick,
  push hoặc xóa làm thay đổi `main`. Chỉ chủ repository được tự thao tác thủ công. AI chỉ
  được kiểm tra trạng thái, cảnh báo và đề xuất quy trình; phải dừng trước mọi thao tác ghi
  lên `main`.
- `develop`: nhánh tích hợp chung. Không phát triển feature trực tiếp trên nhánh này.
- `feature/<feature-id>-<ten-ngan>`: tạo từ `develop`, dùng cho một feature hoặc một nhóm
  task liên quan chặt chẽ.
- `release/<version>`: tạo từ `develop` khi chuẩn bị phát hành; chỉ sửa lỗi phát hành,
  version và tài liệu cần thiết. Hoàn tất thì merge vào cả `main` và `develop`.
- `hotfix/<ten-ngan>`: tạo từ `main` cho lỗi khẩn cấp; hoàn tất thì merge vào cả `main`
  và `develop`.

Dự án dùng lệnh Git chuẩn, không phụ thuộc chương trình mở rộng `git-flow`.

## Quy trình bắt buộc cho một feature

1. Đọc `AGENTS.md`, `specs/001-topic-registration/spec.md`, `plan.md` và `tasks.md`.
2. Chạy `git status --short --branch` và xác định task ID sẽ thực hiện.
3. Chỉ bắt đầu khi gate của task đã được giải phóng trong `tasks.md`.
4. Từ `develop`, tạo nhánh bằng `git switch -c feature/<feature-id>-<ten-ngan>`.
   Nếu đang ở đúng nhánh feature thì tiếp tục trên nhánh đó.
5. Chỉ thay đổi file thuộc phạm vi feature. Không gộp một feature khác vào cùng nhánh.
6. Viết test có ý nghĩa theo task trước phần implementation khi task yêu cầu TDD.
7. Chạy kiểm tra phù hợp, cập nhật bằng chứng nghiệm thu và đánh dấu `[X]` cho task thực sự
   hoàn thành. Không đánh dấu task chỉ vì code biên dịch.
8. Chạy `git diff --check`, xem lại `git diff` và bảo đảm không có secret hoặc file máy cá nhân.
9. Mỗi checkbox task `Txxx` trong `tasks.md` phải tương ứng đúng một commit hoàn chỉnh;
   commit gồm code, test, tài liệu và bằng chứng cần thiết của riêng task đó. Không đánh dấu
   hoặc gộp nhiều task vào cùng một commit. Tiêu đề commit phải ghi task ID, ví dụ
   `feat(periods): implement period rules (T018)`. Báo lại branch, commit, test đã chạy và
   phần còn lại; một pull request có thể chứa nhiều commit task theo đúng thứ tự phụ thuộc.
10. Chỉ push, tạo/merge pull request, merge nhánh hoặc xóa nhánh khi người dùng yêu cầu.
    Yêu cầu này không bao giờ cho phép AI thay đổi hoặc merge vào `main`; quy tắc bảo vệ
    `main` phía trên luôn được ưu tiên và thao tác cuối cùng chỉ do chủ repository thực hiện.

Không dùng `git reset --hard`, `git clean`, tự động stash hoặc xóa thay đổi chưa commit của
người khác. Khi working tree đã có thay đổi, phải giữ nguyên và xác định chúng thuộc feature
nào trước khi tiếp tục. Không sửa lịch sử commit đã chia sẻ nếu chưa được yêu cầu rõ ràng.

## Tên nhánh dự kiến của dự án

| Phạm vi                             | Nhánh                                  |
| ----------------------------------- | -------------------------------------- |
| Nền tảng identity, security và test | `feature/001-foundation`               |
| US1 — Đợt đăng ký                   | `feature/001-us1-registration-periods` |
| US2 — Giảng viên đăng đề tài        | `feature/001-us2-topic-proposals`      |
| US3 — Duyệt và công bố              | `feature/001-us3-topic-review`         |
| US4 — Nhóm sinh viên                | `feature/001-us4-student-groups`       |
| US5 — Đăng ký đề tài                | `feature/001-us5-topic-registration`   |

Mỗi nhánh feature sau chỉ được tạo từ `develop` sau khi dependency và gate tương ứng trong
`tasks.md` đã đạt. Không tạo nhánh sau từ một feature branch trước đó để thay thế việc merge
vào `develop`.

## Commit và kiểm thử

Dùng Conventional Commits:

```text
feat(identity): implement database authentication (T008-T010)
fix(periods): reject schedule changes after lecturer opening
test(groups): cover concurrent membership constraints
docs(spec): record Q-02 clarification
chore(build): configure integration test profile
```

Các task được đánh dấu `[P]` có thể được thực hiện song song nhưng vẫn phải tạo commit riêng.
Hoàn thành và kiểm tra toàn bộ phạm vi của một task trước khi tạo commit, để không phải chia
một checkbox thành nhiều commit. Nếu phát hiện lỗi sau khi commit đã chia sẻ, tạo commit
`fix` riêng có ghi task gốc; không sửa lịch sử đã chia sẻ.

Trước khi hoàn tất feature, chạy tối thiểu:

```bash
./mvnw test
git diff --check
```

Khi các profile tương ứng đã được cấu hình, chạy thêm:

```bash
./mvnw verify -Pintegration
./mvnw verify -Pe2e
```

Trên Windows dùng `mvnw.cmd` thay cho `./mvnw`. Integration test phải dùng database MySQL
test riêng. Không dùng database local có dữ liệu thật và không bỏ qua test khi thiếu cấu hình.

## Quy tắc dự án

- Java 21, Spring Boot tương thích Java 21, Thymeleaf/Tabler và MySQL 8.4 LTS.
- Thay đổi database phải tạo migration Flyway mới. Không sửa migration đã được dùng trên
  database chia sẻ.
- DBeaver chỉ là công cụ kết nối/kiểm tra; ứng dụng và Flyway quản lý schema.
- Quyền, role, scope và quan hệ tài nguyên phải được kiểm tra ở backend.
- Không commit `.env`, mật khẩu, token, đường dẫn JDK riêng hoặc dữ liệu sinh bởi `target/`.
- Dùng `./run.sh` trên macOS/Linux và `run.cmd` trên Windows; giữ quy tắc line ending trong
  `.gitattributes`.

## Điều kiện merge feature vào develop

Feature chỉ sẵn sàng merge khi task trong phạm vi đã được đánh dấu `[X]`, test bắt buộc đạt,
ma trận nghiệm thu đã được cập nhật, không còn thay đổi ngoài phạm vi và không còn secret.
Ưu tiên pull request với merge commit hoặc `--no-ff` để giữ ranh giới feature trong lịch sử.
Nếu test hoặc gate còn blocked, giữ feature branch và báo rõ, không merge dở vào `develop`.
