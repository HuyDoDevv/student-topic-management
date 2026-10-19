# Hướng dẫn kiểm thử nghiệp vụ thủ công theo vai trò

Tài liệu này mô tả cách kiểm thử thủ công các chức năng hiện có của hệ thống bằng giao
diện web. Mỗi kịch bản nêu vai trò, điều kiện tiên quyết, các bước thao tác và kết quả
mong đợi. Khi một nghiệp vụ phụ thuộc vai trò khác, kịch bản dẫn tới mã test case cần
hoàn thành trước thay vì giả định dữ liệu đã tồn tại.

## 1. Phạm vi và quy ước

Các vai trò được kiểm tra:

- `ADMIN`: Quản trị viên.
- `DEAN`: Trưởng khoa.
- `LECTURER`: Giảng viên.
- `STUDENT`: Sinh viên.

Quy ước kết quả:

- **PASS**: kết quả thực tế trùng toàn bộ kết quả mong đợi.
- **FAIL**: sai dữ liệu, sai trạng thái, sai quyền hoặc có thay đổi dở dang.
- **BLOCKED**: thiếu dữ liệu/điều kiện từ một test case trước; ghi rõ mã test case bị thiếu.
- Không sửa trực tiếp database để ép trạng thái một nghiệp vụ đang test.
- Dùng cửa sổ ẩn danh hoặc đăng xuất hoàn toàn trước khi đổi vai trò.
- Các mật khẩu được chọn trong lúc test phải lưu ngoài repository; không ghi mật khẩu thật
  vào biên bản hoặc commit.

## 2. Chuẩn bị môi trường

### 2.1 Database và ứng dụng

1. Dùng database local rỗng, không dùng database chia sẻ hoặc production.
2. Cấu hình `.env` theo `README.md` và giữ `SPRING_PROFILES_ACTIVE=local`.
3. Để kiểm thử clean-slate, giữ `DEMO_SEED_ENABLED=false`.
4. Khởi động bằng `./workspace/scripts/run.sh` hoặc `workspace\scripts\run.cmd`.
5. Mở `http://127.0.0.1:8088`.
6. Xác nhận Flyway đã chạy và trang đăng nhập hiển thị.

Database baseline có sẵn:

- Tài khoản local `admin`, role `ADMIN`, mật khẩu bootstrap được ghi trong `README.md`.
- Khoa `Khoa Công nghệ Thông tin`.

### 2.2 Bộ dữ liệu nên tạo

Thực hiện các test case ADMIN ở phần 5 để tạo:

| Ký hiệu | Số lượng | Mục đích |
|---|---:|---|
| Bộ môn A | 1 | Phạm vi chính của đợt, giảng viên và sinh viên |
| Bộ môn B | 1 | Kiểm tra lọc/phạm vi khác Bộ môn trong cùng Khoa |
| DEAN-A | 1 | Quản lý Khoa |
| LECTURER-A1 | 1 | Tác giả/GVHD chính |
| LECTURER-A2 | 1 | GVHD phối hợp hoặc người phản biện |
| LECTURER-A3 | 1 | Chủ tịch hội đồng |
| LECTURER-A4 | 1 | Thư ký hội đồng |
| LECTURER-A5 | 1 | Thành viên hội đồng/người chấm độc lập |
| STUDENT-A1 | 1 | Trưởng nhóm ban đầu |
| STUDENT-A2 | 1 | Thành viên nhóm |
| STUDENT-A3 | 1 | Thành viên nhóm |
| STUDENT-A4 | 1 | Kiểm tra lời mời từ chối/quyền ngoài nhóm |

Giá trị mẫu hợp lệ:

- MSSV: tám chữ số khác nhau, ví dụ `26110001` đến `26110004`.
- Lớp: chỉ chữ không dấu và số, ví dụ `26110CLST1`.
- Khóa: định dạng `K2026`.
- Tên đăng nhập nhân sự: chỉ chữ, số, dấu `.`, `_`, `-`; không bắt đầu/kết thúc bằng dấu.
- Mật khẩu tạm thời: người test tự chọn, từ 8 đến 72 ký tự.

### 2.3 Chuỗi nghiệp vụ tổng quát

```text
ADMIN tạo Bộ môn và tài khoản
  → DEAN tạo đợt
  → LECTURER tạo và gửi đề tài
  → DEAN duyệt, công bố đề tài
  → STUDENT tạo đủ nhóm ba người
  → STUDENT đăng ký đề tài
  → DEAN chấp nhận đăng ký
  → STUDENT nộp báo cáo
  → phản biện/chấm điểm
  → DEAN công bố kết quả
  → STUDENT xem kết quả
```

## 3. Mẫu ghi nhận một test case

Mỗi lần chạy nên ghi:

```text
Mã test case:
Ngày/giờ:
Người kiểm thử:
Database:
Tài khoản/vai trò:
Dữ liệu nhập:
Kết quả thực tế:
PASS / FAIL / BLOCKED:
Ảnh hoặc log bằng chứng:
```

## 4. Chức năng chung cho mọi vai trò

### COMMON-01 — Đăng nhập, Dashboard và đăng xuất

**Điều kiện:** Có một tài khoản đang hoạt động.

1. Mở trang đăng nhập.
2. Nhập tên đăng nhập và mật khẩu đúng.
3. Nhấn **Đăng nhập**.
4. Xác nhận hệ thống chuyển tới **Tổng quan**.
5. Kiểm tra tên người dùng và menu đúng với vai trò.
6. Chọn **Đăng xuất**.
7. Thử mở lại `/dashboard`.

**Mong đợi:** Đăng nhập thành công, Dashboard chỉ hiển thị dữ liệu trong phạm vi của tài
khoản; sau đăng xuất, route bảo vệ chuyển về trang đăng nhập.

**Kiểm tra âm:** Nhập sai mật khẩu nhiều lần; xác nhận đăng nhập bị từ chối và không tạo
phiên xác thực hợp lệ.

### COMMON-02 — Đổi mật khẩu

**Điều kiện:** Đã đăng nhập.

1. Mở menu tài khoản và chọn **Đổi mật khẩu**.
2. Nhập **Mật khẩu hiện tại**.
3. Nhập **Mật khẩu mới** từ 8 đến 72 ký tự.
4. Nhập **Xác nhận mật khẩu mới** trùng khớp.
5. Nhấn **Đổi mật khẩu**.
6. Đăng xuất rồi đăng nhập bằng mật khẩu mới.

**Mong đợi:** Đổi mật khẩu thành công; mật khẩu cũ không còn đăng nhập được.

**Kiểm tra âm:** Sai mật khẩu hiện tại, mật khẩu mới quá ngắn hoặc xác nhận không khớp đều
bị từ chối mà mật khẩu cũ vẫn còn hiệu lực.

### COMMON-03 — Xem và lọc thông báo

**Điều kiện:** Tài khoản đã nhận ít nhất một thông báo từ một kịch bản liên vai trò.

1. Mở biểu tượng chuông hoặc menu **Thông báo**.
2. Lọc theo **Trạng thái** và **Loại thông báo**.
3. Chuyển trang nếu danh sách có nhiều hơn một trang.
4. Đối chiếu tiêu đề, nội dung tóm tắt, thời gian và trạng thái đọc.

**Mong đợi:** Chỉ thông báo của tài khoản hiện tại xuất hiện; bộ lọc và phân trang giữ đúng
điều kiện đã chọn.

### COMMON-04 — Đọc, đọc tất cả và mở đích thông báo

1. Với một thông báo chưa đọc, nhấn **Đánh dấu đã đọc**.
2. Xác nhận badge chưa đọc giảm tương ứng.
3. Nhấn vào nội dung một thông báo có đích nghiệp vụ.
4. Quay lại danh sách, nhấn **Đánh dấu tất cả đã đọc**.

**Mong đợi:** Trạng thái đọc được lưu; mở thông báo đánh dấu đã đọc và điều hướng tới đúng
tài nguyên mà tài khoản có quyền xem; badge chưa đọc trở về 0 sau thao tác đọc tất cả.

### COMMON-05 — Từ chối truy cập ngoài vai trò

1. Đăng nhập lần lượt bằng ADMIN, DEAN, LECTURER và STUDENT.
2. Với mỗi tài khoản, nhập trực tiếp một URL chỉ dành cho vai trò khác.
3. Thử cả GET và thao tác POST thông qua UI nếu có nút/hành động tương ứng.

**Mong đợi:** Hệ thống trả trang từ chối/không tìm thấy phù hợp, không lộ dữ liệu được bảo
vệ và không tạo thay đổi nghiệp vụ.

## 5. Vai trò ADMIN

### ADM-DEPT-01 — Tạo, tìm và sửa Bộ môn

**Điều kiện:** Đăng nhập bằng `admin`.

1. Vào **Quản trị hệ thống → Quản lý bộ môn**.
2. Nhấn **Thêm Bộ môn**.
3. Chọn **Khoa Công nghệ Thông tin**.
4. Nhập **Tên Bộ môn**, ví dụ `Công nghệ Phần mềm`.
5. Nhấn **Lưu**.
6. Tìm Bộ môn theo tên và Khoa.
7. Nhấn **Sửa**, đổi tên thành một giá trị hợp lệ rồi lưu.

**Mong đợi:** Bộ môn được tạo, xuất hiện trong bộ lọc/danh sách và tên mới được lưu. Tên
trùng trong cùng Khoa hoặc tên trống bị từ chối.

### ADM-ACC-01 — Tạo tài khoản Sinh viên

1. Vào **Quản lý sinh viên** và nhấn **Thêm sinh viên**.
2. Nhập **Họ tên**, **MSSV**, **Lớp**, **Khóa** và **Mật khẩu tạm thời**.
3. Nhấn **Tạo sinh viên**.
4. Tìm bằng MSSV hoặc họ tên.
5. Đăng nhập thử bằng MSSV vừa tạo.

**Mong đợi:** Tài khoản có role STUDENT, username là MSSV, có hồ sơ học tập và đăng nhập
được. MSSV sai định dạng hoặc trùng bị từ chối.

### ADM-ACC-02 — Tạo tài khoản Giảng viên

**Phụ thuộc:** `ADM-DEPT-01`.

1. Vào **Quản lý giảng viên** và nhấn **Thêm giảng viên**.
2. Chọn **Bộ môn**.
3. Nhập **Họ tên**, **Tên đăng nhập**, **Mật khẩu tạm thời**.
4. Nhấn **Tạo giảng viên**.
5. Tìm theo tên đăng nhập, họ tên, Khoa, Bộ môn và trạng thái.

**Mong đợi:** Tài khoản có role LECTURER và đúng phạm vi Khoa/Bộ môn.

### ADM-ACC-03 — Tạo tài khoản Trưởng khoa

**Phụ thuộc:** `ADM-DEPT-01`.

1. Vào **Quản lý trưởng khoa** và nhấn **Thêm trưởng khoa**.
2. Chọn **Bộ môn**, nhập **Họ tên**, **Tên đăng nhập**, **Mật khẩu tạm thời**.
3. Nhấn **Tạo trưởng khoa**.
4. Tìm và đăng nhập thử bằng tài khoản vừa tạo.

**Mong đợi:** Tài khoản có role DEAN và được gắn Khoa/Bộ môn tương ứng. Nếu Khoa đã có
Trưởng khoa hoạt động và rule hiện tại không cho phép thêm, hệ thống từ chối mà không tạo
tài khoản thứ hai.

### ADM-ACC-04 — Danh sách, lọc và xem/gán Bộ môn

1. Mở từng danh sách Sinh viên, Giảng viên và Trưởng khoa.
2. Lọc theo từ khóa, trạng thái, Khoa và Bộ môn khi bộ lọc có sẵn.
3. Với tài khoản nhân sự chưa có Bộ môn, chọn **Xem/Gán Bộ môn**.
4. Chọn Bộ môn rồi nhấn **Lưu Bộ môn**.
5. Mở lại tài khoản đã có Bộ môn.

**Mong đợi:** Danh sách đúng bộ lọc; tài khoản chưa có Bộ môn được gán thành công. Với tài
khoản đã có Bộ môn, màn hình hiện tại chỉ cho xem và không tự ý đổi phạm vi đã gán.

### ADM-ACC-05 — Đặt lại mật khẩu

1. Tại danh sách tài khoản, chọn **Đặt lại mật khẩu**.
2. Nhập **Mật khẩu tạm thời mới** và **Xác nhận mật khẩu**.
3. Nhấn **Đặt lại mật khẩu**.
4. Đăng nhập bằng mật khẩu mới và thử lại mật khẩu cũ.

**Mong đợi:** Chỉ mật khẩu mới còn hiệu lực; hai trường không khớp hoặc dưới 8 ký tự bị từ
chối.

### ADM-ACC-06 — Khóa và mở khóa tài khoản

1. Chọn **Khóa** trên một tài khoản thử nghiệm.
2. Đăng xuất và thử đăng nhập bằng tài khoản đó.
3. Đăng nhập ADMIN, lọc trạng thái đã khóa và nhấn **Mở khóa**.
4. Thử đăng nhập lại.

**Mong đợi:** Tài khoản khóa không đăng nhập được; sau mở khóa đăng nhập bình thường. Không
ảnh hưởng tài khoản khác.

### ADM-ANN-01 — Thông báo cấp trường

1. Vào **Thông báo chính thức → Tạo thông báo**.
2. Xác nhận phạm vi hiển thị là **Nhà trường**.
3. Nhập **Tiêu đề** và **Nội dung** rồi nhấn **Tạo bản nháp**.
4. Mở chi tiết, chọn **Sửa bản nháp**, thay đổi nội dung và lưu.
5. Nhấn **Công bố**.
6. Đăng nhập DEAN, LECTURER và STUDENT đang hoạt động để kiểm tra hộp thông báo.

**Mong đợi:** Bản nháp chỉ do ADMIN đúng phạm vi quản lý; sau công bố, mọi tài khoản đang
hoạt động trong hệ thống, trừ chính người công bố, nhận một thông báo. Thông báo đã công bố
không còn sửa như bản nháp.

### ADM-TOPIC-01 — Kiểm duyệt khẩn cấp đề tài

Backend có thao tác ADMIN ẩn đề tài và gửi thông báo cho đối tượng liên quan, nhưng giao
diện hiện tại không có nút/menu để gọi thao tác này. Kiểm thử thủ công thuần UI được đánh
dấu **BLOCKED BY UI**. Quyền backend được kiểm tra tự động trong `AuthorizationMatrixTest`;
không giả lập POST hoặc sửa database trong phiên kiểm thử UI.

### ADM-AUTH-01 — Giới hạn học vụ của ADMIN

1. Đăng nhập ADMIN.
2. Thử mở trực tiếp trang quản lý đợt, hội đồng, chấm điểm và kết quả Khoa.

**Mong đợi:** ADMIN không được sử dụng quyền quản trị hệ thống để đọc hoặc quyết định dữ
liệu học vụ dành cho DEAN/LECTURER/STUDENT.

## 6. Vai trò DEAN

### DEAN-ANN-01 — Thông báo trong phạm vi Khoa

**Phụ thuộc:** `ADM-ACC-03` và có tài khoản cùng/khác phạm vi để đối chiếu.

1. Đăng nhập DEAN, vào **Thông báo chính thức**.
2. Tạo bản nháp với **Tiêu đề** và **Nội dung**.
3. Sửa bản nháp rồi công bố.
4. Kiểm tra hộp thông báo của tài khoản trong cùng Khoa và tài khoản ngoài Khoa.

**Mong đợi:** Nguồn/phạm vi là Khoa của DEAN; chỉ tài khoản đang hoạt động cùng Khoa, trừ
người công bố, nhận thông báo. DEAN không xem/sửa thông báo cấp trường hoặc Khoa khác.

### DEAN-PERIOD-01 — Tạo đợt đăng ký

**Phụ thuộc:** `ADM-DEPT-01`, `ADM-ACC-03`.

1. Vào **Đợt đăng ký → Tạo đợt**.
2. Chọn **Bộ môn** và nhập **Tên đợt**.
3. Chọn **Loại đề tài**: `COURSE`, `NCKH`, `TLCN` hoặc `KLTN`.
4. Nhập ngày **Bắt đầu/Kết thúc** cho Giảng viên.
5. Nhập ngày **Bắt đầu/Kết thúc** cho Sinh viên.
6. Với luồng liên quan, nhập **Hạn nộp điểm GVPB** và **Ngày báo cáo hội đồng**.
7. Nhấn **Tạo đợt** và mở trang chi tiết.

**Mong đợi:** Đợt thuộc đúng Khoa/Bộ môn, loại và các cửa sổ thời gian. Khoảng ngày không
hợp lệ bị từ chối mà không tạo đợt dở dang.

**Gợi ý:** Tạo tối thiểu một đợt `COURSE` và một đợt `KLTN`; chọn ngày bao quanh ngày test
để có thể thao tác tuần tự các vai trò.

### DEAN-PERIOD-02 — Xem và sửa cấu hình đợt

**Phụ thuộc:** `DEAN-PERIOD-01`.

1. Mở **Đợt đăng ký**, kiểm tra danh sách và nhãn Bộ môn/loại/trạng thái thời gian.
2. Mở **Xem chi tiết**.
3. Nhấn **Sửa cấu hình**, thay đổi trường còn được phép rồi **Lưu thay đổi**.
4. Thử mở ID của đợt thuộc Khoa khác nếu có fixture đối chiếu.

**Mong đợi:** Thay đổi hợp lệ được lưu theo version hiện tại; dữ liệu phụ thuộc không bị
mất. DEAN chỉ xem/sửa đợt trong Khoa mình.

### DEAN-TOPIC-01 — Duyệt hoặc từ chối đề tài

**Phụ thuộc:** `LECTURER-TOPIC-02`.

1. Mở chi tiết đợt và chọn **Duyệt nội dung đề tài**.
2. Mở đề tài trạng thái chờ duyệt.
3. Kiểm tra tên, mô tả, mục tiêu, yêu cầu, kết quả dự kiến và GVHD.
4. Chọn **Duyệt** cho một đề tài.
5. Với đề tài khác, nhập lý do và chọn **Từ chối**.
6. Kiểm tra thông báo của tác giả/GVHD.

**Mong đợi:** Trạng thái đổi đúng quyết định; từ chối có lý do; người liên quan nhận thông
báo; DEAN Khoa khác không quyết định được.

### DEAN-TOPIC-02 — Công bố đề tài đã duyệt

**Phụ thuộc:** `DEAN-TOPIC-01` có ít nhất một đề tài được duyệt.

1. Mở danh sách duyệt của đợt.
2. Nhấn thao tác **Công bố đề tài** của đợt.
3. Đăng nhập STUDENT thuộc phạm vi và mở đợt.

**Mong đợi:** Đề tài đã duyệt chuyển sang công bố và xuất hiện cho Sinh viên; đề tài nháp,
chờ duyệt hoặc bị từ chối không xuất hiện để đăng ký.

### DEAN-REG-01 — Chấp nhận đăng ký đề tài

**Phụ thuộc:** `STUDENT-REG-01`.

1. Mở chi tiết đợt, phần **Xét nhóm đăng ký đề tài**.
2. Mở một đăng ký đang chờ.
3. Kiểm tra nhóm, snapshot ba thành viên và đề tài.
4. Nhấn **Chấp nhận**.
5. Kiểm tra trang đăng ký và thông báo của ba thành viên/GVHD.

**Mong đợi:** Đăng ký chuyển sang `ACCEPTED`, snapshot thành viên được giữ và người liên
quan nhận thông báo.

### DEAN-REG-02 — Từ chối đăng ký đề tài

**Phụ thuộc:** Có đăng ký chờ khác từ `STUDENT-REG-01`.

1. Mở màn hình xét duyệt.
2. Nhập **Lý do** và nhấn **Từ chối đăng ký**.
3. Đăng nhập trưởng nhóm để kiểm tra trạng thái/thông báo.

**Mong đợi:** Đăng ký bị từ chối kèm lý do, nhóm được phép thực hiện bước tiếp theo theo
rule hiện tại; không ảnh hưởng đăng ký khác.

### DEAN-REPORT-01 — Xem và tải báo cáo trong Khoa

**Phụ thuộc:** `STUDENT-REPORT-01`.

1. Từ đăng ký đã chấp nhận, mở **Báo cáo**.
2. Kiểm tra tên file, người nộp, thời gian, version.
3. Nhấn **Tải PDF** và mở file tải về.
4. Thử URL báo cáo ngoài Khoa nếu có dữ liệu đối chiếu.

**Mong đợi:** DEAN đúng Khoa đọc/tải được file hiện hành; ngoài Khoa bị từ chối.

### DEAN-REVIEW-01 — Phân công và thay phản biện TLCN/KLTN

**Phụ thuộc:** `DEAN-REG-01` trên đợt TLCN/KLTN.

1. Vào **Phân công phản biện**.
2. Chọn đăng ký đã duyệt.
3. Chọn Giảng viên cùng Khoa, không phải GVHD của đề tài.
4. Nhấn **Phân công**.
5. Kiểm tra thông báo và danh sách **Phản biện của tôi** của Giảng viên.
6. Khi trạng thái còn cho phép, chọn một Giảng viên hợp lệ khác và nhấn **Thay phản biện**.

**Mong đợi:** Chỉ người đủ điều kiện được chọn; nhiệm vụ cũ mất hiệu lực sau thay và nhiệm
vụ mới xuất hiện đúng người. Chọn GVHD, tài khoản khóa hoặc ngoài Khoa bị từ chối.

### DEAN-BOARD-01 — Tạo hội đồng TLCN/KLTN

**Phụ thuộc:** `ADM-ACC-02`, `DEAN-PERIOD-01` với loại TLCN/KLTN.

1. Vào **Hội đồng → Lập hội đồng**.
2. Chọn đợt TLCN/KLTN.
3. Nhập **Tên hội đồng**.
4. Chọn **Chủ tịch**, **Thư ký** và từ một đến ba **Thành viên bổ sung**.
5. Nhấn **Tạo hội đồng nháp**.

**Mong đợi:** Hội đồng DRAFT có 3–5 thành viên duy nhất, đúng một Chủ tịch, một Thư ký
khác Chủ tịch và mọi thành viên thuộc đúng Khoa. Trùng người hoặc chọn GVHD của đề tài dự
kiến gán phải bị từ chối ở bước kiểm tra tương ứng.

### DEAN-BOARD-02 — Sửa thành viên, gán đăng ký và lịch bảo vệ

**Phụ thuộc:** `DEAN-BOARD-01`, `DEAN-REG-01` cho TLCN/KLTN.

1. Mở chi tiết hội đồng nháp.
2. Sửa **Chủ tịch**, **Thư ký**, **Thành viên bổ sung** rồi nhấn **Lưu thành viên**.
3. Trong **Gán đăng ký và lịch bảo vệ**, chọn đăng ký `ACCEPTED`.
4. Nhập **Thời gian** và **Địa điểm**.
5. Nhấn **Lưu phân công**.

**Mong đợi:** Chỉ đăng ký cùng đợt/Khoa và đúng loại được gán; lịch/địa điểm hiển thị trong
chi tiết hội đồng. Đăng ký COURSE/NCKH hoặc ngoài phạm vi bị từ chối.

### DEAN-BOARD-03 — Chuyển trạng thái và kích hoạt hội đồng

**Phụ thuộc:** `DEAN-BOARD-02` và các điều kiện báo cáo/phản biện theo màn hình.

1. Mở chi tiết hội đồng.
2. Thực hiện lần lượt nút hành động tiếp theo: gán/lập lịch/kích hoạt khi xuất hiện.
3. Đăng nhập từng thành viên hội đồng và mở **Chấm điểm**.

**Mong đợi:** Hội đồng chỉ chuyển trạng thái khi đủ thành viên, đăng ký, lịch và điều kiện
nghiệp vụ. Khi ACTIVE, nhiệm vụ chấm xuất hiện cho đúng thành viên và báo cáo bị khóa thay
thế theo rule hiện tại.

### DEAN-DIRECT-01 — Phân công chấm độc lập COURSE/NCKH

**Phụ thuộc:** `DEAN-REG-01` và `STUDENT-REPORT-01` trên đợt COURSE/NCKH.

1. Vào **Phân công chấm**.
2. Trong **Đề tài sẵn sàng phân công**, chọn đăng ký.
3. Chọn Giảng viên chấm cùng Khoa, không phải tác giả/GVHD.
4. Nhấn **Phân công**.
5. Đăng nhập Giảng viên và kiểm tra **Chấm điểm**.

**Mong đợi:** Tạo đúng một lượt chấm độc lập; người được chọn nhận nhiệm vụ/thông báo. TLCN,
KLTN, người ngoài Khoa hoặc GVHD bị từ chối.

### DEAN-RESULT-01 — Hoàn tất và công bố chấm độc lập

**Phụ thuộc:** `LECTURER-SCORE-01` cho lượt DIRECT đã submit đủ điểm.

1. Vào **Phân công chấm** và mở danh sách lượt đã phân công.
2. Nhấn **Hoàn tất** khi nút được bật.
3. Kiểm tra kết quả READY.
4. Nhấn **Công bố**.
5. Đăng nhập từng Sinh viên trong snapshot để kiểm tra kết quả.

**Mong đợi:** Không thể hoàn tất khi thiếu điểm; sau công bố, mỗi Sinh viên nhận thông báo
và chỉ xem được kết quả của mình.

### DEAN-RESULT-02 — Xem, lọc và công bố kết quả hội đồng

**Phụ thuộc:** `LECTURER-SCORE-02` và `LECTURER-CHAIR-01`.

1. Vào **Kết quả hội đồng**.
2. Lọc theo **Đợt đăng ký**, **Loại đề tài**, **Đề tài hoặc mã nhóm**,
   **Người chấm/phản biện**, **Trạng thái**, **Sắp xếp** và kích thước trang.
3. Nhấn **Xem căn cứ** cho từng Sinh viên.
4. Mở hội đồng COMPLETED và thực hiện **Công bố**.
5. Lọc trạng thái PUBLISHED và đối chiếu lại.

**Mong đợi:** Chỉ kết quả trong Khoa xuất hiện; trang chi tiết hiển thị điểm tổng hợp và
căn cứ từng nguồn; publish phát thông báo cho đúng snapshot Sinh viên.

## 7. Vai trò LECTURER

### LECTURER-TOPIC-01 — Tạo và sửa bản nháp đề tài

**Phụ thuộc:** `DEAN-PERIOD-01`; cửa sổ Giảng viên đang mở và cùng Bộ môn.

1. Đăng nhập Giảng viên không kiêm Trưởng khoa.
2. Vào **Đề tài**, chọn đợt phù hợp rồi nhấn tạo đề tài.
3. Nhập **Tên đề tài**, **Mô tả**, **Mục tiêu**, **Yêu cầu sinh viên**,
   **Kết quả dự kiến**.
4. Nếu cần, chọn tối đa một **Giảng viên hướng dẫn phối hợp** cùng Bộ môn.
5. Nhấn **Lưu bản nháp**.
6. Mở lại, nhấn **Sửa bản nháp**, thay đổi nội dung và lưu.

**Mong đợi:** Bản nháp thuộc đúng tác giả, đợt, Khoa/Bộ môn; chỉ tác giả hợp lệ sửa được.
Tên quá 500 ký tự, chọn hơn một GVHD phối hợp hoặc chọn khác Bộ môn bị từ chối.

### LECTURER-TOPIC-02 — Gửi đề tài để duyệt

**Phụ thuộc:** `LECTURER-TOPIC-01`.

1. Mở chi tiết đề tài nháp.
2. Kiểm tra đầy đủ nội dung bắt buộc.
3. Nhấn **Gửi duyệt**.
4. Đăng nhập DEAN và mở hàng đợi duyệt.

**Mong đợi:** Đề tài chuyển sang trạng thái chờ duyệt và xuất hiện cho DEAN đúng Khoa;
không thể gửi ngoài cửa sổ Giảng viên.

### LECTURER-TOPIC-03 — Trả đề tài về nháp

**Phụ thuộc:** Đề tài đã gửi nhưng trạng thái hiện tại còn cho phép trả về.

1. Mở chi tiết đề tài.
2. Nhấn **Trả về bản nháp để sửa**.
3. Sửa nội dung rồi lưu hoặc gửi lại.

**Mong đợi:** Đề tài trở về DRAFT mà không tạo bản sao; đề tài đã ở trạng thái không cho
phép chỉnh sửa bị từ chối.

### LECTURER-REPORT-01 — Xem báo cáo với tư cách GVHD/người được phân công

**Phụ thuộc:** `STUDENT-REPORT-01`.

1. Từ đề tài/nhiệm vụ liên quan, mở **Xem báo cáo**.
2. Nhấn **Tải PDF**.
3. Thử URL báo cáo của đề tài không hướng dẫn và không được phân công.

**Mong đợi:** GVHD, phản biện hoặc thành viên hội đồng hợp lệ xem/tải được báo cáo; Giảng
viên không liên quan bị từ chối.

### LECTURER-REVIEW-01 — Nộp phản biện TLCN/KLTN

**Phụ thuộc:** `DEAN-REVIEW-01` và báo cáo hiện hành đã tồn tại.

1. Vào **Phản biện của tôi**.
2. Mở nhiệm vụ được phân công.
3. Chọn **Báo cáo phản biện PDF** tối đa 20 MB.
4. Với từng Sinh viên snapshot, nhập **Điểm** từ 0 đến 10 theo bước 0,25 và **Nhận xét**.
5. Nhấn **Nộp phản biện** và xác nhận.

**Mong đợi:** PDF và điểm của tất cả Sinh viên được lưu cùng lúc; trạng thái chuyển
SUBMITTED và không thể sửa như bản nháp. Thiếu file, nhận xét hoặc điểm sai bước bị từ chối.

### LECTURER-SCORE-01 — Chấm độc lập COURSE/NCKH

**Phụ thuộc:** `DEAN-DIRECT-01`.

1. Vào **Chấm điểm** và mở nhiệm vụ DIRECT.
2. Với từng Sinh viên, nhập điểm 0–10 theo bước 0,25 và nhận xét.
3. Nhấn **Lưu nháp**.
4. Mở lại để xác nhận dữ liệu nháp.
5. Nhấn **Nộp điểm** và xác nhận.

**Mong đợi:** Chỉ người được phân công sửa được phiếu; nháp được lưu; submit đủ toàn nhóm
và khóa thay đổi theo lifecycle hiện tại.

### LECTURER-SCORE-02 — Thành viên hội đồng chấm TLCN/KLTN

**Phụ thuộc:** `DEAN-BOARD-03` với hội đồng ACTIVE.

1. Đăng nhập lần lượt từng thành viên hội đồng.
2. Vào **Chấm điểm**, mở đúng đề tài được phân công.
3. Xem báo cáo và nhập điểm/nhận xét cho từng Sinh viên.
4. Lưu nháp, mở lại và nộp điểm.
5. Thử mở đề tài của hội đồng mà tài khoản không phải thành viên.

**Mong đợi:** Mỗi thành viên chỉ quản lý evaluation của chính mình; người ngoài hội đồng
hoặc GVHD bị cấm chấm; trạng thái tiến độ trên chi tiết hội đồng cập nhật đúng.

### LECTURER-CHAIR-01 — Chủ tịch hoàn tất hội đồng

**Phụ thuộc:** Mọi thành viên đã hoàn thành `LECTURER-SCORE-02`.

1. Đăng nhập tài khoản Chủ tịch.
2. Mở hội đồng hoặc danh sách chấm điểm.
3. Kiểm tra mọi phiếu đều SUBMITTED.
4. Nhấn **Hoàn tất hội đồng**.
5. Đăng nhập một thành viên thường và thử thao tác tương tự nếu nút/URL có thể truy cập.

**Mong đợi:** Chỉ Chủ tịch hoàn tất được khi đủ phiếu; hệ thống tạo điểm tổng hợp theo từng
Sinh viên và chuyển hội đồng sang COMPLETED. Thiếu phiếu hoặc sai vai trò bị từ chối.

### LECTURER-AUTH-01 — Giới hạn phạm vi Giảng viên

1. Thử tạo đề tài ở đợt khác Bộ môn.
2. Thử chọn GVHD phối hợp khác Bộ môn.
3. Thử mở nhiệm vụ phản biện/chấm điểm không được phân công.
4. Thử chấm đề tài mà tài khoản là tác giả/GVHD.

**Mong đợi:** Tất cả thao tác ngoài phạm vi bị từ chối và không tạo dữ liệu một phần.

## 8. Vai trò STUDENT

### STUDENT-GROUP-01 — Tạo nhóm

**Phụ thuộc:** `DEAN-PERIOD-01`; cửa sổ Sinh viên đang mở.

1. Đăng nhập STUDENT-A1.
2. Vào **Nhóm sinh viên**.
3. Chọn thẻ đợt đang đăng ký và nhấn tạo nhóm.
4. Xác nhận trang chi tiết hiển thị STUDENT-A1 là **Trưởng nhóm** ở vị trí 1.

**Mong đợi:** Tạo một nhóm trong đúng đợt; Sinh viên không thể tạo/tham gia nhóm thứ hai
trong cùng phạm vi bị ràng buộc.

### STUDENT-GROUP-02 — Mời, chấp nhận và từ chối thành viên

**Phụ thuộc:** `STUDENT-GROUP-01`.

1. STUDENT-A1 chọn **Mời thành viên**, chọn STUDENT-A2 và gửi lời mời.
2. Đăng nhập STUDENT-A2, vào **Lời mời của tôi**, nhấn **Chấp nhận**.
3. Xác nhận STUDENT-A2 xuất hiện ở vị trí 2.
4. STUDENT-A1 mời STUDENT-A4.
5. STUDENT-A4 mở lời mời và nhấn **Từ chối**.

**Mong đợi:** Người được mời nhận thông báo; chỉ sau chấp nhận mới trở thành thành viên;
từ chối giải phóng vị trí và không thêm membership.

### STUDENT-GROUP-03 — Hủy lời mời và giới hạn ba vị trí

1. STUDENT-A1 gửi một lời mời đang chờ.
2. Trong **Lời mời đã gửi**, nhấn **Hủy lời mời**.
3. Mời và cho STUDENT-A3 chấp nhận để nhóm có đúng ba thành viên.
4. Thử mời người thứ tư.

**Mong đợi:** Lời mời bị hủy không thể chấp nhận; tổng thành viên và lời mời giữ chỗ không
vượt ba. Nhóm phải có đúng ba thành viên chính thức mới được đăng ký đề tài.

### STUDENT-GROUP-04 — Chuyển trưởng nhóm và xóa thành viên

**Điều kiện:** Nhóm chưa có đăng ký đang xử lý và cửa sổ Sinh viên còn mở.

1. Trưởng nhóm nhấn **Chuyển trưởng nhóm** cho STUDENT-A2.
2. Xác nhận STUDENT-A2 trở thành Trưởng nhóm và STUDENT-A1 thành thành viên.
3. STUDENT-A2 nhấn **Xóa khỏi nhóm** với một thành viên thường.

**Mong đợi:** Tại mọi thời điểm có đúng một Trưởng nhóm; chỉ Trưởng nhóm quản lý thành viên;
member bị xóa có thể tham gia nhóm khác khi rule cho phép.

### STUDENT-GROUP-05 — Rời nhóm và giải tán nhóm

1. Với tài khoản thành viên thường, nhấn **Rời nhóm**.
2. Tạo một nhóm thử nghiệm chỉ có Trưởng nhóm và nhấn **Giải tán nhóm**.
3. Nếu Trưởng nhóm của nhóm nhiều người muốn rời, kiểm tra hướng dẫn chuyển quyền trước.

**Mong đợi:** Thành viên thường rời được khi chưa khóa; nhóm một người được giải tán và mọi
lời mời chờ bị hủy; Trưởng nhóm không rời nhóm nhiều người khi chưa chuyển quyền.

### STUDENT-REG-01 — Đăng ký đề tài

**Phụ thuộc:** `DEAN-TOPIC-02` và nhóm có đúng ba thành viên từ `STUDENT-GROUP-03`.

1. Đăng nhập Trưởng nhóm.
2. Mở chi tiết nhóm và nhấn **Đăng ký đề tài**.
3. Chọn một đề tài đã công bố.
4. Nhấn nút xác nhận đăng ký.
5. Mở **Xem đăng ký đề tài**.

**Mong đợi:** Tạo một đăng ký PENDING với snapshot đúng ba thành viên. Thành viên thường,
nhóm thiếu thành viên, còn lời mời chờ hoặc đề tài chưa công bố đều không đăng ký được.

### STUDENT-REG-02 — Rút đăng ký

**Điều kiện:** Đăng ký còn ở trạng thái cho phép rút.

1. Trưởng nhóm mở chi tiết đăng ký.
2. Nhấn **Rút đăng ký** và xác nhận.
3. Mở lại trang nhóm.

**Mong đợi:** Đăng ký chuyển trạng thái rút; snapshot/lịch sử được giữ; nhóm chỉ được thực
hiện đăng ký tiếp theo theo đúng rule hiện tại. Thành viên thường không rút được.

### STUDENT-REPORT-01 — Nộp và thay báo cáo PDF

**Phụ thuộc:** `DEAN-REG-01` đã chấp nhận đăng ký.

1. Đăng nhập Trưởng nhóm trong snapshot.
2. Mở chi tiết đăng ký và chọn **Báo cáo**.
3. Chọn một file PDF hợp lệ, tối đa 20 MB.
4. Nhấn **Nộp báo cáo**.
5. Tải PDF để đối chiếu.
6. Trước khi hội đồng ACTIVE, chọn PDF khác và nhấn **Thay báo cáo**.

**Mong đợi:** Chỉ PDF hợp lệ được lưu; metadata/version cập nhật; file tải về là bản hiện
hành. Thành viên thường không upload; sau khi hội đồng/lượt chấm khóa báo cáo, không thể thay.

### STUDENT-RESULT-01 — Xem kết quả đã công bố

**Phụ thuộc:** `DEAN-RESULT-01` hoặc `DEAN-RESULT-02`.

1. Đăng nhập từng Sinh viên trong snapshot.
2. Vào **Kết quả của tôi**.
3. Mở **Xem chi tiết**.
4. Đối chiếu đề tài, nhóm, điểm tổng hợp và nhận xét/căn cứ được phép hiển thị.
5. Đăng nhập STUDENT-A4 không thuộc snapshot và thử URL chi tiết.

**Mong đợi:** Chỉ kết quả PUBLISHED của chính snapshot xuất hiện; mỗi Sinh viên xem đúng
điểm của mình; người ngoài nhóm và kết quả chưa công bố không bị lộ.

### STUDENT-AUTH-01 — Giới hạn quyền Sinh viên

1. Thử quản lý nhóm khi không phải Trưởng nhóm.
2. Thử đăng ký hoặc rút thay nhóm khác.
3. Thử upload báo cáo khi không phải Trưởng nhóm.
4. Thử xem kết quả/báo cáo không thuộc nhóm.

**Mong đợi:** Backend từ chối mọi thao tác ngoài ownership/snapshot và dữ liệu không đổi.

## 9. Chuỗi kiểm thử liên vai trò hoàn chỉnh

### FLOW-COURSE-01 — COURSE/NCKH chấm độc lập

Thực hiện theo thứ tự:

1. `ADM-DEPT-01` — tạo Bộ môn.
2. `ADM-ACC-01` đến `ADM-ACC-03` — tạo tài khoản.
3. `DEAN-PERIOD-01` — tạo đợt COURSE hoặc NCKH.
4. `LECTURER-TOPIC-01` và `LECTURER-TOPIC-02` — tạo/gửi đề tài.
5. `DEAN-TOPIC-01` và `DEAN-TOPIC-02` — duyệt/công bố.
6. `STUDENT-GROUP-01` đến `STUDENT-GROUP-03` — lập đủ nhóm.
7. `STUDENT-REG-01` — đăng ký.
8. `DEAN-REG-01` — chấp nhận.
9. `STUDENT-REPORT-01` — nộp báo cáo.
10. `DEAN-DIRECT-01` — phân công người chấm.
11. `LECTURER-SCORE-01` — nộp điểm.
12. `DEAN-RESULT-01` — hoàn tất/công bố.
13. `STUDENT-RESULT-01` — xem kết quả.
14. `COMMON-03` và `COMMON-04` — đối chiếu thông báo ở từng mốc.

### FLOW-THESIS-01 — TLCN/KLTN phản biện và hội đồng

Thực hiện theo thứ tự:

1. Hoàn thành dữ liệu ADMIN như FLOW-COURSE-01.
2. `DEAN-PERIOD-01` — tạo đợt TLCN/KLTN.
3. `LECTURER-TOPIC-01` và `LECTURER-TOPIC-02`.
4. `DEAN-TOPIC-01` và `DEAN-TOPIC-02`.
5. `STUDENT-GROUP-01` đến `STUDENT-GROUP-03`.
6. `STUDENT-REG-01` và `DEAN-REG-01`.
7. `STUDENT-REPORT-01`.
8. `DEAN-REVIEW-01` và `LECTURER-REVIEW-01`.
9. `DEAN-BOARD-01`, `DEAN-BOARD-02`, `DEAN-BOARD-03`.
10. `LECTURER-SCORE-02` cho mọi thành viên.
11. `LECTURER-CHAIR-01`.
12. `DEAN-RESULT-02`.
13. `STUDENT-RESULT-01`.
14. `COMMON-03` và `COMMON-04` ở mọi tài khoản nhận sự kiện.

## 10. Ma trận thông báo cần đối chiếu

| Sự kiện | Người thực hiện | Người nhận chính cần kiểm tra |
|---|---|---|
| Công bố thông báo cấp trường | ADMIN | Mọi account active trừ publisher |
| Công bố thông báo cấp Khoa | DEAN | Account active cùng Khoa trừ publisher |
| Mời vào nhóm | Trưởng nhóm | Sinh viên được mời |
| Duyệt/từ chối đề tài | DEAN | Tác giả/GVHD liên quan |
| Chấp nhận/từ chối đăng ký | DEAN | Snapshot nhóm và người liên quan theo policy |
| Phân công phản biện | DEAN | Giảng viên phản biện |
| Phân công hội đồng/chấm | DEAN | Giảng viên được phân công |
| Công bố kết quả | DEAN | Sinh viên thuộc snapshot đăng ký |

Với mỗi sự kiện:

1. Kiểm tra số badge chưa đọc trước thao tác.
2. Thực hiện thao tác nguồn đúng một lần.
3. Đăng nhập người nhận, kiểm tra chỉ sinh đúng một thông báo mong đợi.
4. Mở thông báo và kiểm tra đích điều hướng.
5. Đăng nhập tài khoản không thuộc phạm vi để xác nhận không nhận.

## 11. Checklist kết thúc phiên kiểm thử

- [ ] Đã ghi database và thời gian chạy.
- [ ] Đã kiểm tra đủ bốn vai trò.
- [ ] Đã kiểm tra các chức năng dùng chung.
- [ ] Đã chạy ít nhất một luồng COURSE/NCKH hoặc ghi rõ BLOCKED.
- [ ] Đã chạy ít nhất một luồng TLCN/KLTN hoặc ghi rõ BLOCKED.
- [ ] Đã kiểm tra quyền âm và dữ liệu không thay đổi khi bị từ chối.
- [ ] Đã đối chiếu notification recipient theo phạm vi.
- [ ] Đã lưu bằng chứng cho test FAIL/BLOCKED.
- [ ] Không thao tác destructive trên database chia sẻ/production.
- [ ] Không ghi mật khẩu thật hoặc file PDF nhạy cảm vào repository.
