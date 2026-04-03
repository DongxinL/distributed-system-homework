CREATE DATABASE IF NOT EXISTS test_db;
USE test_db;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    image_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 库存表
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    version INT DEFAULT 0, -- 乐观锁版本号
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL COMMENT '使用雪花算法生成的全局唯一订单ID',
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '订单状态: CREATED/PAID/PAYMENT_FAILED/CANCELLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 本地消息表（保障基于消息的一致性）
CREATE TABLE IF NOT EXISTS local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_key VARCHAR(100) NOT NULL COMMENT '消息唯一标识（如订单ID）',
    topic VARCHAR(100) NOT NULL COMMENT 'Kafka Topic',
    message_body TEXT NOT NULL COMMENT '消息内容JSON',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/CONSUMED/FAILED',
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_key_topic (message_key, topic),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表 - 保障分布式事务最终一致性';

-- 初始化数据
INSERT INTO users (username, password, email) VALUES 
('admin', 'admin123', 'admin@example.com'),
('user1', '123456', 'user1@example.com');

INSERT INTO products (name, description, price, image_url) VALUES 
('iPhone 15', 'Latest Apple iPhone', 5999.00, 'iphone15.jpg'),
('MacBook Pro', 'M3 Chip Laptop', 12999.00, 'macbook.jpg');

INSERT INTO inventory (product_id, stock) VALUES 
(1, 100),
(2, 50);
