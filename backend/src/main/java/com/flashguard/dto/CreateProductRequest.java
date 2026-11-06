package com.flashguard.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
        String name,

        @NotNull(message = "Giá gốc là bắt buộc")
        @DecimalMin(value = "0", inclusive = false, message = "Giá gốc phải lớn hơn 0")
        @Digits(integer = 13, fraction = 2, message = "Giá gốc tối đa 2 chữ số thập phân")
        BigDecimal originalPrice,

        @NotNull(message = "Tồn kho là bắt buộc")
        @Min(value = 0, message = "Tồn kho không được âm")
        Integer stockQuantity
) {
}
