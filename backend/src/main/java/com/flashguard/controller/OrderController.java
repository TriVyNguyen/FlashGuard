package com.flashguard.controller;

import com.flashguard.dto.OrderResponse;
import com.flashguard.dto.PayOrderRequest;
import com.flashguard.dto.PayOrderResponse;
import com.flashguard.dto.PaymentResponse;
import com.flashguard.service.OrderService;
import com.flashguard.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders & Payments")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem đơn hàng")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Mô phỏng thanh toán. Body {\"success\": false} để mô phỏng thất bại")
    public PayOrderResponse pay(@PathVariable Long id, @RequestBody(required = false) PayOrderRequest req) {
        boolean success = req == null || req.success() == null || req.success();
        return paymentService.pay(id, success);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn (chỉ đơn PENDING), trả hàng giữ về Flash Sale")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "Lịch sử thanh toán của đơn")
    public List<PaymentResponse> payments(@PathVariable Long id) {
        return paymentService.listByOrder(id);
    }

    @PostMapping("/expire-overdue")
    @Operation(summary = "Chạy ngay việc hết hạn các đơn quá hạn (thường do job tự chạy mỗi 2 giây)")
    public Map<String, Integer> expireOverdue() {
        return Map.of("expired", orderService.expireOverdueOrders());
    }
}
