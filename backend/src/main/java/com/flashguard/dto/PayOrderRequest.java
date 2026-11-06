package com.flashguard.dto;

/** success = false để mô phỏng thanh toán thất bại. Không gửi body thì coi như thành công. */
public record PayOrderRequest(Boolean success) {
}
