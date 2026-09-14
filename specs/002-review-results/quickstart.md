# Quickstart Validation: Báo cáo, hội đồng, chấm điểm và kết quả

## Prerequisites

- Java 21 và MySQL 8.4 test/local riêng theo README.
- Feature 001 đã có registration `ACCEPTED` của period `TLCN` hoặc `KLTN` với snapshot 3 SV.
- Ba đến năm active LECTURER cùng department và không ai là supervisor của topic.
- Thư mục report local không nằm trong repository.

```bash
export REPORT_STORAGE_ROOT=/đường/dẫn/tới/thư-mục-test-reports
env -u JAVA_HOME ./mvnw test
```

Ứng dụng nhận giới hạn request từ cấu hình và service vẫn kiểm tra PDF <= 20 MB. Không dùng
database có dữ liệu thật cho integration/concurrency.

## Scenario 1 — Report

1. Đăng nhập snapshot leader, mở registration ACCEPTED và upload PDF <= 20 MB.
2. Download, so sánh bytes và Content-Type.
3. Upload PDF thứ hai trước ACTIVE; xác nhận UI chỉ hiện bản hiện hành.
4. Dùng member thường thử POST trực tiếp; mong đợi 403.
5. Sau khi board ACTIVE, leader thử replace; mong đợi 422 và file cũ còn tải được.

## Scenario 2 — Council

1. DEAN tạo board cho period TLCN/KLTN, department đúng, 3 member với Chair/Secretary khác nhau.
2. Gán ít nhất hai ACCEPTED registrations cùng period/department, lịch và địa điểm riêng.
3. Chuyển DRAFT → ASSIGNED → SCHEDULED → ACTIVE.
4. Thử thêm supervisor, lecturer ngoài department, registration COURSE/NCKH hoặc registration
   đã thuộc board khác; mong đợi từ chối không đổi dữ liệu.

## Scenario 3 — Scoring and results

1. Lần lượt đăng nhập mọi council member; mỗi người nhập một điểm 0..10 và submit cho mọi group.
2. Thử 10.01, -0.01, ba chữ số thập phân, người ngoài board và supervisor; mong đợi từ chối.
3. Chair complete board; kiểm tra result READY từng group và arithmetic mean scale 2.
4. DEAN publish; kiểm tra board/result PUBLISHED và không sửa được score/report.

## Scenario 4 — Student privacy

1. Đăng nhập từng snapshot member, mở `/student/results`; thấy result group mình.
2. Sinh viên group khác gọi URL detail trực tiếp; mong đợi 403/404 và không lộ score.
3. Result READY không xuất hiện cho student.
4. DEAN chỉ thấy result trong faculty; ADMIN không thấy academic result.

## Verification commands

```bash
env -u JAVA_HOME ./mvnw test
env -u JAVA_HOME ./mvnw verify -Pintegration
env -u JAVA_HOME ./mvnw verify -Pe2e
git diff --check
```

Nếu thiếu `TEST_DATABASE_URL`/credentials hoặc Playwright browser, ghi trạng thái blocked;
không chuyển sang database local có dữ liệu thật và không đánh dấu acceptance đã pass.
