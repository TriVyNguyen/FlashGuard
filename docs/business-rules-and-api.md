# FlashGuard — Business rules & API

## Luật nghiệp vụ và nơi cài đặt

| # | Luật | Cài đặt ở đâu |
|---|------|---------------|
| 1 | Chỉ đặt được khi Flash Sale `ACTIVE` và đang trong khung giờ | Điều kiện `status = ACTIVE AND start_time <= now AND end_time > now` nằm ngay trong câu UPDATE giữ hàng (`FlashSaleRepository.tryReserve`) |
| 2 | Số lượng đặt không vượt số lượng còn lại | Điều kiện `sale_quantity - reserved - sold >= qty` trong cùng câu UPDATE |
| 3 | Mỗi yêu cầu đặt phải có Idempotency Key | Header `Idempotency-Key` bắt buộc + `UNIQUE(idempotency_key)` trên bảng `orders` |
| 4 | Tạo đơn → giữ hàng; thanh toán thành công → chuyển giữ sang đã bán | `tryReserve` khi tạo đơn; `confirmSold` (reserved − q, sold + q) khi thanh toán |
| 5 | Hủy / hết hạn → trả hàng đúng một lần | Đổi trạng thái bằng UPDATE có điều kiện `WHERE status = 'PENDING'`; chỉ request nào đổi được (1 dòng) mới trả hàng |
| 6 | Không bán vượt, không âm tồn kho | Mọi thay đổi số lượng đều là UPDATE có điều kiện; `products.stock_quantity` chỉ giảm khi `stock_quantity >= q` |

**Công thức:** `Available = Sale Quantity − Reserved Quantity − Sold Quantity`

**Phân bổ khi tạo Flash Sale:** tổng `(sale_quantity − sold_quantity)` của các Flash Sale chưa kết thúc của cùng sản phẩm + số lượng mới ≤ `stock_quantity`. Dòng sản phẩm bị khóa (`SELECT … FOR UPDATE`) trong lúc kiểm tra.

## Vì sao không bán vượt?

Cách sai (race condition):

```
đọc available = 1        ← request A
đọc available = 1        ← request B (cùng lúc)
A ghi reserved + 1
B ghi reserved + 1       → bán 2 trong khi chỉ còn 1
```

Cách FlashGuard làm (Atomic SQL Update):

```sql
UPDATE flash_sales
SET reserved_quantity = reserved_quantity + :qty
WHERE id = :id
  AND status = 'ACTIVE' AND start_time <= :now AND end_time > :now
  AND sale_quantity - reserved_quantity - sold_quantity >= :qty;
```

MySQL khóa dòng trong lúc UPDATE nên các request xếp hàng lần lượt. Câu lệnh trả về số dòng bị ảnh hưởng:
`1` = giữ hàng thành công, `0` = hết hàng / chưa mở bán → trả 409.

## Vòng đời đơn hàng

```
           pay(success)
PENDING ─────────────────▶ PAID
   │  \
   │   \ cancel
   │    ▶ CANCELLED   (trả hàng)
   │
   └── quá expires_at ──▶ EXPIRED (trả hàng; job quét mỗi 2 giây + kiểm tra khi GET đơn)
```

Thanh toán thất bại (`{"success": false}`) chỉ ghi một payment `FAILED`, đơn vẫn `PENDING` và có thể thanh toán lại tới khi hết hạn.

## API

| Method | Endpoint | Mô tả | Mã trả về |
|--------|----------|-------|-----------|
| POST | `/api/products` | Tạo sản phẩm | 201, 400 |
| GET | `/api/products` · `/api/products/{id}` | Xem sản phẩm | 200, 404 |
| POST | `/api/flash-sales` | Tạo Flash Sale | 201, 400, 404 |
| GET | `/api/flash-sales` · `/api/flash-sales/{id}` | Xem Flash Sale | 200, 404 |
| GET | `/api/flash-sales/{id}/statistics` | Thống kê | 200, 404 |
| POST | `/api/flash-sales/{id}/orders` | Đặt hàng (header `Idempotency-Key`) | 201 mới, 200 gửi lại cùng key, 400, 404, 409, 422 |
| GET | `/api/flash-sales/{id}/orders` | Danh sách đơn | 200 |
| GET | `/api/orders/{id}` | Xem đơn | 200, 404 |
| POST | `/api/orders/{id}/pay` | Mô phỏng thanh toán, body `{"success": true/false}` | 200, 404, 409 |
| POST | `/api/orders/{id}/cancel` | Hủy đơn | 200, 404, 409 |
| GET | `/api/orders/{id}/payments` | Lịch sử thanh toán | 200 |
| POST | `/api/orders/expire-overdue` | Chạy ngay việc hết hạn đơn | 200 |

### Ví dụ

```http
POST /api/flash-sales/1/orders
Idempotency-Key: 6f1c...-a2
Content-Type: application/json

{ "quantity": 1, "holdSeconds": 300 }
```

Lỗi luôn có dạng:

```json
{ "timestamp": "...", "status": 409, "code": "OUT_OF_STOCK", "message": "...", "path": "...", "fieldErrors": null }
```

### Mã lỗi

| code | HTTP | Khi nào |
|------|------|---------|
| `VALIDATION_ERROR` | 400 | Thiếu/sai trường (xem `fieldErrors`) |
| `MALFORMED_REQUEST` | 400 | JSON hoặc ngày giờ sai định dạng |
| `MISSING_IDEMPOTENCY_KEY` | 400 | Thiếu header `Idempotency-Key` |
| `INVALID_SALE_PRICE` | 400 | Giá sale ≥ giá gốc |
| `INVALID_TIME_RANGE` | 400 | Kết thúc ≤ bắt đầu, hoặc kết thúc ở quá khứ |
| `SALE_QUANTITY_EXCEEDS_STOCK` | 400 | Số lượng sale vượt tồn kho có thể bán |
| `PRODUCT_NOT_FOUND` / `FLASH_SALE_NOT_FOUND` / `ORDER_NOT_FOUND` | 404 | Không tồn tại |
| `FLASH_SALE_NOT_ACTIVE` / `FLASH_SALE_NOT_STARTED` / `FLASH_SALE_ENDED` | 409 | Ngoài thời gian / tạm dừng |
| `OUT_OF_STOCK` | 409 | Không đủ hàng |
| `IDEMPOTENCY_KEY_CONFLICT` | 422 | Key đã dùng cho yêu cầu khác (khác Flash Sale / số lượng) |
| `ORDER_ALREADY_PAID` / `ORDER_ALREADY_CANCELLED` / `ORDER_EXPIRED` | 409 | Đơn không còn ở trạng thái PENDING |
