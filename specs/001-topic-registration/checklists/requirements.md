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

- [x] Không còn điểm chưa xác nhận hoặc placeholder vô danh trong phạm vi hiện tại.
- [x] Mọi yêu cầu kiểm chứng được và không mơ hồ; các Q trong phạm vi hiện tại đã đóng.
- [x] Tiêu chí thành công đo được và đã được xác nhận cho bản local ngày 2026-09-09.
- [x] Tiêu chí thành công độc lập công nghệ.
- [x] Mọi tình huống nghiệm thu đã xác định đầy đủ cho phạm vi hiện tại.
- [x] Đã nhận diện trường hợp biên và đồng thời.
- [x] Ranh giới đợt đầu và phạm vi toàn dự án rõ ràng.
- [x] Phụ thuộc và trạng thái giả định được ghi rõ.

## Feature Readiness

- [x] Mọi FR có tiêu chí nghiệm thu rõ và đã ánh xạ.
- [x] User stories bao phủ luồng chính và nhánh từ chối.
- [x] Có đủ định nghĩa để xác minh toàn bộ kết quả đo được; G-Publish và Q-09 đã đóng.
- [x] Không đưa lựa chọn triển khai vào đặc tả.

## Notes

Rà soát hoàn tất ngày 2026-09-11: Q-02, Q-05, Q-06, Q-07, Q-09, Q-18, Q-20 và G-Publish
đã được khách hàng xác nhận. Q-09 chốt reason thủ công bắt buộc, khóa period trước thắng
khi rút/chấp nhận đồng thời và giữ toàn bộ lịch sử đăng ký không thời hạn. Đặc tả đủ điều
kiện chuyển sang triển khai; kết quả code/test vẫn được ghi theo từng task.

FR-12 có nguồn U từ yêu cầu hiện tại; không nhận nhầm nhánh từ chối là nội dung tường minh
của REQ-16. REQ-11 được tách phần GVHD/GVPB; REQ-06–07 chỉ cấu hình ngày ở đợt này.

Preview test hiện có không thay thế kiểm thử nghiệp vụ; AT được cập nhật khi triển khai.
