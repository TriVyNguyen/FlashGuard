package com.flashguard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đơn hàng (bảng orders). Đặt tên class là PurchaseOrder vì "Order" là từ khóa trong JPQL.
 * Trạng thái chỉ đổi bằng UPDATE có điều kiện trong {@link com.flashguard.repository.OrderRepository}
 * để mỗi lần chuyển trạng thái (thanh toán / hủy / hết hạn) chỉ xảy ra đúng một lần.
 */
@Entity
@Table(name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_idempotency_key", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_orders_flash_sale", columnList = "flash_sale_id"),
                @Index(name = "idx_orders_status_expires", columnList = "status, expires_at")
        })
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "flash_sale_id", nullable = false)
    private FlashSale flashSale;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public FlashSale getFlashSale() { return flashSale; }
    public void setFlashSale(FlashSale flashSale) { this.flashSale = flashSale; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
