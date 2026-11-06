package com.flashguard.service;

import com.flashguard.dto.OrderResponse;
import com.flashguard.dto.PayOrderResponse;
import com.flashguard.dto.PaymentResponse;
import com.flashguard.entity.OrderStatus;
import com.flashguard.entity.Payment;
import com.flashguard.entity.PaymentStatus;
import com.flashguard.entity.PurchaseOrder;
import com.flashguard.exception.ApiException;
import com.flashguard.exception.ErrorCode;
import com.flashguard.repository.FlashSaleRepository;
import com.flashguard.repository.OrderRepository;
import com.flashguard.repository.PaymentRepository;
import com.flashguard.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Mô phỏng thanh toán: không gọi cổng thanh toán thật. */
@Service
public class PaymentService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    public PaymentService(OrderService orderService, OrderRepository orderRepository,
                          FlashSaleRepository flashSaleRepository, ProductRepository productRepository,
                          PaymentRepository paymentRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PayOrderResponse pay(Long orderId, boolean success) {
        PurchaseOrder order = orderService.find(orderId);
        Long flashSaleId = order.getFlashSale().getId();
        Long productId = order.getFlashSale().getProduct().getId();
        int qty = order.getQuantity();
        LocalDateTime now = LocalDateTime.now();

        if (!success) {
            // Thanh toán thất bại: chỉ ghi nhận giao dịch lỗi, đơn vẫn PENDING và vẫn giữ hàng tới khi hết hạn.
            if (order.getStatus() != OrderStatus.PENDING || !order.getExpiresAt().isAfter(now)) {
                throw OrderService.cannotChange(order.getStatus(), "thanh toán");
            }
            Payment failed = savePayment(order, PaymentStatus.FAILED);
            return new PayOrderResponse(OrderResponse.from(order), PaymentResponse.from(failed));
        }

        // PENDING → PAID bằng UPDATE có điều kiện: hai lần thanh toán đồng thời chỉ một lần thành công.
        int changed = orderRepository.markPaid(orderId, OrderStatus.PENDING, OrderStatus.PAID, now);
        if (changed == 0) {
            throw OrderService.cannotChange(orderService.find(orderId).getStatus(), "thanh toán");
        }
        if (flashSaleRepository.confirmSold(flashSaleId, qty) != 1) {
            throw new IllegalStateException("Số lượng giữ của Flash Sale #" + flashSaleId + " không khớp");
        }
        if (productRepository.decreaseStock(productId, qty) != 1) {
            throw ApiException.conflict(ErrorCode.INSUFFICIENT_PHYSICAL_STOCK,
                    "Tồn kho vật lý không đủ để hoàn tất đơn");
        }

        PurchaseOrder paid = orderService.find(orderId);
        Payment payment = savePayment(paid, PaymentStatus.SUCCESS);
        return new PayOrderResponse(OrderResponse.from(paid), PaymentResponse.from(payment));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrder(Long orderId) {
        orderService.find(orderId);
        return paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(PaymentResponse::from).toList();
    }

    private Payment savePayment(PurchaseOrder order, PaymentStatus status) {
        Payment p = new Payment();
        p.setOrder(order);
        p.setAmount(order.getTotalAmount());
        p.setStatus(status);
        p.setTransactionReference("SIM-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase());
        return paymentRepository.save(p);
    }
}
