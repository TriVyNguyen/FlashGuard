-- Dữ liệu mẫu cho demo. Chạy sau khi backend đã khởi động ít nhất 1 lần (đã có bảng).
USE flashguard;

INSERT INTO products (name, original_price, stock_quantity, created_at) VALUES
    ('iPhone 17 Pro 256GB', 30000000, 50, NOW(6)),
    ('Tai nghe AirPods Pro 3', 6500000, 100, NOW(6));

-- Flash Sale 10 sản phẩm, đang diễn ra trong 2 giờ tới
INSERT INTO flash_sales (product_id, sale_price, sale_quantity, reserved_quantity, sold_quantity,
                         start_time, end_time, status, created_at)
SELECT id, 19990000, 10, 0, 0, NOW(6), DATE_ADD(NOW(6), INTERVAL 2 HOUR), 'ACTIVE', NOW(6)
FROM products WHERE name = 'iPhone 17 Pro 256GB' LIMIT 1;
