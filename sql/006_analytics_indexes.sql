-- 为运营看板统计查询补充联合索引，避免按状态+完成/验收时间范围查询时全表扫描
CREATE INDEX idx_order_status_completed ON sales_order(status, completed_at);
CREATE INDEX idx_return_status_received ON return_request(status, received_at);