import {expect, test} from "@playwright/test";

import {
    annotateCase,
    businessStep,
    capturePageStep,
    claimOfferScenario,
    createMatrixAccount,
    createOfferScenario,
    installBrowserSession,
    uniqueRunId,
} from "./helpers/business-api";

test.describe("UI Matrix / Offer", () => {
  test("OFFER-SELLER-001 seller sees created offer workspace", async ({ page }, testInfo) => {
    await annotateCase({
      id: "OFFER-SELLER-001",
      epic: "UI 业务状态矩阵",
      feature: "Offer",
      story: "卖家发布 Offer 后看到创建成功页面",
      description: "验证卖家完成 Offer 发布后，Offer 详情页能展示 Offer 标题和 item 信息，并保存卖家视角截图。",
      tags: ["ui-matrix", "offer", "seller", "created"],
    });
    const runId = uniqueRunId();
    const seller = await createMatrixAccount("seller", runId);
    const offer = await createOfferScenario(seller, runId);

    await installBrowserSession(page, seller);
    await businessStep("卖家打开 Offer 详情页", async (stepContext) => {
      await stepContext.parameter("url", `/market/offers/${offer.offerNo}`);
      await page.goto(`/market/offers/${offer.offerNo}`);
      await capturePageStep(page, testInfo, {
        title: "截图：卖家进入 Offer 详情页",
        business: "offer",
        role: "seller",
        state: "01-open-offer-detail",
      });
    });

    await businessStep("断言：卖家能看到 Offer 和 item", async () => {
      // 中文注释：截图前先断言业务状态，保证 Allure 里的图片对应真实创建成功状态。
      await expect(page.getByRole("heading", { name: offer.title }).first()).toBeVisible();
      await expect(page.getByText(offer.itemTitle).first()).toBeVisible();
      await capturePageStep(page, testInfo, {
        title: "截图：卖家创建成功最终状态",
        business: "offer",
        role: "seller",
        state: "02-created-final",
      });
    });
  });

  test("OFFER-BUYER-001 buyer sees payment pending order", async ({ page }, testInfo) => {
    await annotateCase({
      id: "OFFER-BUYER-001",
      epic: "UI 业务状态矩阵",
      feature: "Offer",
      story: "买家认领 Offer item 后看到待支付订单",
      description: "验证买家完成 claim 后，订单页进入等待钱包签名和待支付状态，并保存买家视角截图。",
      tags: ["ui-matrix", "offer", "buyer", "payment-pending"],
    });
    const runId = uniqueRunId();
    const seller = await createMatrixAccount("seller", runId);
    const buyer = await createMatrixAccount("buyer", runId);
    const offer = await createOfferScenario(seller, runId);
    const claimed = await claimOfferScenario(buyer, offer);

    await installBrowserSession(page, buyer);
    await businessStep("买家打开订单详情页", async (stepContext) => {
      await stepContext.parameter("url", `/orders/${claimed.orderNo}`);
      await page.goto(`/orders/${claimed.orderNo}`);
      await capturePageStep(page, testInfo, {
        title: "截图：买家进入订单详情页",
        business: "offer",
        role: "buyer",
        state: "01-open-order-detail",
      });
    });

    await businessStep("断言：买家订单处于待支付状态", async () => {
      // 中文注释：待支付截图绑定订单详情和 claim receipt，便于 Allure 中追溯角色与状态来源。
      await expect(page.getByRole("heading", { name: offer.itemTitle }).first()).toBeVisible();
      await expect(page.getByText("等待钱包签名").first()).toBeVisible();
      await expect(page.getByText("待支付").first()).toBeVisible();
      await capturePageStep(page, testInfo, {
        title: "截图：买家待支付最终状态",
        business: "offer",
        role: "buyer",
        state: "02-payment-pending-final",
      });
    });
  });
});
