package com.flashguard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** holdSeconds tùy chọn: thời gian giữ hàng (giây), dùng cho demo/test đơn hết hạn. */
public record PlaceOrderRequest(
        @NotNull(message = "Số lượng là bắt buộc")
        @Min(value = 1, message = "Số lượng đặt phải từ 1 trở lên")
        @Max(value = 1000, message = "Số lượng đặt tối đa 1000")
        Integer quantity,

        @Min(value = 1, message = "holdSeconds phải từ 1 trở lên")
        @Max(value = 86400, message = "holdSeconds tối đa 86400")
        Integer holdSeconds
) {
}
