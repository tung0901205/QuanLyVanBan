# Hướng dẫn chạy QLDA V5 Pro

## 1. Yêu cầu

- Windows 10/11, macOS hoặc Linux 64-bit.
- Docker Desktop 4.x trở lên, bật Docker Compose V2.
- Khuyến nghị tối thiểu: 8 GB RAM trống, 4 CPU và 20 GB ổ đĩa. Với kho văn bản lớn, dành riêng dung lượng cho Docker Desktop.

Không cần cài Java, Maven, Node.js hay PostgreSQL trên máy khi chạy bằng Docker.

## 2. Cấu hình lần đầu

Mở PowerShell tại thư mục chứa `docker-compose.yml`:

```powershell
Copy-Item .env.example .env
notepad .env
```

Phải đổi hai giá trị sau trong `.env`:

- `DB_PASSWORD`: mật khẩu PostgreSQL mạnh.
- `INTERNAL_SERVICE_TOKEN`: chuỗi ngẫu nhiên ít nhất 32 ký tự, dùng chung cho giao tiếp nội bộ giữa các service.

AI vẫn chạy ở chế độ fallback khi `GEMINI_API_KEY` để trống. Để có chất lượng tóm tắt/phân loại/chatbot tốt nhất, điền Gemini API key hợp lệ và giữ tên model trong `.env` có thể cấu hình.

## 3. Khởi động

```powershell
docker compose up -d --build
docker compose ps
```

Lần đầu thường mất 5–15 phút vì Docker cần tải image và biên dịch các service. Chỉ đăng nhập khi các container `qlda-auth`, `qlda-document`, `qlda-workflow`, `qlda-ai`, `qlda-notification`, `qlda-gateway` và `qlda-eureka` đều hiển thị `healthy`.

Mở: <http://localhost:5173>

Tài khoản dữ liệu mẫu cục bộ:

- Tên đăng nhập: `admin`
- Mật khẩu: `123456`

Hãy đổi mật khẩu tài khoản quản trị ngay khi đưa hệ thống ra ngoài máy phát triển.

Các cổng chính:

| Thành phần | Địa chỉ |
|---|---|
| Giao diện | <http://localhost:5173> |
| API Gateway | <http://localhost:8080> |
| Eureka | <http://localhost:8761> |
| PostgreSQL | `localhost:5432` |

## 4. Dừng, chạy lại và xem log

```powershell
docker compose stop
docker compose start
docker compose logs -f --tail 200 ai-service
```

`docker compose stop` và `docker compose down` không xóa dữ liệu trong volume. Không dùng `docker compose down -v` nếu chưa sao lưu vì tùy chọn `-v` xóa cả cơ sở dữ liệu và kho tệp.

## 5. Lưu trữ nhiều văn bản

Tệp được lưu trong volume `document_uploads` theo cấu trúc thư mục phân tầng, tránh giới hạn hiệu năng của một thư mục chứa quá nhiều tệp. Cơ sở dữ liệu và tệp không nằm trong image nên không mất khi build lại.

- Mặc định mỗi tệp tối đa 100 MB; thay đổi đồng thời `MAX_UPLOAD_FILE_SIZE`, `MAX_UPLOAD_REQUEST_SIZE` và `MAX_UPLOAD_FILE_SIZE_BYTES` trong `.env` nếu cần.
- Dung lượng tổng phụ thuộc ổ đĩa dành cho Docker Desktop. Theo dõi định kỳ bằng `docker system df` và dung lượng ổ đĩa hệ điều hành.
- Bốn volume bền vững là `postgres_data` (cơ sở dữ liệu), `document_uploads` (tệp văn bản), `report_exports` (báo cáo) và `auth_backups` (bản dump PostgreSQL).
- Sao lưu cả PostgreSQL và volume `document_uploads`; chỉ sao lưu một trong hai sẽ tạo bản dữ liệu không đồng bộ.
- Với môi trường thật, nên đặt Docker data root trên SSD dung lượng lớn hoặc chuyển volume sang object storage tương thích S3 trong một đợt triển khai riêng.

## 6. Sao lưu và khôi phục cơ sở dữ liệu

API quản trị tạo file custom dump PostgreSQL 17 thật bằng `pg_dump`; file được giữ trong volume `auth_backups`. Ví dụ PowerShell:

```powershell
$loginBody = @{ username='admin'; password='123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
$body = @{ backupType='FULL'; description='Sao lưu thủ công' } | ConvertTo-Json
$backup = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/backups' -Headers $headers -ContentType 'application/json' -Body $body
$backup.data
```

Trước mỗi lần khôi phục, hệ thống tự tạo thêm một bản `backup_pre_restore_*.dump`. Khôi phục là thao tác có ảnh hưởng toàn bộ cơ sở dữ liệu: dừng người dùng ghi dữ liệu, kiểm tra đúng tên file và chỉ gọi `/api/auth/backups/restore` khi đã có kế hoạch quay lui. Bản dump cơ sở dữ liệu không chứa tệp trong `document_uploads`; volume này phải được sao lưu riêng.

## 7. Kiểm tra sau khi chạy

```powershell
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8084/actuator/health
```

Kiểm thử mã nguồn frontend:

```powershell
Set-Location frontend
npm ci
npm run build
npm run test
npm run lint
```

Backend yêu cầu Java 21. Nếu không cài Java 21 trên máy, dùng các image Maven/Docker như trong `docker-compose.yml`.

## 8. Lỗi thường gặp

- `Set DB_PASSWORD in .env`: chưa tạo `.env`, hoặc thiếu biến bắt buộc.
- Một service không `healthy`: chạy `docker compose logs --tail 300 <tên-service>` và kiểm tra lỗi đầu tiên.
- AI trả lời ở chế độ local fallback: kiểm tra `GEMINI_API_KEY`, quota, model và kết nối Internet trong log `ai-service`.
- Không OCR được ảnh/PDF: dùng ảnh rõ nét, đúng chiều, tiếng Việt/Anh; kiểm tra tệp không vượt giới hạn và thuộc loại cho phép.
- Cổng đã được sử dụng: dừng ứng dụng đang chiếm cổng 5173, 8080–8085, 8761, 5432, 9092 hoặc đổi mapping trong Compose.

## 9. Lưu ý triển khai thật

- Thay khóa RSA mẫu của auth-service bằng cặp khóa riêng; không chia sẻ private key.
- Không commit `.env`, API key, mật khẩu hoặc token dịch vụ lên Git.
- Đặt reverse proxy HTTPS trước frontend/API Gateway, không công khai trực tiếp các cổng service 8081–8085.
- Thiết lập backup tự động, giám sát dung lượng, log tập trung và quy trình phục hồi thử nghiệm định kỳ.
- Gemini, Microsoft 365, email và Teams là các tích hợp bên ngoài; muốn kiểm chứng đầy đủ ở môi trường thật phải cung cấp credential hợp lệ và cho container truy cập Internet. Khi chưa có Gemini key, hệ thống vẫn chạy bằng chế độ AI fallback cục bộ.
