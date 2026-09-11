# Specification Quality Checklist: Quản lý đợt, đề tài và đăng ký nhóm

**Purpose**: Kiểm tra chất lượng đặc tả trước khi lập kế hoạch.
**Created**: 2026-09-08
**Feature**: [spec.md](../spec.md)
**Review Ownership**: Được rà soát trong speckit-specify; speckit-clarify cập nhật sau xác nhận.
**Marker Semantics**: Đánh dấu hoàn thành chỉ nói về chất lượng tài liệu, không phải triển khai.

## Content Quality

- [x] Không lựa chọn ngôn ngữ, framework, API hoặc giải pháp triển khai.
- [x] Tập trung giá trị người dùng và nghiệp vụ.
- [x] User stories viết cho người đọc nghiệp vụ; kiểm thử kỹ thuật đặt riêng theo yêu cầu.
- [x] Các phần bắt buộc đã được điền.

## Requirement Completeness

- [x] Mọi điểm chưa xác nhận còn lại được ghi bằng Q và gate, không có placeholder vô danh.
- [ ] Mọi yêu cầu kiểm chứng được và không mơ hồ: FR phụ thuộc Q chưa đạt.
- [x] Tiêu chí thành công đo được và đã được xác nhận cho bản local ngày 2026-09-09.
- [x] Tiêu chí thành công độc lập công nghệ.
- [ ] Mọi tình huống nghiệm thu đã xác định đầy đủ: các AS có điều kiện chưa có chính sách cuối.
- [x] Đã nhận diện trường hợp biên và đồng thời.
- [x] Ranh giới đợt đầu và phạm vi toàn dự án rõ ràng.
- [x] Phụ thuộc và trạng thái giả định được ghi rõ.

## Feature Readiness

- [ ] Mọi FR có tiêu chí nghiệm thu rõ hoàn toàn: đã ánh xạ nhưng còn Q.
- [x] User stories bao phủ luồng chính và nhánh từ chối.
- [ ] Có đủ định nghĩa để xác minh toàn bộ kết quả đo được: AS/SC đã xác nhận nhưng kết quả còn phụ thuộc G-Publish và Q-09.
- [x] Không đưa lựa chọn triển khai vào đặc tả.

## Notes

Rà soát ngày 2026-09-09: Q-18 và Q-20 đã giải quyết; AS/SC được xác nhận cho bản local.
Q-02, Q-05, Q-06 và Q-07 đã được giải quyết; Q-07 được chốt lại ngày 2026-09-11 theo luồng
lời mời có xác nhận và tự rời/giải tán. G-Publish và Q-09 còn mở trong bảng gate. Không tự
điền chính sách để làm checklist đạt; phần độc lập chỉ triển khai khi gate tương ứng cho phép.

FR-12 có nguồn U từ yêu cầu hiện tại; không nhận nhầm nhánh từ chối là nội dung tường minh
của REQ-16. REQ-11 được tách phần GVHD/GVPB; REQ-06–07 chỉ cấu hình ngày ở đợt này.

Các mục chưa đạt cần cập nhật qua xác nhận khách hàng/speckit-clarify trước user story liên
quan. Preview test hiện có không thay thế kiểm thử nghiệp vụ; AT được cập nhật khi triển khai.
