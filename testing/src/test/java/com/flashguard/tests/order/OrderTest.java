package com.flashguard.tests.order;

import com.flashguard.tests.support.ApiClient;
import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static com.flashguard.tests.support.ApiClient.flashSaleBody;
import static com.flashguard.tests.support.ApiClient.newKey;
import static org.testng.Assert.assertEquals;

@Epic("FlashGuard")
@Feature("Order")
public class OrderTest extends BaseApiTest {

    @Test(description = "Đặt hàng thành công: 201, đơn PENDING, hàng chuyển sang trạng thái giữ")
    public void placeOrderSuccessfully() {
        long fs = api.setupOngoingFlashSale(20, 10);

        Response r = api.placeOrder(fs, newKey(), 2);

        assertEquals(r.statusCode(), 201, r.asString());
        assertEquals(r.jsonPath().getString("status"), "PENDING");
        assertEquals(r.jsonPath().getInt("quantity"), 2);
        assertEquals(r.jsonPath().getDouble("totalAmount"), 1_000_000.0, "2 x 500.000");
        assertInventory(fs, 2, 0, 8);

        long orderId = r.jsonPath().getLong("id");
        Response get = api.getOrder(orderId);
        assertEquals(get.statusCode(), 200);
        assertEquals(get.jsonPath().getString("status"), "PENDING");
    }

    @Test(description = "Hết hàng: đặt khi available = 0 bị từ chối 409 OUT_OF_STOCK")
    public void rejectWhenSoldOut() {
        long fs = api.setupOngoingFlashSale(10, 2);
        api.placeOrderOk(fs, 1);
        api.placeOrderOk(fs, 1);

        assertError(api.placeOrder(fs, newKey(), 1), 409, "OUT_OF_STOCK");
        assertInventory(fs, 2, 0, 0);
    }

    @Test(description = "Số lượng đặt vượt số lượng còn lại bị từ chối, không giữ một phần")
    public void rejectQuantityAboveAvailable() {
        long fs = api.setupOngoingFlashSale(10, 5);
        api.placeOrderOk(fs, 3);

        assertError(api.placeOrder(fs, newKey(), 3), 409, "OUT_OF_STOCK");
        assertInventory(fs, 3, 0, 2);
    }

    @Test(description = "Số lượng đặt bằng 0 bị từ chối 400")
    public void rejectZeroQuantity() {
        long fs = api.setupOngoingFlashSale(10, 5);
        assertError(api.placeOrder(fs, newKey(), 0), 400, "VALIDATION_ERROR");
        assertInventory(fs, 0, 0, 5);
    }

    @Test(description = "Thiếu Idempotency-Key bị từ chối 400")
    public void rejectMissingIdempotencyKey() {
        long fs = api.setupOngoingFlashSale(10, 5);
        assertError(api.placeOrder(fs, null, 1), 400, "MISSING_IDEMPOTENCY_KEY");
        assertInventory(fs, 0, 0, 5);
    }

    @Test(description = "Flash Sale chưa bắt đầu: không cho đặt")
    public void rejectBeforeStart() {
        long p = api.createProductOk(1_000_000, 10);
        LocalDateTime now = LocalDateTime.now();
        Response created = api.createFlashSale(flashSaleBody(p, 500_000, 5, now.plusHours(1), now.plusHours(2)));
        long fs = created.jsonPath().getLong("id");
        assertEquals(created.jsonPath().getString("phase"), "UPCOMING");

        assertError(api.placeOrder(fs, newKey(), 1), 409, "FLASH_SALE_NOT_STARTED");
    }

    @Test(description = "Flash Sale INACTIVE: không cho đặt dù đang trong khung giờ")
    public void rejectInactiveFlashSale() {
        long p = api.createProductOk(1_000_000, 10);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> body = flashSaleBody(p, 500_000, 5, now.minusMinutes(1), now.plusHours(1));
        body.put("status", "INACTIVE");
        long fs = api.createFlashSale(body).jsonPath().getLong("id");

        assertError(api.placeOrder(fs, newKey(), 1), 409, "FLASH_SALE_NOT_ACTIVE");
    }

    @Test(description = "Flash Sale đã kết thúc: không cho đặt")
    public void rejectAfterEnd() throws InterruptedException {
        long p = api.createProductOk(1_000_000, 10);
        LocalDateTime now = LocalDateTime.now();
        long fs = api.createFlashSale(flashSaleBody(p, 500_000, 5, now.minusMinutes(1), now.plusSeconds(2)))
                .jsonPath().getLong("id");

        Thread.sleep(3_000);

        assertError(api.placeOrder(fs, newKey(), 1), 409, "FLASH_SALE_ENDED");
    }

    @Test(description = "Đặt vào Flash Sale không tồn tại trả về 404")
    public void rejectUnknownFlashSale() {
        assertError(api.placeOrder(999_999_999L, newKey(), 1), 404, "FLASH_SALE_NOT_FOUND");
    }

    @Test(description = "Hủy đơn PENDING: trạng thái CANCELLED và trả hàng giữ về")
    public void cancelPendingOrder() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 2);
        assertInventory(fs, 2, 0, 3);

        Response r = api.cancel(orderId);

        assertEquals(r.statusCode(), 200, r.asString());
        assertEquals(r.jsonPath().getString("status"), "CANCELLED");
        assertInventory(fs, 0, 0, 5);
    }

    @Test(description = "Hủy hai lần: lần hai bị từ chối, hàng chỉ được trả đúng một lần")
    public void cancelTwiceReleasesOnce() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long keep = api.placeOrderOk(fs, 1);
        long orderId = api.placeOrderOk(fs, 2);

        assertEquals(api.cancel(orderId).statusCode(), 200);
        assertError(api.cancel(orderId), 409, "ORDER_ALREADY_CANCELLED");

        // Đơn "keep" vẫn đang giữ 1 → reserved phải là 1 (không bị trừ thêm lần nữa)
        assertInventory(fs, 1, 0, 4);
        assertEquals(api.getOrder(keep).jsonPath().getString("status"), "PENDING");
    }

    @Test(description = "Đơn hết hạn: tự chuyển EXPIRED và trả hàng về")
    public void pendingOrderExpires() throws InterruptedException {
        long fs = api.setupOngoingFlashSale(10, 5);
        Response r = api.placeOrder(fs, newKey(), 3, 1);
        assertEquals(r.statusCode(), 201, r.asString());
        long orderId = r.jsonPath().getLong("id");
        assertInventory(fs, 3, 0, 2);

        Thread.sleep(2_500);

        assertEquals(api.getOrder(orderId).jsonPath().getString("status"), "EXPIRED");
        assertInventory(fs, 0, 0, 5);
        assertError(api.cancel(orderId), 409, "ORDER_EXPIRED");
        assertInventory(fs, 0, 0, 5);
    }

    @Test(description = "Xem đơn không tồn tại trả về 404")
    public void getUnknownOrder() {
        assertError(api.getOrder(999_999_999L), 404, "ORDER_NOT_FOUND");
    }

    @Test(description = "Sau khi hủy, người khác mua lại được phần hàng vừa trả")
    public void releasedStockCanBeBoughtAgain() {
        long fs = api.setupOngoingFlashSale(10, 1);
        long first = api.placeOrderOk(fs, 1);
        assertError(api.placeOrder(fs, ApiClient.newKey(), 1), 409, "OUT_OF_STOCK");

        api.cancel(first);

        assertEquals(api.placeOrder(fs, ApiClient.newKey(), 1).statusCode(), 201);
        assertInventory(fs, 1, 0, 0);
    }
}
