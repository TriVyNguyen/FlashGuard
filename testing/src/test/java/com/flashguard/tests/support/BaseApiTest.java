package com.flashguard.tests.support;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeSuite;

import static org.testng.Assert.assertEquals;

public abstract class BaseApiTest {

    protected final ApiClient api = ApiClient.standard();

    @BeforeSuite(alwaysRun = true)
    public void checkBackendIsRunning() {
        try {
            RestAssured.given().baseUri(ApiClient.BASE_URL).get("/api/products").then().statusCode(200);
        } catch (Throwable e) {
            throw new SkipException("Backend chưa chạy ở " + ApiClient.BASE_URL
                    + " — hãy khởi động backend trước khi chạy test. Chi tiết: " + e.getMessage());
        }
    }

    /** Kiểm tra mã HTTP và mã lỗi nghiệp vụ (field "code" trong body lỗi). */
    protected static void assertError(Response r, int httpStatus, String code) {
        assertEquals(r.statusCode(), httpStatus, r.asString());
        assertEquals(r.jsonPath().getString("code"), code, r.asString());
    }

    /** Kiểm tra số liệu tồn kho của Flash Sale. */
    protected void assertInventory(long flashSaleId, int reserved, int sold, int available) {
        Response s = api.statistics(flashSaleId);
        assertEquals(s.jsonPath().getInt("reservedQuantity"), reserved, "reserved");
        assertEquals(s.jsonPath().getInt("soldQuantity"), sold, "sold");
        assertEquals(s.jsonPath().getInt("availableQuantity"), available, "available");
        assertEquals(s.jsonPath().getBoolean("oversold"), false, "oversold");
        assertEquals(s.jsonPath().getBoolean("consistent"), true, "consistent: " + s.asString());
    }
}
