#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";

const DEFAULT_BASE_BRANCH = "origin/master";
const GIT_OUTPUT_MAX_BUFFER = 64 * 1024 * 1024;
const findingLevels = {
  error: "error",
  warn: "warn",
};

function main() {
  const diff = readDiff();
  const findings = [
    ...checkAddedLines(diff),
    ...checkRepositoryInvariants(),
  ];
  for (const finding of findings) {
    console.error(`${finding.level}: ${finding.message}`);
    if (finding.file) {
      console.error(`  at ${finding.file}${finding.line ? `:${finding.line}` : ""}`);
    }
  }
  if (findings.some((finding) => finding.level === findingLevels.error)) {
    process.exit(1);
  }
  console.log("PR security policy passed");
}

function readDiff() {
  const explicitBase = valueAfterFlag("--base") ?? process.env.PR_SECURITY_BASE_REF;
  const explicitHead = valueAfterFlag("--head") ?? process.env.PR_SECURITY_HEAD_REF ?? "HEAD";
  const base = explicitBase ?? githubBaseRef() ?? DEFAULT_BASE_BRANCH;
  ensureRefAvailable(base);
  const mergeBase = git(["merge-base", base, explicitHead]).trim();
  if (!valueAfterFlag("--head") && !process.env.PR_SECURITY_HEAD_REF) {
    // 中文注释：本地执行需要扫描当前工作树，避免已修复的未提交改动仍按旧 HEAD 报错。
    return git(["diff", "--unified=0", "--no-ext-diff", mergeBase]);
  }
  return git(["diff", "--unified=0", "--no-ext-diff", `${mergeBase}...${explicitHead}`]);
}

function checkAddedLines(diff) {
  const findings = [];
  let file = "";
  let line = 0;
  for (const rawLine of diff.split(/\r?\n/)) {
    if (rawLine.startsWith("+++ b/")) {
      file = rawLine.slice("+++ b/".length);
      line = 0;
      continue;
    }
    if (rawLine.startsWith("@@")) {
      const match = /\+(\d+)/.exec(rawLine);
      line = match ? Number(match[1]) - 1 : 0;
      continue;
    }
    if (!rawLine.startsWith("+") || rawLine.startsWith("+++")) {
      continue;
    }
    line += 1;
    const added = rawLine.slice(1);
    findings.push(...scanAddedLine(file, line, added));
  }
  return findings;
}

function scanAddedLine(file, line, added) {
  const findings = [];
  const text = added.trim();
  if (!text) return findings;
  const workflow = file.startsWith(".github/workflows/");
  if (!workflow && !isExecutablePolicyTarget(file)) {
    return findings;
  }

  // 中文注释：只扫描可执行代码和 workflow，避免 skill/runbook 中的反例文本触发误报。
  const hardRules = [
    [/pull_request_target\s*:/i, "pull_request_target 会让外部 PR 代码靠近仓库 token"],
    [/permissions\s*:\s*write-all/i, "GitHub Actions 权限禁止 write-all"],
    [/\$\{\{\s*secrets\.[^}]+}}\s*.*\|\s*(sh|bash|zsh)\b/i, "secret 参与管道执行远程脚本"],
    [/(curl|wget)\b.*\|\s*(sh|bash|zsh)\b/i, "远程脚本管道执行需要改为固定版本校验"],
    [/\bchmod\s+777\b/i, "chmod 777 会扩大执行面"],
    [/\b(eval|new Function)\s*\(/, "动态代码执行需要安全评审"],
    [/\bchild_process\.(exec|execSync)\s*\(/, "Node exec shell 拼接需要安全评审"],
    [/\bRuntime\.getRuntime\(\)\.exec\s*\(/, "Java Runtime.exec 需要安全评审"],
    [/\bnew\s+ProcessBuilder\s*\(/, "Java ProcessBuilder 需要安全评审"],
    [/\b(PRIVATE_KEY|MNEMONIC|SEED_PHRASE|OKX_ONCHAIN_PAY_API_SECRET)\s*=/i, "提交内容包含高风险密钥字段"],
  ];
  for (const [pattern, message] of hardRules) {
    if (pattern.test(text)) {
      findings.push({ level: findingLevels.error, file, line, message });
    }
  }
  if (workflow && /\$\{\{\s*secrets\.[^}]+}}/i.test(text) && /\b(curl|wget|nc|python|node|bash|sh|zsh)\b/i.test(text)) {
    findings.push({
      level: findingLevels.error,
      file,
      line,
      message: "workflow run 脚本读取 secret 后调用网络或解释器",
    });
  }
  return findings;
}

function isExecutablePolicyTarget(file) {
  return /\.(js|mjs|cjs|ts|tsx|jsx|java|sh|bash|zsh|yml|yaml)$/.test(file)
    && !file.startsWith("docs/")
    && !file.startsWith("skills/");
}

function checkRepositoryInvariants() {
  const findings = [];
  requireFileContains(findings, "apps/api/src/main/java/com/monopolyfun/modules/order/service/command/OrderCommandService.java", [
    ["validateRegisteredArtifacts", "订单 proof 必须绑定已登记 artifact"],
    ["findByOrderIdAndArtifactRef", "artifact 必须按 orderId 和 artifactRef 双重绑定"],
    ["ProofAssetStatus.UPLOADED", "proof asset 完成上传后才能提交"],
    ["ProofAssetStatus.VERIFIED", "proof asset 通过审核后才能提交"],
    ["evidenceSnapshot", "proof 必须写入 evidence snapshot"],
    ["snapshotHash", "外部链接必须进入 snapshot hash"],
  ]);
  requireFileContains(findings, "apps/api/src/main/java/com/monopolyfun/modules/upload/service/UploadService.java", [
    ["requireUploadParticipant", "上传入口必须校验订单参与者"],
    ["requireCompleteActor", "上传完成必须校验上传者"],
    ["requireDownloadActor", "下载入口必须校验证据可见范围"],
    ["checksumSha256", "proof asset 必须携带内容校验和"],
  ]);
  // 中文注释：开源安全门禁聚焦可复现扫描，页面级 agent harness 已移除，业务边界由 API 测试承接。
  requireFileContains(findings, ".github/workflows/security.yml", [
    ["pnpm check:flyway-migrations", "CI 必须校验 Flyway migration"],
    ["pnpm security:pr-policy", "CI 必须执行 PR 安全策略"],
    ["gitleaks/gitleaks-action", "CI 必须扫描 secret 泄漏"],
    ["semgrep/semgrep-action", "CI 必须执行 Semgrep"],
  ]);
  requireFileContains(findings, "scripts/security/check-proof-pr-status.mjs", [
    ["proof PR 至少需要一个成功 CI status check", "proof PR 校验必须要求成功 CI"],
    ["proof repo 必须公开", "proof PR 校验必须要求公开仓库"],
    ["head commit 不匹配", "proof PR 校验必须绑定 head commit"],
  ]);
  return findings;
}

function requireFileContains(findings, file, anchors) {
  if (!existsSync(file)) {
    findings.push({ level: findingLevels.error, file, message: "安全锚点文件缺失" });
    return;
  }
  const content = readFileSync(file, "utf8");
  for (const [anchor, message] of anchors) {
    if (!content.includes(anchor)) {
      findings.push({ level: findingLevels.error, file, message });
    }
  }
}

function githubBaseRef() {
  if (!process.env.GITHUB_BASE_REF) return null;
  return `origin/${process.env.GITHUB_BASE_REF}`;
}

function ensureRefAvailable(ref) {
  if (git(["rev-parse", "--verify", ref], { allowFailure: true }).trim()) {
    return;
  }
  if (ref.startsWith("origin/")) {
    const branch = ref.slice("origin/".length);
    git(["fetch", "--no-tags", "--depth=50", "origin", `${branch}:${ref}`], { allowFailure: true });
  }
}

function valueAfterFlag(flag) {
  const index = process.argv.indexOf(flag);
  return index >= 0 ? process.argv[index + 1] : null;
}

function git(args, options = {}) {
  try {
    // 中文注释：大规模删除会让 diff 超过 Node 默认缓冲区，安全扫描需要完整读取变更内容。
    return execFileSync("git", args, { encoding: "utf8", maxBuffer: GIT_OUTPUT_MAX_BUFFER, stdio: ["ignore", "pipe", "pipe"] });
  } catch (error) {
    if (options.allowFailure) {
      return "";
    }
    throw error;
  }
}

main();
