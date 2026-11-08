package com.flashguard.service;

import com.flashguard.entity.FlashSale;
import com.flashguard.entity.FlashSaleStatus;
import com.flashguard.entity.Product;
import com.flashguard.repository.FlashSaleRepository;
import com.flashguard.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dữ liệu mẫu cho buổi demo: 2 đợt Flash Sale.
 * - Tự tạo khi server khởi động mà database chưa có sản phẩm nào.
 * - Gọi reset() (nút "Làm mới dữ liệu demo") để xóa sạch dữ liệu cũ, kể cả dữ liệu TestNG sinh ra, rồi tạo lại.
 */
@Service
public class DemoDataService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    private final EntityManager em;
    private final ProductRepository productRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final boolean seedOnStartup;

    public DemoDataService(EntityManager em, ProductRepository productRepository,
                           FlashSaleRepository flashSaleRepository,
                           @Value("${app.demo.seed-on-startup:true}") boolean seedOnStartup) {
        this.em = em;
        this.productRepository = productRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.seedOnStartup = seedOnStartup;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedOnStartup && productRepository.count() == 0) {
            seed();
            log.info("Database trống → đã tạo 2 đợt Flash Sale mẫu");
        }
    }

    /** Xóa toàn bộ dữ liệu rồi tạo lại 2 đợt mẫu. Trả về số dòng đã xóa theo từng bảng. */
    @Transactional
    public Map<String, Integer> reset() {
        // Xóa theo thứ tự con → cha để không vướng khóa ngoại
        int payments = em.createQuery("DELETE FROM Payment").executeUpdate();
        int orders = em.createQuery("DELETE FROM PurchaseOrder").executeUpdate();
        int flashSales = em.createQuery("DELETE FROM FlashSale").executeUpdate();
        int products = em.createQuery("DELETE FROM Product").executeUpdate();
        em.clear();

        seed();
        log.info("Đã làm mới dữ liệu demo: xóa {} sản phẩm, {} Flash Sale, {} đơn, {} thanh toán",
                products, flashSales, orders, payments);
        return Map.of("products", products, "flashSales", flashSales, "orders", orders, "payments", payments);
    }

    private void seed() {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // Đợt 1: dùng cho màn chính "100 người mua 10 sản phẩm"
        Product iphone = product("iPhone 17 Pro 256GB", 30_000_000, 50);
        flashSale(iphone, 19_990_000, 10, now.minusMinutes(1), now.plusHours(3));

        // Đợt 2: dùng để demo từng bước đặt hàng, thanh toán, hủy, hết hạn
        Product airpods = product("AirPods Pro 3", 6_500_000, 100);
        flashSale(airpods, 3_990_000, 20, now.minusMinutes(1), now.plusHours(3));
    }

    private Product product(String name, long price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setOriginalPrice(BigDecimal.valueOf(price));
        p.setStockQuantity(stock);
        return productRepository.save(p);
    }

    private void flashSale(Product product, long price, int quantity, LocalDateTime start, LocalDateTime end) {
        FlashSale f = new FlashSale();
        f.setProduct(product);
        f.setSalePrice(BigDecimal.valueOf(price));
        f.setSaleQuantity(quantity);
        f.setStartTime(start);
        f.setEndTime(end);
        f.setStatus(FlashSaleStatus.ACTIVE);
        flashSaleRepository.save(f);
    }
}
