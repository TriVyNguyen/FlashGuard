package com.flashguard.entity;

public enum OrderStatus {
    /** Đang giữ hàng, chờ thanh toán. */
    PENDING,
    /** Đã thanh toán: hàng chuyển từ "giữ" sang "đã bán". */
    PAID,
    /** Người mua hủy: hàng giữ được trả lại. */
    CANCELLED,
    /** Quá hạn thanh toán: hàng giữ được trả lại. */
    EXPIRED
}
