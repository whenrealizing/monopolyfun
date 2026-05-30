import { readFileSync, readdirSync, statSync } from "node:fs";
import { extname, join, relative } from "node:path";

const root = process.cwd();
const sourceExtensions = new Set([".java", ".ts", ".tsx", ".js", ".mjs"]);
const ignoredDirs = new Set([".git", ".next", "node_modules", "target", "dist", "build", "allure-report", "allure-results"]);
const failures = [];

const orderControllerPath = join(root, "apps/api/src/main/java/com/monopolyfun/modules/order/api/OrderController.java");
const generatedApiPath = join(root, "apps/web/lib/generated/api/monopolyfun.ts");
const generatedModelIndexPath = join(root, "apps/web/lib/generated/api/model/index.ts");
const publicApiPath = join(root, "apps/web/lib/api/index.ts");
const workQueryServicePath = join(root, "apps/api/src/main/java/com/monopolyfun/modules/work/service/WorkQueryService.java");
const publishProjectRequestPath = join(root, "apps/api/src/main/java/com/monopolyfun/modules/project/api/request/PublishProjectRequest.java");
const paymentControllerPath = join(root, "apps/api/src/main/java/com/monopolyfun/modules/payment/api/PaymentController.java");
const forbiddenPublicOrderWritePattern = /\/api\/v1\/orders\/(?:\$\{[^}]+}|[^/"`']+|\{orderNo})\/(?:proofs|progress|accept|dispute|cancel-dispute|appeal|assign-reviewer|override-review|close)/g;

for (const file of walk(join(root, "apps/api/src/main/java"))) {
  const text = readFileSync(file, "utf8");
  if (text.includes('RequestMapping("/api/v1/review-tasks"') || text.includes('"/api/v1/review-tasks"')) {
    failures.push(`${relative(root, file)}: review task public API has been replaced by WorkItem claim`);
  }
  if (text.includes("class ReviewTaskController") || text.includes("ClaimReviewTaskRequest")) {
    failures.push(`${relative(root, file)}: review task compatibility classes must stay removed`);
  }
  for (const token of ["WorkSourceSyncService", "WorkbenchItemProvider"]) {
    if (text.includes(token)) {
      failures.push(`${relative(root, file)}: Workbench maintenance adapter must stay removed; write WorkItem facts at source mutations`);
    }
  }
  // 中文注释：订单写入统一收口到 Work API，隐藏 internal route 也会造成 agent/client 契约分叉。
  if (text.includes("/api/v1/internal/orders") || text.includes("InternalOrderCommandController")) {
    failures.push(`${relative(root, file)}: internal order command route must stay removed; use /api/v1/work/orders`);
  }
  // 中文注释：Agent action card 也会成为运行时契约，旧订单写路由需要和 Controller 一起被门禁拦住。
  for (const match of text.matchAll(forbiddenPublicOrderWritePattern)) {
    failures.push(`${relative(root, file)}: ${match[0]} must use /api/v1/work/orders for public write actions`);
  }
}

if (exists(orderControllerPath)) {
  const orderController = readFileSync(orderControllerPath, "utf8");
  const forbiddenOrderWrites = [
    "/{orderNo}/proofs",
    "/{orderNo}/progress",
    "/{orderNo}/accept",
    "/{orderNo}/dispute",
    "/{orderNo}/cancel-dispute",
    "/{orderNo}/appeal",
    "/{orderNo}/assign-reviewer",
    "/{orderNo}/override-review",
    "/{orderNo}/close",
  ];
  for (const route of forbiddenOrderWrites) {
    if (orderController.includes(`@PostMapping("${route}")`)) {
      failures.push(`${relative(root, orderControllerPath)}: ${route} must be exposed through WorkController or internal orders only`);
    }
  }
}

if (exists(publicApiPath)) {
  const publicApi = readFileSync(publicApiPath, "utf8");
  for (const match of publicApi.matchAll(forbiddenPublicOrderWritePattern)) {
    failures.push(`${relative(root, publicApiPath)}: ${match[0]} must use /api/v1/work/orders`);
  }
}

if (exists(workQueryServicePath)) {
  const workQueryService = readFileSync(workQueryServicePath, "utf8");
  for (const token of ["WorkSourceSyncService", "refreshAccountItems"]) {
    if (workQueryService.includes(token)) {
      failures.push(`${relative(root, workQueryServicePath)}: Workbench reads must consume persisted WorkItem facts only`);
    }
  }
}

if (exists(publishProjectRequestPath)) {
  const publishProjectRequest = readFileSync(publishProjectRequestPath, "utf8");
  if (publishProjectRequest.includes("rewardModel")) {
    failures.push(`${relative(root, publishProjectRequestPath)}: rewardModel old pricing field must stay removed from public project publish contract`);
  }
}

for (const controllerPath of [paymentControllerPath].filter(exists)) {
  const controller = readFileSync(controllerPath, "utf8");
  if (controller.includes("/callback/okx/a2a") && !controller.includes("@Hidden")) {
    failures.push(`${relative(root, controllerPath)}: provider callbacks must stay hidden from generated public clients`);
  }
}

for (const file of [generatedApiPath, generatedModelIndexPath].filter(exists)) {
  const text = readFileSync(file, "utf8");
  const forbiddenTokens = [
    "ClaimReviewTaskRequest",
    "claimReviewTask",
    "getClaimReviewTaskUrl",
    "fakeCallback",
    "getFakeCallbackUrl",
    "okxA2aCallback",
    "getOkxA2aCallbackUrl",
    "getHandleUrl",
    "rewardModel",
  ];
  for (const token of forbiddenTokens) {
    if (text.includes(token)) {
      failures.push(`${relative(root, file)}: generated client still exposes ${token}`);
    }
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("legacy compatibility surface checks passed");

function exists(path) {
  try {
    statSync(path);
    return true;
  } catch {
    return false;
  }
}

function walk(dir, files = []) {
  for (const entry of readdirSync(dir)) {
    if (ignoredDirs.has(entry)) continue;
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      walk(path, files);
      continue;
    }
    if (sourceExtensions.has(extname(path))) files.push(path);
  }
  return files;
}
