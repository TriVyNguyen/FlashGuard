package com.flashguard.tests.inventory;

import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static com.flashguard.tests.support.ApiClient.newKey;
import static org.testng.Assert.assertEquals;

@Epic("FlashGuard")
@Feature("Inventory")
public class InventoryTest extends BaseApiTest {

    @Test(description = "Vòng đời đầy đủ: giữ / bán / hủy / hết hạn → reserved, sold, available và tồn kho vật lý đều đúng")
    public void fullLifecycleKeepsInventoryCorrect() throws InterruptedException {
        long productId = api.createProductOk(1_000_000, 50);
        long fs = api.createOngoingFlashSale(productId, 400_000, 10);

        long o1 = api.placeOrderOk(fs, 2);
        long o2 = api.placeOrderOk(fs, 1);
        long o3 = api.placeOrderOk(fs, 3);
        long o4 = api.placeOrder(fs, newKey(), 1, 1).jsonPath().getLong("id"); // sẽ hết hạn
        assertInventory(fs, 7, 0, 3);

        api.pay(o1, true);           // 2 → sold
        api.pay(o3, true);           // 3 → sold
        api.cancel(o2);              // 1 → trả lại
        Thread.sleep(2_000);
        api.getOrder(o4);            // 1 → hết hạn, trả lại

        assertInventory(fs, 0, 5, 5);
        assertEquals(api.getProduct(productId).jsonPath().getInt("stockQuantity"), 45, "50 - 5 đã bán");

        Response s = api.statistics(fs);
        assertEquals(s.jsonPath().getLong("ordersByStatus.PAID"), 2L);
        assertEquals(s.jsonPath().getLong("ordersByStatus.CANCELLED"), 1L);
        assertEquals(s.jsonPath().getLong("ordersByStatus.EXPIRED"), 1L);
        assertEquals(s.jsonPath().getDouble("revenue"), 2_000_000.0, "5 x 400.000");
    }

    @Test(description = "Công thức: Available = Sale Quantity - Reserved - Sold")
    public void availableFormula() {
        long fs = api.setupOngoingFlashSale(20, 12);
        long a = api.placeOrderOk(fs, 4);
        api.placeOrderOk(fs, 3);
        api.pay(a, true);

        Response s = api.statistics(fs);
        int sale = s.jsonPath().getInt("saleQuantity");
        int reserved = s.jsonPath().getInt("reservedQuantity");
        int sold = s.jsonPath().getInt("soldQuantity");
        int available = s.jsonPath().getInt("availableQuantity");

        assertEquals(available, sale - reserved - sold);
        assertEquals(available, 5);
    }

    @Test(description = "Bán hết rồi hủy tất cả: tồn kho quay về ban đầu, không âm")
    public void sellOutThenCancelAll() {
        long fs = api.setupOngoingFlashSale(5, 5);
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) ids[i] = api.placeOrderOk(fs, 1);
        assertInventory(fs, 5, 0, 0);

        for (long id : ids) api.cancel(id);
        for (long id : ids) api.cancel(id); // hủy lặp lại không được trừ thêm

        assertInventory(fs, 0, 0, 5);
    }
}
