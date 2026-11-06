package com.flashguard.tests.flashsale;

import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static com.flashguard.tests.support.ApiClient.flashSaleBody;
import static org.testng.Assert.assertEquals;

@Epic("FlashGuard")
@Feature("Validation - Flash Sale")
public class FlashSaleValidationTest extends BaseApiTest {

    /** Sản phẩm giá gốc 1.000.000, tồn kho 100. */
    private long productId;

    @BeforeClass
    public void createProduct() {
        productId = api.createProductOk(1_000_000, 100);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    @Test(description = "Tạo Flash Sale hợp lệ: 201, mặc định ACTIVE, available = sale quantity")
    public void createValidFlashSale() {
        Response r = api.createFlashSale(flashSaleBody(productId, 600_000, 10, now().minusMinutes(1), now().plusHours(1)));

        assertEquals(r.statusCode(), 201, r.asString());
        assertEquals(r.jsonPath().getString("status"), "ACTIVE");
        assertEquals(r.jsonPath().getString("phase"), "ONGOING");
        assertEquals(r.jsonPath().getInt("saleQuantity"), 10);
        assertEquals(r.jsonPath().getInt("reservedQuantity"), 0);
        assertEquals(r.jsonPath().getInt("soldQuantity"), 0);
        assertEquals(r.jsonPath().getInt("availableQuantity"), 10);

        long id = r.jsonPath().getLong("id");
        Response get = api.getFlashSale(id);
        assertEquals(get.statusCode(), 200);
        assertEquals(get.jsonPath().getLong("productId"), productId);
    }

    @Test(description = "Giá sale bằng giá gốc bị từ chối")
    public void rejectSalePriceEqualOriginal() {
        Response r = api.createFlashSale(flashSaleBody(productId, 1_000_000, 5, now(), now().plusHours(1)));
        assertError(r, 400, "INVALID_SALE_PRICE");
    }

    @Test(description = "Giá sale cao hơn giá gốc bị từ chối")
    public void rejectSalePriceAboveOriginal() {
        Response r = api.createFlashSale(flashSaleBody(productId, 1_200_000, 5, now(), now().plusHours(1)));
        assertError(r, 400, "INVALID_SALE_PRICE");
    }

    @Test(description = "Giá sale bằng 0 bị từ chối")
    public void rejectZeroSalePrice() {
        Response r = api.createFlashSale(flashSaleBody(productId, 0, 5, now(), now().plusHours(1)));
        assertError(r, 400, "VALIDATION_ERROR");
    }

    @Test(description = "Số lượng sale bằng 0 bị từ chối")
    public void rejectZeroQuantity() {
        Response r = api.createFlashSale(flashSaleBody(productId, 500_000, 0, now(), now().plusHours(1)));
        assertError(r, 400, "VALIDATION_ERROR");
    }

    @Test(description = "Số lượng sale vượt tồn kho sản phẩm bị từ chối")
    public void rejectQuantityAboveStock() {
        long smallProduct = api.createProductOk(1_000_000, 5);
        Response r = api.createFlashSale(flashSaleBody(smallProduct, 500_000, 6, now(), now().plusHours(1)));
        assertError(r, 400, "SALE_QUANTITY_EXCEEDS_STOCK");
    }

    @Test(description = "Tổng số lượng của nhiều Flash Sale không được vượt tồn kho")
    public void rejectOverAllocationAcrossFlashSales() {
        long p = api.createProductOk(1_000_000, 10);
        assertEquals(api.createFlashSale(flashSaleBody(p, 500_000, 7, now(), now().plusHours(1))).statusCode(), 201);

        Response second = api.createFlashSale(flashSaleBody(p, 500_000, 4, now(), now().plusHours(1)));
        assertError(second, 400, "SALE_QUANTITY_EXCEEDS_STOCK");

        Response fits = api.createFlashSale(flashSaleBody(p, 500_000, 3, now(), now().plusHours(1)));
        assertEquals(fits.statusCode(), 201, fits.asString());
    }

    @Test(description = "Thời gian kết thúc trước thời gian bắt đầu bị từ chối")
    public void rejectEndBeforeStart() {
        Response r = api.createFlashSale(flashSaleBody(productId, 500_000, 5, now().plusHours(2), now().plusHours(1)));
        assertError(r, 400, "INVALID_TIME_RANGE");
    }

    @Test(description = "Thời gian kết thúc ở quá khứ bị từ chối")
    public void rejectEndInPast() {
        Response r = api.createFlashSale(flashSaleBody(productId, 500_000, 5, now().minusHours(2), now().minusHours(1)));
        assertError(r, 400, "INVALID_TIME_RANGE");
    }

    @Test(description = "Thiếu thời gian bắt đầu bị từ chối")
    public void rejectMissingStartTime() {
        Map<String, Object> body = flashSaleBody(productId, 500_000, 5, now(), now().plusHours(1));
        body.remove("startTime");
        assertError(api.createFlashSale(body), 400, "VALIDATION_ERROR");
    }

    @Test(description = "Ngày giờ sai định dạng bị từ chối")
    public void rejectMalformedTime() {
        Map<String, Object> body = flashSaleBody(productId, 500_000, 5, now(), now().plusHours(1));
        body.put("startTime", "06/11/2026 10:00");
        assertError(api.createFlashSale(body), 400, "MALFORMED_REQUEST");
    }

    @Test(description = "Sản phẩm không tồn tại trả về 404")
    public void rejectUnknownProduct() {
        Response r = api.createFlashSale(flashSaleBody(999_999_999L, 500_000, 5, now(), now().plusHours(1)));
        assertError(r, 404, "PRODUCT_NOT_FOUND");
    }

    @Test(description = "Xem Flash Sale không tồn tại trả về 404")
    public void getUnknownFlashSale() {
        assertError(api.getFlashSale(999_999_999L), 404, "FLASH_SALE_NOT_FOUND");
    }
}
