package com.flashguard.dto;

import com.flashguard.entity.OrderStatus;
import com.flashguard.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(Long id, Long flashSaleId, int quantity, BigDecimal totalAmount, OrderStatus status,
                            String idempotencyKey, LocalDateTime expiresAt, LocalDateTime createdAt) {

    public static OrderResponse from(PurchaseOrder o) {
        return new OrderResponse(o.getId(), o.getFlashSale().getId(), o.getQuantity(), o.getTotalAmount(),
                o.getStatus(), o.getIdempotencyKey(), o.getExpiresAt(), o.getCreatedAt());
    }
}
