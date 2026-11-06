# FlashGuard — Flash Sale Concurrency Testing System

Mô phỏng Flash Sale và kiểm tra việc chống **bán vượt số lượng (overselling)** khi nhiều người mua cùng lúc.
Kịch bản chính: **100 request mua cùng lúc 10 sản phẩm → đúng 10 giữ hàng thành công, 90 bị từ chối vì hết hàng.**

Stack: Java 25 · Spring Boot 4 · MySQL 8 · Spring Data JPA · Maven · TestNG · REST Assured · Allure · Swagger

```
FlashGuard/
├── backend/    # Spring Boot REST API + trang web demo (src/main/resources/static)
├── testing/    # TestNG + REST Assured + Allure
├── database/   # schema.sql, sample-data.sql
└── docs/       # Business rules, API
```

## 1. Chạy backend

Yêu cầu: JDK 25+, Maven 3.9+, MySQL 8 đang chạy.

```bash
cd backend
# Windows PowerShell:  $env:DB_PASSWORD="mat_khau_mysql"
# Git Bash / macOS:    export DB_PASSWORD=mat_khau_mysql
mvn spring-boot:run
```

- Database `flashguard` được tạo tự động (`createDatabaseIfNotExist=true`), bảng tạo tự động bởi JPA.
- Biến môi trường: `DB_URL`, `DB_USERNAME` (mặc định `root`), `DB_PASSWORD` (mặc định rỗng), `PORT` (mặc định `8080`).

Chưa có MySQL? Chạy bằng H2 trong bộ nhớ:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Mở:

- Trang web demo: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

## 2. Dùng trang web demo

1. **Tạo sản phẩm** (vd tồn kho 50).
2. **Tạo Flash Sale** với số lượng 10, thời gian mặc định là từ bây giờ tới 1 giờ sau.
3. Chọn Flash Sale trong danh sách:
   - **Đặt hàng**: thử gửi lại cùng một Idempotency-Key sẽ nhận lại đơn cũ.
   - **Mô phỏng nhiều người mua cùng lúc**: 100 người × 1 sản phẩm → thấy 10 thành công / 90 hết hàng.
   - Bảng đơn: **Thanh toán**, **TT lỗi** (mô phỏng thất bại), **Hủy**. Đặt "Giữ hàng (giây)" = 10 để xem đơn tự hết hạn.
4. Thanh tiến trình hiển thị Đã bán / Đang giữ / Còn lại, kèm dòng kiểm tra số liệu có khớp hay không.

## 3. Chạy TestNG

Backend phải đang chạy trước.

```bash
cd testing
mvn test                                   # chạy toàn bộ testng.xml
mvn test -DbaseUrl=http://localhost:9090   # nếu backend ở cổng khác
```

| Nhóm | Class | Nội dung |
|------|-------|----------|
| Validation | `ProductValidationTest`, `FlashSaleValidationTest` | Giá, số lượng, thời gian hợp lệ / không hợp lệ |
| Order | `OrderTest` | Đặt thành công, hết hàng, chưa mở / đã đóng, hủy, hết hạn |
| Payment | `PaymentTest` | Thành công / thất bại, thanh toán trùng (cả đồng thời) |
| Idempotency | `IdempotencyTest` | Gửi lại cùng key, 20 request đồng thời cùng key |
| Concurrency | `OversellingConcurrencyTest` | **100 request / 10 sản phẩm**, 200 request / 1 sản phẩm, đặt-hủy-trả đan xen |
| Inventory | `InventoryTest` | Reserved / sold / available và tồn kho vật lý sau toàn bộ vòng đời |

## 4. Báo cáo Allure

```bash
cd testing
mvn allure:serve      # tạo báo cáo và mở trình duyệt
# hoặc
mvn allure:report     # tạo file tĩnh ở target/site/allure-maven-plugin/
```

## 5. Cơ chế chống overselling

TestNG chỉ gửi request và kiểm tra kết quả; việc bảo vệ tồn kho nằm hoàn toàn ở backend:

- **Atomic SQL Update**: giữ hàng, trả hàng và chuyển sang đã bán đều dùng **một** câu `UPDATE … WHERE còn đủ hàng`. Không có bước đọc số lượng rồi mới ghi ở tầng Java. Xem [`FlashSaleRepository`](backend/src/main/java/com/flashguard/repository/FlashSaleRepository.java).
- **Chuyển trạng thái có điều kiện**: `UPDATE orders SET status = … WHERE status = 'PENDING'`, nên hủy, thanh toán và hết hạn mỗi việc chỉ xảy ra đúng một lần.
- **Database lock**: khi tạo Flash Sale, dòng sản phẩm bị khóa bằng `SELECT … FOR UPDATE` để không phân bổ vượt tồn kho.
- **Idempotency**: ràng buộc `UNIQUE(idempotency_key)`. Giữ hàng và tạo đơn nằm trong cùng một transaction, nên nếu trùng key thì phần hàng vừa giữ cũng bị rollback.

Chi tiết: [docs/business-rules-and-api.md](docs/business-rules-and-api.md)
