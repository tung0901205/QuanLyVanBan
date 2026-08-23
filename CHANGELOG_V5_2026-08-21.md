# QLDA V5 – các sửa đổi chính

- Bảo vệ `/internal/ai/**` bằng token dịch vụ và danh sách service cho phép.
- Danh tính AI lấy từ JWT; chặn giả mạo `userId`, giới hạn quyền đọc/xóa kết quả AI và phản hồi chatbot.
- Kiểm tra quyền văn bản thật theo admin, người tạo, đơn vị chủ trì và người tham gia workflow; chatbot fail-closed khi dịch vụ quyền lỗi.
- AI indexing chạy trong transaction; thêm test bảo mật backend.
- Kho tệp phân tầng theo UUID, ghi nguyên tử, kiểm tra path traversal, loại tệp và giới hạn kích thước cấu hình được.
- Tải xuống giữ tên tệp gốc; xóa tệp chỉ sau khi transaction DB commit.
- Lịch sử phiên bản, tệp OCR, trạng thái workflow, người xử lý và phân loại hồ sơ được lưu bền vững trong PostgreSQL.
- Hồ sơ công việc hỗ trợ nhiều văn bản qua bảng liên kết `HoSoVanBan`.
- Thêm migration V011 và index phục vụ dữ liệu lớn.
- Thêm migration V012: refresh token chỉ lưu SHA-256 trong PostgreSQL, audit log bền vững và bảng chống xử lý trùng sự kiện thông báo.
- Thay publisher no-op bằng Kafka thật cho workflow/document; bật `acks=all`, idempotence và chờ xác nhận broker để không báo thành công giả.
- Xuất báo cáo XLSX/PDF nhị phân thật, tải qua endpoint có xác thực và lưu trong volume riêng.
- Thay API backup giả bằng `pg_dump`/`pg_restore` PostgreSQL 17 thật; tự tạo bản an toàn trước restore, kiểm tra tên file và lưu ở volume `auth_backups`.
- Migration chạy fail-fast: lỗi SQL làm container migration thất bại thay vì tiếp tục khởi động hệ thống.
- Sửa toàn bộ test/lint frontend hiện có; chuẩn hóa lỗi 401 và luồng danh bạ ủy quyền.
- Thiết kế lại đăng nhập, sidebar người dùng/admin, dashboard và bộ icon SVG; chuyển bảng màu quản trị sang navy–blue–teal.
- Cập nhật dependency frontend; `npm audit` không còn lỗ hổng đã biết tại thời điểm kiểm tra 2026-08-21.
- Loại mật khẩu/token nội bộ hard-code khỏi Docker Compose; thêm `.env.example` và hướng dẫn chạy V5.
