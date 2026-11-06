package com.flashguard.service;

import com.flashguard.dto.OrderResponse;
import com.flashguard.dto.PlaceOrderRequest;
import com.flashguard.entity.OrderStatus;
import com.flashguard.entity.PurchaseOrder;
import com.flashguard.exception.ApiException;
import com.flashguard.exception.ErrorCode;
import com.flashguard.repository.FlashSaleRepository;
import com.flashguard.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    /** Kết quả đặt hàng: replayed = true nghĩa là trả lại đơn cũ của cùng Idempotency Key. */
    public record PlaceOrderResult(OrderResponse order, boolean replayed) {
    }

    private final OrderRepository orderRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final OrderTransactions tx;

    public OrderService(OrderRepository orderRepository, FlashSaleRepository flashSaleRepository,
                        OrderTransactions tx) {
        this.orderRepository = orderRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.tx = tx;
    }

    public PlaceOrderResult placeOrder(Long flashSaleId, String idempotencyKey, PlaceOrderRequest req) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw ApiException.badRequest(ErrorCode.MISSING_IDEMPOTENCY_KEY,
                    "Header Idempotency-Key là bắt buộc (1–100 ký tự)");
        }

        // 1) Key đã dùng → trả lại đơn cũ, không giữ thêm hàng.
        var existing = orderRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replay(existing.get(), flashSaleId, req);
        }

        // 2) Key mới → giữ hàng + tạo đơn. Hai request cùng key chạy song song:
        //    request thua sẽ vấp unique constraint, transaction của nó rollback (hàng giữ được hoàn lại),
        //    rồi trả về đơn của request thắng.
        try {
            PurchaseOrder created = tx.reserveAndCreate(flashSaleId, req.quantity(), req.holdSeconds(), key);
            return new PlaceOrderResult(OrderResponse.from(created), false);
        } catch (DataIntegrityViolationException e) {
            PurchaseOrder winner = orderRepository.findByIdempotencyKey(key).orElseThrow(() -> e);
            return replay(winner, flashSaleId, req);
        }
    }

    private PlaceOrderResult replay(PurchaseOrder order, Long flashSaleId, PlaceOrderRequest req) {
        if (!order.getFlashSale().getId().equals(flashSaleId) || order.getQuantity() != req.quantity()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "Idempotency-Key này đã được dùng cho một yêu cầu khác (khác Flash Sale hoặc số lượng)");
        }
        return new PlaceOrderResult(OrderResponse.from(order), true);
    }

    public OrderResponse get(Long id) {
        PurchaseOrder order = find(id);
        // Hết hạn "lười": xem đơn đúng lúc đã quá hạn thì xử lý luôn, không chờ job quét.
        if (order.getStatus() == OrderStatus.PENDING && !order.getExpiresAt().isAfter(LocalDateTime.now())) {
            tx.expireIfOverdue(id);
            order = find(id);
        }
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        PurchaseOrder order = find(id);
        Long flashSaleId = order.getFlashSale().getId();
        int qty = order.getQuantity();

        int changed = orderRepository.transition(id, OrderStatus.PENDING, OrderStatus.CANCELLED);
        if (changed == 0) {
            throw cannotChange(find(id).getStatus(), "hủy");
        }
        tx.releaseOrFail(flashSaleId, qty);
        return OrderResponse.from(find(id));
    }

    /** Quét và hết hạn tất cả đơn quá hạn. Trả về số đơn đã xử lý. */
    public int expireOverdueOrders() {
        int count = 0;
        for (Long id : orderRepository.findOverdueIds(OrderStatus.PENDING, LocalDateTime.now())) {
            if (tx.expireIfOverdue(id)) count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listByFlashSale(Long flashSaleId) {
        if (!flashSaleRepository.existsById(flashSaleId)) {
            throw ApiException.notFound(ErrorCode.FLASH_SALE_NOT_FOUND, "Không tìm thấy Flash Sale #" + flashSaleId);
        }
        return orderRepository.findByFlashSaleIdOrderByIdDesc(flashSaleId).stream()
                .map(OrderResponse::from).toList();
    }

    PurchaseOrder find(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn #" + id));
    }

    static ApiException cannotChange(OrderStatus current, String action) {
        return switch (current) {
            case PAID -> ApiException.conflict(ErrorCode.ORDER_ALREADY_PAID,
                    "Không thể " + action + ": đơn đã được thanh toán");
            case CANCELLED -> ApiException.conflict(ErrorCode.ORDER_ALREADY_CANCELLED,
                    "Không thể " + action + ": đơn đã bị hủy");
            case EXPIRED, PENDING -> ApiException.conflict(ErrorCode.ORDER_EXPIRED,
                    "Không thể " + action + ": đơn đã hết hạn giữ hàng");
        };
    }
}
