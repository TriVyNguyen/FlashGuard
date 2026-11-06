-- FlashGuard schema (MySQL 8)
-- Backend dùng spring.jpa.hibernate.ddl-auto=update nên sẽ tự tạo bảng.
-- File này dùng để tham khảo / tạo tay khi cần.

CREATE DATABASE IF NOT EXISTS flashguard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE flashguard;

CREATE TABLE IF NOT EXISTS products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(200)   NOT NULL,
    original_price DECIMAL(15, 2) NOT NULL,
    stock_quantity INT            NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    CONSTRAINT chk_products_stock CHECK (stock_quantity >= 0)
);

CREATE TABLE IF NOT EXISTS flash_sales (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT         NOT NULL,
    sale_price        DECIMAL(15, 2) NOT NULL,
    sale_quantity     INT            NOT NULL,
    reserved_quantity INT            NOT NULL DEFAULT 0,
    sold_quantity     INT            NOT NULL DEFAULT 0,
    start_time        DATETIME(6)    NOT NULL,
    end_time          DATETIME(6)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    CONSTRAINT fk_flash_sales_product FOREIGN KEY (product_id) REFERENCES products (id),
    -- Lưới an toàn cuối cùng ở tầng DB: không bao giờ âm, không bao giờ vượt số lượng sale
    CONSTRAINT chk_flash_sales_non_negative CHECK (reserved_quantity >= 0 AND sold_quantity >= 0),
    CONSTRAINT chk_flash_sales_no_oversell CHECK (reserved_quantity + sold_quantity <= sale_quantity)
);

CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    flash_sale_id   BIGINT         NOT NULL,
    quantity        INT            NOT NULL,
    total_amount    DECIMAL(15, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,
    expires_at      DATETIME(6)    NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    CONSTRAINT fk_orders_flash_sale FOREIGN KEY (flash_sale_id) REFERENCES flash_sales (id),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_orders_flash_sale (flash_sale_id),
    INDEX idx_orders_status_expires (status, expires_at)
);

CREATE TABLE IF NOT EXISTS payments (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id              BIGINT         NOT NULL,
    amount                DECIMAL(15, 2) NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    transaction_reference VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
