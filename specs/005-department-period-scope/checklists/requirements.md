# Specification Quality Checklist: Đợt đăng ký theo Bộ môn

**Purpose**: Kiểm tra đặc tả 005 trước khi lập kế hoạch triển khai.
**Created**: 2026-10-13
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Không chứa chi tiết triển khai, API hoặc migration cụ thể.
- [x] Tập trung vào giá trị và quy tắc nghiệp vụ người dùng cần.
- [x] Viết cho người phụ trách nghiệp vụ; thuật ngữ được giải thích theo ngữ cảnh.
- [x] Các phần bắt buộc của mẫu đặc tả đã hoàn thành.

## Requirement Completeness

- [x] Không còn marker `[NEEDS CLARIFICATION]` trong đặc tả.
- [x] Các yêu cầu có thể kiểm thử và không mơ hồ trong phạm vi 005.
- [x] Tiêu chí thành công đo được và không phụ thuộc công nghệ.
- [x] Kịch bản nghiệm thu bao phủ các luồng chính và trường hợp từ chối quyền.
- [x] Trường hợp biên và cổng dữ liệu cũ đã được nêu.
- [x] Trong/ngoài phạm vi và giả định đã được nêu.
- [x] Phụ thuộc feature 004 và dữ liệu cũ đã được nhận diện.

## Feature Readiness

- [x] Mỗi yêu cầu chức năng có kịch bản hoặc tiêu chí nghiệm thu tương ứng.
- [x] User stories bao phủ Trưởng khoa, giảng viên, sinh viên và người chấm.
- [x] Các tiêu chí thành công gắn với kết quả có thể quan sát của người dùng.
- [x] Đặc tả không ràng buộc công nghệ triển khai.

## Notes

- Quyết định “đợt thuộc một Bộ môn” là U-005-01 của chủ dự án, không phải yêu
  cầu được nêu trực tiếp trong PDF.
- Phạm vi một nhóm SV xuyên nhiều đợt và workflow GVPB là các hạng mục riêng;
  không được coi là đã hoàn thành bởi feature 005.
- Cần kiểm kê dữ liệu đợt cũ trước khi lập task backfill/enforce.
