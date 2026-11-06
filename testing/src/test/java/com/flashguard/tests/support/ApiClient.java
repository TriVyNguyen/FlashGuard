package com.flashguard.tests.support;

import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;

/**
 * Bọc các API của FlashGuard. TestNG chỉ GỬI request và KIỂM TRA kết quả;
 * toàn bộ cơ chế bảo vệ tồn kho nằm ở backend.
 */
public class ApiClient {

    public static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    private final RequestSpecification spec;

    private ApiClient(boolean attachToAllure) {
        RequestSpecBuilder b = new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON);
        if (attachToAllure) {
            b.addFilter(new AllureRestAssured());
        }
        this.spec = b.build();
    }

    /** Client thường: mỗi request/response được đính kèm vào Allure report. */
    public static ApiClient standard() {
        return new ApiClient(true);
    }

    /** Client cho test đồng thời: không đính kèm Allure (100 luồng ghi report cùng lúc sẽ rối). */
    public static ApiClient silent() {
        return new ApiClient(false);
    }

    public static String newKey() {
        return "test-" + UUID.randomUUID();
    }

    public static String time(LocalDateTime t) {
        return t.withNano(0).toString();
    }

    // ---------- Product ----------

    public Response createProduct(Map<String, Object> body) {
        return given(spec).body(body).post("/api/products");
    }

    @Step("Tạo sản phẩm giá {originalPrice}, tồn kho {stock}")
    public long createProductOk(double originalPrice, int stock) {
        Response r = createProduct(Map.of(
                "name", "Sản phẩm test " + UUID.randomUUID().toString().substring(0, 8),
                "originalPrice", originalPrice,
                "stockQuantity", stock));
        assertEquals(r.statusCode(), 201, r.asString());
        return r.jsonPath().getLong("id");
    }

    public Response getProduct(long id) {
        return given(spec).get("/api/products/{id}", id);
    }

    // ---------- Flash Sale ----------

    public Response createFlashSale(Map<String, Object> body) {
        return given(spec).body(body).post("/api/flash-sales");
    }

    public static Map<String, Object> flashSaleBody(long productId, double salePrice, int quantity,
                                                    LocalDateTime start, LocalDateTime end) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("salePrice", salePrice);
        body.put("saleQuantity", quantity);
        body.put("startTime", time(start));
        body.put("endTime", time(end));
        return body;
    }

    @Step("Tạo Flash Sale đang diễn ra với {quantity} sản phẩm")
    public long createOngoingFlashSale(long productId, double salePrice, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        Response r = createFlashSale(flashSaleBody(productId, salePrice, quantity, now.minusMinutes(1), now.plusHours(1)));
        assertEquals(r.statusCode(), 201, r.asString());
        return r.jsonPath().getLong("id");
    }

    /** Tạo sản phẩm (giá 1.000.000, tồn kho = stock) + Flash Sale đang chạy (giá 500.000). */
    @Step("Chuẩn bị Flash Sale: tồn kho {stock}, số lượng sale {saleQuantity}")
    public long setupOngoingFlashSale(int stock, int saleQuantity) {
        long productId = createProductOk(1_000_000, stock);
        return createOngoingFlashSale(productId, 500_000, saleQuantity);
    }

    public Response getFlashSale(long id) {
        return given(spec).get("/api/flash-sales/{id}", id);
    }

    @Step("Xem thống kê Flash Sale #{flashSaleId}")
    public Response statistics(long flashSaleId) {
        Response r = given(spec).get("/api/flash-sales/{id}/statistics", flashSaleId);
        assertEquals(r.statusCode(), 200, r.asString());
        return r;
    }

    // ---------- Order ----------

    public Response placeOrder(long flashSaleId, String key, int quantity) {
        return placeOrder(flashSaleId, key, quantity, null);
    }

    public Response placeOrder(long flashSaleId, String key, int quantity, Integer holdSeconds) {
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", quantity);
        if (holdSeconds != null) body.put("holdSeconds", holdSeconds);
        RequestSpecification req = given(spec).body(body);
        if (key != null) req.header("Idempotency-Key", key);
        return req.post("/api/flash-sales/{id}/orders", flashSaleId);
    }

    @Step("Đặt {quantity} sản phẩm (kỳ vọng thành công)")
    public long placeOrderOk(long flashSaleId, int quantity) {
        Response r = placeOrder(flashSaleId, newKey(), quantity);
        assertEquals(r.statusCode(), 201, r.asString());
        return r.jsonPath().getLong("id");
    }

    public Response getOrder(long orderId) {
        return given(spec).get("/api/orders/{id}", orderId);
    }

    public Response pay(long orderId, boolean success) {
        return given(spec).body(Map.of("success", success)).post("/api/orders/{id}/pay", orderId);
    }

    public Response cancel(long orderId) {
        return given(spec).post("/api/orders/{id}/cancel", orderId);
    }

    public Response payments(long orderId) {
        return given(spec).get("/api/orders/{id}/payments", orderId);
    }
}
