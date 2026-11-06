package com.flashguard.dto;

import com.flashguard.entity.Payment;
import com.flashguard.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long id, Long orderId, BigDecimal amount, PaymentStatus status,
                              String transactionReference, LocalDateTime createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrder().getId(), p.getAmount(), p.getStatus(),
                p.getTransactionReference(), p.getCreatedAt());
    }
}
