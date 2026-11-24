import { test, expect } from "@playwright/test";
const password = process.env.E2E_PASSWORD;
async function login(page, username) {
  await page.goto("/login");
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录工作台", exact: true }).click();
  await expect(page).toHaveURL(/(analytics|inventory)$/);
}
async function token(request, username) {
  const r = await request.post("/api/auth/login", {
    data: { username, password },
  });
  expect(r.ok()).toBeTruthy();
  return (await r.json()).data.token;
}
test("从零库存入库到发货完成，库存与流水一致且角色隔离", async ({
  page,
  request,
}) => {
  test.setTimeout(60000);
  if (!password) throw new Error("请设置E2E_PASSWORD");
  const operator = await token(request, "operator"),
    warehouse = await token(request, "warehouse");
  const operatorHeaders = { Authorization: "Bearer " + operator },
    warehouseHeaders = { Authorization: "Bearer " + warehouse };
  const suffix = Date.now().toString(),
    code = "FULFILL-" + suffix;
  const created = await request.post("/api/products", {
    headers: operatorHeaders,
    data: {
      name: "发货验收商品-" + suffix,
      description: "真实接口建立零库存SKU",
    },
  });
  expect(created.ok()).toBeTruthy();
  const product = (await created.json()).data;
  expect(
    (
      await request.patch("/api/products/" + product.id + "/status", {
        headers: operatorHeaders,
        data: { status: "ON_SALE" },
      })
    ).ok(),
  ).toBeTruthy();
  const skuResponse = await request.post(
    "/api/products/" + product.id + "/skus",
    {
      headers: operatorHeaders,
      data: { code, name: "验收标准款", price: "25.00", status: "ON_SALE" },
    },
  );
  expect(skuResponse.ok()).toBeTruthy();
  const sku = (await skuResponse.json()).data;
  async function inventory() {
    const r = await request.get("/api/inventory", {
      headers: operatorHeaders,
      params: { keyword: code },
    });
    expect(r.ok()).toBeTruthy();
    return (await r.json()).data.records[0];
  }
  expect((await inventory()).quantity).toBe(0);
  await login(page, "warehouse");
  await page.getByRole("link", { name: "库存管理" }).click();
  await page.getByLabel("商品 / SKU", { exact: true }).fill(code);
  await page.getByRole("button", { name: "查询库存", exact: true }).click();
  const row = page.getByRole("row").filter({ hasText: code });
  await row.getByRole("button", { name: "入库", exact: true }).click();
  const receipt = page.getByRole("dialog", { name: "库存入库", exact: true });
  await receipt.getByRole("spinbutton").fill("7");
  await receipt.getByLabel("入库备注", { exact: true }).fill("浏览器实际入库");
  await receipt.getByRole("button", { name: "确认入库", exact: true }).click();
  await expect(receipt).toBeHidden();
  expect((await inventory()).quantity).toBe(7);
  const orderResponse = await request.post("/api/orders", {
    headers: operatorHeaders,
    data: {
      recipientName: "模拟收件人",
      recipientPhone: "13800000000",
      recipientAddress: "演示地址",
      remark: "仓库发货完整验收",
      items: [{ skuId: sku.id, quantity: 3 }],
    },
  });
  expect(orderResponse.ok()).toBeTruthy();
  const order = (await orderResponse.json()).data;
  expect(
    (
      await request.post("/api/orders/" + order.id + "/simulate-payment", {
        headers: operatorHeaders,
      })
    ).ok(),
  ).toBeTruthy();
  await page.getByRole("link", { name: "订单管理" }).click();
  await page.getByRole("button", { name: "待发货订单", exact: true }).click();
  await page.getByLabel("订单编号", { exact: true }).fill(order.orderNo);
  await page.getByRole("button", { name: "查询", exact: true }).click();
  await page
    .getByRole("row")
    .filter({ hasText: order.orderNo })
    .getByRole("button", { name: "查看详情", exact: true })
    .click();
  await page.getByRole("button", { name: "订单发货", exact: true }).click();
  const shipDialog = page.getByRole("dialog", {
    name: "填写发货信息",
    exact: true,
  });
  await shipDialog.getByLabel("物流公司", { exact: true }).fill("演示物流");
  await shipDialog
    .getByLabel("物流单号", { exact: true })
    .fill("TRACK-" + suffix);
  await shipDialog
    .getByRole("button", { name: "确认发货", exact: true })
    .click();
  await expect(shipDialog).toBeHidden();
  await expect(page.getByText("已发货", { exact: true })).toBeVisible();
  await expect(
    page.getByText("TRACK-" + suffix, { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "模拟确认收货", exact: true }),
  ).toHaveCount(0);
  const shipped = await inventory();
  expect(shipped.quantity).toBe(4);
  expect(shipped.reservedQuantity).toBe(0);
  expect(shipped.availableQuantity).toBe(4);
  expect(
    (
      await request.post("/api/orders/" + order.id + "/cancel", {
        headers: operatorHeaders,
      })
    ).status(),
  ).toBe(409);
  expect(
    (
      await request.post("/api/orders/" + order.id + "/ship", {
        headers: warehouseHeaders,
        data: { carrier: "重复", trackingNo: "重复" },
      })
    ).status(),
  ).toBe(409);
  expect((await inventory()).quantity).toBe(4);
  await page.getByRole("button", { name: "退出登录" }).click();
  await login(page, "operator");
  await page.goto("/orders/" + order.id);
  await page.getByRole("button", { name: "模拟确认收货", exact: true }).click();
  await page.getByRole("button", { name: "确认模拟收货", exact: true }).click();
  await expect(page.getByText("已完成", { exact: true })).toBeVisible();
  await expect(page.getByText("完成时间", { exact: true })).toBeVisible();
  expect((await inventory()).quantity).toBe(4);
  await page.getByRole("link", { name: "库存管理" }).click();
  await page.getByLabel("商品 / SKU", { exact: true }).fill(code);
  await page.getByRole("button", { name: "查询库存", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "入库", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("row")
    .filter({ hasText: code })
    .getByRole("button", { name: "查看流水", exact: true })
    .click();
  await expect(
    page.getByRole("cell", { name: "发货出库", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "浏览器实际入库", exact: true }),
  ).toBeVisible();
  const ledgerResponse = await request.get("/api/inventory/movements", {
    headers: operatorHeaders,
    params: { skuId: sku.id },
  });
  const ledger = (await ledgerResponse.json()).data.records;
  expect(ledger).toHaveLength(3);
  expect(ledger.map((m) => m.movementType).sort()).toEqual([
    "RECEIPT",
    "RESERVE",
    "SHIP",
  ]);
  expect(ledger.find((m) => m.movementType === "SHIP").quantityDelta).toBe(-3);
  expect(ledger.find((m) => m.movementType === "SHIP").reservedDelta).toBe(-3);
  await expect(page.locator(".el-message")).toHaveCount(0);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "test-results/inventory-fulfillment.png",
    animations: "disabled",
  });
});
