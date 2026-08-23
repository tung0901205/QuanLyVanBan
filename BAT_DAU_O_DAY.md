# BẮT ĐẦU Ở ĐÂY – BẢN SỬA PHÂN QUYỀN VÀ LUỒNG VĂN BẢN

## 1. Chạy đúng thư mục

Chỉ chạy file `docker-compose.yml` ở **thư mục gốc** này. File Compose cũ trong `qlda-system` đã được đổi tên thành `docker-compose.legacy.yml` để tránh chạy nhầm.

```powershell
powershell -ExecutionPolicy Bypass -File .\START_FIXED_SYSTEM.ps1
```

Hoặc chạy thủ công:

```powershell
docker compose down --remove-orphans
docker compose up -d --build
docker compose ps
docker compose logs db-migrations --tail=120
```

Không dùng `docker compose down -v` vì lệnh đó xóa dữ liệu PostgreSQL.

## 2. Tài khoản và giao diện

- `admin / 123456`: giao diện Quản trị viên.
- `icetruong / 123456`: giao diện Chuyên viên.
- `lanhdao / 123456`: giao diện Trưởng đơn vị, được migration bảo đảm tồn tại với role `LANH_DAO`.

Các tài khoản Trưởng đơn vị khác trong CSDL vẫn được giữ nguyên; `lanhdao` là tài khoản demo ổn định để kiểm thử.

Sau khi chạy source mới, xóa token giao diện cũ:

```javascript
sessionStorage.clear();
location.href = "/login";
```

## 3. Menu theo vai trò

### Chuyên viên

- Tổng quan
- Văn bản đến
- Văn bản đi
- Hồ sơ công việc
- Văn bản nội bộ
- Tải lên
- Tìm kiếm
- Tài khoản
- Thông báo

### Trưởng đơn vị

- Tổng quan điều hành
- Văn bản đến
- Văn bản đi
- Phê duyệt
- Quy trình xử lý
- Template văn bản
- Báo cáo & Thống kê
- Ủy quyền xử lý
- Tìm kiếm
- Tài khoản
- Thông báo

### Quản trị viên

Dùng giao diện `/admin/**` cho các chức năng quản trị hệ thống: tài khoản, phân quyền, đơn vị, loại văn bản, giám sát dịch vụ, cấu hình SLA và nhật ký hệ thống. Quy trình xử lý, template văn bản và báo cáo thống kê đã chuyển sang giao diện Trưởng đơn vị.

## 4. Luồng demo đúng

1. Đăng nhập `icetruong`.
2. Chọn **Tải lên**.
3. Chọn loại văn bản, đơn vị ban hành, đơn vị chủ trì; nhập người ký và đính kèm file.
4. Chọn **Trưởng đơn vị nhận xử lý**.
5. Nhấn **Gửi vào luồng**.
6. Hệ thống tạo văn bản, lưu tệp, tạo bản ghi workflow và chuyển trạng thái sang đã chuyển xử lý.
7. Đăng xuất, đăng nhập tài khoản có role `LANH_DAO`.
8. Chọn **Phê duyệt**, mở văn bản, chọn Duyệt hoặc Từ chối.
9. Sau khi Duyệt, trạng thái văn bản là **Trình ký**.
10. Mở chi tiết và nhấn **Ký số**. Sau khi ký, trạng thái là **Đã ký**.
11. Trưởng đơn vị có thể nhấn **Ban hành** để chuyển sang **Đã ban hành**.

## 5. Các lỗi đã sửa

- Dropdown loại văn bản, đơn vị và người nhận không còn bị rỗng vì một API `403` làm hỏng toàn bộ `Promise.all`.
- Tách API danh bạ đọc `/api/auth/directory/users` khỏi API quản trị `/api/auth/users`.
- Tạo endpoint chi tiết chung `/api/documents/{id}` cho văn bản đến, đi và nội bộ.
- Luân chuyển văn bản dùng Document Service, tự lấy người gửi từ JWT và tạo Workflow.
- Chỉ tài khoản `LANH_DAO`/`ADMIN` được nhận bước trình duyệt.
- Chuyên viên và Trưởng đơn vị có menu, route và nút chức năng khác nhau.
- Trưởng đơn vị thực hiện nghiệp vụ phê duyệt, ký số, ban hành; Admin chỉ còn menu quản trị hệ thống.
- Bổ sung migration `chukyso`, `uyquyen` và các cột AI để hết lỗi `500` với CSDL cũ.
- Frontend chạy bằng Nginx tĩnh thay vì Vite dev server, loại bỏ lỗi WebSocket HMR `400`.
- Compose chờ healthcheck nên giảm lỗi đăng nhập `503` khi service chưa đăng ký Eureka.
- Chuẩn hóa role quy trình và tài khoản cũ (`MANAGER`, `TRUONG_PHONG`, `STAFF`...) thành `LANH_DAO`/`CHUYEN_VIEN`, đồng thời tạo tài khoản Trưởng đơn vị mẫu.
- Sửa payload trình duyệt văn bản đi giữa Document Service và Workflow Service.
- Template cho phép chọn file Word/PDF trực tiếp từ máy và lưu bền vững trong volume `document_uploads`.
- Nút **Tạo từ mẫu** tạo văn bản nháp và gắn tệp mẫu nếu có.
- Luân chuyển tự khóa đơn vị theo người nhận; văn bản **Đã ban hành** không thể luân chuyển.
- Trang Tài khoản đã bỏ toàn bộ khối **Cài đặt thông báo** và nội dung Azure AD mẫu.

## 6. Kiểm tra nhanh

```powershell
powershell -ExecutionPolicy Bypass -File .\VERIFY_ROLE_WORKFLOW.ps1
```

Kết quả cần có:

- Service `db-migrations` tồn tại.
- Các role `ADMIN`, `CHUYEN_VIEN`, `LANH_DAO` tồn tại.
- Có ít nhất một tài khoản Trưởng đơn vị hoạt động.
- Các bảng `vanban`, `chukyso`, `uyquyen` tồn tại.
- Các cột `aiconfidence`, `aiphanloai`, `noidungocr` tồn tại.
- Các health endpoint trả `200`.
