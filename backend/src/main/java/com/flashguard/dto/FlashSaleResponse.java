package com.flashguard.dto;

import com.flashguard.entity.FlashSale;
import com.flashguard.entity.FlashSaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * phase: trạng thái hiển thị tính theo giờ hiện tại
 * (INACTIVE / UPCOMING / ONGOING / ENDED) — chỉ để xem, không lưu DB.
 */
public record FlashSaleResponse(Long id, Long productId, String productName, BigDecimal originalPrice,
                                BigDecimal salePrice, int saleQuantity, int reservedQuantity, int soldQuantity,
                                int availableQuantity, LocalDateTime startTime, LocalDateTime endTime,
                                FlashSaleStatus status, String phase) {

    public static FlashSaleResponse from(FlashSale f) {
        LocalDateTime now = LocalDateTime.now();
        String phase;
        if (f.getStatus() != FlashSaleStatus.ACTIVE) phase = "INACTIVE";
        else if (now.isBefore(f.getStartTime())) phase = "UPCOMING";
        else if (!now.isBefore(f.getEndTime())) phase = "ENDED";
        else phase = "ONGOING";

        return new FlashSaleResponse(f.getId(), f.getProduct().getId(), f.getProduct().getName(),
                f.getProduct().getOriginalPrice(), f.getSalePrice(), f.getSaleQuantity(),
                f.getReservedQuantity(), f.getSoldQuantity(), f.getAvailableQuantity(),
                f.getStartTime(), f.getEndTime(), f.getStatus(), phase);
    }
}
