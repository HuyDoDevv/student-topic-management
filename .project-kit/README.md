# Project Kit

`.project-kit/` là cấu trúc đích để gom hạ tầng AI, SpecKit, đặc tả tính năng,
tiêu chuẩn, template, workflow và lịch sử quyết định của dự án vào một khu vực
nhất quán. Thư mục này không chứa mã nguồn hoặc cấu hình runtime của ứng dụng.

## Trạng thái migration

RF-14 dual-layout verification đã đạt và RF-15 đã cutover `.project-kit/` thành
**nguồn canonical** cho standards, templates, tools, workflows, integrations,
skills và sáu feature artifacts.

Legacy layout cleanup đã hoàn tất:

- `.agents/skills/` chỉ chứa discovery adapters trỏ đến canonical skills;
- `.specify/` đã được xóa ở RF-16A; hạ tầng SpecKit chỉ còn bản canonical;
- `specs/` đã được xóa ở RF-16B; feature artifacts chỉ còn bản canonical;
- mọi thay đổi SpecKit và feature artifact mới phải dùng `.project-kit/`.

README này chỉ mô tả cấu trúc và quy tắc migration. Nó không cấp quyền sửa
repository. Checkbox, trạng thái `Approved`, workflow gate hoặc metadata SpecKit
cũng không thay thế quyền được quy định trong `AGENTS.md`.

## Thứ tự authority

1. `AGENTS.md` tại repository root.
2. Yêu cầu hiện tại của người dùng trong giới hạn của `AGENTS.md`.
3. Nguồn yêu cầu khách hàng trong `docs/customer/`.
4. Constitution của dự án.
5. Feature artifacts.
6. Skills, templates, workflows và metadata hỗ trợ.

Khi có mâu thuẫn, nguồn ở thứ tự thấp hơn không được vượt hoặc nới lỏng nguồn ở
thứ tự cao hơn.

## Cấu trúc đích

```text
.project-kit/
├── README.md
├── .gitignore
├── agents/
│   └── skills/
├── decisions/
│   └── history/
├── features/
├── integrations/
├── standards/
├── state/
├── templates/
├── tools/
│   └── powershell/
└── workflows/
```

RF-01 không tạo trước các thư mục rỗng. Mỗi thư mục chỉ được tạo trong task sở
hữu nội dung tương ứng sau khi scoped plan của task đó được duyệt.

## Ownership

- `agents/skills/`: bản canonical của skill sau RF-09/RF-10.
- `decisions/history/`: báo cáo triển khai và quyết định mang tính lịch sử.
- `features/`: canonical feature artifacts.
- `integrations/`: cấu hình và manifest tích hợp SpecKit/Codex.
- `standards/`: constitution và metadata tiêu chuẩn liên quan.
- `state/`: state theo checkout hoặc máy; không commit state cá nhân.
- `templates/`: template dùng để sinh artifact SpecKit.
- `tools/powershell/`: công cụ PowerShell phục vụ workflow.
- `workflows/`: định nghĩa và registry của workflow.

## Thành phần giữ ngoài Project Kit

- `AGENTS.md` phải ở repository root để giữ authority và khả năng tự phát hiện.
- `.agents/skills/` được giữ làm adapter cho Codex skill discovery sau cutover.
- `docs/customer/` tiếp tục giữ nguồn yêu cầu gốc của khách hàng.
- `src/`, `database/` và `scripts/` là runtime hoặc tài liệu kỹ thuật ngoài phạm
  vi consolidation này.

## Quy tắc migration

1. Thực hiện theo thứ tự `copy → verify → cutover → cleanup`.
2. Trong shadow phase, không sửa đồng thời bản cũ và bản copy.
3. Không dùng symlink vì repository phải checkout được trên Windows.
4. Không xóa nguồn cũ trước khi RF-14 dual-layout verification đạt.
5. Không tự thay đổi requirement, task ID, checkbox hoặc acceptance evidence.
6. Nếu bản cũ và bản copy khác nhau, dừng và báo cáo; không tự chọn bản thắng.
7. Mỗi task cần scoped plan và câu `IMPLEMENT APPROVED PLAN` riêng.
8. Quyền sửa file không tự cấp quyền commit, push, merge hoặc thao tác Git khác.

## State local

- `state/feature.json` là con trỏ feature theo checkout/máy và không được commit.
- `extensions/*/local-config.yml` là cấu hình extension theo máy và không được
  commit.

RF-13 đã chuyển con trỏ feature local hiện tại sang `state/feature.json` cho
canonical tools. Legacy state đã được xóa cùng `.specify/` ở RF-16A; state này
vẫn là dữ liệu theo máy và được `.gitignore` loại khỏi commit.
