package com.flashguard.tests.concurrency;

import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.flashguard.tests.support.ApiClient.newKey;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Cùng kịch bản "100 người mua 10 sản phẩm" như {@link OversellingConcurrencyTest},
 * nhưng để TestNG tự giả lập 100 người mua thay vì tự viết code tạo luồng.
 *
 * <pre>
 *                      OversellingConcurrencyTest       TestNgInvocationConcurrencyTest (file này)
 * Ai tạo 100 người     Java: ExecutorService            TestNG: invocationCount + threadPoolSize
 * Số test trong báo cáo 1 test                          100 lần chạy (mỗi người mua 1 dòng) + 1 bước kiểm tra
 * Allure Timeline      1 thanh                          100 thanh song song
 * Request đính kèm     không (chỉ có bảng tổng hợp)     từng người mua có request/response riêng
 * Một người mua lỗi    khó thấy, gộp trong 1 test       thấy ngay lần chạy nào đỏ; bước kiểm tra tự SKIP
 * </pre>
 */
@Epic("FlashGuard")
@Feature("Concurrency")
public class TestNgInvocationConcurrencyTest extends BaseApiTest {

    /** Hằng số vì giá trị trong annotation @Test phải biết trước lúc biên dịch. */
    private static final int BUYERS = 100;
    private static final int STOCK = 10;

    private long flashSaleId;

    /** Vạch xuất phát: lần chạy nào tới trước phải đứng chờ đủ 100 lần chạy rồi mới cùng gửi request. */
    private final CyclicBarrier startLine = new CyclicBarrier(BUYERS);

    /** 100 luồng cùng ghi kết quả nên phải dùng cấu trúc dữ liệu an toàn cho đa luồng. */
    private final Map<String, Integer> outcomes = new ConcurrentHashMap<>();
    private final Set<Long> createdOrderIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger buyerCounter = new AtomicInteger();

    /** Chạy 1 lần trước cả 100 người mua: tạo một Flash Sale có 10 sản phẩm. */
    @BeforeClass
    public void prepareFlashSale() {
        flashSaleId = api.setupOngoingFlashSale(STOCK, STOCK);
    }

    /**
     * Một người mua đặt 1 sản phẩm.
     * invocationCount = 100: TestNG gọi hàm này 100 lần.
     * threadPoolSize  = 100: 100 lần đó chạy trên 100 luồng song song → 100 người mua cùng lúc.
     *
     * Mỗi người mua chỉ cần nhận về một kết quả hợp lệ: 201 (mua được) hoặc 409 OUT_OF_STOCK (hết hàng).
     * Việc kiểm tra "đúng 10 người mua được" thuộc về bước verifyNoOverselling bên dưới.
     */
    @Test(invocationCount = BUYERS, threadPoolSize = BUYERS,
            description = "TestNG giả lập 1 người mua (chạy 100 lần song song)")
    @Story("TestNG invocationCount: 100 người mua cùng lúc")
    @Severity(SeverityLevel.BLOCKER)
    public void oneBuyerPlacesOrder() throws Exception {
        int buyerNo = buyerCounter.incrementAndGet();
        Allure.parameter("Người mua số", buyerNo);

        startLine.await(30, TimeUnit.SECONDS);

        Response r = api.placeOrder(flashSaleId, newKey(), 1);

        String outcome = r.statusCode() == 201 ? "201" : r.statusCode() + " " + r.jsonPath().getString("code");
        outcomes.merge(outcome, 1, Integer::sum);
        if (r.statusCode() == 201) {
            createdOrderIds.add(r.jsonPath().getLong("id"));
        }

        assertTrue(outcome.equals("201") || outcome.equals("409 OUT_OF_STOCK"),
                "Người mua số " + buyerNo + " nhận kết quả không hợp lệ: " + outcome + " — " + r.asString());
    }

    /**
     * dependsOnMethods: chỉ chạy sau khi đủ 100 lần oneBuyerPlacesOrder đều PASS.
     * Nếu có người mua nhận lỗi lạ (vd 500), bước này tự chuyển SKIP chứ không báo FAIL,
     * nhờ vậy nhìn báo cáo là biết lỗi gốc nằm ở người mua nào.
     */
    @Test(dependsOnMethods = "oneBuyerPlacesOrder",
            description = "Sau 100 người mua: đúng 10 thành công, 90 hết hàng, không overselling")
    @Story("TestNG invocationCount: 100 người mua cùng lúc")
    @Severity(SeverityLevel.BLOCKER)
    public void verifyNoOverselling() {
        Allure.addAttachment("Tổng hợp " + BUYERS + " người mua (TestNG invocationCount)", outcomes.toString());
        System.out.printf("       TestNG invocationCount=%d → %s%n", BUYERS, outcomes);

        assertEquals(outcomes.getOrDefault("201", 0).intValue(), STOCK, "Số người mua được: " + outcomes);
        assertEquals(outcomes.getOrDefault("409 OUT_OF_STOCK", 0).intValue(), BUYERS - STOCK,
                "Số người bị từ chối vì hết hàng: " + outcomes);
        assertEquals(createdOrderIds.size(), STOCK, "Không có đơn trùng");
        assertInventory(flashSaleId, STOCK, 0, 0);
    }
}
