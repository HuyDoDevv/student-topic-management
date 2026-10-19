# Quickstart Verification: Khắc phục phản hồi nghiệp vụ và hoàn thiện chấm điểm

**Mục đích**: hướng dẫn nghiệm thu feature 006 sau khi T002–T017 được triển khai.
Các lệnh/kịch bản dưới đây chưa phải evidence đã chạy; evidence thật chỉ được ghi
ở T018 trong `acceptance-matrix.md`.

## Prerequisites and safety

- JDK 21; MySQL 8.4 LTS; DB test riêng `topic_registration_test` trên
  `127.0.0.1:3307`.
- `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` được
  truyền qua môi trường; không ghi secret vào repo, tài liệu hoặc log.
- Trước migration/reset/destructive fixture, xác minh URL và schema là DB test,
  không phải dev/shared/prod. Không chạy đồng thời suite dùng chung DB test.
- Baseline migration là V21. Chụp số row và checksum nghiệp vụ liên quan account,
  topic, supervisor, registration, report, council, evaluation và final result.

## Acceptance dataset

Tạo dữ liệu test có kiểm soát:

- Khoa A có hai Bộ môn A1/A2; Khoa B để kiểm tra scope.
- DEAN-A cũ, DEAN-A mới, DEAN-B; account role kép legacy; lecturer enabled/
  disabled; GVHD, đồng hướng dẫn, GVPB, grader và 3–5 council member.
- Mỗi loại COURSE/NCKH/TLCN/KLTN có đợt, topic, registration accepted và nhóm
  1, 2, 3 sinh viên; thêm topic approved chưa published có/không có registration.
- Notification target còn hiệu lực và hết hiệu lực.
- Council `DRAFT`, `ASSIGNED`, `ACTIVE`, có/không có evaluation; lịch quá khứ,
  hiện tại và tương lai.
- Final result `READY` và `PUBLISHED` lịch sử để chứng minh bất biến.

## Automated checks sau implementation

```bash
./mvnw test
./mvnw verify -Pintegration
./mvnw verify -Pe2e
git diff --check
```

Chỉ chạy profile cần DB sau khi safety check thành công. Ghi command, timestamp,
environment và số test pass/fail thực tế; lỗi baseline ngoài scope được tách riêng,
không tự sửa trong T018.

## Migration verification

1. Migrate database trống V1→V23 và Flyway validate.
2. Khởi tạo fixture ở V21, ghi snapshot business rows, migrate V21→V23.
3. Xác nhận role LECTURER bị bỏ khỏi DEAN nhưng account/FK lịch sử còn nguyên.
4. Xác nhận không có reviewer assignment/evaluation/report nào được tự suy diễn
   cho TLCN/KLTN lịch sử.
5. So snapshot trước/sau; mọi row lịch sử và final result `PUBLISHED` không đổi.
6. Chạy lại Flyway/validate theo cơ chế test hiện hành; không có migration trùng,
   checksum sai hoặc thao tác không idempotent ngoài chuẩn Flyway.

## Scenarios theo 18 phản hồi

1. **FB-01 — DEAN ở đề xuất**: đăng nhập DEAN, menu/route topic proposal không
   có quyền; GET/POST trực tiếp bị từ chối, lecturer thường vẫn tạo được đúng scope.
2. **FB-02 — filter Bộ môn**: DEAN duyệt đợt A1 chỉ thấy topic A1; không có
   “Tất cả Bộ môn”; query/body A2 không mở rộng kết quả.
3. **FB-03 — DEAN trong grader list**: mọi list direct grader/reviewer/member
   không có DEAN legacy hay mới.
4. **FB-04 — notification link**: từng category mở đúng route của recipient;
   target đóng/hủy quay về inbox có thông báo, không 404.
5. **FB-05 — chấm toàn nhóm**: nhóm ba SV hiển thị một form; một score lỗi hoặc
   stale version làm rollback cả request; request hợp lệ lưu/nộp đủ ba.
6. **FB-06 — danh sách kết quả**: tạo nhiều đợt/topic/group và kiểm tra filter,
   sort, pagination, grouping, link detail không trộn scope.
7. **FB-07 — sửa topic approved**: topic approved chưa published/chưa đăng ký/
   còn hạn trả về draft và submit lại; ba điều kiện âm đều bị chặn.
8. **FB-08 — student progress**: dựng dữ liệu ở từng mốc và xác nhận chín nhãn
   từ “Chờ xét đăng ký” đến “Đã công bố kết quả”.
9. **FB-09 — vai trò nhóm**: một SV có bốn đợt, hai lần leader; mỗi thẻ hiển thị
   đúng đợt, Bộ môn, mã nhóm, topic, role, status và action.
10. **FB-10 — tự gán DEAN chấm**: gửi ID DEAN qua POST dù không có trong select;
    service từ chối và không tạo assignment.
11. **FB-11 — TLCN không hội đồng**: TLCN không xuất hiện ở create/assign council;
    dùng reviewer rồi grader thứ hai.
12. **FB-12 — UI hội đồng**: member/role hiện rõ; thao tác thêm/xóa trực tiếp có
    feedback validation và giữ input khi lỗi.
13. **FB-13 — sửa member**: sửa ở DRAFT/ASSIGNED chưa có điểm thành công; có điểm
    hoặc ACTIVE bị khóa; hai request version cũ không ghi đè.
14. **FB-14 — loại GVHD**: POST gán GVHD/đồng hướng dẫn làm reviewer, grader hoặc
    member đều bị từ chối; topic khác không xung đột vẫn hợp lệ.
15. **FB-15 — lịch quá khứ**: form min hỗ trợ UI; service từ chối past/equal-now,
    chấp nhận future theo fixed clock và render field error.
16. **FB-16 — căn cứ công bố**: trang chi tiết hiện actor/role/status/score/formula/
    source count/average/audit; thiếu nguồn không cho publish.
17. **FB-17 — chair progress**: chair thấy trạng thái và số SV của member council
    mình, không thấy council khác hoặc điểm draft; DEAN xem đúng Khoa.
18. **FB-18 — reviewer workflow**: TLCN/KLTN nộp PDF + đủ điểm trước/đúng hạn;
    thiếu PDF/score/student hoặc quá hạn bị rollback.

## Formula scenarios

### COURSE/NCKH

- Một submitted grader score 8.125 được xử lý theo quy tắc scale/rounding đã
  duyệt; không có reviewer/council source.
- Grader là supervisor, DEAN, disabled hoặc khác Khoa bị từ chối.

### TLCN

- Reviewer chưa submit: không cho assign grader thứ hai.
- Reviewer 7.00 và grader 9.00: final 8.00.
- Thiếu score của một SV ở một trong hai nguồn: SV đó và registration chưa đủ để
  công bố; không tạo final result sai completeness.

### KLTN

- Reviewer + ba member cùng 7.00: `(7 + 7 + 7 + 7) / 4 = 7.00`.
- Một member chưa submit: không consolidate/publish.
- Số member ngoài 3–5, reviewer trùng member, member là DEAN/GVHD: từ chối.
- Dùng các bộ điểm tạo phần thập phân để xác nhận hai chữ số `HALF_UP`.

## Deadline boundary

Dùng fixed clock và deadline D:

- Trước cuối ngày D theo `Asia/Ho_Chi_Minh`: chấp nhận.
- Đúng thời điểm biên cuối ngày được định nghĩa: chấp nhận theo rule “trước hoặc
  đúng hạn”.
- Ngay sau biên: từ chối, không lưu file metadata, score hay status một phần.
- Browser gửi timezone/date khác không làm đổi quyết định server.

## Authorization and tampering

Với mỗi GET/POST nhạy cảm, thử DEAN, lecturer không được giao, reviewer khác,
supervisor, student, actor Khoa khác, account disabled, ID không tồn tại, ID ngoài
scope, CSRF thiếu và version cũ. Xác nhận response theo policy hiện hành và DB
không đổi sau request bị từ chối.

## Completion rule

Chỉ tick task khi test mục tiêu của task đó có evidence. Chỉ tuyên bố feature 006
hoàn thành khi FB-01–FB-18, role matrix, bốn loại đợt, nhóm 1–3 SV, deadline,
concurrency, V21→V23, history preservation, full test, integration, E2E và
`git diff --check` đều có kết quả được ghi; phần chưa verify phải để mở.
