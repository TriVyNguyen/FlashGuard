# Hướng dẫn demo FlashGuard

Thời lượng: khoảng **15 phút**. Nhân vật chính: **TestNG**. Swagger và Allure là hai vai phụ.

Một câu tóm tắt để mở đầu và kết thúc:
> **Swagger** cho biết API trông thế nào → **TestNG + REST Assured** tự động gọi API, kể cả 100 người cùng lúc, và kiểm tra kết quả → **Allure** ghi lại bằng chứng.

Thuật ngữ: đây là **API testing + concurrency testing**, không phải unit test.

---

## A. Chuẩn bị từ hôm trước (làm 1 lần)

1. **Chạy thử toàn bộ một lượt** theo mục B và C bên dưới, để Maven tải sẵn các thư viện và công cụ Allure. Lần đầu tải có thể mất vài phút, đừng để việc này xảy ra ngay trước mặt thầy.
2. **Chụp ảnh màn hình dự phòng**, phòng khi máy gặp sự cố:
   - Terminal có dòng `100 request đồng thời → {201=10, 409 OUT_OF_STOCK=90}`, dòng `TestNG invocationCount=100 → ...` và `Tests run: 158, Failures: 0`
   - Allure: Timeline với 100 thanh song song của cách 2
   - Allure: Overview, Behaviors, và test `hundredBuyersTenItems`
   - Trang web sau khi mô phỏng 100 người
3. Phóng to chữ terminal và VS Code (Ctrl + lăn chuột) cho cuối lớp đọc được.

---

## B. Chuẩn bị 10 phút trước khi lên

### B1. Bật backend (terminal số 1)
```powershell
cd D:\FlashGuard\backend
$env:DB_PASSWORD="mat_khau_mysql"
mvn spring-boot:run
```
Chờ tới khi thấy dòng `Started FlashGuardApplication`. **Để nguyên cửa sổ này, không tắt.**

### B2. Mở sẵn các cửa sổ theo thứ tự sẽ dùng
| # | Cửa sổ | Mở gì |
|---|--------|-------|
| 1 | Trình duyệt, tab 1 | `http://localhost:8080` (trang web demo) |
| 2 | Trình duyệt, tab 2 | `http://localhost:8080/swagger-ui.html` |
| 3 | Terminal số 2 | Đứng sẵn ở `D:\FlashGuard\testing` |
| 4 | VS Code | Mở sẵn 5 file (bên dưới) |

5 file mở sẵn trong VS Code:
- `testing/src/test/resources/testng.xml`
- `testing/src/test/java/com/flashguard/tests/support/ApiClient.java`
- `testing/src/test/java/com/flashguard/tests/concurrency/OversellingConcurrencyTest.java`
- `testing/src/test/java/com/flashguard/tests/concurrency/TestNgInvocationConcurrencyTest.java`
- `backend/src/main/java/com/flashguard/repository/FlashSaleRepository.java`

### B3. Làm sạch dữ liệu
Trên trang web, bấm **"Làm mới dữ liệu demo"** → danh sách chỉ còn 2 đợt: **iPhone 17 Pro** (10 cái) và **AirPods Pro 3** (20 cái).

---

## C. Kịch bản trên sân khấu

### Màn 1 — Đặt vấn đề (1 phút) · *cửa sổ: trang web*

**Nói:**
> "11 giờ trưa mở bán 10 chiếc iPhone giá sốc, 100 người bấm Mua cùng lúc. Mọi người đoán hệ thống bán ra bao nhiêu chiếc?"

Để lớp đoán vài giây, rồi nói tiếp:
> "Nếu code viết theo kiểu đọc số lượng rồi mới trừ, hai người có thể cùng thấy 'còn 1' và cùng mua được, tức là bán vượt. Lỗi này gọi là race condition. Nhóm em xây hệ thống chặn lỗi đó và dùng TestNG để **chứng minh** nó thật sự được chặn."

### Màn 2 — Giới thiệu hệ thống qua trang web (2 phút) · *cửa sổ: trang web*

**Làm:** chọn **AirPods Pro 3**.

**Nói** (chỉ vào thanh màu và các ô số):
> "Số lượng sale là 20. **Đang giữ** là hàng của đơn đã đặt nhưng chưa trả tiền. **Đã bán** là đơn đã trả tiền. **Còn lại** bằng sale trừ đang giữ trừ đã bán."

**Làm:** đặt hàng 2 cái → thấy Đang giữ = 2 → bấm **Thanh toán** ở đơn vừa tạo → Đã bán = 2.

**Nói:**
> "Đó là luồng mua hàng bình thường. Một người bấm thì luôn đúng. Câu hỏi là 100 người cùng bấm thì sao."

### Màn 3 — Swagger: hợp đồng API (2 phút) · *cửa sổ: tab Swagger*

**Nói:**
> "Phía sau trang web là các API này. Swagger được sinh tự động từ code, cho biết gọi API nào, gửi gì, nhận gì."

**Làm:**
1. Mở `POST /api/flash-sales/{id}/orders` → bấm **Try it out**.
2. Điền `id` của đợt AirPods (xem trên trang web, ví dụ `#2`), `Idempotency-Key` = `demo-001`, body `{ "quantity": 1 }` → bấm **Execute** → kết quả **201**.
3. Bấm **Execute** thêm lần nữa → kết quả **200**, cùng mã đơn.

**Nói:**
> "Gửi lại cùng key thì không tạo đơn thứ hai, giống khi người dùng bấm 2 lần vì mạng lag. Nhưng Swagger chỉ bấm được từng request một. 100 người cùng lúc thì tay người không làm được. Đó là việc của TestNG."

### Màn 4 — Từ Swagger sang code: REST Assured (1 phút) · *cửa sổ: VS Code → `ApiClient.java`*

**Làm:** chỉ vào hàm `placeOrder`.

**Nói:**
> "Đúng URL, header và body vừa bấm trên Swagger, giờ viết thành code bằng REST Assured để máy gửi được bao nhiêu lần cũng được."

**Làm:** chỉ vào dòng `addFilter(new AllureRestAssured())`.

**Nói:**
> "Dòng này nối với Allure: mọi request đều tự động được ghi vào báo cáo. Lát nữa mọi người sẽ thấy."

### Màn 5 — TestNG: nhân vật chính (6 phút)

**5a. Bảng điều khiển** · *VS Code → `testng.xml`*
> "TestNG tổ chức các test thành 6 nhóm, đúng bảng kế hoạch kiểm thử: Validation, Order, Payment, Idempotency, Concurrency, Inventory. Thứ tự chạy và listener đều khai báo ở đây."

**5b. Cách 1 — tự viết code Java tạo 100 người** · *VS Code → `OversellingConcurrencyTest.java`*

Chỉ vào hàm `fireConcurrently`, phần `ExecutorService` và 2 `CountDownLatch`:
> "Cách thứ nhất: tự viết khoảng 30 dòng Java để tạo 100 luồng, cho đứng chờ ở vạch xuất phát rồi thả cùng lúc. Chạy được, nhưng đây là **Java thuần**, TestNG chỉ gọi hàm và chấm kết quả."

Chỉ vào hàm `hundredBuyersTenItems`, đọc các dòng kiểm tra:
> "Đúng 10 người mua được, 90 người bị từ chối vì hết hàng, không có đơn trùng, tồn kho không âm."

**5c. Cách 2 — để TestNG tự tạo 100 người** · *VS Code → `TestNgInvocationConcurrencyTest.java`*

Chỉ vào đúng một dòng annotation:
```java
@Test(invocationCount = BUYERS, threadPoolSize = BUYERS)
public void oneBuyerPlacesOrder()
```
> "Cách thứ hai: hàm này chỉ mô tả **một** người mua. `invocationCount = 100` là TestNG gọi nó 100 lần, `threadPoolSize = 100` là chạy 100 lần đó song song. **Một dòng annotation thay cho 30 dòng code tạo luồng.**"

Chỉ vào `@Test(dependsOnMethods = "oneBuyerPlacesOrder")` của hàm `verifyNoOverselling`:
> "Bước kiểm tra chỉ chạy khi đủ 100 người mua xong. Nếu có người mua gặp lỗi lạ, bước này tự chuyển **SKIP**, nhìn báo cáo là biết lỗi gốc nằm ở người mua nào."

Chốt sự khác biệt:
| | Cách 1: code Java | Cách 2: TestNG |
|---|---|---|
| Ai tạo 100 người | Mình tự viết `ExecutorService` | TestNG, bằng `invocationCount` + `threadPoolSize` |
| Trong báo cáo | 1 test, 1 bảng tổng hợp | 100 lần chạy, **mỗi người mua có request/response riêng** |
| Một người mua lỗi | Gộp trong 1 test, khó thấy | Thấy ngay lần chạy nào đỏ |

**5d. Chạy thật** · *terminal số 2*
```powershell
mvn clean test
```
Trong lúc chờ (khoảng 25 giây), chỉ cho lớp các dòng `PASS` chạy dần theo từng nhóm.

Khi tới nhóm Concurrency, **dừng lại 2 giây** ở dòng:
```
100 request đồng thời → {201=10, 409 OUT_OF_STOCK=90}
```
> "Cách 1: 100 người, đúng 10 người mua được."

Ngay sau đó là dòng của cách 2:
```
TestNG invocationCount=100 → {201=10, 409 OUT_OF_STOCK=90}
```
> "Cách 2 cho cùng kết quả. Hai cách khác nhau, cùng chứng minh không bán vượt."

Kết thúc ở dòng:
```
Tests run: 158, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
> "158 lần chạy: 57 test, trong đó test cách 2 tính là 100 lần chạy của người mua cộng 1 bước kiểm tra."

### Màn 6 — Allure: bằng chứng (3 phút) · *terminal số 2 → trình duyệt*
```powershell
mvn allure:serve
```
Trình duyệt tự mở báo cáo.

| Bấm vào | Nói |
|---------|-----|
| **Overview** | "158 lần chạy, 100% đạt." |
| **Behaviors** | "6 nhóm, đúng bảng kế hoạch trong đề." |
| Một test trong **Payment** (ví dụ `duplicatePaymentRejected`) → phần request/response | "Đây là request thật đã gửi, giống request mình bấm trên Swagger lúc nãy, giờ được ghi lại tự động." |
| **Concurrency** → `hundredBuyersTenItems` → file đính kèm | "Cách 1: chỉ có **1 test** với một bảng tổng hợp: 10 thành công, 90 hết hàng." |
| **Concurrency** → **TestNG giả lập 1 người mua** | "Cách 2: **100 dòng**, mỗi dòng là một người mua." Bấm vào một dòng → có tham số **Người mua số**, kèm request/response riêng: "người này nhận 201, mua được" hoặc "409, hết hàng". |
| **Timeline** | "Cách 2: **100 thanh nằm song song cùng một thời điểm**, mỗi thanh một luồng. Đây là '100 người cùng lúc' nhìn bằng mắt." |
| **Concurrency** → `verifyNoOverselling` → file đính kèm | "Bước kiểm tra sau cùng tổng hợp lại: 10 thành công, 90 hết hàng." |

Xem xong, quay lại terminal nhấn **Ctrl+C** để tắt Allure.

### Màn 7 — Vì sao test xanh (1 phút) · *VS Code → `FlashSaleRepository.java`*

Chỉ vào câu `UPDATE` trong hàm `tryReserve`:
> "Kiểm tra còn hàng và giữ hàng nằm trong **một câu lệnh**. Database khóa dòng trong lúc chạy, nên 100 request phải xếp hàng lần lượt. Câu lệnh trả về 1 là giữ được hàng, trả về 0 là hết hàng."

### Màn 8 — Kết (30 giây)

> "Quay lại câu hỏi đầu: bán được bao nhiêu chiếc? Backend chặn bán vượt bằng câu UPDATE nguyên tử. TestNG đứng ngoài, bắn 100 request cùng lúc để **chứng minh** điều đó, chạy bao nhiêu lần cũng ra 10. Allure ghi lại bằng chứng. Em xin hết."

---

## D. Nếu có sự cố

| Hiện tượng | Nguyên nhân | Xử lý |
|------------|-------------|-------|
| `mvn test` báo toàn **SKIP**, có chữ "Backend chưa chạy" | Backend chưa bật hoặc đã tắt | Kiểm tra terminal số 1, chạy lại B1 |
| Backend báo `Port 8080 was already in use` | Còn một backend cũ đang chạy | Tìm và tắt tiến trình cũ: `netstat -ano \| findstr :8080`, rồi `taskkill /PID <số> /F` |
| Backend báo lỗi `Access denied for user 'root'` | Sai mật khẩu MySQL | Đặt lại `$env:DB_PASSWORD` cho đúng rồi chạy lại |
| Trang web đầy Flash Sale rác | Mỗi lần chạy test tạo thêm dữ liệu | Bấm **"Làm mới dữ liệu demo"** |
| Đặt hàng báo `FLASH_SALE_ENDED` | Đợt mẫu chỉ kéo dài 3 giờ | Bấm **"Làm mới dữ liệu demo"** để có đợt mới |
| Allure hiện hơn 158 lần chạy | Quên `clean`, kết quả cũ bị cộng dồn | Chạy lại `mvn clean test` rồi `mvn allure:serve` |
| Allure mở ra trang trắng | Mở trực tiếp file `index.html` | Luôn dùng `mvn allure:serve` |
| Máy treo, mạng hỏng | | Dùng ảnh chụp dự phòng ở mục A |

## E. Thứ tự lệnh tóm tắt (dán lên màn hình phụ)

```
Terminal 1:  cd D:\FlashGuard\backend   →  $env:DB_PASSWORD="..."  →  mvn spring-boot:run
Web:         Làm mới dữ liệu demo
Terminal 2:  cd D:\FlashGuard\testing   →  mvn clean test  →  mvn allure:serve
```
