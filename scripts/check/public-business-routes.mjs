import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const ignored = new Set([".git", ".next", "node_modules", "target", "dist", "build"]);
const fileExtensions = new Set([".ts", ".tsx", ".js", ".mjs"]);

const routeDirFragments = [
  "app/orders/[orderId]",
  "app/market/offers/[offerId]",
  "app/market/requests/[requestId]",
  "app/market/projects/[projectId]",
];

const forbiddenPatterns = [
  { pattern: /\/orders\/\$\{[^}]*\.id[^}]*\}/, reason: "订单 URL 必须使用 orderNo" },
  { pattern: /\/market\/offers\/\$\{[^}]*\.id[^}]*\}/, reason: "Offer URL 必须使用 offerNo" },
  { pattern: /\/market\/requests\/\$\{[^}]*\.id[^}]*\}/, reason: "Request URL 必须使用 requestNo" },
  { pattern: /\/market\/projects\/\$\{[^}]*\.id[^}]*\}/, reason: "Project URL 必须使用 projectNo" },
  { pattern: /router\.push\(`\/orders\/\$\{[^}]*subjectId[^}]*\}`\)/, reason: "订单跳转必须使用 receipt.payload.orderNo" },
];

function walk(dir, files = []) {
  for (const entry of readdirSync(dir)) {
    if (ignored.has(entry)) continue;
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      walk(path, files);
      continue;
    }
    if ([...fileExtensions].some((suffix) => path.endsWith(suffix))) files.push(path);
  }
  return files;
}

const failures = [];

for (const fragment of routeDirFragments) {
  const routePath = join(root, "apps/web", fragment);
  try {
    if (statSync(routePath).isDirectory()) {
      failures.push(`${fragment}: 动态路由目录仍使用内部 id 命名`);
    }
  } catch {
    // 中文注释：目录不存在代表已迁移到业务编号路由名。
  }
}

for (const file of walk(join(root, "apps/web"))) {
  if (file.includes("/lib/generated/")) continue;
  const text = readFileSync(file, "utf8");
  for (const rule of forbiddenPatterns) {
    if (rule.pattern.test(text)) {
      failures.push(`${relative(root, file)}: ${rule.reason}`);
    }
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("public business routes check passed");
