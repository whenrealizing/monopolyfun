import {mkdir, writeFile} from "node:fs/promises";
import {dirname} from "node:path";

import {
    type APIRequestContext,
    type APIResponse,
    expect,
    type Page,
    request as playwrightRequest,
    type TestInfo
} from "@playwright/test";
import * as allure from "allure-js-commons";
import sharp from "sharp";

type JsonRecord = Record<string, unknown>;

export type MatrixAccount = {
  api: APIRequestContext;
  accountId: string;
  displayName: string;
  handle: string;
  localSession: {
    accountId: string;
    displayName: string;
    handle: string;
    expiresAt?: string;
  };
};

export type OfferScenario = {
  title: string;
  itemTitle: string;
  offerNo: string;
  itemId: string;
  workspace: JsonRecord;
};

export type ClaimedOfferScenario = OfferScenario & {
  orderId: string;
  orderNo: string;
  claimReceipt: JsonRecord;
  orderDetail: JsonRecord;
};

const apiBaseURL = process.env.PLAYWRIGHT_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const appBaseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
const testPassword = "Playwright@260506";

export function uniqueRunId() {
  return Date.now().toString(36).slice(-6);
}

export async function createMatrixAccount(role: string, runId: string): Promise<MatrixAccount> {
  return businessStep(`准备${roleLabel(role)}账号`, async (stepContext) => {
    const api = await playwrightRequest.newContext({
      baseURL: apiBaseURL,
      extraHTTPHeaders: { Accept: "application/json" },
    });
    const handle = `ui_${role}_${runId}`.slice(0, 20);
    await stepContext.parameter("handle", handle);
    const response = await api.post("/api/v1/auth/register", {
      data: { handle, password: testPassword },
    });
    await expectApiOk(response, `register ${role}`);

    const session = await response.json() as JsonRecord;
    await attachJson(`${role}-session.json`, session);
    const account = readRecord(session.account, "account");
    const displaySkin = readRecord(account.displaySkin, "displaySkin");
    const accountId = readString(account.id, "account.id");
    const displayName = readString(displaySkin.displayName ?? account.displayName, "account.displayName");
    const displayHandle = readString(displaySkin.displayHandle ?? account.handle, "account.handle").replace(/^@+/, "");

    return {
      api,
      accountId,
      displayName,
      handle: displayHandle,
      localSession: {
        accountId,
        displayName,
        handle: displayHandle,
        expiresAt: typeof session.expiresAt === "string" ? session.expiresAt : undefined,
      },
    };
  });
}

export async function installBrowserSession(page: Page, account: MatrixAccount) {
  await businessStep(`切换浏览器为 ${account.handle} 登录态`, async (stepContext) => {
    await stepContext.parameter("accountId", account.accountId);
    const storageState = await account.api.storageState();
    await page.context().addCookies(storageState.cookies);
    await page.addInitScript((session) => {
      // 中文注释：页面门禁依赖本地 session，浏览器 cookie 负责服务端鉴权，两者都要同步。
      window.localStorage.setItem("monopolyfun-session", JSON.stringify(session));
    }, account.localSession);
    await attachJson("browser-session.json", account.localSession);
  });
}

export async function createOfferScenario(seller: MatrixAccount, runId: string): Promise<OfferScenario> {
  return businessStep("卖家发布 Offer 并生成一个可购买 item", async (stepContext) => {
    const title = `UI Offer ${runId}`;
    const itemTitle = `UI Item ${runId}`;
    await stepContext.parameter("title", title);
    await stepContext.parameter("itemTitle", itemTitle);
    const created = await postJson(seller.api, "/api/v1/offers", {
      title,
      description: "UI 矩阵测试使用的 Offer。",
      currency: "USD",
      paymentMethod: "okx_direct_pay",
      paymentRecipient: "0x1111111111111111111111111111111111111111",
      items: [{
        name: itemTitle,
        description: "用于截图矩阵的商品。",
        deliveryStandard: "交付可验证链接和说明。",
        acceptanceCriteria: ["链接可打开", "说明完整"],
        amount: 120,
        quantity: 1,
        agentInstruction: "按买家备注生成交付说明。",
      }],
    }, "publish offer");
    await attachJson("offer-create-response.json", created);
    const offer = readRecord(created.offer, "offer");
    const offerNo = readString(offer.offerNo, "offer.offerNo");
    await stepContext.parameter("offerNo", offerNo);
    const workspace = await getJson(seller.api, `/api/v1/posts/${offerNo}/workspace`, "offer workspace");
    await attachJson("offer-workspace.json", workspace);
    const items = readArray(workspace.items, "workspace.items");
    const item = readRecord(items[0], "workspace.items[0]");
    const itemId = readString(item.id, "item.id");

    return { title, itemTitle, offerNo, itemId, workspace };
  });
}

export async function claimOfferScenario(buyer: MatrixAccount, scenario: OfferScenario): Promise<ClaimedOfferScenario> {
  return businessStep("买家认领 item 并进入待支付订单", async (stepContext) => {
    await stepContext.parameter("itemId", scenario.itemId);
    const claimReceipt = await postJson(buyer.api, `/api/v1/items/${scenario.itemId}/claim`, {
      actorAccountId: buyer.accountId,
      buyerNote: "UI 矩阵测试买家备注。",
    }, "claim offer item");
    await attachJson("claim-receipt.json", claimReceipt);
    const orderId = readString(claimReceipt.subjectId, "claim.subjectId");
    const orders = await getJson(buyer.api, "/api/v1/orders?limit=20", "list buyer orders");
    await attachJson("buyer-orders.json", orders);
    const orderItems = readArray(orders.items, "orders.items");
    // 中文注释：命令回执可能返回内部 id 或公开 orderNo，这里统一解析成页面可打开的 orderNo。
    const order = orderItems
      .map((item) => readRecord(item, "orders.items[]"))
      .find((item) => item.id === orderId || item.orderNo === orderId);
    if (!order) {
      throw new Error(`未在买家订单列表找到订单 ${orderId}`);
    }
    const orderNo = readString(order.orderNo, "order.orderNo");
    await stepContext.parameter("orderNo", orderNo);
    const orderDetail = await getJson(buyer.api, `/api/v1/orders/${orderNo}`, "buyer order detail");
    await attachJson("order-detail.json", orderDetail);

    return { ...scenario, orderId, orderNo, claimReceipt, orderDetail };
  });
}

export async function attachJson(name: string, value: unknown) {
  // 中文注释：Allure 产物可能进入 CI artifact，写入前统一脱敏 session、cookie 和 token 字段。
  await allure.attachment(name, JSON.stringify(redactSensitiveJson(value), null, 2), { contentType: "application/json" });
}

function redactSensitiveJson(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(redactSensitiveJson);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(Object.entries(value as JsonRecord).map(([key, item]) => {
    if (/(token|password|cookie|secret|authorization|email)/i.test(key)) {
      return [key, "[REDACTED]"];
    }
    return [key, redactSensitiveJson(item)];
  }));
}

export async function annotateCase(input: {
  id: string;
  epic: string;
  feature: string;
  story: string;
  description: string;
  tags: string[];
}) {
  await allure.allureId(input.id);
  await allure.epic(input.epic);
  await allure.feature(input.feature);
  await allure.story(input.story);
  await allure.description(input.description);
  await allure.tags(...input.tags);
}

export async function businessStep<T>(
  name: string,
  body: (context: allure.StepContext) => T | PromiseLike<T>,
): Promise<T> {
  return allure.step(name, body) as Promise<T>;
}

export async function capturePageStep(page: Page, testInfo: TestInfo, input: {
  title: string;
  business: string;
  role: string;
  state: string;
  scrollRoot?: string;
}) {
  return businessStep(input.title, async () => {
    const screenshot = await captureStitchedScrollScreenshot(page, input.scrollRoot ?? "main");
    const path = screenshotPath(testInfo, input.business, input.role, input.state);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, screenshot);
    await allure.attachment(`${input.business}/${input.role}/${input.state}.png`, screenshot, { contentType: "image/png" });
    return screenshot;
  });
}

export function screenshotPath(testInfo: TestInfo, business: string, role: string, state: string) {
  return testInfo.outputPath("ui-matrix", business, role, `${state}.png`);
}

async function getJson(api: APIRequestContext, path: string, action: string) {
  const response = await api.get(path);
  await expectApiOk(response, action);
  return response.json() as Promise<JsonRecord>;
}

async function postJson(api: APIRequestContext, path: string, data: JsonRecord, action: string) {
  const csrfToken = await readCsrfToken(api);
  const response = await api.post(path, {
    data,
    headers: csrfToken ? { "X-CSRF-Token": csrfToken } : undefined,
  });
  await expectApiOk(response, action);
  return response.json() as Promise<JsonRecord>;
}

async function readCsrfToken(api: APIRequestContext) {
  const storageState = await api.storageState();
  const csrfCookie = storageState.cookies.find((cookie) => cookie.name === "MONOPOLYFUN_CSRF");
  return csrfCookie ? decodeURIComponent(csrfCookie.value) : null;
}

async function expectApiOk(response: APIResponse, action: string) {
  if (response.ok()) {
    return;
  }
  const body = await response.text();
  expect(response.ok(), `${action} failed: ${response.status()} ${body}`).toBeTruthy();
}

function readRecord(value: unknown, label: string): JsonRecord {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} 不是对象`);
  }
  return value as JsonRecord;
}

function readArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new Error(`${label} 不是数组`);
  }
  return value;
}

function readString(value: unknown, label: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label} 不是有效字符串`);
  }
  return value;
}

function roleLabel(role: string) {
  const labels: Record<string, string> = {
    seller: "卖家",
    buyer: "买家",
    requester: "需求方",
    worker: "执行者",
    owner: "Owner",
    reviewer: "评审员",
  };
  return labels[role] ?? role;
}

async function captureStitchedScrollScreenshot(page: Page, scrollRootSelector: string) {
  const rootInfo = await page.evaluate((selector) => {
    const candidates = [
      document.querySelector("[data-ui-scroll-root]"),
      document.querySelector(selector),
      document.querySelector("main"),
      document.scrollingElement,
    ].filter(Boolean) as Element[];
    const root = candidates.find((element) => element.scrollHeight > element.clientHeight) ?? candidates[0];
    if (!root) {
      throw new Error("没有找到可截图的滚动容器");
    }
    const rect = root.getBoundingClientRect();
    return {
      scrollHeight: root.scrollHeight,
      clientHeight: root.clientHeight,
      clientWidth: root.clientWidth,
      x: Math.max(0, rect.x),
      y: Math.max(0, rect.y),
      width: Math.max(1, Math.min(rect.width, window.innerWidth - Math.max(0, rect.x))),
      height: Math.max(1, Math.min(rect.height, window.innerHeight - Math.max(0, rect.y))),
    };
  }, scrollRootSelector);

  const maxScrollTop = Math.max(0, rootInfo.scrollHeight - rootInfo.clientHeight);
  const requestedTops: number[] = [];
  for (let top = 0; top < maxScrollTop; top += rootInfo.clientHeight) {
    requestedTops.push(top);
  }
  requestedTops.push(maxScrollTop);

  const segments: Array<{ top: number; image: Buffer }> = [];
  const seenTops = new Set<number>();
  for (const requestedTop of requestedTops) {
    const actualTop = await page.evaluate(({ selector, top }) => {
      const candidates = [
        document.querySelector("[data-ui-scroll-root]"),
        document.querySelector(selector),
        document.querySelector("main"),
        document.scrollingElement,
      ].filter(Boolean) as Element[];
      const root = candidates.find((element) => element.scrollHeight > element.clientHeight) ?? candidates[0];
      if (!root) {
        throw new Error("没有找到可滚动容器");
      }
      // 中文注释：长图按真实滚动位置拼接，避免末屏被浏览器自动夹到最大 scrollTop 后重复。
      root.scrollTo(0, top);
      return root.scrollTop;
    }, { selector: scrollRootSelector, top: requestedTop });
    if (seenTops.has(actualTop)) {
      continue;
    }
    seenTops.add(actualTop);
    await page.waitForTimeout(120);
    const image = await page.screenshot({
      clip: {
        x: rootInfo.x,
        y: rootInfo.y,
        width: rootInfo.width,
        height: rootInfo.height,
      },
    });
    segments.push({ top: actualTop, image });
  }

  await page.evaluate((selector) => {
    const candidates = [
      document.querySelector("[data-ui-scroll-root]"),
      document.querySelector(selector),
      document.querySelector("main"),
      document.scrollingElement,
    ].filter(Boolean) as Element[];
    const root = candidates.find((element) => element.scrollHeight > element.clientHeight) ?? candidates[0];
    root?.scrollTo(0, 0);
  }, scrollRootSelector);

  if (segments.length === 0) {
    return page.screenshot({ fullPage: true });
  }

  const firstMetadata = await sharp(segments[0].image).metadata();
  const scale = (firstMetadata.width ?? rootInfo.clientWidth) / rootInfo.width;
  const outputWidth = Math.round(rootInfo.width * scale);
  const outputHeight = Math.max(1, Math.round(rootInfo.scrollHeight * scale));
  const composites = await Promise.all(segments.map(async (segment) => {
    const visibleCssHeight = Math.min(rootInfo.clientHeight, rootInfo.scrollHeight - segment.top);
    const visiblePixelHeight = Math.max(1, Math.round(visibleCssHeight * scale));
    const image = await sharp(segment.image)
      .extract({
        left: 0,
        top: 0,
        width: outputWidth,
        height: Math.min(visiblePixelHeight, firstMetadata.height ?? visiblePixelHeight),
      })
      .toBuffer();
    return {
      input: image,
      left: 0,
      top: Math.round(segment.top * scale),
    };
  }));

  return sharp({
    create: {
      width: outputWidth,
      height: outputHeight,
      channels: 4,
      background: "#ffffff",
    },
  })
    .composite(composites)
    .png()
    .toBuffer();
}

export { apiBaseURL, appBaseURL };
