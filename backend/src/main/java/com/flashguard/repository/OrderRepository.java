package com.flashguard.repository;

import com.flashguard.entity.OrderStatus;
import com.flashguard.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByIdempotencyKey(String idempotencyKey);

    List<PurchaseOrder> findByFlashSaleIdOrderByIdDesc(Long flashSaleId);

    /**
     * Chuyển trạng thái có điều kiện: chỉ thành công nếu đơn đang ở trạng thái {@code from}.
     * Hai request hủy cùng lúc → chỉ một request nhận 1, request còn lại nhận 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseOrder o SET o.status = :to WHERE o.id = :id AND o.status = :from")
    int transition(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);

    /** PENDING → PAID, chỉ khi chưa quá hạn. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseOrder o SET o.status = :paid "
            + "WHERE o.id = :id AND o.status = :pending AND o.expiresAt > :now")
    int markPaid(@Param("id") Long id,
                 @Param("pending") OrderStatus pending,
                 @Param("paid") OrderStatus paid,
                 @Param("now") LocalDateTime now);

    /** PENDING → EXPIRED, chỉ khi đã quá hạn. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseOrder o SET o.status = :expired "
            + "WHERE o.id = :id AND o.status = :pending AND o.expiresAt <= :now")
    int markExpired(@Param("id") Long id,
                    @Param("pending") OrderStatus pending,
                    @Param("expired") OrderStatus expired,
                    @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM PurchaseOrder o WHERE o.status = :pending AND o.expiresAt <= :now")
    List<Long> findOverdueIds(@Param("pending") OrderStatus pending, @Param("now") LocalDateTime now);

    /** Mỗi dòng: [status, số đơn, tổng số lượng, tổng tiền]. */
    @Query("SELECT o.status, COUNT(o), COALESCE(SUM(o.quantity), 0), COALESCE(SUM(o.totalAmount), 0) "
            + "FROM PurchaseOrder o WHERE o.flashSale.id = :flashSaleId GROUP BY o.status")
    List<Object[]> summarizeByStatus(@Param("flashSaleId") Long flashSaleId);
}
