package com.flashguard.service;

import com.flashguard.dto.CreateFlashSaleRequest;
import com.flashguard.dto.FlashSaleResponse;
import com.flashguard.dto.FlashSaleStatisticsResponse;
import com.flashguard.entity.FlashSale;
import com.flashguard.entity.FlashSaleStatus;
import com.flashguard.entity.OrderStatus;
import com.flashguard.entity.Product;
import com.flashguard.exception.ApiException;
import com.flashguard.exception.ErrorCode;
import com.flashguard.repository.FlashSaleRepository;
import com.flashguard.repository.OrderRepository;
import com.flashguard.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public FlashSaleService(FlashSaleRepository flashSaleRepository, ProductRepository productRepository,
                            OrderRepository orderRepository) {
        this.flashSaleRepository = flashSaleRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public FlashSaleResponse create(CreateFlashSaleRequest req) {
        LocalDateTime now = LocalDateTime.now();

        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest(ErrorCode.INVALID_TIME_RANGE,
                    "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (!req.endTime().isAfter(now)) {
            throw ApiException.badRequest(ErrorCode.INVALID_TIME_RANGE,
                    "Thời gian kết thúc phải ở tương lai");
        }

        // Khóa dòng sản phẩm để việc kiểm tra + phân bổ số lượng không bị chen ngang.
        Product product = productRepository.findByIdForUpdate(req.productId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.PRODUCT_NOT_FOUND,
                        "Không tìm thấy sản phẩm #" + req.productId()));

        if (req.salePrice().compareTo(product.getOriginalPrice()) >= 0) {
            throw ApiException.badRequest(ErrorCode.INVALID_SALE_PRICE,
                    "Giá Flash Sale phải nhỏ hơn giá gốc (" + product.getOriginalPrice() + ")");
        }

        long allocated = flashSaleRepository.sumOpenAllocation(product.getId(), now);
        long sellable = product.getStockQuantity() - allocated;
        if (req.saleQuantity() > sellable) {
            throw ApiException.badRequest(ErrorCode.SALE_QUANTITY_EXCEEDS_STOCK,
                    "Số lượng Flash Sale (" + req.saleQuantity() + ") vượt tồn kho có thể bán ("
                            + Math.max(sellable, 0) + ")");
        }

        FlashSale f = new FlashSale();
        f.setProduct(product);
        f.setSalePrice(req.salePrice());
        f.setSaleQuantity(req.saleQuantity());
        f.setStartTime(req.startTime());
        f.setEndTime(req.endTime());
        f.setStatus(req.status() == null ? FlashSaleStatus.ACTIVE : req.status());
        return FlashSaleResponse.from(flashSaleRepository.save(f));
    }

    @Transactional(readOnly = true)
    public FlashSaleResponse get(Long id) {
        return FlashSaleResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<FlashSaleResponse> list() {
        return flashSaleRepository.findAllByOrderByIdDesc().stream().map(FlashSaleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FlashSaleStatisticsResponse statistics(Long id) {
        FlashSale f = find(id);

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        Map<String, Long> quantityByStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            ordersByStatus.put(s.name(), 0L);
            quantityByStatus.put(s.name(), 0L);
        }
        long totalOrders = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (Object[] row : orderRepository.summarizeByStatus(id)) {
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            long qty = ((Number) row[2]).longValue();
            ordersByStatus.put(status.name(), count);
            quantityByStatus.put(status.name(), qty);
            totalOrders += count;
            if (status == OrderStatus.PAID) {
                revenue = toBigDecimal(row[3]);
            }
        }

        boolean oversold = f.getReservedQuantity() + f.getSoldQuantity() > f.getSaleQuantity();
        boolean consistent = !oversold
                && f.getAvailableQuantity() >= 0
                && f.getReservedQuantity() == quantityByStatus.get(OrderStatus.PENDING.name())
                && f.getSoldQuantity() == quantityByStatus.get(OrderStatus.PAID.name());

        return new FlashSaleStatisticsResponse(f.getId(), f.getProduct().getName(), f.getSaleQuantity(),
                f.getReservedQuantity(), f.getSoldQuantity(), f.getAvailableQuantity(), totalOrders,
                ordersByStatus, quantityByStatus, revenue, oversold, consistent);
    }

    private FlashSale find(Long id) {
        return flashSaleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.FLASH_SALE_NOT_FOUND,
                        "Không tìm thấy Flash Sale #" + id));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
