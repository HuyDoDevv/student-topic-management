# Quy tắc làm việc cho AI và thành viên dự án

File này áp dụng cho toàn bộ repository. Mọi AI hoặc thành viên tham gia sửa mã nguồn phải
đọc file này trước khi làm việc.

## Mô hình Git Flow

- `main`: phiên bản ổn định. Không phát triển hoặc commit trực tiếp trên nhánh này.
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
9. Tạo commit nhỏ, có nội dung hoàn chỉnh và báo lại branch, commit, test đã chạy và phần
   còn lại. Chuẩn bị pull request vào `develop`.
10. Chỉ push, tạo/merge pull request, merge nhánh hoặc xóa nhánh khi người dùng yêu cầu.

Không dùng `git reset --hard`, `git clean`, tự động stash hoặc xóa thay đổi chưa commit của
người khác. Khi working tree đã có thay đổi, phải giữ nguyên và xác định chúng thuộc feature
nào trước khi tiếp tục. Không sửa lịch sử commit đã chia sẻ nếu chưa được yêu cầu rõ ràng.

## Tên nhánh dự kiến của dự án

| Phạm vi | Nhánh |
|---|---|
| Nền tảng identity, security và test | `feature/001-foundation` |
| US1 — Đợt đăng ký | `feature/001-us1-registration-periods` |
| US2 — Giảng viên đăng đề tài | `feature/001-us2-topic-proposals` |
| US3 — Duyệt và công bố | `feature/001-us3-topic-review` |
| US4 — Nhóm sinh viên | `feature/001-us4-student-groups` |
| US5 — Đăng ký đề tài | `feature/001-us5-topic-registration` |

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
