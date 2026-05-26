#!/usr/bin/env node

import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const runId = `openclaw-reset-${new Date().toISOString().replace(/[:.]/g, "-")}`;
const evidenceDir = resolve(repoRoot, "docs/evidence/openclaw-reset", runId);

const containers = [
  "openclaw-monopolyfun-smoke-openclaw-owner-1",
  "openclaw-monopolyfun-smoke-openclaw-dev-1",
  "openclaw-monopolyfun-smoke-openclaw-reviewer-1",
];

const resetTargets = [
  "/home/node/.openclaw/agents/main/sessions",
  "/home/node/.openclaw/memory",
  "/home/node/.openclaw/tasks",
  "/home/node/.openclaw/cron",
  "/home/node/.openclaw/credentials",
  "/home/node/.openclaw/workspace/.monopolyfun",
  "/home/node/.openclaw/monopolyfun",
];

await main();

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const results = [];
  for (const container of containers) {
    assertContainerRunning(container);
    killOpenClawAgents(container);
    const before = inspect(container);
    reset(container);
    killOpenClawAgents(container);
    reset(container);
    const after = inspect(container);
    results.push({ container, before, after });
  }

  const report = {
    runId,
    evidenceDir,
    resetTargets,
    preserved: [
      "/home/node/.openclaw/identity",
      "/home/node/.openclaw/skills",
      "/home/node/.openclaw/plugin-skills",
      "/home/node/.openclaw/workspace/AGENTS.md",
    ],
    results,
    clean: results.every((item) => Object.values(item.after).every((count) => count === 0)),
  };

  await writeFile(join(evidenceDir, "reset.json"), `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

function assertContainerRunning(container) {
  const result = spawnSync("docker", ["inspect", "-f", "{{.State.Running}}", container], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  if (result.status !== 0 || result.stdout.trim() !== "true") {
    throw new Error(`${container} is not running`);
  }
}

function inspect(container) {
  const script = resetTargets
    .map((target) => {
      const escaped = shellQuote(target);
      const findArgs = target.endsWith("/tasks")
        ? `${escaped} -mindepth 1 ! -name 'runs.sqlite' ! -name 'runs.sqlite-shm' ! -name 'runs.sqlite-wal'`
        : `${escaped} -mindepth 1`;
      return `if [ -e ${escaped} ]; then printf '%s\\t%s\\n' ${escaped} "$(find ${findArgs} | wc -l | tr -d ' ')"; else printf '%s\\t0\\n' ${escaped}; fi`;
    })
    .join("\n");
  const output = dockerExec(container, script);
  return Object.fromEntries(
    output
      .trim()
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => {
        const [path, count] = line.split("\t");
        return [path, Number(count)];
      }),
  );
}

function reset(container) {
  const directoryScript = resetTargets
    .map((target) => {
      const escaped = shellQuote(target);
      // 清理会话、记忆、任务、cron 和账号凭据，保证每轮 smoke 都从新账号和空上下文启动。
      return `mkdir -p ${escaped} && find ${escaped} -mindepth 1 -maxdepth 1 -exec rm -rf {} +`;
    })
    .join("\n");
  dockerExec(container, directoryScript);
}

function killOpenClawAgents(container) {
  // 清理强制 LLM 模式遗留的 OpenClaw turn 进程，避免 reset 后进程继续回写 session 文件。
  dockerExec(container, "pkill -TERM -f '[o]penclaw.mjs agent|[o]penclaw-agent|[o]penclaw$' || true; sleep 1; pkill -KILL -f '[o]penclaw.mjs agent|[o]penclaw-agent|[o]penclaw$' || true");
}

function dockerExec(container, script) {
  const result = spawnSync("docker", ["exec", container, "sh", "-lc", script], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  if (result.status !== 0) {
    const error = new Error(`docker exec ${container} failed`);
    error.stdout = result.stdout;
    error.stderr = result.stderr;
    throw error;
  }
  return result.stdout;
}

function shellQuote(value) {
  return `'${value.replaceAll("'", "'\\''")}'`;
}
