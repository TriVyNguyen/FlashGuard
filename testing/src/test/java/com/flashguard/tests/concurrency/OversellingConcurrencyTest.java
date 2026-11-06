package com.flashguard.tests.concurrency;

import com.flashguard.tests.support.ApiClient;
import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Kịch bản chính của FlashGuard: nhiều người mua cùng lúc một sản phẩm có số lượng giới hạn.
 * Tất cả luồng chờ ở vạch xuất phát (CountDownLatch) rồi cùng gửi request để tối đa hóa va chạm.
 */
@Epic("FlashGuard")
@Feature("Concurrency")
public class OversellingConcurrencyTest extends BaseApiTest {

    private final ApiClient silent = ApiClient.silent();

    /** Kết quả của một đợt bắn đồng thời: đếm theo HTTP status + mã lỗi. */
    private record Burst(Map<String, Long> outcomes, List<Long> createdOrderIds, long elapsedMs) {
        long count(String key) {
            return outcomes.getOrDefault(key, 0L);
        }
    }

    private Burst fireConcurrently(long flashSaleId, int requests, int quantityEach) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();

        for (int i = 0; i < requests; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return silent.placeOrder(flashSaleId, ApiClient.newKey(), quantityEach);
            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        long t0 = System.nanoTime();
        start.countDown();

        List<Response> responses = new ArrayList<>();
        for (Future<Response> f : futures) {
            responses.add(f.get(60, TimeUnit.SECONDS));
        }
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();

        Map<String, Long> outcomes = responses.stream().collect(Collectors.groupingBy(
                r -> r.statusCode() == 201 ? "201" : r.statusCode() + " " + r.jsonPath().getString("code"),
                Collectors.counting()));
        List<Long> created = responses.stream().filter(r -> r.statusCode() == 201)
                .map(r -> r.jsonPath().getLong("id")).toList();

        Allure.addAttachment("Kết quả " + requests + " request đồng thời",
                "Thời gian: " + elapsed + " ms\n" + outcomes);
        System.out.printf("       %d request đồng thời → %s (%d ms)%n", requests, outcomes, elapsed);
        return new Burst(outcomes, created, elapsed);
    }

    @Test(description = "100 request mua cùng lúc 10 sản phẩm: đúng 10 thành công, 90 hết hàng, không overselling")
    @Severity(SeverityLevel.BLOCKER)
    public void hundredBuyersTenItems() throws Exception {
        long fs = api.setupOngoingFlashSale(10, 10);

        Burst burst = fireConcurrently(fs, 100, 1);

        assertEquals(burst.count("201"), 10L, "Số đơn giữ hàng thành công: " + burst.outcomes());
        assertEquals(burst.count("409 OUT_OF_STOCK"), 90L, "Số request bị từ chối vì hết hàng: " + burst.outcomes());
        assertEquals(burst.createdOrderIds().stream().distinct().count(), 10L, "Không có đơn trùng");
        assertInventory(fs, 10, 0, 0);
        assertEquals(api.statistics(fs).jsonPath().getLong("totalOrders"), 10L);
    }

    @Test(description = "100 request, mỗi request mua 3, Flash Sale có 10: tối đa 3 đơn (9 sản phẩm), không vượt 10")
    public void multiQuantityNeverExceedsLimit() throws Exception {
        long fs = api.setupOngoingFlashSale(10, 10);

        Burst burst = fireConcurrently(fs, 100, 3);

        assertEquals(burst.count("201"), 3L, burst.outcomes().toString());
        assertEquals(burst.count("409 OUT_OF_STOCK"), 97L, burst.outcomes().toString());
        assertInventory(fs, 9, 0, 1);
    }

    @Test(description = "200 request mua cùng lúc 1 sản phẩm duy nhất: đúng 1 người mua được")
    public void lastItemRace() throws Exception {
        long fs = api.setupOngoingFlashSale(1, 1);

        Burst burst = fireConcurrently(fs, 200, 1);

        assertEquals(burst.count("201"), 1L, burst.outcomes().toString());
        assertInventory(fs, 1, 0, 0);
    }

    @Test(description = "Đặt, hủy và thanh toán đan xen đồng thời: số liệu cuối vẫn khớp, không âm, không vượt")
    public void mixedConcurrentOperationsStayConsistent() throws Exception {
        long fs = api.setupOngoingFlashSale(30, 20);

        // Đợt 1: 60 người tranh 20 sản phẩm
        Burst first = fireConcurrently(fs, 60, 1);
        assertEquals(first.count("201"), 20L, first.outcomes().toString());

        // Đồng thời: hủy 5 đơn đầu, thanh toán 5 đơn kế tiếp, và 40 người khác tiếp tục đặt
        List<Long> ids = first.createdOrderIds();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long id = ids.get(i);
            futures.add(pool.submit(() -> { start.await(); return silent.cancel(id); }));
        }
        for (int i = 5; i < 10; i++) {
            long id = ids.get(i);
            futures.add(pool.submit(() -> { start.await(); return silent.pay(id, true); }));
        }
        List<Future<Response>> newOrders = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            newOrders.add(pool.submit(() -> { start.await(); return silent.placeOrder(fs, ApiClient.newKey(), 1); }));
        }
        start.countDown();
        for (Future<Response> f : futures) {
            assertEquals(f.get(60, TimeUnit.SECONDS).statusCode(), 200);
        }
        long secondWave = 0;
        for (Future<Response> f : newOrders) {
            if (f.get(60, TimeUnit.SECONDS).statusCode() == 201) secondWave++;
        }
        pool.shutdown();

        // 5 suất được trả lại do hủy → đợt 2 mua được tối đa 5
        assertTrue(secondWave <= 5, "Đợt 2 mua được " + secondWave + " > 5 suất vừa trả");

        Response s = api.statistics(fs);
        int reserved = s.jsonPath().getInt("reservedQuantity");
        int sold = s.jsonPath().getInt("soldQuantity");
        assertEquals(sold, 5);
        assertEquals(reserved, 10 + (int) secondWave);
        assertTrue(reserved + sold <= 20, "Không được vượt số lượng sale");
        assertEquals(s.jsonPath().getBoolean("consistent"), true, s.asString());
    }
}
