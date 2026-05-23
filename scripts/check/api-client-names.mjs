import { readFileSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const generatedApiPath = join(root, "apps/web/lib/generated/api/monopolyfun.ts");
const publicApiPath = join(root, "apps/web/lib/api/index.ts");

const generatedApi = readFileSync(generatedApiPath, "utf8");
const publicApi = readFileSync(publicApiPath, "utf8");
const failures = [];

const numberedExportPattern = /export const ([A-Za-z_$][\w$]*\d+)\b/g;
for (const match of generatedApi.matchAll(numberedExportPattern)) {
  failures.push(`${relative(root, generatedApiPath)}: generated client export "${match[1]}" has an unstable numeric suffix`);
}

const requiredExports = ["listPublicAccounts", "listRiskAccounts", "getRiskAccount"];
for (const name of requiredExports) {
  if (!new RegExp(`export const ${name}\\b`).test(generatedApi)) {
    failures.push(`${relative(root, generatedApiPath)}: missing generated client export "${name}"`);
  }
}

const publicAccountWrapperPattern = /export async function listAccounts[\s\S]*?Api\.listPublicAccounts\(/;
if (!publicAccountWrapperPattern.test(publicApi)) {
  failures.push(`${relative(root, publicApiPath)}: listAccounts() must bind to Api.listPublicAccounts()`);
}

const publicAccountWrapperBody = publicApi.match(/export async function listAccounts[\s\S]*?\n\}/)?.[0] ?? "";
const forbiddenPublicBindings = [
  "Api.listAccounts(",
  "Api.listAccounts1(",
  "Api.listRiskAccounts(",
];
for (const binding of forbiddenPublicBindings) {
  // 中文注释：这里只约束公开账号 wrapper，风控 wrapper 仍然必须能调用 RiskAccounts 生成接口。
  if (publicAccountWrapperBody.includes(binding)) {
    failures.push(`${relative(root, publicApiPath)}: public account wrapper must not use ${binding}`);
  }
}

// 中文注释：风控账号接口与公共账号接口共享 accounts 词根，生成命名必须携带明确领域避免误接线。
const generatedEndpoints = readGeneratedEndpointMap(generatedApi);
for (const [name, endpoint] of generatedEndpoints) {
  if (endpoint === "/api/v1/accounts" && name !== "listPublicAccounts") {
    failures.push(`${relative(root, generatedApiPath)}: ${name} maps to ${endpoint}; public accounts endpoint must use Public naming`);
  }
  if (endpoint.startsWith("/api/v1/backoffice/risk/accounts") && !/RiskAccount|RiskAccounts/.test(name)) {
    failures.push(`${relative(root, generatedApiPath)}: ${name} maps to ${endpoint}; risk accounts endpoint must use Risk naming`);
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("api client naming checks passed");

function readGeneratedEndpointMap(text) {
  const urlHelpers = new Map();
  const urlPattern = /export const (get[A-Za-z0-9_$]+Url)\s*=[\s\S]*?=>\s*\{([\s\S]*?)\n\}/g;
  for (const match of text.matchAll(urlPattern)) {
    const returnMatch = [...match[2].matchAll(/return[\s\S]*?`([^`]+)`/g)].at(0);
    if (returnMatch) {
      // 中文注释：分页 helper 使用三元表达式，检查只关心稳定 path，忽略查询串分支。
      urlHelpers.set(match[1], returnMatch[1].split("?")[0]);
    }
  }

  const endpoints = new Map();
  const functionPattern = /export const ([A-Za-z_$][\w$]*)\s*=\s*async[\s\S]*?monopolyfunFetch[^(]*\((get[A-Za-z0-9_$]+Url)\(/g;
  for (const match of text.matchAll(functionPattern)) {
    const endpoint = urlHelpers.get(match[2]);
    if (endpoint) endpoints.set(match[1], endpoint);
  }
  return endpoints;
}
