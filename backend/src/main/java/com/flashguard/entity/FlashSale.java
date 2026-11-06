package com.flashguard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một chương trình Flash Sale.
 * Available = saleQuantity - reservedQuantity - soldQuantity.
 * reservedQuantity / soldQuantity CHỈ được thay đổi bằng các câu UPDATE nguyên tử
 * trong {@link com.flashguard.repository.FlashSaleRepository}, không set qua entity.
 */
@Entity
@Table(name = "flash_sales")
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sale_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_quantity", nullable = false)
    private int saleQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashSaleStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public int getAvailableQuantity() {
        return saleQuantity - reservedQuantity - soldQuantity;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public int getSaleQuantity() { return saleQuantity; }
    public void setSaleQuantity(int saleQuantity) { this.saleQuantity = saleQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getSoldQuantity() { return soldQuantity; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public FlashSaleStatus getStatus() { return status; }
    public void setStatus(FlashSaleStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
