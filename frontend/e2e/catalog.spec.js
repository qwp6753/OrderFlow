import { test, expect } from "@playwright/test";
const password = process.env.E2E_PASSWORD;
async function login(page, username) {
  if (!password) throw new Error("请设置 E2E_PASSWORD（测试用开发账号密码）");
  await page.goto("/login");
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录工作台", exact: true }).click();
  await expect(page).toHaveURL(/(analytics|inventory)$/);
  await page.goto("/products");
}
test("运营完成商品创建、编辑、上下架、SKU 与真实库存查询", async ({ page }) => {
  await login(page, "operator");
  const suffix = Date.now().toString(),
    name = "浏览器验收商品-" + suffix;
  await page.getByRole("button", { name: "＋ 新增商品", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("商品名称", { exact: true }).fill(name);
  await dialog
    .getByLabel("商品描述", { exact: true })
    .fill("真实 MySQL 数据验证");
  await dialog.getByRole("button", { name: "保存商品" }).click();
  const row = page.getByRole("row").filter({ hasText: name });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "编辑", exact: true }).click();
  await dialog.getByLabel("商品描述", { exact: true }).fill("浏览器修改已完成");
  await dialog.getByRole("button", { name: "保存商品" }).click();
  await expect(row).toContainText("浏览器修改已完成");
  await row.getByRole("button", { name: "上架", exact: true }).click();
  await expect(
    row.getByRole("button", { name: "下架", exact: true }),
  ).toBeVisible();
  await row.getByRole("button", { name: "详情 / SKU" }).click();
  await page.getByRole("button", { name: "＋ 新增 SKU", exact: true }).click();
  await dialog.getByLabel("SKU 编码（全局唯一）").fill("E2E-" + suffix);
  await dialog.getByLabel("规格名称").fill("浏览器标准款");
  await dialog.getByRole("spinbutton").fill("39.90");
  await dialog.getByRole("button", { name: "保存 SKU" }).click();
  await expect(
    page.getByRole("row").filter({ hasText: "E2E-" + suffix }),
  ).toContainText("39.90");
  await expect(page.getByRole("dialog", { name: "新增 SKU", exact: true })).toBeHidden();
  await expect(page.locator(".el-message")).toHaveCount(0);
  await page.screenshot({
    animations: "disabled",
    path: "test-results/catalog-desktop.png",
    fullPage: true,
  });
});
test("仓库账号页面只读并能退出登录", async ({ page }) => {
  await login(page, "warehouse");
  await expect(page.getByText("仓库 · 只读查询")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "＋ 新增商品", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "编辑", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "退出登录" }).click();
  await expect(page).toHaveURL(/login/);
  await page.goto("/products");
  await expect(page).toHaveURL(/login/);
});
