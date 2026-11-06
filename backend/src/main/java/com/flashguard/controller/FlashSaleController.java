package com.flashguard.controller;

import com.flashguard.dto.*;
import com.flashguard.service.FlashSaleService;
import com.flashguard.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/flash-sales")
@Tag(name = "Flash Sales")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;
    private final OrderService orderService;

    public FlashSaleController(FlashSaleService flashSaleService, OrderService orderService) {
        this.flashSaleService = flashSaleService;
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Tạo Flash Sale")
    public ResponseEntity<FlashSaleResponse> create(@Valid @RequestBody CreateFlashSaleRequest req) {
        FlashSaleResponse created = flashSaleService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/flash-sales/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Danh sách Flash Sale")
    public List<FlashSaleResponse> list() {
        return flashSaleService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem Flash Sale")
    public FlashSaleResponse get(@PathVariable Long id) {
        return flashSaleService.get(id);
    }

    @GetMapping("/{id}/statistics")
    @Operation(summary = "Thống kê Flash Sale: giữ / đã bán / còn lại, số đơn theo trạng thái")
    public FlashSaleStatisticsResponse statistics(@PathVariable Long id) {
        return flashSaleService.statistics(id);
    }

    @PostMapping("/{id}/orders")
    @Operation(summary = "Đặt hàng (giữ hàng). 201 = đơn mới, 200 = gửi lại cùng Idempotency-Key")
    public ResponseEntity<OrderResponse> placeOrder(
            @PathVariable Long id,
            @Parameter(description = "Khóa chống tạo đơn trùng, mỗi lần mua một khóa mới")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest req) {
        OrderService.PlaceOrderResult result = orderService.placeOrder(id, idempotencyKey, req);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.order());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/orders/" + result.order().id()))
                .body(result.order());
    }

    @GetMapping("/{id}/orders")
    @Operation(summary = "Danh sách đơn của Flash Sale")
    public List<OrderResponse> orders(@PathVariable Long id) {
        return orderService.listByFlashSale(id);
    }
}
