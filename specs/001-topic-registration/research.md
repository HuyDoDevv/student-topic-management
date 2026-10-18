# Research: Quản lý đợt, đề tài và đăng ký nhóm

**Trạng thái:** nền tảng đã xác nhận; nghiên cứu kỹ thuật có kết luận, nhưng Phase 0
chưa đạt gate do các Q nghiệp vụ trong [plan](plan.md#các-quyết-định-còn-chặn).
Ngày đối chiếu nguồn: 2026-09-08. Không có thử nghiệm ứng dụng ở bước này.

## R-01 — Nền tảng

- **Decision:** Java 21 (bắt buộc), Spring Boot **4.1.1** (lựa chọn kỹ thuật theo yêu cầu
  kiểm tra bản ổn định), chạy JVM local. Không dùng milestone/snapshot.
- **Rationale:** trang chính thức liệt kê 4.1.1 là Stable và Java 17–26 được hỗ trợ,
  bao gồm Java 21. Dùng dependency management của Boot, tránh ghép tùy ý Spring Framework.
- **Alternatives considered:** nhánh Boot 3.5/4.0 cũng được liệt kê nhưng không có ràng
  buộc tương thích dự án cũ buộc chọn chúng; native image không cần cho đồ án local.
- **Source:** [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html).
  Phiên bản được cố định trong plan; không tự thay theo trang “current” khi triển khai.

## R-02 — Render và kiến trúc

- **Decision:** người dùng đã đồng ý Spring MVC + Thymeleaf với Tabler. Một JAR phục vụ
  HTML và assets; form POST cho mutation, redirect sau thành công. JavaScript bổ sung
  tương tác menu/modal/validation trải nghiệm; mọi quyết định nghiệp vụ ở backend.
- **Rationale:** cấu trúc biểu mẫu/danh sách phù hợp render server, dễ demo một tiến trình.
  Boot hỗ trợ Thymeleaf và static resources. Session cookie cùng origin phù hợp luồng này.
- **Alternatives considered:** SPA riêng đòi thêm frontend build và API/auth contract;
  JSP không đem lại lợi ích cho executable JAR này.
- **Source:** [Servlet web applications](https://docs.spring.io/spring-boot/reference/web/servlet.html).

## R-03 — Tabler 1.4.0 và tài nguyên

- **Decision:** tái sử dụng bản local người dùng cung cấp; đã đọc core/package.json
  (version 1.4.0), LICENSE (MIT), kiểm tra core/dist/css, core/dist/js và preview.
  Đường dẫn nguồn chỉ dùng lúc lấy tài nguyên:
  `/Users/huydo/Workspace/hcmute/HK1(26-27)/lap-trinh-web/tabler--tabler-core-1.4.0/`.
- **Rationale:** đã có CSS/JS build sẵn; không cần Node để chạy ứng dụng sau đóng gói.
  Lấy layout/component HTML từ preview, chuyển thành fragments Thymeleaf và thay nội dung
  theo US1–5; không đưa dashboard/demo nghiệp vụ không liên quan vào ứng dụng.
- **Alternatives considered:** CDN hoặc liên kết ngoài repository không đáp ứng portability;
  sao chép toàn bộ demo/node_modules không cần thiết.

Kế hoạch sao chép ở bước triển khai:

| Nguồn tương đối trong Tabler | Đích trong dự án | Cách dùng |
|---|---|---|
| core/dist/css/tabler.min.css | src/main/resources/static/vendor/tabler/1.4.0/css/ | CSS gốc |
| core/dist/js/tabler.min.js | src/main/resources/static/vendor/tabler/1.4.0/js/ | menu, dropdown, modal; kiểm tra bundle để tránh nạp Bootstrap hai lần |
| preview/dist và preview/pages | src/main/resources/templates/fragments và các trang nghiệp vụ | Tham khảo/chuyển HTML, không copy nguyên site |
| LICENSE | licenses/tabler-1.4.0-LICENSE | Giữ nguyên copyright và điều khoản |
| Các ảnh/font/icon/plugin thực sự được tham chiếu | static/vendor/... và licenses/ tương ứng | Liệt kê từng file, nguồn, version, license trong THIRD_PARTY_NOTICES.md |

Giữ banner bản quyền trong CSS/JS, kể cả khi năm banner khác LICENSE.
Kiểm tra mọi URL CSS, source map, font và import JS; đưa tài nguyên cần thiết vào JAR.
Không đưa font CDN vào runtime; nếu không cần font riêng dùng system font bằng app.css.
Chỉ đưa plugin khi component yêu cầu, kiểm tra license riêng trước khi chọn.
Ghi checksum tài nguyên thực sự copy và thay đổi template trong manifest notices.
Kiểm thử chạy JAR từ thư mục khác, không truy cập thư mục nguồn Tabler, assets trả 200;
kiểm tra network browser không có request bắt buộc ra CDN. Bước này chưa copy tài nguyên.

## R-04 — Database và đồng thời

- **Decision:** MySQL 8.4 LTS được người dùng chọn ngày 2026-09-09. DBeaver là SQL client,
  không phải DB server. READ COMMITTED với khóa FOR UPDATE hàng đợt trước mỗi mutation;
  generated-column unique key là lớp bảo vệ thêm. Xem [data-model](data-model.md).
- **Rationale:** MySQL không có partial unique index, nên generated columns nullable
  biểu đạt “một đăng ký có hiệu lực/nhóm” và “một nhóm được chấp nhận/đề tài/đợt”. Khóa đợt tránh vòng chờ giữa nhóm/đề tài
  khi tự từ chối nhiều đăng ký, đổi lại giảm song song cùng đợt.
- **Alternatives considered:** chỉ kiểm tra rồi insert không an toàn; chỉ khóa đăng ký
  không bảo vệ đăng ký mới chưa tồn tại; khóa nhỏ hơn phức tạp hơn mức cần thiết hiện tại.
  Không mặc định kết quả ưu tiên rút/accept từ thứ tự lấy khóa.
- **Sources:** [MySQL CHECK constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html),
  [CREATE TABLE và foreign keys](https://dev.mysql.com/doc/refman/8.4/en/create-table.html),
  [Connector/J datetime](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html).

## R-05 — Migration và dữ liệu mẫu

- **Decision:** dùng SQL V1, V2… import chủ động bằng DBeaver; `ddl-auto=validate`,
  không dùng Hibernate update hoặc `schema.sql` song song. Seed demo tách khỏi schema.
- **Rationale:** giữ cách chạy local đơn giản mà người dùng yêu cầu và không để ORM tự đổi bảng.
- **Alternatives considered:** Liquibase có thể đáp ứng nhưng changelog đa định dạng
  chưa cần; tạo schema bằng ORM không đáp ứng nguyên tắc migrations có phiên bản.
- **Source:** [Database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## R-06 — Xác thực, kiểm thử và secrets

- **Decision:** Spring Security form login/session; hash mật khẩu bằng PasswordEncoder,
  giữ CSRF cho form mutation; kiểm tra policy tại service trên dữ liệu tin cậy.
  Không nhận roles/faculty từ form để cấp quyền; tài khoản học vụ được gán khoa CNTT mặc định ở backend.
- **Rationale:** phù hợp ứng dụng HTML cùng origin; không cần token API tự thiết kế.
  Test thuần nghiệp vụ, MockMvc, DB MySQL riêng và browser Chromium.
- **Alternatives considered:** JWT/SSO không cần cho tài khoản nội bộ cấp sẵn đã chốt;
  H2 không đại diện generated columns, trigger và hành vi khóa của MySQL.
- **Sources:** [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html),
  [Password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
- Cấu hình DB/password từ biến môi trường; cookie HttpOnly, SameSite=Lax, session đổi ID
  sau login. Local HTTP bind 127.0.0.1; Secure cookie chỉ khi thực sự dùng HTTPS.
  Không log mật khẩu/session/CSRF token; thông báo login sai không phân biệt tài khoản tồn tại.
  Các thiết lập này là thiết kế bảo mật theo constitution, không đóng Q-20.

## R-07 — Các quyết định cần xác nhận khách hàng

Q-09 đã được khách hàng chốt ngày 2026-09-11: reason thủ công bắt buộc; giao dịch lấy khóa
period trước thắng khi rút/chấp nhận đồng thời; đăng ký lại tạo hàng mới và giữ lịch sử
không thời hạn. Q-07 đã được khách hàng chốt lại ngày 2026-09-11 thành luồng lời mời có xác nhận,
giữ chỗ và tự rời/giải tán; thiết kế tương ứng nằm trong data-model. Q-05/Q-06 đã chốt mô hình topic theo đợt, nguồn danh mục
seed/import và khóa GVHD từ PENDING_APPROVAL; không cần nghiên cứu phương án catalog/reuse
hoặc thay GVHD trong quá trình thực hiện cho phiên bản đầu. T080 chỉ bổ sung cấp tài khoản
mới theo ba loại cố định; không bổ sung API sửa/xóa/chuyển cơ cấu tài khoản, quản trị catalog
hoặc chức năng chuyển giảng viên trong phiên bản đầu.

Đã có rà soát nghiên cứu độc lập về constraints/khóa; kết luận được tích hợp vào data-model.
Không có extension hooks before_plan/after_plan trong workspace.
