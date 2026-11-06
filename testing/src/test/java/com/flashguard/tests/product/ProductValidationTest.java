package com.flashguard.tests.product;

import com.flashguard.tests.support.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Epic("FlashGuard")
@Feature("Validation - Product")
public class ProductValidationTest extends BaseApiTest {

    @Test(description = "Tạo sản phẩm hợp lệ trả về 201 và đúng dữ liệu")
    public void createValidProduct() {
        Response r = api.createProduct(Map.of("name", "Tai nghe A", "originalPrice", 1_500_000, "stockQuantity", 20));

        assertEquals(r.statusCode(), 201, r.asString());
        assertTrue(r.jsonPath().getLong("id") > 0);
        assertEquals(r.jsonPath().getString("name"), "Tai nghe A");
        assertEquals(r.jsonPath().getDouble("originalPrice"), 1_500_000.0);
        assertEquals(r.jsonPath().getInt("stockQuantity"), 20);

        long id = r.jsonPath().getLong("id");
        assertEquals(api.getProduct(id).statusCode(), 200);
    }

    @DataProvider
    public Object[][] invalidProducts() {
        return new Object[][]{
                {"Tên rỗng", "  ", 100_000, 10, "name"},
                {"Thiếu tên", null, 100_000, 10, "name"},
                {"Giá bằng 0", "SP", 0, 10, "originalPrice"},
                {"Giá âm", "SP", -1, 10, "originalPrice"},
                {"Thiếu giá", "SP", null, 10, "originalPrice"},
                {"Tồn kho âm", "SP", 100_000, -5, "stockQuantity"},
                {"Thiếu tồn kho", "SP", 100_000, null, "stockQuantity"},
        };
    }

    @Test(dataProvider = "invalidProducts", description = "Dữ liệu sản phẩm không hợp lệ bị từ chối với 400")
    public void rejectInvalidProduct(String caseName, String name, Integer price, Integer stock, String field) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("originalPrice", price);
        body.put("stockQuantity", stock);

        Response r = api.createProduct(body);

        assertError(r, 400, "VALIDATION_ERROR");
        assertTrue(r.jsonPath().getMap("fieldErrors").containsKey(field), caseName + ": " + r.asString());
    }

    @Test(description = "Xem sản phẩm không tồn tại trả về 404")
    public void getUnknownProduct() {
        assertError(api.getProduct(999_999_999L), 404, "PRODUCT_NOT_FOUND");
    }
}
