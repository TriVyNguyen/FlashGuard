package com.flashguard.service;

import com.flashguard.entity.FlashSale;
import com.flashguard.entity.FlashSaleStatus;
import com.flashguard.entity.OrderStatus;
import com.flashguard.entity.PurchaseOrder;
import com.flashguard.exception.ApiException;
import com.flashguard.exception.ErrorCode;
import com.flashguard.repository.FlashSaleRepository;
import com.flashguard.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Các transaction nhỏ của đơn hàng. Tách thành bean riêng để OrderService/PaymentService gọi qua proxy
 * (mỗi lời gọi là một transaction độc lập, rollback được riêng).
 */
@Component
public class OrderTransactions {

    private final FlashSaleRepository flashSaleRepository;
    private final OrderRepository orderRepository;
    private final long defaultHoldSeconds;

    public OrderTransactions(FlashSaleRepository flashSaleRepository, OrderRepository orderRepository,
                             @Value("${app.order.hold-seconds:300}") long defaultHoldSeconds) {
        this.flashSaleRepository = flashSaleRepository;
        this.orderRepository = orderRepository;
        this.defaultHoldSeconds = defaultHoldSeconds;
    }

    /**
     * Giữ hàng + tạo đơn trong CÙNG một transaction.
     * Nếu insert đơn lỗi (vd: trùng Idempotency Key), transaction rollback và phần hàng vừa giữ cũng được hoàn lại.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurchaseOrder reserveAndCreate(Long flashSaleId, int quantity, Integer holdSeconds, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        FlashSale snapshot = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.FLASH_SALE_NOT_FOUND,
                        "Không tìm thấy Flash Sale #" + flashSaleId));
        BigDecimal salePrice = snapshot.getSalePrice();

        int updated = flashSaleRepository.tryReserve(flashSaleId, quantity, FlashSaleStatus.ACTIVE, now);
        if (updated == 0) {
            throw explainReserveFailure(snapshot, now);
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setFlashSale(flashSaleRepository.getReferenceById(flashSaleId));
        order.setQuantity(quantity);
        order.setTotalAmount(salePrice.multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(OrderStatus.PENDING);
        order.setIdempotencyKey(idempotencyKey);
        order.setExpiresAt(now.plusSeconds(holdSeconds != null ? holdSeconds : defaultHoldSeconds));
        return orderRepository.saveAndFlush(order);
    }

    /** PENDING quá hạn → EXPIRED và trả hàng. Trả về true nếu lần gọi này thực sự làm việc đó. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfOverdue(Long orderId) {
        PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;
        Long flashSaleId = order.getFlashSale().getId();
        int qty = order.getQuantity();

        int changed = orderRepository.markExpired(orderId, OrderStatus.PENDING, OrderStatus.EXPIRED,
                LocalDateTime.now());
        if (changed == 0) return false;

        releaseOrFail(flashSaleId, qty);
        return true;
    }

    /** Không tự mở transaction: chạy trong transaction của nơi gọi. */
    public void releaseOrFail(Long flashSaleId, int qty) {
        if (flashSaleRepository.releaseReserved(flashSaleId, qty) != 1) {
            // Không bao giờ xảy ra nếu dữ liệu nhất quán; ném lỗi để rollback thay vì làm lệch số liệu.
            throw new IllegalStateException("Số lượng giữ của Flash Sale #" + flashSaleId + " không đủ để trả lại");
        }
    }

    private ApiException explainReserveFailure(FlashSale f, LocalDateTime now) {
        if (f.getStatus() != FlashSaleStatus.ACTIVE) {
            return ApiException.conflict(ErrorCode.FLASH_SALE_NOT_ACTIVE, "Flash Sale đang tạm dừng");
        }
        if (now.isBefore(f.getStartTime())) {
            return ApiException.conflict(ErrorCode.FLASH_SALE_NOT_STARTED, "Flash Sale chưa bắt đầu");
        }
        if (!now.isBefore(f.getEndTime())) {
            return ApiException.conflict(ErrorCode.FLASH_SALE_ENDED, "Flash Sale đã kết thúc");
        }
        return ApiException.conflict(ErrorCode.OUT_OF_STOCK, "Không đủ hàng: sản phẩm đã hết hoặc số lượng còn lại ít hơn số lượng đặt");
    }
}
