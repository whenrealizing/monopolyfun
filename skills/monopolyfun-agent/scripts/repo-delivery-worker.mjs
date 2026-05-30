#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { basename, dirname, join } from "node:path";
import { buildFailurePayload, parseArgs, printJson, readOption } from "./runtime-session.mjs";

const DEFAULT_ROOT = join(homedir(), ".openclaw", "monopolyfun", "repo-delivery");

const { flags } = parseWorkerArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write([
    "usage: node scripts/repo-delivery-worker.mjs --session-json '<json>' [--mode prepare|publish|auto]",
    "options:",
    "  --session-json       RepoDeliverySessionResponse JSON",
    "  --session-file       file containing RepoDeliverySessionResponse JSON",
    "  --mode               prepare, publish, or auto; default prepare",
    "  --workspace-root     root directory for cloned repositories",
    "  --workdir            existing work directory for publish mode",
    "  --command            command to run in auto mode; can be repeated",
    "  --check-command      verification command to run before publish; can be repeated",
    "  --commit-message     git commit message",
    "  --pr-title           pull request title",
    "  --pr-body            pull request body",
    "  --dry-run            validate inputs and print planned actions without git writes",
    "",
  ].join("\n") + "\n");
  process.exit(0);
}

try {
  const session = await readSession(flags);
  const mode = readOption(flags, "mode", { defaultValue: "prepare" });
  const workspaceRoot = readOption(flags, "workspace-root", {
    envKeys: ["MONOPOLYFUN_REPO_DELIVERY_ROOT"],
    defaultValue: DEFAULT_ROOT,
  });
  const workdir = flags.get("workdir") || join(workspaceRoot, safeDirName(session.deliverySessionId || session.orderNo || "repo-delivery"));
  const payload = mode === "prepare"
    ? await prepareWorkspace(session, { workdir, flags })
    : mode === "publish"
      ? await publishWorkspace(session, { workdir, flags })
      : mode === "auto"
        ? await autoWorkspace(session, { workdir, flags })
        : fail(`unsupported mode: ${mode}`);
  printJson({ status: "ok", mode, session: summarizeSession(session), ...payload });
} catch (error) {
  printJson(buildFailurePayload(error, { phase: "repo_delivery_worker" }));
  process.exit(1);
}

async function prepareWorkspace(session, input) {
  validateSession(session);
  const cloneUrl = deliveryCloneUrl(session);
  if (input.flags?.has("dry-run")) {
    return {
      phase: "workspace_ready",
      dryRun: true,
      workdir: input.workdir,
      headBranch: session.headBranch,
      baseBranch: session.baseBranch,
      planned: [
        `git clone --branch ${session.baseBranch || "main"} [redacted-clone-url] ${input.workdir}`,
        `git checkout -B ${session.headBranch}`,
      ],
    };
  }
  await mkdir(dirname(input.workdir), { recursive: true });
  if (!existsSync(join(input.workdir, ".git"))) {
    await rm(input.workdir, { recursive: true, force: true });
    // 中文注释：本地 Forgejo 可从公开 repoUrl 派生 clone 地址，并在执行进程内补入服务账号凭据。
    run("git", ["clone", "--branch", session.baseBranch || "main", cloneUrl, input.workdir], { mask: [cloneUrl, session.cloneUrl] });
  } else {
    // 中文注释：复用已有 workspace 时刷新 origin 凭据，避免本地密码或 token 轮换后 push 失败。
    run("git", ["remote", "set-url", "origin", cloneUrl], { cwd: input.workdir, mask: [cloneUrl, session.cloneUrl] });
  }
  run("git", ["checkout", "-B", session.headBranch], { cwd: input.workdir });
  await writeFile(join(input.workdir, ".monopolyfun-repo-delivery.json"), `${JSON.stringify(summarizeSession(session), null, 2)}\n`, { mode: 0o600 });
  return {
    phase: "workspace_ready",
    workdir: input.workdir,
    headBranch: session.headBranch,
    baseBranch: session.baseBranch,
    nextAgentInstruction: "Edit the cloned repository, run checks, then publish with repo-delivery-worker.mjs --mode publish.",
  };
}

async function autoWorkspace(session, input) {
  const prepared = await prepareWorkspace(session, input);
  const commands = input.flags.getAll("command");
  if (commands.length === 0) {
    return {
      ...prepared,
      phase: "workspace_ready",
      blockedReason: "missing_development_commands",
    };
  }
  for (const command of commands) {
    runShell(command, { cwd: input.workdir });
  }
  return await publishWorkspace(session, input);
}

async function publishWorkspace(session, input) {
  validateSession(session);
  const cloneUrl = deliveryCloneUrl(session);
  const checkCommands = input.flags.getAll("check-command");
  if (input.flags.has("dry-run")) {
    return {
      phase: "pull_request_ready",
      dryRun: true,
      workdir: input.workdir,
      prUrl: session.prUrl || `${session.repoUrl}/pulls/dry-run`,
      headCommit: session.headCommit || "dryrun000000000000000000000000000000000",
      plannedChecks: checkCommands,
    };
  }
  if (!existsSync(join(input.workdir, ".git"))) {
    throw new Error(`git workspace not found: ${input.workdir}`);
  }
  for (const command of checkCommands) {
    runShell(command, { cwd: input.workdir });
  }
  ensureGitIdentity(input.workdir);
  const status = run("git", ["status", "--short"], { cwd: input.workdir }).stdout.trim();
  if (status) {
    run("git", ["add", "-A"], { cwd: input.workdir });
    run("git", ["commit", "-m", input.flags.get("commit-message") || defaultCommitMessage(session)], { cwd: input.workdir });
  }
  const headCommit = run("git", ["rev-parse", "HEAD"], { cwd: input.workdir }).stdout.trim();
  run("git", ["remote", "set-url", "origin", cloneUrl], { cwd: input.workdir, mask: [cloneUrl, session.cloneUrl] });
  run("git", ["push", "-u", "origin", session.headBranch], { cwd: input.workdir, mask: [cloneUrl, session.cloneUrl] });
  const prUrl = await createPullRequest(session, input.workdir, input.flags);
  return {
    phase: "pull_request_ready",
    workdir: input.workdir,
    prUrl,
    headCommit,
    publishCommand: `node scripts/execute-action.mjs --action develop-task --params '${JSON.stringify({
      projectNo: session.projectNo,
      prUrl,
      headCommit,
    })}'`,
  };
}

function ensureGitIdentity(workdir) {
  const email = run("git", ["config", "--get", "user.email"], { cwd: workdir, allowFailure: true }).stdout.trim();
  const name = run("git", ["config", "--get", "user.name"], { cwd: workdir, allowFailure: true }).stdout.trim();
  // 中文注释：OpenClaw 容器常见为空 git identity，仓库级配置可让自动交付生成可追踪提交。
  if (!email) {
    run("git", ["config", "user.email", "openclaw@monopolyfun.local"], { cwd: workdir });
  }
  if (!name) {
    run("git", ["config", "user.name", "OpenClaw Agent"], { cwd: workdir });
  }
}

async function createPullRequest(session, workdir, flags) {
  return await createPullRequestWithForgejoApi(session, flags);
}

async function createPullRequestWithForgejoApi(session, flags) {
  const repo = forgejoRepoCoordinates(session.repoUrl);
  const existing = await forgejoApiJson("GET", `/repos/${repo.owner}/${repo.name}/pulls?state=open`);
  const matched = Array.isArray(existing)
    ? existing.find((item) => item?.head?.ref === session.headBranch)
    : null;
  if (matched?.html_url) {
    return matched.html_url;
  }
  // 中文注释：本地 Forgejo API 是唯一仓库交付写入口，agent 只依赖本地服务账号凭证。
  const created = await forgejoApiJson("POST", `/repos/${repo.owner}/${repo.name}/pulls`, {
    title: flags.get("pr-title") || defaultPrTitle(session),
    body: flags.get("pr-body") || defaultPrBody(session),
    head: session.headBranch,
    base: session.baseBranch || "main",
  });
  if (!created?.html_url) {
    throw new Error("Forgejo pull request response missing html_url");
  }
  return created.html_url;
}

async function forgejoApiJson(method, path, body = null) {
  const apiBaseUrl = trimRight(process.env.FORGEJO_API_BASE_URL || "http://localhost:3001/api/v1", "/");
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      Authorization: forgejoAuthorizationHeader(),
      "Content-Type": "application/json",
    },
    body: body == null ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(`Forgejo API ${method} ${path} failed: ${payload?.message || response.status}`);
  }
  return payload;
}

function forgejoAuthorizationHeader() {
  const token = process.env.FORGEJO_ACCESS_TOKEN;
  if (token) {
    return `token ${token}`;
  }
  const username = process.env.FORGEJO_USERNAME || "monopolyfun";
  const password = process.env.FORGEJO_PASSWORD || "monopolyfun-dev-password";
  return `Basic ${Buffer.from(`${username}:${password}`, "utf8").toString("base64")}`;
}

function forgejoRepoCoordinates(repoUrl) {
  const url = new URL(repoUrl);
  const parts = url.pathname.replace(/^\/+/, "").replace(/\.git$/, "").split("/");
  if (parts.length < 2) {
    throw new Error("repoUrl must include owner and repository name");
  }
  return { owner: parts[0], name: parts[1] };
}

function deliveryCloneUrl(session) {
  const raw = session.cloneUrl || repoUrlToCloneUrl(session.repoUrl);
  return withForgejoCredentials(raw);
}

function repoUrlToCloneUrl(repoUrl) {
  if (!repoUrl) {
    return "";
  }
  const text = String(repoUrl).trim();
  return text.endsWith(".git") ? text : `${trimRight(text, "/")}.git`;
}

function withForgejoCredentials(rawUrl) {
  const url = new URL(rawUrl);
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) {
    return rawUrl;
  }
  const token = process.env.FORGEJO_ACCESS_TOKEN;
  const username = process.env.FORGEJO_USERNAME || "monopolyfun";
  const password = token || process.env.FORGEJO_PASSWORD || "monopolyfun-dev-password";
  url.username = username;
  url.password = password;
  return url.toString();
}

async function readSession(flags) {
  if (flags.get("session-json")) {
    return JSON.parse(flags.get("session-json"));
  }
  if (flags.get("session-file")) {
    return JSON.parse(await readFile(flags.get("session-file"), "utf8"));
  }
  throw new Error("session-json or session-file is required");
}

function validateSession(session) {
  for (const key of ["deliverySessionId", "projectNo", "orderNo", "baseBranch", "headBranch"]) {
    if (!session?.[key]) {
      throw new Error(`repo delivery session missing ${key}`);
    }
  }
  if (!session?.cloneUrl && !session?.repoUrl) {
    throw new Error("repo delivery session missing cloneUrl or repoUrl");
  }
}

function runShell(command, input = {}) {
  return run(process.env.SHELL || "sh", ["-lc", command], input);
}

function run(command, args, input = {}) {
  const result = spawnSync(command, args, {
    cwd: input.cwd,
    env: input.env ?? process.env,
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 20,
  });
  const stdout = maskText(result.stdout || "", input.mask);
  const stderr = maskText(result.stderr || "", input.mask);
  if (!input.allowFailure && result.status !== 0) {
    const maskedArgs = args.map((arg) => maskText(arg, input.mask));
    throw new Error(`${command} ${maskedArgs.join(" ")} failed: ${stderr || stdout}`);
  }
  return { code: result.status, stdout, stderr };
}

function parseWorkerArgs(argv) {
  const parsed = parseArgs(argv);
  const multi = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const raw = argv[index];
    if (!raw.startsWith("--")) continue;
    const key = raw.slice(2);
    const next = argv[index + 1];
    const value = next && !next.startsWith("--") ? next : "true";
    if (!multi.has(key)) multi.set(key, []);
    multi.get(key).push(value);
    if (value !== "true") index += 1;
  }
  parsed.flags.getAll = (key) => multi.get(key) || [];
  return parsed;
}

function summarizeSession(session) {
  return {
    deliverySessionId: session.deliverySessionId,
    projectNo: session.projectNo,
    orderNo: session.orderNo,
    repoUrl: session.repoUrl,
    cloneUrlPresent: Boolean(session.cloneUrl),
    baseBranch: session.baseBranch,
    headBranch: session.headBranch,
    status: session.status,
    expiresAt: session.expiresAt,
  };
}

function safeDirName(value) {
  return basename(String(value || "repo-delivery").replace(/[^a-z0-9_.-]+/gi, "-"));
}

function trimRight(value, suffix) {
  let text = String(value || "");
  while (text.endsWith(suffix)) {
    text = text.slice(0, -suffix.length);
  }
  return text;
}

function maskText(text, secret) {
  let masked = String(text);
  for (const item of [secret].flat().filter(Boolean)) {
    masked = masked.replaceAll(item, "[redacted-clone-url]");
  }
  return masked;
}

function defaultCommitMessage(session) {
  return `Complete MonopolyFun delivery ${session.orderNo}`;
}

function defaultPrTitle(session) {
  return `Complete MonopolyFun task ${session.orderNo}`;
}

function defaultPrBody(session) {
  return [
    `Project: ${session.projectNo}`,
    `Order: ${session.orderNo}`,
    `Delivery session: ${session.deliverySessionId}`,
    "",
    "OpenClaw prepared this pull request through the MonopolyFun autonomous developer flow.",
  ].join("\n");
}

function fail(message) {
  throw new Error(message);
}
