import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { appendFile, cp, mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const args = parseArgs(process.argv.slice(2));
const runId = argString(args, "run-id", createRunId());
const evidenceDir = resolve(repoRoot, argString(args, "evidence-dir", join("docs", "evidence", "qa", "runs", runId)));
const quick = Boolean(args.quick);
const ci = Boolean(args.ci);
const security = Boolean(args.security);
const prod = Boolean(args.prod);
const only = argString(args, "only");
const startedAt = new Date();

const baseSteps = [
  {
    id: "static-contract",
    title: "静态契约检查",
    command: "pnpm",
    args: ["check"],
  },
  {
    id: quick ? "api-unit" : "api-lifecycle",
    title: quick ? "API 单元测试" : "API 生命周期测试",
    command: "pnpm",
    args: [quick ? "api:test:unit" : "api:test"],
  },
  {
    id: "browser-lifecycle",
    title: "Playwright 页面生命周期测试",
    command: "pnpm",
    args: ["--dir", "apps/web", "test:ui"],
    after: collectPlaywrightArtifacts,
  },
];
const securitySteps = [
  {
    id: "security-web",
    title: "Web 依赖安全检查",
    command: "pnpm",
    args: ["security:web"],
  },
  {
    id: "security-secrets",
    title: "Secrets 泄漏扫描",
    command: "pnpm",
    args: ["security:secrets"],
  },
  {
    id: "security-pr-policy",
    title: "PR 安全策略检查",
    command: "pnpm",
    args: ["security:pr-policy"],
  },
  {
    id: "security-proof-pr",
    title: "Proof PR 状态检查",
    command: "pnpm",
    args: ["security:proof-pr"],
  },
  {
    id: "security-semgrep",
    title: "Semgrep 规则扫描",
    command: "pnpm",
    args: ["security:semgrep"],
  },
];
const prodSteps = [
  {
    id: "production-smoke",
    title: "生产配置 smoke",
    command: "bash",
    args: ["scripts/production-smoke.sh"],
  },
];
const allSteps = [...baseSteps, ...securitySteps, ...prodSteps];

const steps = selectSteps(allSteps, only, quick, ci);
const results = [];

try {
  await mkdir(join(evidenceDir, "logs"), { recursive: true });

  for (const step of steps) {
    const result = await runStep(step);
    results.push(result);
    if (step.after) {
      await step.after();
    }
  }

  const failed = results.filter((result) => result.status !== 0);
  await writeReports(failed);

  if (failed.length > 0) {
    await appendClosureLog(failed);
    process.exit(1);
  }
} catch (error) {
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(
    join(evidenceDir, "fatal-error.json"),
    `${JSON.stringify({
      runId,
      status: "failed",
      error: error instanceof Error ? error.message : String(error),
      finishedAt: new Date().toISOString(),
    }, null, 2)}\n`,
  );
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}

async function runStep(step) {
  const commandLine = `${step.command} ${step.args.join(" ")}`;
  const started = new Date();
  console.log(`\n[qa] ${step.title}`);
  console.log(`[qa] $ ${commandLine}`);

  const result = spawnSync(step.command, step.args, {
    cwd: repoRoot,
    env: {
      ...process.env,
      MONOPOLYFUN_QA_RUN_ID: runId,
      MONOPOLYFUN_QA_EVIDENCE_DIR: evidenceDir,
    },
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 80,
  });
  const spawnError = result.error ? `\n${result.error.message}\n` : "";
  const output = redactSensitiveOutput(`${result.stdout ?? ""}${result.stderr ?? ""}${spawnError}`);
  if (result.stdout) {
    process.stdout.write(redactSensitiveOutput(result.stdout));
  }
  if (result.stderr) {
    process.stderr.write(redactSensitiveOutput(result.stderr));
  }
  if (result.error) {
    process.stderr.write(`${result.error.message}\n`);
  }

  const finished = new Date();
  const logPath = join(evidenceDir, "logs", `${step.id}.log`);
  await writeFile(logPath, output);

  return {
    id: step.id,
    title: step.title,
    command: commandLine,
    status: result.status ?? 1,
    signal: result.signal,
    startedAt: started.toISOString(),
    finishedAt: finished.toISOString(),
    durationMs: finished.getTime() - started.getTime(),
    logPath: displayPath(logPath),
  };
}

function redactSensitiveOutput(value) {
  // 中文注释：QA 原始日志可落到证据目录，先做通用密钥字段脱敏再写盘或回显。
  return value
    .replace(/\b([A-Z0-9_]*(?:SECRET|TOKEN|PASSWORD|PRIVATE_KEY|API_KEY|AUTHORIZATION)[A-Z0-9_]*)=([^\s]+)/gi, "$1=[REDACTED]")
    .replace(/\b(Bearer\s+)[A-Za-z0-9._~+/=-]+/g, "$1[REDACTED]")
    .replace(/(x-access-token:)[^@/\s]+/gi, "$1[REDACTED]");
}

async function collectPlaywrightArtifacts() {
  // 中文注释：Playwright 原始产物集中复制到 QA run 目录，避免页面证据散落在 apps/web 下。
  const targets = [
    ["apps/web/test-results", "playwright/test-results"],
    ["apps/web/playwright-report", "playwright/report"],
    ["apps/web/allure-results", "playwright/allure-results"],
  ];
  for (const [source, target] of targets) {
    const sourcePath = resolve(repoRoot, source);
    if (!existsSync(sourcePath)) {
      continue;
    }
    await cp(sourcePath, join(evidenceDir, target), {
      recursive: true,
      force: true,
    });
  }
}

async function writeReports(failed) {
  const status = failed.length > 0 ? "failed" : "passed";
  const report = {
    runId,
    status,
    mode: runMode(),
    evidenceDir: displayPath(evidenceDir),
    startedAt: startedAt.toISOString(),
    finishedAt: new Date().toISOString(),
    steps: results,
  };
  await writeFile(join(evidenceDir, "run-report.json"), `${JSON.stringify(report, null, 2)}\n`);

  const lines = [
    "# QA Run Report",
    "",
    `- Run: ${runId}`,
    `- Status: ${status}`,
    `- Evidence: ${displayPath(evidenceDir)}`,
    `- Started: ${startedAt.toISOString()}`,
    `- Finished: ${report.finishedAt}`,
    "",
    "## Steps",
    "",
    ...results.map((result) => `- ${result.status === 0 ? "PASS" : "FAIL"} ${result.id}: \`${result.command}\` (${result.durationMs}ms)`),
    "",
  ];

  if (failed.length > 0) {
    lines.push("## Failed Steps", "");
    failed.forEach((result) => {
      lines.push(`- ${result.id}: ${result.logPath}`);
    });
    lines.push("");
  }

  await writeFile(join(evidenceDir, "run-report.md"), `${lines.join("\n")}\n`);
}

async function appendClosureLog(failed) {
  // 中文注释：失败才写入人工总账，成功运行只保留机器证据，减少无意义文档漂移。
  const closurePath = join(repoRoot, "docs", "qa", "qa-closure-log.md");
  await mkdir(dirname(closurePath), { recursive: true });
  if (!existsSync(closurePath)) {
    await writeFile(closurePath, "# QA Closure Log\n\n");
  }
  const lines = [
    `\n## ${runId}`,
    "",
    `- Status: failed`,
    `- Evidence: ${displayPath(evidenceDir)}`,
    `- Failed steps: ${failed.map((result) => result.id).join(", ")}`,
    `- Retest: \`pnpm qa -- --run-id ${runId}-retest\``,
    "",
  ];
  await appendFile(closurePath, lines.join("\n"));
}

function selectSteps(available, onlyValue, isQuick, isCi) {
  if (onlyValue) {
    const ids = new Set(onlyValue.split(",").map((value) => value.trim()).filter(Boolean));
    const selected = available.filter((step) => ids.has(step.id));
    if (selected.length !== ids.size) {
      throw new Error(`Unknown QA step in --only=${onlyValue}`);
    }
    return selected;
  }
  if (isQuick) {
    return baseSteps.filter((step) => ["static-contract", "api-unit"].includes(step.id));
  }
  if (security) {
    return securitySteps;
  }
  if (prod) {
    return prodSteps;
  }
  if (isCi) {
    return [...baseSteps, ...securitySteps];
  }
  return baseSteps;
}

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === "--") {
      continue;
    }
    if (!token.startsWith("--")) {
      continue;
    }
    const raw = token.slice(2);
    const split = raw.indexOf("=");
    const key = split >= 0 ? raw.slice(0, split) : raw;
    const inline = split >= 0 ? raw.slice(split + 1) : undefined;
    parsed[key] = inline ?? (argv[index + 1]?.startsWith("--") || argv[index + 1] === undefined ? true : argv[++index]);
  }
  return parsed;
}

function argString(parsed, key, fallback = undefined) {
  const value = parsed[key];
  if (value === undefined || value === true) {
    return fallback;
  }
  return String(value);
}

function createRunId(date = new Date()) {
  return `qa-${date.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z")}`;
}

function displayPath(path) {
  return path.startsWith(repoRoot) ? path.slice(repoRoot.length + 1) : path;
}

function runMode() {
  if (quick) return "quick";
  if (ci) return "ci";
  if (security) return "security";
  if (prod) return "prod";
  return "full";
}
