package com.flashguard.repository;

import com.flashguard.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * SELECT ... FOR UPDATE: khóa dòng sản phẩm trong lúc tạo Flash Sale,
     * để hai Flash Sale tạo cùng lúc không cùng phân bổ vượt tồn kho.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /** Trừ tồn kho vật lý khi thanh toán thành công. Trả về 0 nếu không đủ hàng (không bao giờ âm). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty "
            + "WHERE p.id = :id AND p.stockQuantity >= :qty")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);
}
