import { test, expect } from "@playwright/test";

async function login(page, username) {
  await page.goto("/login");
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(process.env.E2E_PASSWORD);
  await page.getByRole("button", { name: "登录工作台" }).click();
}

test("运营首页显示真实金额、趋势与商品排行并支持日期筛选", async ({ page }) => {
  const isDashboard = (response) =>
    response.url().includes("/api/analytics/dashboard") && response.ok();
  const initial = page.waitForResponse(isDashboard);
  await login(page, "operator");
  const result = (await (await initial).json()).data;
  await expect(page).toHaveURL(/analytics$/);
  await expect(page.getByTestId("orders-total")).toHaveText(
    String(result.orders.total),
  );
  const money = new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
  });
  for (const [id, amount] of [
    ["gross", result.grossAmount],
    ["returns-amount", result.completedReturns.amount],
    ["net", result.netAmount],
  ]) {
    await expect(page.getByTestId(id)).toHaveText(money.format(amount));
  }
  await expect(
    page.getByRole("img", { name: "每日新建和完成订单数量" }),
  ).toBeVisible();
  await expect(
    page.getByRole("img", { name: "每日模拟成交金额" }),
  ).toBeVisible();
  await page.getByText("按商品", { exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "SKU 快照", exact: true }),
  ).toHaveCount(0);

  const monthly = page.waitForResponse(
    (response) => isDashboard(response) && response.url().includes("days=30"),
  );
  await page.getByRole("button", { name: "最近 30 天" }).click();
  expect((await (await monthly).json()).data.trend).toHaveLength(30);
  await expect(page.getByRole("button", { name: "最近 7 天" })).toBeEnabled();
  const custom = page.waitForResponse(
    (response) => isDashboard(response) && response.url().includes("from="),
  );
  await page.getByPlaceholder("开始日期").click();
  const day = page.locator(
    ".el-date-range-picker td.available:not(.prev-month):not(.next-month)",
  );
  await day.nth(0).click();
  await day.nth(2).click();
  const range = (await (await custom).json()).data;
  expect(range.trend).toHaveLength(3);
  await expect(page.getByTestId("orders-total")).toHaveText(
    String(range.orders.total),
  );
  await expect(page.getByRole("heading", { name: "低库存预警" })).toBeVisible();
});

test("仓库首页和直接访问均遵守看板权限", async ({ page, request }) => {
  await login(page, "warehouse");
  await expect(page).toHaveURL(/inventory$/);
  await expect(page.getByRole("link", { name: "运营看板" })).toHaveCount(0);
  await page.goto("/analytics");
  await expect(page).toHaveURL(/inventory$/);
  const loginResponse = await request.post("/api/auth/login", {
    data: { username: "warehouse", password: process.env.E2E_PASSWORD },
  });
  const token = (await loginResponse.json()).data.token;
  const response = await request.get("/api/analytics/dashboard", {
    headers: { Authorization: "Bearer " + token },
  });
  expect(response.status()).toBe(403);
});
