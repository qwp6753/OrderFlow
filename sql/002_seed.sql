USE orderflow;
-- 首次安装执行一次；重复执行会触发唯一约束，不清理或覆盖已有数据。
INSERT INTO sys_role(id,code,name) VALUES (1,'ADMIN','系统管理员'),(2,'OPERATOR','运营人员'),(3,'WAREHOUSE','仓库人员');
INSERT INTO product(id,name,description,status) VALUES (1,'轻量通勤双肩包','适用于日常办公的轻量双肩包。','ON_SALE'),(2,'陶瓷随行杯','简洁耐用，适合办公室使用。','OFF_SALE');
INSERT INTO sku(id,product_id,code,name,price,status) VALUES (1,1,'BAG-BLACK','曜石黑 / 标准款',169.00,'ON_SALE'),(2,1,'BAG-GREEN','森林绿 / 标准款',179.00,'ON_SALE'),(3,2,'CUP-WHITE','暖白 / 350ml',59.00,'OFF_SALE');
INSERT INTO stock(sku_id,quantity,reserved_quantity) VALUES (1,0,0),(2,0,0),(3,0,0);
-- 测试账号由 dev profile 在首次启动时以 DEV_SEED_PASSWORD 的 BCrypt 哈希创建。
-- 不在 SQL 中存放通用默认口令或固定密码哈希。
