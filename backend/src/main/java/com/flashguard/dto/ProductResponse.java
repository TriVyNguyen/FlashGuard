package com.flashguard.dto;

import com.flashguard.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Long id, String name, BigDecimal originalPrice, int stockQuantity,
                              LocalDateTime createdAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getOriginalPrice(), p.getStockQuantity(),
                p.getCreatedAt());
    }
}
