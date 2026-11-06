package com.flashguard.tests.payment;

import com.flashguard.tests.support.ApiClient;
import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.flashguard.tests.support.ApiClient.newKey;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Epic("FlashGuard")
@Feature("Payment")
public class PaymentTest extends BaseApiTest {

    @Test(description = "Thanh toán thành công: đơn PAID, hàng chuyển từ giữ sang đã bán, tồn kho vật lý giảm")
    public void paySuccessfully() {
        long productId = api.createProductOk(1_000_000, 20);
        long fs = api.createOngoingFlashSale(productId, 500_000, 10);
        long orderId = api.placeOrderOk(fs, 2);

        Response r = api.pay(orderId, true);

        assertEquals(r.statusCode(), 200, r.asString());
        assertEquals(r.jsonPath().getString("order.status"), "PAID");
        assertEquals(r.jsonPath().getString("payment.status"), "SUCCESS");
        assertEquals(r.jsonPath().getDouble("payment.amount"), 1_000_000.0);
        assertTrue(r.jsonPath().getString("payment.transactionReference").startsWith("SIM-"));

        assertInventory(fs, 0, 2, 8);
        assertEquals(api.getProduct(productId).jsonPath().getInt("stockQuantity"), 18);
        assertEquals(api.statistics(fs).jsonPath().getDouble("revenue"), 1_000_000.0);
    }

    @Test(description = "Thanh toán thất bại: ghi nhận FAILED, đơn vẫn PENDING và vẫn giữ hàng")
    public void payFailed() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 1);

        Response r = api.pay(orderId, false);

        assertEquals(r.statusCode(), 200, r.asString());
        assertEquals(r.jsonPath().getString("payment.status"), "FAILED");
        assertEquals(r.jsonPath().getString("order.status"), "PENDING");
        assertInventory(fs, 1, 0, 4);
    }

    @Test(description = "Thanh toán thất bại rồi thử lại thành công")
    public void retryAfterFailedPayment() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 1);

        api.pay(orderId, false);
        Response retry = api.pay(orderId, true);

        assertEquals(retry.statusCode(), 200, retry.asString());
        assertEquals(retry.jsonPath().getString("order.status"), "PAID");
        assertEquals(api.payments(orderId).jsonPath().getList("status"), List.of("FAILED", "SUCCESS"));
        assertInventory(fs, 0, 1, 4);
    }

    @Test(description = "Thanh toán trùng: lần hai bị từ chối, chỉ bán một lần")
    public void duplicatePaymentRejected() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 2);

        assertEquals(api.pay(orderId, true).statusCode(), 200);
        assertError(api.pay(orderId, true), 409, "ORDER_ALREADY_PAID");

        assertInventory(fs, 0, 2, 3);
        assertEquals(api.payments(orderId).jsonPath().getList("status"), List.of("SUCCESS"));
    }

    @Test(description = "Gửi 10 request thanh toán cùng lúc cho một đơn: chỉ đúng 1 thành công")
    public void concurrentDuplicatePayment() throws Exception {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 1);
        ApiClient client = ApiClient.silent();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return client.pay(orderId, true).statusCode();
            }));
        }
        start.countDown();
        int ok = 0, conflict = 0;
        for (Future<Integer> f : futures) {
            int s = f.get(30, TimeUnit.SECONDS);
            if (s == 200) ok++;
            else if (s == 409) conflict++;
        }
        pool.shutdown();

        assertEquals(ok, 1, "Chỉ một lần thanh toán được chấp nhận");
        assertEquals(conflict, threads - 1);
        assertInventory(fs, 0, 1, 4);
    }

    @Test(description = "Không thể hủy đơn đã thanh toán")
    public void cannotCancelPaidOrder() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 1);
        api.pay(orderId, true);

        assertError(api.cancel(orderId), 409, "ORDER_ALREADY_PAID");
        assertInventory(fs, 0, 1, 4);
    }

    @Test(description = "Không thể thanh toán đơn đã hủy")
    public void cannotPayCancelledOrder() {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrderOk(fs, 1);
        api.cancel(orderId);

        assertError(api.pay(orderId, true), 409, "ORDER_ALREADY_CANCELLED");
        assertInventory(fs, 0, 0, 5);
    }

    @Test(description = "Không thể thanh toán đơn đã hết hạn")
    public void cannotPayExpiredOrder() throws InterruptedException {
        long fs = api.setupOngoingFlashSale(10, 5);
        long orderId = api.placeOrder(fs, newKey(), 1, 1).jsonPath().getLong("id");

        Thread.sleep(2_000);

        assertError(api.pay(orderId, true), 409, "ORDER_EXPIRED");
        assertEquals(api.getOrder(orderId).jsonPath().getString("status"), "EXPIRED");
        assertInventory(fs, 0, 0, 5);
    }

    @Test(description = "Thanh toán đơn không tồn tại trả về 404")
    public void payUnknownOrder() {
        assertError(api.pay(999_999_999L, true), 404, "ORDER_NOT_FOUND");
    }
}
