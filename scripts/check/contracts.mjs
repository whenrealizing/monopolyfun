#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const checks = [
  ["api-client-names", "scripts/check/api-client-names.mjs"],
  ["public-route-api-boundaries", "scripts/check/public-route-api-boundaries.mjs"],
  ["public-business-routes", "scripts/check/public-business-routes.mjs"],
  ["workbench-contract", "scripts/check/workbench-contract.mjs"],
  ["legacy-compat-surface", "scripts/check/legacy-compat-surface.mjs"],
];

const failures = [];

for (const [id, script] of checks) {
  // 中文注释：公开仓库只暴露一个稳定 contract gate，具体规则集中在 scripts/check 下维护。
  const result = spawnSync(process.execPath, [script], {
    cwd: repoRoot,
    encoding: "utf8",
    stdio: "inherit",
  });
  if (result.status !== 0) {
    failures.push(id);
  }
}

if (failures.length > 0) {
  process.stderr.write(`contract checks failed: ${failures.join(", ")}\n`);
  process.exit(1);
}

process.stdout.write(`contract checks passed (${checks.length} checks)\n`);
