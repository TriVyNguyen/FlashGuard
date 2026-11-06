package com.flashguard.dto;

import com.flashguard.entity.FlashSaleStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** status không truyền thì mặc định ACTIVE. */
public record CreateFlashSaleRequest(
        @NotNull(message = "productId là bắt buộc")
        Long productId,

        @NotNull(message = "Giá Flash Sale là bắt buộc")
        @DecimalMin(value = "0", inclusive = false, message = "Giá Flash Sale phải lớn hơn 0")
        @Digits(integer = 13, fraction = 2, message = "Giá Flash Sale tối đa 2 chữ số thập phân")
        BigDecimal salePrice,

        @NotNull(message = "Số lượng Flash Sale là bắt buộc")
        @Min(value = 1, message = "Số lượng Flash Sale phải từ 1 trở lên")
        Integer saleQuantity,

        @NotNull(message = "Thời gian bắt đầu là bắt buộc")
        LocalDateTime startTime,

        @NotNull(message = "Thời gian kết thúc là bắt buộc")
        LocalDateTime endTime,

        FlashSaleStatus status
) {
}
