package com.flashguard.tests.idempotency;

import com.flashguard.tests.support.ApiClient;
import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static com.flashguard.tests.support.ApiClient.newKey;
import static org.testng.Assert.assertEquals;

@Epic("FlashGuard")
@Feature("Idempotency")
public class IdempotencyTest extends BaseApiTest {

    @Test(description = "Gửi lại cùng key: trả về cùng đơn (200), không giữ thêm hàng")
    public void sameKeyReturnsSameOrder() {
        long fs = api.setupOngoingFlashSale(10, 5);
        String key = newKey();

        Response first = api.placeOrder(fs, key, 2);
        Response second = api.placeOrder(fs, key, 2);

        assertEquals(first.statusCode(), 201, first.asString());
        assertEquals(second.statusCode(), 200, second.asString());
        assertEquals(second.header("Idempotent-Replayed"), "true");
        assertEquals(second.jsonPath().getLong("id"), first.jsonPath().getLong("id"));
        assertEquals(api.statistics(fs).jsonPath().getLong("totalOrders"), 1L);
        assertInventory(fs, 2, 0, 3);
    }

    @Test(description = "Dùng lại key cho yêu cầu khác (khác số lượng) bị từ chối 422")
    public void sameKeyDifferentPayloadRejected() {
        long fs = api.setupOngoingFlashSale(10, 5);
        String key = newKey();
        api.placeOrder(fs, key, 1);

        assertError(api.placeOrder(fs, key, 3), 422, "IDEMPOTENCY_KEY_CONFLICT");
        assertInventory(fs, 1, 0, 4);
    }

    @Test(description = "Dùng lại key cho Flash Sale khác bị từ chối 422")
    public void sameKeyOtherFlashSaleRejected() {
        long fsA = api.setupOngoingFlashSale(10, 5);
        long fsB = api.setupOngoingFlashSale(10, 5);
        String key = newKey();
        api.placeOrder(fsA, key, 1);

        assertError(api.placeOrder(fsB, key, 1), 422, "IDEMPOTENCY_KEY_CONFLICT");
        assertInventory(fsB, 0, 0, 5);
    }

    @Test(description = "Gửi lại key sau khi đơn đã thanh toán vẫn trả về đơn cũ, không tạo đơn mới")
    public void replayAfterPaid() {
        long fs = api.setupOngoingFlashSale(10, 5);
        String key = newKey();
        long orderId = api.placeOrder(fs, key, 1).jsonPath().getLong("id");
        api.pay(orderId, true);

        Response replay = api.placeOrder(fs, key, 1);

        assertEquals(replay.statusCode(), 200);
        assertEquals(replay.jsonPath().getLong("id"), orderId);
        assertEquals(replay.jsonPath().getString("status"), "PAID");
        assertInventory(fs, 0, 1, 4);
    }

    @Test(description = "20 request CÙNG LÚC với cùng một key: chỉ tạo đúng 1 đơn, giữ đúng 1 sản phẩm")
    public void concurrentSameKeyCreatesOneOrder() throws Exception {
        long fs = api.setupOngoingFlashSale(10, 5);
        String key = newKey();
        ApiClient client = ApiClient.silent();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return client.placeOrder(fs, key, 1);
            }));
        }
        start.countDown();

        Set<Long> orderIds = new HashSet<>();
        int created = 0;
        for (Future<Response> f : futures) {
            Response r = f.get(30, TimeUnit.SECONDS);
            assertEquals(r.statusCode() / 100, 2, r.asString());
            if (r.statusCode() == 201) created++;
            orderIds.add(r.jsonPath().getLong("id"));
        }
        pool.shutdown();

        assertEquals(created, 1, "Chỉ một request được tạo đơn mới");
        assertEquals(orderIds.size(), 1, "Mọi request đều nhận về cùng một đơn");
        assertEquals(api.statistics(fs).jsonPath().getLong("totalOrders"), 1L);
        assertInventory(fs, 1, 0, 4);
    }
}
