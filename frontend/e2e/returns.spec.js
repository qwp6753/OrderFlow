import { test, expect } from "@playwright/test";

test("运营申请并审核，仓库验收入库", async ({ page, request }) => {
  const password = process.env.E2E_PASSWORD;
  async function token(username) {
    const response = await request.post("/api/auth/login", {
      data: { username, password },
    });
    expect(response.ok()).toBeTruthy();
    return { Authorization: "Bearer " + (await response.json()).data.token };
  }
  async function post(path, headers, data) {
    const response = await request.post("/api" + path, { headers, data });
    expect(response.ok()).toBeTruthy();
    return (await response.json()).data;
  }
  async function login(username) {
    await page.goto("/login");
    await page.getByLabel("用户名", { exact: true }).fill(username);
    await page.getByLabel("密码", { exact: true }).fill(password);
    await page.getByRole("button", { name: "登录工作台" }).click();
    await expect(page).toHaveURL(/(analytics|inventory)$/);
  }
  const operator = await token("operator"),
    warehouse = await token("warehouse");
  await post("/inventory/1/receipts", warehouse, {
    quantity: 2,
    remark: "售后验收测试",
  });
  const order = await post("/orders", operator, {
    recipientName: "演示用户",
    recipientPhone: "13800000000",
    recipientAddress: "演示地址",
    remark: "",
    items: [{ skuId: 1, quantity: 2 }],
  });
  await post("/orders/" + order.id + "/simulate-payment", operator);
  await post("/orders/" + order.id + "/ship", warehouse, {
    carrier: "演示物流",
    trackingNo: "RT-TEST",
  });
  await post("/orders/" + order.id + "/complete", operator);

  await login("operator");
  await page.goto("/orders/" + order.id);
  await page.getByRole("button", { name: "售后记录", exact: true }).click();
  await page.getByRole("button", { name: "申请退货", exact: true }).click();
  const form = page.getByRole("dialog", { name: "申请退货", exact: true });
  await form.getByRole("combobox").press("ArrowDown");
  await page.getByRole("option", { name: /BAG-BLACK/ }).click();
  await form.getByLabel("退货原因", { exact: true }).fill("演示部分退货");
  await form.getByRole("button", { name: "提交退货申请" }).click();
  const detail = page.getByRole("dialog", { name: "售后详情", exact: true });
  await expect(detail.getByText("¥169.00", { exact: true })).toBeVisible();
  await detail.getByRole("button", { name: "审核通过", exact: true }).click();
  await page.getByRole("button", { name: "确定", exact: true }).click();
  await expect(detail.getByText("待验收", { exact: true })).toBeVisible();

  await login("warehouse");
  await page.goto("/returns?orderId=" + order.id);
  await page.getByRole("button", { name: "查看售后" }).click();
  await detail
    .getByRole("button", { name: "确认全部验收入库", exact: true })
    .click();
  const confirm = page.getByRole("dialog", {
    name: "确认全部验收入库",
    exact: true,
  });
  await confirm.getByRole("textbox").fill("商品符合重新销售条件");
  await confirm.getByRole("button", { name: "确定", exact: true }).click();
  await expect(detail.getByText("退货完成", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(detail).toBeHidden();
  await page.getByRole("link", { name: "库存管理" }).click();
  await page.getByRole("tab", { name: "库存流水" }).click();
  await page.getByLabel("SKU 编码", { exact: true }).fill("BAG-BLACK");
  await page
    .getByRole("combobox", { name: "业务类型", exact: true })
    .press("ArrowDown");
  await page.getByRole("option", { name: "退货入库", exact: true }).click();
  await page.getByRole("button", { name: "查询流水", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "退货入库", exact: true }).first(),
  ).toBeVisible();
  const result = await request.get("/api/inventory/movements", {
    headers: warehouse,
    params: { skuId: 1, type: "RETURN" },
  });
  expect((await result.json()).data.records[0]).toMatchObject({
    orderId: order.id,
    quantityDelta: 1,
    reservedDelta: 0,
  });
});
