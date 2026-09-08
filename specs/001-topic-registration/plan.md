> Cập nhật theo yêu cầu người dùng tạo DB toàn hệ thống và đơn giản hóa chạy local:
> SQL hiện tại là `database/V1__full_system.sql` (20 bảng MySQL), import thủ công.
> Đã bỏ Flyway, scripts Python và cache .tools; chạy bằng ./run.sh trên macOS hoặc run.cmd trên Windows.
> Quyết định công nghệ ngày 2026-09-09: MySQL 8.4 LTS thay PostgreSQL cho code,
> kiểm thử DB và hướng dẫn DBeaver. Dùng `database/V1__full_system.sql` bản MySQL.
> Nội dung bên dưới lưu lại kế hoạch/báo cáo feature 001 trước thay đổi này;
> các lệnh Flyway/scripts cũ không còn là hướng dẫn chạy hiện tại. Xem README.md gốc.

# Implementation Plan: Quản lý đợt, đề tài và đăng ký nhóm

**Feature identifier**: `001-topic-registration` (kết quả setup-plan; không tạo nhánh Git).
**Date**: 2026-09-08 | **Spec**: [spec.md](spec.md)
**Status**: Thiết kế có điều kiện — nền tảng đã chốt, chưa đạt gate nghiệp vụ để triển khai toàn bộ.

**Implementation update 2026-09-09:** đã có khung ứng dụng/local UI và SQL MySQL V1,
chưa có bảng/tài khoản nghiệp vụ. Profile preview được thêm để xem UI mà không cần DB;
local vẫn yêu cầu kết nối MySQL và không tự fallback. Xem [foundation-report.md](foundation-report.md).

## Summary

Triển khai FR-01–15 bằng một ứng dụng Java 21, Spring Boot 4.1.1, Spring MVC,
Thymeleaf và MySQL, chỉ chạy local. Tái sử dụng Tabler 1.4.0 từ bản được người dùng
cung cấp; đóng gói tài nguyên cùng ứng dụng. DBeaver là công cụ quản lý/kiểm tra DB.

Quyết định người dùng: Java 21 và Tabler bắt buộc; đã đồng ý Thymeleaf + MySQL
và xác nhận chỉ local trong cuộc hội thoại lập kế hoạch. Spring Boot 4.1.1 được chọn
qua tài liệu chính thức; xem [research.md](research.md). Những lựa chọn kỹ thuật
còn lại trong tài liệu là thiết kế đề xuất, không phải yêu cầu mới của khách hàng.

Không mở rộng sang nộp báo cáo, phản biện/hội đồng, chấm điểm, kết quả, thông báo hoặc
toàn bộ quản trị tài khoản. REQ-06–07 chỉ cấu hình ngày cho giai đoạn sau.
Giữ nguyên spec, bản phân tích, constitution và PDF.

## Technical Context

| Thuộc tính | Quyết định |
|---|---|
| Language/Version | Java 21 |
| Primary Dependencies | Spring Boot **4.1.1**; Spring MVC, Thymeleaf, Spring Security, Bean Validation, Spring Data JPA; phiên bản phụ thuộc do BOM Boot quản lý |
| Storage | MySQL **8.4 LTS**, InnoDB và utf8mb4 cho local/integration test |
| Migration | SQL có phiên bản import bằng DBeaver; Hibernate chỉ validate schema |
| Build | Maven Wrapper 3.9.x, khóa patch trong wrapper khi triển khai |
| Testing | JUnit Jupiter/Spring Boot Test theo BOM, MockMvc + Spring Security Test; MySQL thật cho integration/concurrency; Playwright Java cho browser E2E |
| Target Platform | JVM trên máy local; MySQL local; trình duyệt Chromium cho nghiệm thu đề xuất |
| Project Type | Monolith web render HTML phía server; một executable JAR |
| Performance Goals | Q-20 chưa có ngưỡng được xác nhận; không tự đặt SLA hoặc tuyên bố đạt tải |
| Constraints | Backend auth/validation; giao dịch nhất quán; không secrets trong repository; không runtime CDN/đường dẫn Tabler tuyệt đối |
| Scale/Scope | 5 user stories, FR-01–15; số người dùng/dữ liệu chưa xác nhận; khóa theo đợt ưu tiên đơn giản và đúng dữ liệu |

Không yêu cầu Docker để chạy local. Integration test dùng database riêng được cấu hình
qua biến môi trường. Có thể bổ sung Testcontainers sau nếu môi trường có Docker;
không dùng H2 thay MySQL cho kiểm thử ràng buộc và đồng thời.

## Constitution Check

| Nguyên tắc | Trước nghiên cứu | Sau thiết kế / bằng chứng |
|---|---|---|
| I. Nguồn nghiệp vụ | BLOCKED một phần do Q | Giữ gate bên dưới; thiết kế không xác nhận các Q |
| II. Truy vết | PASS về phương pháp | Bảng FR/REQ/AT và contracts giữ ID |
| III. Backend quyền | PASS về thiết kế | Ma trận quyền và policy scope chưa chốt được đánh dấu, không mặc định cấp quyền |
| IV. Boundary validation | PASS về thiết kế | DTO whitelist, kiểm tra service, lỗi không ghi dở dang |
| V. Tách nghiệp vụ | PASS về thiết kế | web → application/domain → persistence; Thymeleaf chỉ trình bày |
| VI. Đồng thời | PASS về thiết kế, chưa kiểm thử | Khóa đợt + unique/FK/check; ma trận tranh chấp |
| VII. Migration | PASS về thiết kế | SQL V1, V2… quản lý thay đổi; Hibernate validate khi boot |
| VIII. Tests | PASS về kế hoạch, chưa chạy | Unit, HTTP, DB, concurrency, browser |
| IX. Secrets | PASS về thiết kế | Biến môi trường; mật khẩu mẫu do người chạy cung cấp |
| X. Nghiệm thu | BLOCKED | AS/SC còn đề xuất, phần Q chưa có kết quả kỳ vọng cuối cùng |

**Gate tổng: ERROR / chưa đạt**, không phải miễn trừ constitution. Theo yêu cầu tiếp tục
lập kế hoạch, các tài liệu Phase 1 được cung cấp dưới dạng **draft có điều kiện**.
Không coi Phase 0 đã giải quyết mọi clarification, không dùng bộ tài liệu này làm
lệnh triển khai đầy đủ. Chỉ công việc độc lập với Q mới có thể được phân rã sau này.

### Các quyết định còn chặn

| ID | Chưa chốt | Phần bị chặn |
|---|---|---|
| Q-02 | Ngày có điều kiện bắt buộc không; đổi loại xử lý ngày cũ; sửa tên/ngày giai đoạn sau | Schema nullability/validation và form đợt hoàn chỉnh |
| Q-05 | Tái sử dụng đề tài, nguồn/người quản lý danh mục | Thiết kế catalog/reuse, cấp dữ liệu thật |
| Q-06 | Đổi GVHD bởi vai trò khác hoặc sau công bố | Không thêm endpoint đổi phân công; mô hình hiện tại chỉ nháp |
| Q-07 | Thời điểm tạo nhóm; tự rời/chuyển nhóm | Create-group và luồng rời nhóm chưa đủ điều kiện cuối cùng |
| Q-09 | Lý do từ chối thủ công bắt buộc; ưu tiên rút/chấp nhận; lưu lịch sử | Validation rejection, kỳ vọng tranh chấp, retention |
| Q-18 | Cấp tài khoản/role/bộ môn; tổ hợp role; phạm vi trưởng khoa/SV; GV đổi bộ môn | Policy dữ liệu, provisioning thật, schema tổ chức cuối cùng |
| Q-20 | Chất lượng, quy mô, bảo mật định lượng | Nghiệm thu phi chức năng; không ngăn ghi kế hoạch kiểm tra correctness |
| AC/SC | Tiêu chí suy luận chưa được xác nhận toàn bộ | Không tuyên bố tính năng hoàn thành |

Việc người dùng bỏ qua clarify không xác nhận bất kỳ phương án nghiệp vụ nào.
Hạn xử lý duyệt/công bố và quyết định của trưởng khoa chưa được quy định riêng:
không áp hạn GV/SV cho các thao tác này một cách suy diễn; cần rà soát khi chốt AC.

## Project Structure

### Documentation (this feature)

- [research.md](research.md): quyết định nền tảng, nguồn và điểm chưa giải quyết.
- [data-model.md](data-model.md): thực thể, trạng thái, ràng buộc và giao dịch.
- [contracts/web.md](contracts/web.md): giao diện HTTP/HTML, ma trận quyền và lỗi.
- [quickstart.md](quickstart.md): hướng dẫn local dự kiến và nghiệm thu.

### Source Code (repository root)

Cấu trúc dự kiến, **chưa tạo mã nguồn hoặc sao chép assets**:

```text
pom.xml
mvnw / mvnw.cmd / .mvn/wrapper/
src/main/java/.../
  identity/
  periods/
  topics/
  groups/
  registrations/
  shared/
    security/
    time/
  (mỗi module: web/, application/, domain/, persistence/)
src/main/resources/
  templates/
    fragments/
    auth/ periods/ topics/ groups/ registrations/
  static/
    vendor/tabler/1.4.0/
    css/app.css
    js/app.js
  db/migration/
  application.yml
  application-local.yml
src/test/java/.../
  unit/ web/ integration/ concurrency/ e2e/
src/test/resources/
THIRD_PARTY_NOTICES.md
licenses/
```

Một tiến trình, một DB. Controllers nhận DTO, gọi service, chuyển lỗi sang view;
service kiểm tra quyền theo dữ liệu và mở transaction; domain giữ quy tắc thuần;
repository thực hiện truy vấn/khóa. Không dùng entity làm form binding hoặc đặt
logic nghiệp vụ trong template. Tắt Open EntityManager in View, dựng view model
trong service để không tải dữ liệu ngoài kiểm tra quyền.

## Implementation Sequence and Traceability

| Thứ tự | Phần việc dự kiến | FR / REQ | Bằng chứng |
|---|---|---|---|
| 1 | Build, cấu hình local, migration cơ sở, Tabler layout, session login | FR-01; REQ-28,31–32; constitution V,VII,IX | AT-09–10; asset portability |
| 2 | Đợt và thời gian, khóa cấu hình | FR-02–04,15; REQ-01–07,31 | AT-01–02,13; Q-02 gate |
| 3 | Nháp, gửi duyệt, GVHD, duyệt/công bố | FR-05–07,14; REQ-02–03,08–11,15,31 | AT-03,06,12 |
| 4 | Nhóm, thành viên, chuyển trưởng nhóm | FR-08–09; REQ-12–13,31 | AT-04,06–08; Q-07/18 gate |
| 5 | Gửi/rút/đăng ký lại, quyết định và auto-reject | FR-10–13; REQ-03,14–16,31 | AT-05–08,11; Q-09/18 gate |
| 6 | E2E, DB migration/seed, kiểm tra DBeaver, đối chiếu acceptance | FR-01–15; REQ-31–32 | AS toàn bộ, SC-001–008 sau xác nhận |

Đây là trình tự kế hoạch, không phải tasks.md và không cho phép bỏ qua gate ở mỗi phần.

## Verification Strategy

- Unit: đồng hồ có thể thay thế, đầy đủ biên start-inclusive/end-exclusive; nội dung
  gửi duyệt, GVHD, quyền trưởng nhóm, chuyển trạng thái hợp lệ/bị cấm.
- HTTP: MockMvc với session/CSRF; cho phép và từ chối cho từng dòng ma trận;
  giả mạo role/department/author, truy cập ID của nhóm khác, input sai và stale version.
- DB: migration từ DB rỗng và nâng từ phiên bản trước với dữ liệu mẫu; kiểm tra
  unique có điều kiện, FK cùng đợt, leader là thành viên và rollback toàn bộ.
- Đồng thời: nhiều connection/transaction thật, barrier/latch thay sleep tùy ý;
  thêm SV thứ 4, cùng SV vào hai nhóm, hai đăng ký cùng nhóm, hai accept cùng topic,
  accept/gửi mới, chuyển leader/gửi, sửa nhóm/gửi, sửa nháp/gửi duyệt, sửa lịch/mở đợt.
  Rút/accept kiểm tra invariant ngay; xác nhận chính sách chọn kết quả trước khi chốt test.
- Browser: đăng nhập theo vai trò → tạo đợt → GV gửi đề tài → duyệt/công bố → lập nhóm →
  gửi → chấp nhận; thêm nhánh từ chối/rút/đăng ký lại. Dùng clock điều khiển chỉ trong
  test, không thêm endpoint đổi giờ cho ứng dụng.
- Secrets/licensing: rà file được commit; kiểm tra tài nguyên từ JAR với thư mục Tabler
  ngoài dự án không có sẵn, không request CDN/font ngoài; giữ notices.
- Báo cáo test ghi AS/AT/FR/REQ; không đánh dấu ca phụ thuộc Q là passed hoặc tự
  xác nhận SC. Chưa có mã ứng dụng nên chưa chạy các test trên.

## Complexity Tracking

Không xin miễn trừ nguyên tắc. Khóa ghi theo đợt đơn giản hóa correctness nhưng giảm
song song trong cùng đợt; Q-20 chưa có tải mục tiêu. Chỉ tối ưu thành khóa nhỏ hơn khi
có đo đạc và kiểm thử bảo toàn invariants tương đương.
