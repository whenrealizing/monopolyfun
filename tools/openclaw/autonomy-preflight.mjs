#!/usr/bin/env node

import { spawnSync } from "node:child_process";

const flags = new Map();
for (let index = 2; index < process.argv.length; index += 1) {
  const value = process.argv[index];
  if (value.startsWith("--")) {
    flags.set(value.slice(2), process.argv[index + 1]?.startsWith("--") ? "true" : process.argv[index + 1] ?? "true");
  }
}
const phase = flags.get("phase") || "notify";

const containers = [
  "openclaw-monopolyfun-smoke-openclaw-owner-1",
  "openclaw-monopolyfun-smoke-openclaw-dev-1",
  "openclaw-monopolyfun-smoke-openclaw-reviewer-1",
];

const checks = [];

for (const container of containers) {
  checks.push(checkContainer(container));
  if (phase === "clean") {
    checks.push(checkCleanState(container));
  } else {
    checks.push(await checkBaseUrl(container));
    checks.push(checkRuntimeTurn(container));
    if (phase === "notify") {
      checks.push(checkCron(container));
      checks.push(checkSessionBinding(container));
    }
  }
}

const blocking = checks.filter((check) => check.status !== "pass");
const payload = {
  status: blocking.length === 0 ? "pass" : "fail",
  checkedAt: new Date().toISOString(),
  phase,
  summary: {
    total: checks.length,
    pass: checks.filter((check) => check.status === "pass").length,
    fail: blocking.length,
  },
  checks,
};

process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
if (blocking.length > 0) {
  process.exit(1);
}

function checkContainer(container) {
  const result = run("docker", ["inspect", "-f", "{{.State.Running}}", container]);
  return record(container, "container_running", result.status === 0 && result.stdout.trim() === "true", {
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim(),
  });
}

function checkCleanState(container) {
  const targets = [
    "/home/node/.openclaw/agents/main/sessions",
    "/home/node/.openclaw/memory",
    "/home/node/.openclaw/tasks",
    "/home/node/.openclaw/cron",
    "/home/node/.openclaw/credentials",
    "/home/node/.openclaw/workspace/.monopolyfun",
    "/home/node/.openclaw/monopolyfun",
  ];
  const counts = Object.fromEntries(targets.map((target) => {
    const findArgs = target.endsWith("/tasks")
      ? `${shellQuote(target)} -mindepth 1 ! -name 'runs.sqlite' ! -name 'runs.sqlite-shm' ! -name 'runs.sqlite-wal'`
      : `${shellQuote(target)} -mindepth 1`;
    // 中文注释：OpenClaw gateway 会自动重建空 task sqlite 文件；clean 判定关注业务残留任务和会话。
    const result = docker(container, `if [ -e ${shellQuote(target)} ]; then find ${findArgs} | wc -l | tr -d ' '; else printf 0; fi`);
    return [target, Number.parseInt(result.stdout.trim() || "0", 10)];
  }));
  return record(container, "clean_openclaw_state", Object.values(counts).every((count) => count === 0), { counts });
}

async function checkBaseUrl(container) {
  const baseUrl = readContainerFile(container, "/home/node/.openclaw/monopolyfun/base-url.txt").trim();
  if (!baseUrl) {
    return record(container, "base_url_ready", false, { reason: "missing-base-url" });
  }
  const result = docker(container, [
    "node -e",
    shellQuote(`fetch('${baseUrl}/actuator/health/readiness').then(r=>{console.log(JSON.stringify({ok:r.ok,status:r.status}))}).catch(e=>{console.log(JSON.stringify({ok:false,error:e.message}));process.exit(1)})`),
  ].join(" "));
  const payload = parseJson(result.stdout) || {};
  return record(container, "base_url_ready", result.status === 0 && payload.ok === true, {
    baseUrl,
    statusCode: payload.status ?? null,
    error: payload.error || result.stderr.trim(),
  });
}

function checkCron(container) {
  const listed = dockerJson(container, "node /app/openclaw.mjs cron list --json 2>/dev/null || true");
  const stored = dockerJson(container, "cat /home/node/.openclaw/cron/jobs.json 2>/dev/null || printf '{}'");
  const listedJobs = Array.isArray(listed.jobs) ? listed.jobs : [];
  const storedJobs = Array.isArray(stored.jobs) ? stored.jobs : [];
  const enabled = [...listedJobs, ...storedJobs].filter((job) => {
    return job?.name === "monopolyfun-autopilot" && job.enabled === true;
  });
  const disabled = storedJobs.filter((job) => job?.name === "monopolyfun-autopilot" && job.enabled === false);
  // 中文注释：workbench 自动提醒依赖 OpenClaw cron 常驻启用，disabled 残留不能算通过。
  return record(container, "cron_enabled", enabled.length > 0, {
    listedCount: listedJobs.length,
    storedCount: storedJobs.length,
    enabledCount: enabled.length,
    disabledCount: disabled.length,
  });
}

function checkSessionBinding(container) {
  const sessions = dockerJson(container, "cat /home/node/.openclaw/agents/main/sessions/sessions.json 2>/dev/null || printf '{}'");
  const entries = Object.entries(sessions || {});
  const deliverable = entries.filter(([, value]) => {
    const channel = String(value?.lastChannel || value?.deliveryContext?.channel || value?.origin?.surface || "");
    return Boolean(value?.sessionId) && Boolean(channel);
  });
  const jobs = dockerJson(container, "cat /home/node/.openclaw/cron/jobs.json 2>/dev/null || printf '{}'");
  const boundJobs = (Array.isArray(jobs.jobs) ? jobs.jobs : []).filter((job) => {
    return job?.name === "monopolyfun-autopilot"
      && job.enabled === true
      && job.sessionTarget
      && job.sessionTarget !== "isolated"
      && (job.delivery?.mode === "announce" || job.delivery?.channel === "last");
  });
  // 中文注释：smoke 环境用 main/last channel 验证本地 OpenClaw 通知通道，外部通道会显示 deliverable session。
  return record(container, "notification_session_bound", boundJobs.length > 0 && (deliverable.length > 0 || boundJobs.some((job) => job.sessionTarget === "main" || String(job.sessionTarget || "").startsWith("session:"))), {
    deliverableSessionCount: deliverable.length,
    boundJobCount: boundJobs.length,
    sessionTargets: boundJobs.map((job) => job.sessionTarget),
    latestSessionKey: deliverable.at(-1)?.[0] || "",
  });
}

function checkRuntimeTurn(container) {
  const command = [
    "MONOPOLYFUN_BASE_URL=$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null)",
    "MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt",
    "MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt",
    "MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json",
    "node /home/node/.openclaw/skills/monopolyfun-agent/scripts/runtime-turn.mjs --summary --input '{\"intent\":\"view\",\"scene\":\"workbench\"}'",
  ].join(" ");
  const result = docker(container, command);
  const parsed = parseJson(result.stdout);
  const ok = result.status === 0 && parsed?.status !== "blocked" && parsed?.scene === "workbench";
  return record(container, "workbench_poll_ready", ok, {
    exitCode: result.status,
    status: parsed?.status || "",
    scene: parsed?.scene || "",
    itemCount: parsed?.counts?.items ?? null,
    error: parsed?.error?.message || result.stderr.trim(),
  });
}

function record(container, name, passed, detail = {}) {
  return {
    container,
    name,
    status: passed ? "pass" : "fail",
    detail,
  };
}

function readContainerFile(container, filePath) {
  return docker(container, `cat ${shellQuote(filePath)} 2>/dev/null || true`).stdout;
}

function dockerJson(container, script) {
  return parseJson(docker(container, script).stdout) || {};
}

function docker(container, script) {
  return run("docker", ["exec", container, "sh", "-lc", script]);
}

function run(command, args) {
  return spawnSync(command, args, {
    cwd: process.cwd(),
    encoding: "utf8",
    maxBuffer: 10 * 1024 * 1024,
  });
}

function parseJson(value) {
  try {
    return JSON.parse(String(value || "").trim());
  } catch {
    return null;
  }
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}
