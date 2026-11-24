# 真实截图来源

2026-11-06 使用已安装 Edge / Playwright，1440×1000 视口、整页截图。页面为本次 npm run build 的真实产物，经本地代理连接当前 Spring Boot 后端；数据库为 127.0.0.1:3307 的 orderflow_phase6_ui_test，沿用前期自动化演示数据。没有 mock 接口、修改 DOM 内容、图片生成或后期合成。

| 文件 | 实际路由 | 覆盖内容 |
| --- | --- | --- |
| login.png | /login | 未输入凭据的真实登录页 |
| analytics.png | /analytics | 管理后台默认入口与运营统计 |
| products.png | /products | 商品与 SKU 管理 |
| orders.png | /orders | 订单分页、状态、金额与演示收货人 |
| inventory.png | /inventory | 实物、预占与可用库存 |
| returns.png | /returns | 售后列表、状态与演示原因 |

登录后使用 ADMIN 查看现有数据，五个业务页均收到真实 API 200，未出现页面 JavaScript 错误；本次只截图，没有下单 / 入库 / 退货写入。已逐张检查，不展示密码、token、真实客户手机号或地址。表中的“浏览器订单验收”“演示用户”等来自已有测试夹具。

原有截图位于 frontend/test-results 的商品、订单详情和库存图片，以及 .runtime/phase5-dashboard.png；这些是以前执行的截图，本次使用新截图替代展示，不删除旧文件。商品页底部仍保留早期“本阶段不执行库存扣减、出库或发货”文案，指该页无这些操作；系统实际发货在订单详情完成，可后续小幅调整文案以免误解，本阶段不改业务页面。
