import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
const apiBaseURL = process.env.PLAYWRIGHT_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  outputDir: "test-results",
  reporter: [
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["allure-playwright", { outputFolder: "allure-results" }],
  ],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    extraHTTPHeaders: {
      "X-Monopolyfun-Test-API-Base": apiBaseURL,
    },
  },
  webServer: [
    {
      // 中文注释：业务截图测试需要真实 API 造数，先等待 API 健康后再启动页面服务。
      command: "pnpm --dir ../.. api:dev",
      url: `${apiBaseURL}/actuator/health`,
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      // 中文注释：UI 矩阵测试默认复用本地 Web 服务，缺少 3000 服务时只启动 Web 端。
      command: "pnpm --dir ../.. web:dev",
      url: baseURL,
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
  projects: [
    {
      name: "chromium-desktop",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
