# Quickstart Verification: Đợt đăng ký theo Bộ môn

**Mục đích**: hướng dẫn nghiệm thu sau khi các task 005 được triển khai; hiện chưa phải chứng cứ test đạt. Xem [contracts/web.md](contracts/web.md), [data-model.md](data-model.md) và [acceptance-matrix.md](acceptance-matrix.md).

## Prerequisites and safety

- JDK 21; MySQL 8.4 LTS; DB test **riêng** `topic_registration_test` trên `127.0.0.1:3307`.
- `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` được cung cấp từ môi trường, không ghi mật khẩu vào repo/lệnh/log.
- Chỉ chạy migration reset/fixture phá dữ liệu trên DB test đã xác minh; không chạy đồng thời các integration/E2E suite dùng chung DB test.
- T010 của 004 đang có full integration/E2E chưa đạt. Ghi lại baseline lỗi trước khi gán failure cho 005; không sửa test ngoài task được duyệt.

## Automated checks sau implementation

```bash
./mvnw -q test
./mvnw -q verify -Pintegration
./mvnw -q verify -Pe2e
git diff --check
```

Chỉ chạy full suite khi cấu hình DB test đã được kiểm tra là không trỏ dev/shared. Test migration phải nâng cấp fixture V18→phiên bản mới, validate Flyway, kiểm tra số đợt/đề tài/nhóm/hội đồng/kết quả trước–sau và trường hợp dữ liệu mơ hồ dừng an toàn. Ghi tên test và kết quả thực tế vào ma trận; không coi lệnh trên là đã được chạy.

## Acceptance dataset

Chuẩn bị hai Bộ môn A/B trong cùng Khoa X, một Bộ môn C ở Khoa Y; một DEAN X, hai GV A/B, một SV X, đề tài đã và chưa công bố, một GVHD A và người chấm B. Tạo đợt thuộc mỗi Bộ môn cho từng loại COURSE/NCKH/TLCN/KLTN. Dùng ID thật được lấy từ DB test/fixture, không hard-code từ DB dev.

## Scenarios

1. **US1**: DEAN X tạo đợt A/B và thấy cả hai cùng nhãn Bộ môn; ID C hoặc ID không tồn tại bị từ chối. Đổi Bộ môn sau mở hoặc khi có dữ liệu phụ thuộc không thành công; dữ liệu cũ còn nguyên.
2. **US2**: GV A chỉ thấy đợt A; GET/POST với ID đợt B bằng URL trực tiếp bị từ chối, không lộ danh sách và không tạo đề tài. Đề tài A có tác giả/1–2 GVHD cùng A; GVHD B hoặc thiếu Bộ môn bị từ chối.
3. **US3**: SV X thấy đợt A/B, thấy Bộ môn tương ứng; chỉ xem/chọn đề tài đã công bố. Đề tài chưa công bố không hiện và request đăng ký trực tiếp bị từ chối.
4. **US4**: DEAN phân công GV B cùng Khoa chấm một đề tài A hợp lệ; B chỉ xem đúng nhiệm vụ đó, không xem list A; GV khác không có phân công không xem được. Hủy/hết hiệu lực phân công làm mất quyền; GVHD của đề tài không được chấm chính đề tài đó.
5. **US5**: Khi DEAN X đang hoạt động, tạo/kích hoạt DEAN X thứ hai bị từ chối, gồm hai request đồng thời. Sau vô hiệu hóa DEAN cũ, người mới quản lý được đợt A/B cũ.
6. **Data gate**: Đợt legacy không có Bộ môn được liệt kê để đối chiếu; đợt có đề tài A và B không bị tự gán. Chỉ ánh xạ có xác nhận mới được backfill. Migration thất bại phải không làm mất dữ liệu.

## Completion rule

Chỉ tick task khi test được yêu cầu của task đó đạt trên môi trường phù hợp. Chỉ tuyên bố feature hoàn thành khi SC-005-01–06 đều có evidence, data gate đã qua và các suite bắt buộc đạt; baseline 004 chưa đạt phải được nêu riêng.
