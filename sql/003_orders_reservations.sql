-- MySQL8.0.16+ 增量迁移；先备份，在维护窗口执行一次；不重建表、不删除业务行。
-- 在选定的 OrderFlow 数据库中执行（mysql --database=orderflow ...）。
-- 旧版本没有订单入口；若人工写入过未结束的旧订单，不猜测其预占含义，直接中止并要求先核对。
DELIMITER $$
CREATE PROCEDURE orderflow_phase2_migrate()
BEGIN
 IF EXISTS (SELECT 1 FROM sales_order WHERE status IN ('CREATED','CONFIRMED')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy active orders exist. Reconcile reservations before migration.';
 END IF;
 ALTER TABLE sales_order DROP CHECK ck_order_status;
 UPDATE sales_order SET status='CANCELED' WHERE status='CANCELLED';
 ALTER TABLE sales_order
  ADD COLUMN remark VARCHAR(500) NOT NULL DEFAULT '',
  ADD CONSTRAINT ck_order_status CHECK(status IN ('PENDING_PAYMENT','PENDING_SHIPMENT','SHIPPED','COMPLETED','CANCELED')),
  ADD INDEX idx_order_created_id(created_at,id);
 ALTER TABLE order_item ADD COLUMN product_name VARCHAR(100) NULL;
 UPDATE order_item oi JOIN sku s ON s.id=oi.sku_id JOIN product p ON p.id=s.product_id SET oi.product_name=p.name;
 ALTER TABLE order_item MODIFY COLUMN product_name VARCHAR(100) NOT NULL;
 ALTER TABLE stock_movement ADD COLUMN order_id BIGINT NULL,
  ADD CONSTRAINT fk_movement_order FOREIGN KEY(order_id) REFERENCES sales_order(id),
  ADD INDEX idx_movement_order_time(order_id,created_at);
END$$
DELIMITER ;
CALL orderflow_phase2_migrate();
DROP PROCEDURE orderflow_phase2_migrate;
