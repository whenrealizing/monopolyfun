import {expect, test} from "@playwright/test";

const smokeRoutes = [
  { id: "CORE-HOME-001", path: "/" },
  { id: "CORE-MARKET-001", path: "/market" },
  { id: "CORE-PUBLISH-001", path: "/publish" },
  { id: "CORE-PROFILE-MARKET-001", path: "/profile/me?section=market&tab=all" },
  { id: "CORE-SHARES-001", path: "/shares" },
  { id: "CORE-BACKOFFICE-001", path: "/backoffice" },
  { id: "CORE-LOGIN-001", path: "/login" },
  { id: "CORE-RESET-001", path: "/reset-password" },
];

test.describe("UI Matrix / Core routes", () => {
  for (const route of smokeRoutes) {
    test(`${route.id} ${route.path} renders without runtime errors`, async ({ page }) => {
      const pageErrors: string[] = [];
      const consoleErrors: string[] = [];
      page.on("pageerror", (error) => pageErrors.push(error.message));
      page.on("console", (message) => {
        if (message.type() === "error") {
          consoleErrors.push(message.text());
        }
      });

      const response = await page.goto(route.path);
      // 中文注释：核心路由 smoke 只校验页面能渲染出内容，作为全站 UI 入口的低成本回归网。
      expect(response?.ok()).toBeTruthy();
      await expect(page.locator("main, body").first()).toBeVisible();
      await expect.poll(() => page.locator("body").innerText()).not.toHaveLength(0);
      expect(pageErrors).toEqual([]);
      expect(consoleErrors.filter((message) => !isIgnorableConsoleError(message))).toEqual([]);
    });
  }
});

function isIgnorableConsoleError(message: string) {
  return /favicon|ResizeObserver|Failed to load resource.*(?:401|404)/i.test(message);
}
