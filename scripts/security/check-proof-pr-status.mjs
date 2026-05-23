#!/usr/bin/env node
import { execFileSync } from "node:child_process";

const args = parseArgs();
const repo = args.repo ?? process.env.PROOF_PR_REPO;
const prNumber = args.pr ?? process.env.PROOF_PR_NUMBER;
const expectedCommit = args.commit ?? process.env.PROOF_PR_COMMIT;
const requirePublic = args["allow-private"] !== "true";

if (!repo || !prNumber) {
  if (process.env.REQUIRE_PROOF_PR === "true") {
    missingArg("repo");
  }
  // 中文注释：proof PR 依赖外部公开仓库，未配置样本时保持门禁可运行并给出机器可读跳过结果。
  console.log(JSON.stringify({
    status: "skipped",
    reason: "PROOF_PR_REPO and PROOF_PR_NUMBER are not configured",
  }, null, 2));
  process.exit(0);
}

const repoInfo = ghJson(["repo", "view", repo, "--json", "nameWithOwner,visibility,isPrivate,url"]);
const prInfo = ghJson([
  "pr",
  "view",
  prNumber,
  "--repo",
  repo,
  "--json",
  "url,state,isDraft,headRefOid,headRefName,baseRefName,commits,statusCheckRollup",
]);

const findings = [];

if (requirePublic && (repoInfo.isPrivate || repoInfo.visibility !== "PUBLIC")) {
  findings.push(`proof repo 必须公开: ${repoInfo.nameWithOwner}`);
}

if (!["OPEN", "MERGED"].includes(prInfo.state)) {
  findings.push(`proof PR 状态必须可审计: ${prInfo.state}`);
}

if (prInfo.isDraft) {
  findings.push("proof PR 需要退出 draft 状态");
}

if (expectedCommit && normalizeSha(prInfo.headRefOid) !== normalizeSha(expectedCommit)) {
  findings.push(`proof PR head commit 不匹配: expected=${expectedCommit} actual=${prInfo.headRefOid}`);
}

const conclusions = readCheckConclusions(prInfo.statusCheckRollup ?? []);
if (!conclusions.some((conclusion) => conclusion === "SUCCESS" || conclusion === "success")) {
  findings.push("proof PR 至少需要一个成功 CI status check");
}

if (findings.length > 0) {
  for (const finding of findings) {
    console.error(`error: ${finding}`);
  }
  process.exit(1);
}

console.log(JSON.stringify({
  repo: repoInfo.url,
  pr: prInfo.url,
  headRef: prInfo.headRefName,
  baseRef: prInfo.baseRefName,
  headCommit: prInfo.headRefOid,
  checks: conclusions,
  status: "passed",
}, null, 2));

function readCheckConclusions(rollup) {
  return rollup
    .map((check) => check.conclusion || check.state || check.status || "")
    .filter(Boolean);
}

function ghJson(commandArgs) {
  const output = execFileSync("gh", commandArgs, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
  return JSON.parse(output);
}

function missingArg(name) {
  console.error(`error: --${name} is required`);
  process.exit(1);
}

function normalizeSha(value) {
  return String(value ?? "").trim().toLowerCase();
}

function parseArgs() {
  const result = {};
  for (let index = 2; index < process.argv.length; index += 1) {
    const token = process.argv[index];
    if (!token.startsWith("--")) {
      continue;
    }
    const key = token.slice(2);
    const next = process.argv[index + 1];
    if (!next || next.startsWith("--")) {
      result[key] = "true";
      continue;
    }
    result[key] = next;
    index += 1;
  }
  return result;
}
