import { test, expect } from "@playwright/test";
const password = process.env.E2E_PASSWORD;
async function login(page, username) {
  if (!password) throw new Error("请设置 E2E_PASSWORD");
  await page.goto("/login");
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录工作台", exact: true }).click();
  await expect(page).toHaveURL(/(analytics|inventory)$/);
}
async function token(request) {
  const r = await request.post("/api/auth/login", {
    data: { username: "operator", password },
  });
  expect(r.ok()).toBeTruthy();
  return (await r.json()).data.token;
}
// 仅在专用开发测试库运行：通过真实入库API准备示例库存，不绕过库存流水。
test.beforeAll(async ({ request }) => {
  const response = await request.post("/api/auth/login", {
    data: { username: "warehouse", password },
  });
  expect(response.ok()).toBeTruthy();
  const warehouse = (await response.json()).data.token;
  for (const skuId of [1, 2]) {
    const receipt = await request.post(
      "/api/inventory/" + skuId + "/receipts",
      {
        headers: { Authorization: "Bearer " + warehouse },
        data: { quantity: 10, remark: "浏览器订单回归准备" },
      },
    );
    expect(receipt.ok()).toBeTruthy();
  }
});
async function inventory(request, auth) {
  const r = await request.get("/api/products/1/skus", {
    headers: { Authorization: "Bearer " + auth },
  });
  expect(r.ok()).toBeTruthy();
  return (await r.json()).data;
}
async function recipient(page) {
  await page.getByLabel("收件人", { exact: true }).fill("浏览器订单验收");
  await page.getByLabel("联系电话", { exact: true }).fill("13800000000");
  await page
    .getByLabel("收件地址", { exact: true })
    .fill("演示地址，不用于真实发货");
  await page
    .getByLabel("订单备注", { exact: true })
    .fill("内部模拟付款与取消测试");
}
async function selectSku(page, code, quantity) {
  await page.getByRole("combobox", { name: "选择商品", exact: true }).click();
  await page
    .getByRole("option", { name: "轻量通勤双肩包", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "选择 SKU", exact: true })
    .press("ArrowDown");
  await page.getByRole("option", { name: new RegExp(code) }).click();
  await page
    .getByRole("spinbutton", { name: "添加数量", exact: true })
    .fill(String(quantity));
  await page.getByRole("button", { name: "添加 SKU", exact: true }).click();
}
test("多SKU下单、模拟付款、取消释放库存及订单筛选", async ({
  page,
  request,
}) => {
  const auth = await token(request),
    before = await inventory(request, auth);
  await login(page, "operator");
  await page.getByRole("link", { name: "订单管理" }).click();
  await page.getByRole("button", { name: "＋ 创建订单", exact: true }).click();
  await recipient(page);
  await selectSku(page, "BAG-BLACK", 2);
  await selectSku(page, "BAG-GREEN", 1);
  await page.getByRole("button", { name: "提交订单并预占库存" }).click();
  await expect(page).toHaveURL(/orders\/\d+$/);
  await expect(page.getByText("待付款", { exact: true })).toBeVisible();
  await expect(page.getByText("¥517.00", { exact: true })).toBeVisible();
  const id = page.url().split("/").at(-1);
  const after = await inventory(request, auth);
  expect(after[0].reservedQuantity).toBe(before[0].reservedQuantity + 2);
  expect(after[1].reservedQuantity).toBe(before[1].reservedQuantity + 1);
  await page.getByRole("button", { name: "模拟确认付款", exact: true }).click();
  await page.getByRole("button", { name: "确认模拟付款", exact: true }).click();
  await expect(page.getByText("待发货", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "取消订单", exact: true }).click();
  await page.getByRole("button", { name: "确认取消", exact: true }).click();
  await expect(page.getByText("已取消", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "取消订单", exact: true }),
  ).toHaveCount(0);
  const released = await inventory(request, auth);
  expect(released.map((s) => s.reservedQuantity)).toEqual(
    before.map((s) => s.reservedQuantity),
  );
  const result = await request.get("/api/orders/" + id, {
    headers: { Authorization: "Bearer " + auth },
  });
  const detail = (await result.json()).data;
  expect(
    detail.movements.filter((m) => m.movementType === "RELEASE"),
  ).toHaveLength(2);
  await expect(page.locator(".el-message")).toHaveCount(0);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "test-results/order-detail.png",
    animations: "disabled",
  });
  await page.getByRole("button", { name: "返回订单列表" }).click();
  await page.getByLabel("订单编号", { exact: true }).fill(detail.order.orderNo);
  await page.getByRole("button", { name: "查询", exact: true }).click();
  await expect(
    page.getByRole("row").filter({ hasText: detail.order.orderNo }),
  ).toContainText("已取消");
});
test("库存不足展示后端错误并保留创建表单", async ({ page, request }) => {
  const auth = await token(request),
    before = await inventory(request, auth);
  await login(page, "operator");
  await page.goto("/orders/new");
  await recipient(page);
  await selectSku(page, "BAG-BLACK", 1000000);
  await page.getByRole("button", { name: "提交订单并预占库存" }).click();
  await expect(
    page.getByText("SKU 可用库存不足：BAG-BLACK", { exact: true }),
  ).toBeVisible();
  await expect(page).toHaveURL(/orders\/new$/);
  expect(
    (await inventory(request, auth)).map((s) => s.reservedQuantity),
  ).toEqual(before.map((s) => s.reservedQuantity));
});
test("仓库角色只能查询订单并被阻止进入创建页", async ({ page, request }) => {
  const auth = await token(request);
  const headers = { Authorization: "Bearer " + auth };
  const response = await request.post("/api/orders", {
    headers,
    data: {
      recipientName: "仓库权限验收",
      recipientPhone: "13800000000",
      recipientAddress: "测试地址",
      remark: "独立权限场景",
      items: [{ skuId: 1, quantity: 1 }],
    },
  });
  expect(response.ok()).toBeTruthy();
  const order = (await response.json()).data;
  try {
    await login(page, "warehouse");
    await page.getByRole("link", { name: "订单管理" }).click();
    await expect(page.getByText("仓库 · 发货作业")).toBeVisible();
    await expect(
      page.getByRole("button", { name: "＋ 创建订单", exact: true }),
    ).toHaveCount(0);
    await page.goto("/orders/new");
    await expect(page).toHaveURL(/orders$/);
    await page.goto("/orders/" + order.id);
    await expect(page.getByText("待付款", { exact: true })).toBeVisible();
    await expect(
      page.getByRole("button", { name: "模拟确认付款", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "取消订单", exact: true }),
    ).toHaveCount(0);
  } finally {
    const canceled = await request.post("/api/orders/" + order.id + "/cancel", {
      headers,
    });
    expect(canceled.ok()).toBeTruthy();
  }
});
