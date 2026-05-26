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
    // 中文注释：clone 使用后端签发的短期 cloneUrl，输出里只保留脱敏 URL，避免泄漏 token。
    run("git", ["clone", "--branch", session.baseBranch || "main", session.cloneUrl, input.workdir], { mask: session.cloneUrl });
  } else {
    // 中文注释：复用已有 workspace 时刷新远端短期 token，避免旧 token 过期后 push 失败。
    run("git", ["remote", "set-url", "origin", session.cloneUrl], { cwd: input.workdir, mask: session.cloneUrl });
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
  const checkCommands = input.flags.getAll("check-command");
  if (input.flags.has("dry-run")) {
    return {
      phase: "pull_request_ready",
      dryRun: true,
      workdir: input.workdir,
      prUrl: session.prUrl || `${session.repoUrl}/pull/dry-run`,
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
  run("git", ["push", "-u", "origin", session.headBranch], { cwd: input.workdir, mask: session.cloneUrl });
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
  const token = githubTokenFromCloneUrl(session.cloneUrl);
  if (token) {
    return await createPullRequestWithApi(session, flags, token);
  }
  const env = githubCliEnv(session);
  const existing = run("gh", ["pr", "view", session.headBranch, "--json", "url", "--jq", ".url"], {
    cwd: workdir,
    allowFailure: true,
    env,
  });
  if (existing.code === 0 && existing.stdout.trim()) {
    return existing.stdout.trim();
  }
  const created = run("gh", [
    "pr",
    "create",
    "--base",
    session.baseBranch || "main",
    "--head",
    session.headBranch,
    "--title",
    flags.get("pr-title") || defaultPrTitle(session),
    "--body",
    flags.get("pr-body") || defaultPrBody(session),
  ], { cwd: workdir, env });
  return created.stdout.trim().split(/\s+/).find((part) => part.startsWith("http")) || created.stdout.trim();
}

async function createPullRequestWithApi(session, flags, token) {
  const repo = githubRepoCoordinates(session.repoUrl);
  const existing = await githubApiJson(
    "GET",
    `/repos/${repo.owner}/${repo.name}/pulls?state=open&head=${encodeURIComponent(`${repo.owner}:${session.headBranch}`)}`,
    token,
  );
  if (Array.isArray(existing) && existing[0]?.html_url) {
    return existing[0].html_url;
  }
  // 中文注释：OpenClaw 容器里可能没有 gh CLI，直接走 GitHub API 保证仓库交付能主动生成 PR 链接。
  const created = await githubApiJson("POST", `/repos/${repo.owner}/${repo.name}/pulls`, token, {
    title: flags.get("pr-title") || defaultPrTitle(session),
    body: flags.get("pr-body") || defaultPrBody(session),
    head: session.headBranch,
    base: session.baseBranch || "main",
  });
  if (!created?.html_url) {
    throw new Error("GitHub pull request response missing html_url");
  }
  return created.html_url;
}

async function githubApiJson(method, path, token, body = null) {
  const response = await fetch(`https://api.github.com${path}`, {
    method,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-GitHub-Api-Version": "2022-11-28",
    },
    body: body == null ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(`GitHub API ${method} ${path} failed: ${payload?.message || response.status}`);
  }
  return payload;
}

function githubRepoCoordinates(repoUrl) {
  const url = new URL(repoUrl);
  const parts = url.pathname.replace(/^\/+/, "").replace(/\.git$/, "").split("/");
  if (!/github\.com$/i.test(url.hostname) || parts.length < 2) {
    throw new Error("repoUrl must be a GitHub repository URL");
  }
  return { owner: parts[0], name: parts[1] };
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
  for (const key of ["deliverySessionId", "projectNo", "orderNo", "cloneUrl", "baseBranch", "headBranch"]) {
    if (!session?.[key]) {
      throw new Error(`repo delivery session missing ${key}`);
    }
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
    throw new Error(`${command} ${args.join(" ")} failed: ${stderr || stdout}`);
  }
  return { code: result.status, stdout, stderr };
}

function githubCliEnv(session) {
  const token = githubTokenFromCloneUrl(session.cloneUrl);
  if (!token) {
    return process.env;
  }
  // 中文注释：GitHub App 签发的短期 clone token 同时用于 gh 创建 PR，保证 agent 能完整提交交付链接。
  return {
    ...process.env,
    GH_TOKEN: token,
    GITHUB_TOKEN: token,
  };
}

function githubTokenFromCloneUrl(value) {
  try {
    const url = new URL(value);
    if (!url.username || !url.password || !/github\.com$/i.test(url.hostname)) {
      return "";
    }
    return decodeURIComponent(url.password);
  } catch {
    return "";
  }
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

function maskText(text, secret) {
  return secret ? String(text).replaceAll(secret, "[redacted-clone-url]") : String(text);
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
