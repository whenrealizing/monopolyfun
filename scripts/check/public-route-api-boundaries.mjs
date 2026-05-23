import { readdirSync, readFileSync, statSync } from "node:fs";
import { extname, join, relative } from "node:path";

const root = process.cwd();
const webRoot = join(root, "apps/web");
const appRoot = join(webRoot, "app");
const generatedApiPath = join(webRoot, "lib/generated/api/monopolyfun.ts");
const publicApiPath = join(webRoot, "lib/api/index.ts");
const ignoredDirs = new Set([".next", "node_modules", "target", "dist", "build"]);
const sourceExtensions = new Set([".ts", ".tsx", ".js", ".mjs"]);
const publicRoutePrefixes = [
  "agent",
  "identity",
  "login",
  "market",
  "oauth",
  "orders",
  "publish",
  "reset-password",
  "u",
  "workbench",
  "page.tsx",
];

const generatedApi = readFileSync(generatedApiPath, "utf8");
const publicApi = readFileSync(publicApiPath, "utf8");
const generatedEndpoints = readGeneratedEndpointMap(generatedApi);
const wrapperRisk = readWrapperRiskMap(publicApi, generatedEndpoints);
const failures = [];

for (const file of walk(appRoot)) {
  const route = relative(appRoot, file);
  if (!isPublicRoute(route)) continue;

  const text = readFileSync(file, "utf8");
  if (text.includes("/api/v1/backoffice/")) {
    failures.push(`${relative(root, file)}: public route embeds a backoffice API path`);
  }

  for (const name of readNamedImportsFromLibApi(text)) {
    const risk = wrapperRisk.get(name);
    if (risk?.backoffice) {
      failures.push(`${relative(root, file)}: imports ${name} from @/lib/api, which reaches ${risk.paths.join(", ")}`);
    }
  }

  if (/from\s+["']@\/lib\/generated\/api\/monopolyfun["']/.test(text)) {
    failures.push(`${relative(root, file)}: public route must use domain wrappers instead of generated API directly`);
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("public route API boundary checks passed");

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

function isPublicRoute(route) {
  if (route.startsWith("backoffice/")) return false;
  return publicRoutePrefixes.some((prefix) => route === prefix || route.startsWith(`${prefix}/`));
}

function readNamedImportsFromLibApi(text) {
  const names = new Set();
  const importPattern = /import\s*\{([\s\S]*?)\}\s*from\s*["']@\/lib\/api["']/g;
  for (const match of text.matchAll(importPattern)) {
    for (const item of match[1].split(",")) {
      const [imported] = item.trim().split(/\s+as\s+/);
      if (imported) names.add(imported.replace(/^type\s+/, "").trim());
    }
  }
  return names;
}

function readGeneratedEndpointMap(text) {
  const urlHelpers = new Map();
  const urlPattern = /export const (get[A-Za-z0-9_$]+Url)\s*=[\s\S]*?return `([^`]+)`/g;
  for (const match of text.matchAll(urlPattern)) {
    urlHelpers.set(match[1], match[2]);
  }

  const endpoints = new Map();
  const functionPattern = /export const ([A-Za-z_$][\w$]*)\s*=\s*async[\s\S]*?monopolyfunFetch[\s\S]*?\((get[A-Za-z0-9_$]+Url)\(/g;
  for (const match of text.matchAll(functionPattern)) {
    const endpoint = urlHelpers.get(match[2]);
    if (endpoint) endpoints.set(match[1], endpoint);
  }
  return endpoints;
}

function readWrapperRiskMap(text, generatedEndpoints) {
  const map = new Map();
  const exportPattern = /export async function ([A-Za-z_$][\w$]*)\s*\(/g;
  for (const match of text.matchAll(exportPattern)) {
    const start = text.indexOf("{", match.index);
    if (start < 0) continue;
    const body = readBalancedBlock(text, start);
    const paths = [];

    for (const apiRef of body.matchAll(/Api\.([A-Za-z_$][\w$]*)\(/g)) {
      const endpoint = generatedEndpoints.get(apiRef[1]);
      if (endpoint?.startsWith("/api/v1/backoffice/")) paths.push(endpoint);
    }

    for (const literal of body.matchAll(/["'`]([^"'`]*\/api\/v1\/backoffice\/[^"'`]*)["'`]/g)) {
      paths.push(literal[1]);
    }

    map.set(match[1], { backoffice: paths.length > 0, paths: [...new Set(paths)] });
  }
  return map;
}

function readBalancedBlock(text, start) {
  let depth = 0;
  for (let index = start; index < text.length; index += 1) {
    const char = text[index];
    if (char === "{") depth += 1;
    if (char === "}") {
      depth -= 1;
      if (depth === 0) return text.slice(start, index + 1);
    }
  }
  return text.slice(start);
}
