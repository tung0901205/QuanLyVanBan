# V4.1 - Sửa lỗi Document Service không khởi động

Lỗi V4: `KafkaNotificationEventPublisher` cần `KafkaTemplate` nhưng Document Service chỉ khai báo `org.springframework.kafka:spring-kafka`. Với Spring Boot 4, cấu hình tự động Kafka của project này được kéo vào qua `spring-boot-starter-kafka` (Notification Service đã dùng starter này).

Đã sửa `qlda-system/document-service/pom.xml`:

- Bỏ `org.springframework.kafka:spring-kafka`
- Dùng `org.springframework.boot:spring-boot-starter-kafka`

Sau khi build lại Document Service, Spring Boot tạo `KafkaTemplate` từ cấu hình `spring.kafka.*` hiện có và `KafkaNotificationEventPublisher` có thể khởi tạo.
