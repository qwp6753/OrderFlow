# 部署、增量迁移与测试指南

本文档补充说明数据库增量迁移注意事项、自动化测试体系配置以及本地常见启动排查。基础架构与快速启动请参见根目录 [README.md](../README.md)。

---

## 一、数据库初始化与增量迁移注意事项

项目采用纯 SQL 脚本管理表结构演进（未使用 Flyway / Liquibase），详细表定义见 [数据库设计](数据库设计.md)。

### 1. 全新安装与账号权限分离

全新空库按顺序执行初始化与增量脚本：

```powershell
mysql --user=root --password --default-character-set=utf8mb4 --execute="source sql/001_schema.sql"
mysql --user=root --password --default-character-set=utf8mb4 --execute="source sql/002_seed.sql"
mysql --user=root --password --database=orderflow --default-character-set=utf8mb4 --execute="source sql/003_orders_reservations.sql"
mysql --user=root --password --database=orderflow --default-character-set=utf8mb4 --execute="source sql/004_fulfillment_inventory.sql"
mysql --user=root --password --database=orderflow --default-character-set=utf8mb4 --execute="source sql/005_returns_receipt.sql"
```

- [001_schema.sql](../sql/001_schema.sql) 仅负责新建表，其中的 `IF NOT EXISTS` 不会升级已有表结构。
- [002_seed.sql](../sql/002_seed.sql) 仅用于新库首次初始化，包含初始角色与示例商品数据，不可重复执行。
- **权限分离建议**：应用运行时账号仅授予 `SELECT, INSERT, UPDATE, DELETE` 权限；执行结构变更时使用单独的管理员或迁移账号。

### 2. 已有库增量升级（003 / 004 / 005）

对于已存在历史数据的数据库，升级前须遵循：**确认目标库版本 → 停写 → 备份并验证可恢复 → 按未应用的编号依次执行 → 核对表结构与数据 → 恢复应用**。

> 注意：迁移脚本包含 MySQL DDL 语句（会产生隐式提交），**不具备幂等性**。若中途部分失败，应逐项核对 `SHOW CREATE TABLE` 后手工补齐，不可直接重跑或清空业务表。

各增量脚本核心变更说明：

| 脚本 | 核心变更 | 迁移前置要求与注意事项 |
| --- | --- | --- |
| [003_orders_reservations.sql](../sql/003_orders_reservations.sql) | 订单状态切换为 `PENDING_PAYMENT` / `PENDING_SHIPMENT` / `SHIPPED` / `COMPLETED` / `CANCELED`；新增 `sales_order.remark`、`order_item.product_name` 快照及 `stock_movement.order_id` 外键 | 迁移账号需具备 `ALTER` 与 `CREATE ROUTINE` 权限；若库中存在遗留 `CREATED` / `CONFIRMED` 未完结订单，存储过程会直接中止迁移以避免猜测预占口径 |
| [004_fulfillment_inventory.sql](../sql/004_fulfillment_inventory.sql) | 新增 `sales_order.completed_at` 与 `stock_movement.remark` | 需在已应用 `003` 的库中执行一次 |
| [005_returns_receipt.sql](../sql/005_returns_receipt.sql) | 售后状态切换为 `PENDING_REVIEW` / `REJECTED` / `PENDING_RECEIPT` / `COMPLETED`；补充审核人/验收人/时间/备注字段、人员外键及 `return_item(order_item_id)` 索引 | 需在已应用 `004` 的库中执行一次，执行后建议核对 `return_request` 与 `return_item` 的字段及约束 |

---

## 二、测试配置与执行说明

### 1. 后端单元测试与 H2 回归

默认执行 Maven 测试会运行基于 H2 内存库的配置与业务回归测试（未设置真实 MySQL 环境变量时，`OrderMySqlIntegrationTest` 自动跳过）：

```powershell
Set-Location backend
mvn.cmd -B -ntp -gs maven-settings.xml -s maven-settings.xml test
```

### 2. 真实 MySQL 订单与并发集成测试

`OrderMySqlIntegrationTest` 用于验证真实 InnoDB 事务隔离级别、行锁并发竞争以及故障注入下的流水/库存回滚。

- **安全前提**：必须连接独立的空测试库（例如 `orderflow_orders_test`）。测试类会在运行期间**清理该库中的订单、库存、商品与用户测试数据**，严禁指向开发或业务库。
- **建库方式**：复制 `sql/001_schema.sql` 并将建库/切换库语句中的库名替换为 `orderflow_orders_test` 后执行，再依次执行 `003`、`004`、`005`（无需执行 `002`，测试夹具会自动写入）。测试账号除 CRUD 外需具备 `ALTER` 权限（仅用于临时约束故障注入验证回滚）。

配置环境变量后执行打包与全量测试：

```powershell
$env:ORDERFLOW_MYSQL_TEST_URL='jdbc:mysql://localhost:3306/orderflow_orders_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:ORDERFLOW_MYSQL_TEST_USERNAME='orderflow_test'
$env:ORDERFLOW_MYSQL_TEST_PASSWORD=Read-Host '专用测试库口令' -MaskInput
Set-Location backend
mvn.cmd -B -ntp -gs maven-settings.xml -s maven-settings.xml package
```

### 3. API 烟雾测试与 Playwright E2E 测试

前端 E2E（`frontend/e2e/*.spec.js`）与 API 烟雾测试（[api-smoke.mjs](../scripts/api-smoke.mjs)）会真实调用写入接口并创建测试数据，请在启动隔离的 `dev` 测试环境后执行：

```powershell
# 1. API 烟雾测试（在仓库根目录执行）
$env:E2E_PASSWORD=Read-Host '测试环境账号密码' -MaskInput
# 可选：$env:API_BASE_URL='http://localhost:8080'
node scripts/api-smoke.mjs

# 2. Playwright E2E 测试（在 frontend 目录执行）
Set-Location frontend
npm.cmd run test:e2e
```

Playwright 配置默认使用 Edge（`msedge`）与 `http://127.0.0.1:5173`。如需使用 Chromium 或自定义前端地址，可先安装浏览器再覆盖环境变量：

```powershell
npx playwright install chromium
$env:E2E_BROWSER_CHANNEL='chromium'
$env:E2E_BASE_URL='http://127.0.0.1:5173'
```

---

## 三、生产构建与部署要点

### 1. 后端打包与运行配置

```powershell
Set-Location backend
mvn.cmd -B -ntp -gs maven-settings.xml -s maven-settings.xml package
java -jar target/orderflow-0.1.0.jar
```

- 生产环境使用 `SPRING_PROFILES_ACTIVE=prod`（默认关闭 Swagger/OpenAPI 文档），不可开启 `dev` Profile。
- 必须显式注入真实的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 以及精确匹配的 `CORS_ORIGINS`。

### 2. 前端生产构建与反向代理

```powershell
Set-Location frontend
npm.cmd run build
```

构建产物输出至 `frontend/dist`。部署时建议通过 Nginx 等反向代理同域转发 `/api` 到后端端口，并为 Vue Router `history` 模式配置 `try_files $uri $uri/ /index.html;` 回退规则。

---

## 四、常见启动问题排查

| 现象 | 排查与处理方式 |
| --- | --- |
| Maven 编译或启动提示 Java 版本不符 | 执行 `java -version` 与 `mvn -version` 确认均为 JDK 21，并在当前终端正确设置 `JAVA_HOME` |
| 启动报数据库连接拒绝或 `Access denied` | 核对 `DB_URL` 主机/端口、库名及数据库账号授权；确保应用账号对目标库拥有 CRUD 权限 |
| 接口报错缺少 `received_at`、`completed_at` 等列 | 确认目标数据库已按顺序应用 `003`、`004`、`005` 增量迁移脚本，切勿重复执行 `001` 替代升级 |
| 启动报 `JWT_SECRET` 或 `DEV_SEED_PASSWORD` 校验失败 | `JWT_SECRET` 须至少 32 字节且不能使用占位示例值；`DEV_SEED_PASSWORD` 须为 12 字符至 72 字节之间。注意修改 `DEV_SEED_PASSWORD` 不会重置数据库中已存在账号的密码 |
| 前端请求 `/api` 失败或触发 CORS 跨域拦截 | 核对后端监听端口、Vite 代理变量 `API_PROXY_TARGET`（默认 `http://localhost:8080`）、直连地址 `VITE_API_BASE_URL` 与后端 `CORS_ORIGINS` 配置；注意 `http://localhost:5173` 与 `http://127.0.0.1:5173` 属于不同 Origin |
