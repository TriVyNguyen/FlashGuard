package com.flashguard.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * consistent = true khi số liệu trên flash_sales khớp với tổng các đơn:
 * reserved == tổng số lượng đơn PENDING, sold == tổng số lượng đơn PAID, available >= 0.
 * oversold = true nếu reserved + sold > saleQuantity (không bao giờ được xảy ra).
 */
public record FlashSaleStatisticsResponse(Long flashSaleId, String productName, int saleQuantity,
                                          int reservedQuantity, int soldQuantity, int availableQuantity,
                                          long totalOrders, Map<String, Long> ordersByStatus,
                                          Map<String, Long> quantityByStatus, BigDecimal revenue,
                                          boolean oversold, boolean consistent) {
}
