# CHANGELOG – Phân quyền và luồng xử lý văn bản

## Frontend

- Tách menu Chuyên viên và Trưởng đơn vị bằng role `CHUYEN_VIEN`/`LANH_DAO`.
- Thêm bảo vệ route bằng `RequireRole`.
- Tải lên văn bản dùng `Promise.allSettled`, không để một API lỗi làm rỗng toàn bộ dropdown.
- Thêm danh sách Trưởng đơn vị và trường chọn người nhận trước khi gửi vào luồng.
- Tách danh bạ nghiệp vụ khỏi API quản trị người dùng.
- Luân chuyển văn bản đến qua Document Service để đồng thời cập nhật trạng thái và tạo workflow.
- Thêm trình duyệt văn bản đi.
- Dùng endpoint chi tiết chung cho văn bản đến, đi và nội bộ.
- Chỉ gọi thông tin chữ ký khi văn bản đã ký.
- Chỉ Trưởng đơn vị thấy Phê duyệt, Ủy quyền, Ký số và Ban hành.
- Dashboard Chuyên viên và Trưởng đơn vị có tiêu đề, thao tác và nội dung khác nhau.
- Sửa ngày ISO có thời gian trước khi gán vào input `type=date`.
- Chatbot không gọi `/api/auth/me` khi chưa có token và tải lại người dùng sau đăng nhập.
- Frontend Docker chuyển từ Vite dev server sang Nginx static build.

## Auth Service và Gateway

- Thêm `/api/auth/directory/users` cho người dùng đã đăng nhập đọc danh bạ nghiệp vụ.
- Giữ `/api/auth/users/**` cho Admin.
- Bật method security và giới hạn API quản trị.
- Bổ sung API cập nhật hồ sơ hiện tại `/api/auth/me`.

## Document Service

- Thêm GET `/api/documents/{id}` cho mọi loại văn bản.
- Giới hạn tạo/sửa/chuyển, hồ sơ, tệp đính kèm, đánh số và phiên bản cho Chuyên viên/Admin; ký và ban hành cho Trưởng đơn vị/Admin.
- Sửa payload trình duyệt văn bản đi gửi sang Workflow Service.
- Không tự khởi tạo workflow thiếu `workflowId`; workflow bắt đầu khi người dùng thực sự luân chuyển/trình duyệt.

## Workflow Service

- Chuyên viên chỉ có thể trình duyệt tới tài khoản `LANH_DAO`/`ADMIN`.
- Người gửi lấy từ JWT, không tin hoàn toàn ID do frontend truyền lên.
- Trưởng đơn vị chỉ được duyệt bản ghi được giao cho chính mình.
- Phê duyệt chuyển văn bản sang trạng thái `3 – Trình ký`.
- Từ chối đưa văn bản về trạng thái `1 – Đang xử lý`.
- Giới hạn API phê duyệt, SLA và ủy quyền theo role.

## Database và Docker Compose

- Thêm migration cho `chukyso`, `uyquyen`, các cột OCR/AI và tài khoản đăng nhập cục bộ.
- Chuẩn hóa role workflow và các mã role cũ (`MANAGER`, `TRUONG_PHONG`, `STAFF`...) thành `LANH_DAO`/`CHUYEN_VIEN`.
- Bảo đảm tài khoản demo `lanhdao / 123456` tồn tại.
- Thêm service `db-migrations` chạy cả với volume PostgreSQL cũ.
- Thêm healthcheck và thứ tự khởi động để giảm lỗi Gateway `503`.

## Bổ sung 18/08/2026

- Chuyển giao diện **Quy trình xử lý**, **Template văn bản**, **Báo cáo & Thống kê** từ Admin sang `LANH_DAO`; menu Admin chỉ còn chức năng quản trị hệ thống.
- Bổ sung quyền Manager cho `MANAGE_WORKFLOWS`, `MANAGE_TEMPLATES`, `VIEW_REPORTS`; đổi tên hiển thị thành **Xem báo cáo thống kê**.
- Template hỗ trợ upload file Word/PDF từ máy tính, tải lại file qua API có xác thực và tạo văn bản nháp từ template.
- Thêm volume `document_uploads` để file tải lên không mất khi rebuild container.
- Luân chuyển văn bản khóa đơn vị theo đúng `donViId` của người nhận ở cả Frontend và Backend.
- Backend chặn tuyệt đối luân chuyển văn bản trạng thái `5 – Đã ban hành`.
- Bỏ khối **Cài đặt thông báo** và thông tin Azure AD mẫu khỏi trang Tài khoản.
- Migration V008 chuyển bước phê duyệt nghiệp vụ từ Admin sang Lãnh đạo, chuyển các nhiệm vụ chờ xử lý sang tài khoản `lanhdao`, xóa dữ liệu demo Azure SSO và đường dẫn template `/templates/...` cũ.
