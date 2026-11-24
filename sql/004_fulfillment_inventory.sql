-- 在已执行003的现有OrderFlow库中执行一次；执行前备份并暂停写入。
-- MySQL DDL隐式提交；不删除/重建任何表，不修改已有库存和订单状态。
ALTER TABLE sales_order ADD COLUMN completed_at DATETIME NULL;
ALTER TABLE stock_movement ADD COLUMN remark VARCHAR(500) NOT NULL DEFAULT '';
-- shipment已有order_id唯一约束、carrier/tracking_no/shipped_by/shipped_at，无需重复建表。
