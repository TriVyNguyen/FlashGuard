package com.flashguard.repository;

import com.flashguard.entity.FlashSale;
import com.flashguard.entity.FlashSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cơ chế chống overselling nằm ở đây: mỗi thay đổi số lượng là MỘT câu UPDATE có điều kiện.
 * Database khóa dòng flash_sales trong lúc UPDATE, nên 100 request đồng thời sẽ được xếp hàng;
 * request nào chạy tới khi điều kiện "còn đủ hàng" không còn đúng sẽ nhận 0 dòng bị ảnh hưởng.
 * Không có bước "đọc số lượng rồi mới ghi" ở tầng Java, nên không có khe hở race condition.
 */
public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    List<FlashSale> findAllByOrderByIdDesc();

    /** Giữ hàng. Trả về 1 nếu giữ được, 0 nếu Flash Sale không mở hoặc không đủ hàng. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FlashSale f SET f.reservedQuantity = f.reservedQuantity + :qty "
            + "WHERE f.id = :id "
            + "AND f.status = :active "
            + "AND f.startTime <= :now AND f.endTime > :now "
            + "AND f.saleQuantity - f.reservedQuantity - f.soldQuantity >= :qty")
    int tryReserve(@Param("id") Long id,
                   @Param("qty") int qty,
                   @Param("active") FlashSaleStatus active,
                   @Param("now") LocalDateTime now);

    /** Trả hàng đang giữ về (hủy / hết hạn). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FlashSale f SET f.reservedQuantity = f.reservedQuantity - :qty "
            + "WHERE f.id = :id AND f.reservedQuantity >= :qty")
    int releaseReserved(@Param("id") Long id, @Param("qty") int qty);

    /** Chuyển hàng từ "đang giữ" sang "đã bán" (thanh toán thành công). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FlashSale f SET f.reservedQuantity = f.reservedQuantity - :qty, "
            + "f.soldQuantity = f.soldQuantity + :qty "
            + "WHERE f.id = :id AND f.reservedQuantity >= :qty")
    int confirmSold(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Số lượng đã phân bổ cho các Flash Sale chưa kết thúc của sản phẩm mà chưa bán
     * (phần đã bán đã được trừ khỏi stock_quantity rồi).
     */
    @Query("SELECT COALESCE(SUM(f.saleQuantity - f.soldQuantity), 0) FROM FlashSale f "
            + "WHERE f.product.id = :productId AND f.endTime > :now")
    long sumOpenAllocation(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
