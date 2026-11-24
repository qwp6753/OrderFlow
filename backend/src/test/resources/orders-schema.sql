CREATE TABLE IF NOT EXISTS sys_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(100) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE, name VARCHAR(50) NOT NULL);
CREATE TABLE IF NOT EXISTS sys_user_role (
 user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY(user_id,role_id),
 FOREIGN KEY(user_id) REFERENCES sys_user(id), FOREIGN KEY(role_id) REFERENCES sys_role(id)
);
CREATE TABLE IF NOT EXISTS product (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'OFF_SALE',
 CONSTRAINT ck_product_status CHECK(status IN ('ON_SALE','OFF_SALE')),
 INDEX idx_product_status_id(status,id)
);
CREATE TABLE IF NOT EXISTS sku (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL, price DECIMAL(12,2) NOT NULL, status VARCHAR(20) NOT NULL,
 FOREIGN KEY(product_id) REFERENCES product(id), INDEX idx_sku_product(product_id),
 CONSTRAINT ck_sku_price CHECK(price>0), CONSTRAINT ck_sku_status CHECK(status IN ('ON_SALE','OFF_SALE'))
);
CREATE TABLE IF NOT EXISTS stock (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_id BIGINT NOT NULL UNIQUE,
 quantity INT NOT NULL DEFAULT 0, reserved_quantity INT NOT NULL DEFAULT 0,
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 CONSTRAINT ck_stock_quantity CHECK(quantity>=0 AND reserved_quantity>=0 AND reserved_quantity<=quantity)
);

CREATE TABLE IF NOT EXISTS sales_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(40) NOT NULL UNIQUE,
 status VARCHAR(24) NOT NULL, remark VARCHAR(500) NOT NULL DEFAULT '', recipient_name VARCHAR(100) NOT NULL, recipient_phone VARCHAR(30) NOT NULL,
 recipient_address VARCHAR(500) NOT NULL, total_amount DECIMAL(14,2) NOT NULL,
 completed_at DATETIME NULL, created_by BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(created_by) REFERENCES sys_user(id), INDEX idx_order_status_time(status,created_at), INDEX idx_order_creator(created_by),
 CONSTRAINT ck_order_status CHECK(status IN ('PENDING_PAYMENT','PENDING_SHIPMENT','SHIPPED','COMPLETED','CANCELED')),
 CONSTRAINT ck_order_total CHECK(total_amount>=0)
);

CREATE TABLE IF NOT EXISTS order_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 product_name VARCHAR(100) NOT NULL, sku_code VARCHAR(64) NOT NULL, sku_name VARCHAR(100) NOT NULL,
 unit_price DECIMAL(12,2) NOT NULL, quantity INT NOT NULL,
 UNIQUE(order_id,sku_id), FOREIGN KEY(order_id) REFERENCES sales_order(id), FOREIGN KEY(sku_id) REFERENCES sku(id),
 CONSTRAINT ck_order_item CHECK(unit_price>0 AND quantity>0)
);

CREATE TABLE IF NOT EXISTS stock_movement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_id BIGINT NOT NULL,
 order_id BIGINT NULL, FOREIGN KEY(order_id) REFERENCES sales_order(id), movement_type VARCHAR(24) NOT NULL, quantity_delta INT NOT NULL, reserved_delta INT NOT NULL,
 quantity_after INT NOT NULL, reserved_after INT NOT NULL,
 remark VARCHAR(500) NOT NULL DEFAULT '', business_key VARCHAR(100) NOT NULL UNIQUE, operator_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(sku_id) REFERENCES sku(id), FOREIGN KEY(operator_id) REFERENCES sys_user(id),
 INDEX idx_movement_sku_time(sku_id,created_at),
 CONSTRAINT ck_movement_type CHECK(movement_type IN ('RECEIPT','RESERVE','RELEASE','SHIP','RETURN')),
 CONSTRAINT ck_movement_delta CHECK(quantity_delta<>0 OR reserved_delta<>0),
 CONSTRAINT ck_movement_balance CHECK(quantity_after>=0 AND reserved_after>=0 AND reserved_after<=quantity_after)
);

CREATE TABLE IF NOT EXISTS shipment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL, carrier VARCHAR(80), tracking_no VARCHAR(80), shipped_by BIGINT, shipped_at DATETIME,
 FOREIGN KEY(order_id) REFERENCES sales_order(id), FOREIGN KEY(shipped_by) REFERENCES sys_user(id),
 CONSTRAINT ck_shipment_status CHECK(status IN ('PENDING','SHIPPED'))
);

CREATE TABLE IF NOT EXISTS return_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, return_no VARCHAR(40) NOT NULL UNIQUE, order_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL, reason VARCHAR(500) NOT NULL, created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_by BIGINT, reviewed_at DATETIME, rejection_reason VARCHAR(500), received_by BIGINT, received_at DATETIME, receipt_remark VARCHAR(500), updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(order_id) REFERENCES sales_order(id), FOREIGN KEY(created_by) REFERENCES sys_user(id),
 INDEX idx_return_order(order_id), INDEX idx_return_status(status),
 CONSTRAINT ck_return_status CHECK(status IN ('PENDING_REVIEW','REJECTED','PENDING_RECEIPT','COMPLETED'))
);
CREATE TABLE IF NOT EXISTS return_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, return_id BIGINT NOT NULL, order_item_id BIGINT NOT NULL,
 quantity INT NOT NULL, refund_amount DECIMAL(14,2) NOT NULL,
 UNIQUE(return_id,order_item_id), FOREIGN KEY(return_id) REFERENCES return_request(id), FOREIGN KEY(order_item_id) REFERENCES order_item(id),
 CONSTRAINT ck_return_item CHECK(quantity>0 AND refund_amount>=0)
);
