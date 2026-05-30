#!/usr/bin/env node

import { createWriteStream, readFileSync, statSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";
import {
  createPublicClient,
  createWalletClient,
  http,
  getAddress,
  parseEventLogs,
} from "viem";
import { privateKeyToAccount } from "viem/accounts";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const runId = new Date().toISOString().replace(/[:.]/g, "-");
const evidenceDir = resolve(repoRoot, "docs/evidence/testchain", `revenue-${runId}`);
const dbName = `monopolyfun-testchain-postgres-${process.pid}`;
const dbPort = 55436;
const apiPort = 18082;
const anvilPort = 18545;
const period = "2026-05";
const totalRevenueMinor = 100000n;
const password = "CodexTestchain123!";
const defaultAnvilDeployerPrivateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
const forceForgeBuild = process.env.TESTCHAIN_FORCE_FORGE === "1";
const children = [];

async function main() {
try {
  await mkdir(evidenceDir, { recursive: true });
  requireCommand("docker");
  requireCommand("anvil");

  await ensurePortFree(dbPort);
  await ensurePortFree(apiPort);
  await ensurePortFree(anvilPort);

  buildContractsIfNeeded();
  await startPostgres();
  const api = await startApi();
  const anvil = await startAnvil();
  const chain = await deployChain(anvil.rpcUrl, anvil.privateKeys);

  const owner = await api.register(`tcown${suffix()}`, password);
  const worker = await api.register(`tcwrk${suffix()}`, password);
  await seedProject(owner.account.id);

  await api.as(owner).post("/api/v1/projects/proj-test/revenue-address", {
    actorAccountId: owner.account.id,
    chainId: "eip155:31337",
    contractAddress: chain.distributor,
    tokenAddress: chain.token,
  });

  const thread = await api.as(owner).post("/api/v1/projects/proj-test/work-threads", {
    actorAccountId: owner.account.id,
    title: "Testchain revenue claim",
    goal: "Prove backend distribution can settle into a local chain transfer",
    deliverables: ["PR link", "Test summary"],
    acceptanceCriteria: ["Backend claim exists", "Token balance increases"],
    taskValue: 5000,
    bountyAmountMinor: 0,
    bountyToken: "USDC",
    repoRef: "localhost:3001/monopolyfun/testchain",
    issueUrl: "http://localhost:3001/monopolyfun/testchain/issues/1",
  });
  await api.as(worker).post(`/api/v1/work-threads/${thread.id}/claim`, {
    actorAccountId: worker.account.id,
    runtime: "openclaw",
  });
  await api.as(worker).post(`/api/v1/work-threads/${thread.id}/result`, {
    actorAccountId: worker.account.id,
    resultMarkdown: resultMarkdown(thread.id),
    runtime: "openclaw",
  });
  await api.as(owner).post(`/api/v1/work-threads/${thread.id}/review`, {
    reviewerAccountId: owner.account.id,
    decision: "accept",
    reason: "Local testchain proof path passed",
  });
  const batch = await api.as(owner).post("/api/v1/projects/proj-test/distributions", {
    actorAccountId: owner.account.id,
    period,
    totalRevenueMinor: Number(totalRevenueMinor),
  });
  const initialClaim = await api.as(worker).post(`/api/v1/projects/proj-test/distributions/${period}/claim`, {
    actorAccountId: worker.account.id,
    walletAddress: chain.member,
  });

  const chainClaim = await executeClaim(chain, batch, initialClaim);
  const submittedClaim = await api.as(worker).post(`/api/v1/projects/proj-test/distributions/${period}/claim`, {
    actorAccountId: worker.account.id,
    walletAddress: chain.member,
    txHash: chainClaim.txHash,
  });
  const confirmedClaim = await api.as(worker).post(`/api/v1/projects/proj-test/distributions/${period}/claim`, {
    actorAccountId: worker.account.id,
    txHash: chainClaim.txHash,
    txConfirmed: chainClaim.confirmation.claimEventMatched && chainClaim.confirmation.transferEventMatched,
  });

  const evidence = {
    runId,
    apiBaseUrl: `http://127.0.0.1:${apiPort}`,
    chainId: 31337,
    token: chain.token,
    distributor: chain.distributor,
    payer: chain.payer,
    member: chain.member,
    projectId: "proj-test",
    threadId: thread.id,
    period,
    batch: {
      id: batch.id,
      totalRevenueMinor: batch.totalRevenueMinor,
      totalSnapshotShares: batch.totalSnapshotShares,
    },
    claim: {
      amountMinor: initialClaim.amountMinor,
      backendStatusBeforeTx: initialClaim.status,
      backendStatusAfterTx: submittedClaim.status,
      backendStatusAfterConfirm: confirmedClaim.status,
      txHash: chainClaim.txHash,
      confirmation: chainClaim.confirmation,
      memberBalanceBefore: chainClaim.balanceBefore.toString(),
      memberBalanceAfter: chainClaim.balanceAfter.toString(),
      transferDelta: (chainClaim.balanceAfter - chainClaim.balanceBefore).toString(),
    },
    checks: {
      backendClaimCreated: initialClaim.status === "claimable",
      chainReceiptConfirmed: chainClaim.confirmation.receiptStatus === "success",
      chainClaimEventMatched: chainClaim.confirmation.claimEventMatched === true,
      chainTransferEventMatched: chainClaim.confirmation.transferEventMatched === true,
      chainTransferSucceeded: chainClaim.balanceAfter - chainClaim.balanceBefore === BigInt(initialClaim.amountMinor),
      backendTxRecorded: submittedClaim.txHash === chainClaim.txHash && submittedClaim.status === "submitted",
      backendTxConfirmed: confirmedClaim.txHash === chainClaim.txHash && confirmedClaim.status === "claimed",
    },
  };
  await writeFile(join(evidenceDir, "evidence.json"), `${JSON.stringify(evidence, null, 2)}\n`);
  const failedChecks = Object.entries(evidence.checks).filter(([, value]) => value !== true).map(([key]) => key);
  if (failedChecks.length > 0) {
    throw new Error(`testchain revenue checks failed: ${failedChecks.join(", ")}`);
  }
  printJson(evidence);
} catch (error) {
  await writeFile(join(evidenceDir, "failure.json"), `${JSON.stringify(errorPayload(error), null, 2)}\n`).catch(() => {});
  printJson({ status: "failed", evidenceDir, error: errorPayload(error) });
  process.exitCode = 1;
} finally {
  cleanup();
}
}

async function startPostgres() {
  run("docker", [
    "run",
    "--name", dbName,
    "-e", "POSTGRES_USER=postgres",
    "-e", "POSTGRES_PASSWORD=postgres",
    "-e", "POSTGRES_DB=monopolyfun",
    "-p", `${dbPort}:5432`,
    "-d",
    "pgvector/pgvector:pg17",
  ], { cwd: repoRoot, stdio: "ignore" });
  await waitFor(async () => {
    const result = spawnSync("docker", ["exec", dbName, "pg_isready", "-U", "postgres", "-d", "monopolyfun"], { encoding: "utf8" });
    return result.status === 0;
  }, "postgres ready");
}

async function startApi() {
  const logPath = join(evidenceDir, "api.log");
  const log = createWriteStream(logPath, { flags: "a" });
  const env = {
    ...process.env,
    DATABASE_URL: `jdbc:postgresql://localhost:${dbPort}/monopolyfun`,
    DATABASE_USERNAME: "postgres",
    DATABASE_PASSWORD: "postgres",
    PAYMENT_PROVIDER: "fake",
    UPLOAD_PROVIDER: "fake",
    MONOPOLYFUN_SCHEDULER_ENABLED: "false",
    MONOPOLYFUN_REVENUE_RPC_EIP155_31337: `http://127.0.0.1:${anvilPort}`,
    MONOPOLYFUN_REVENUE_CLAIM_SIGNER_PRIVATE_KEY: process.env.MONOPOLYFUN_REVENUE_CLAIM_SIGNER_PRIVATE_KEY || defaultAnvilDeployerPrivateKey,
  };
  const child = spawn("mvn", ["-f", "apps/api/pom.xml", "spring-boot:run", `-Dspring-boot.run.arguments=--server.port=${apiPort}`], {
    cwd: repoRoot,
    env,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.pipe(log);
  child.stderr.pipe(log);
  children.push(child);
  await waitFor(async () => {
    try {
      const response = await fetch(`http://127.0.0.1:${apiPort}/actuator/health/readiness`);
      return response.ok;
    } catch {
      return false;
    }
  }, "api ready", 90000);
  return new ApiClient(`http://127.0.0.1:${apiPort}`);
}

async function startAnvil() {
  const logPath = join(evidenceDir, "anvil.log");
  const log = createWriteStream(logPath, { flags: "a" });
  const privateKeys = [];
  const child = spawn("anvil", ["--host", "127.0.0.1", "--port", String(anvilPort), "--chain-id", "31337"], {
    cwd: repoRoot,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.on("data", (chunk) => {
    const text = chunk.toString();
    log.write(text);
    for (const line of text.split(/\r?\n/)) {
      const match = /\(\d+\)\s+(0x[0-9a-fA-F]{64})/.exec(line);
      if (match) {
        privateKeys.push(match[1]);
      }
    }
  });
  child.stderr.pipe(log);
  children.push(child);
  const rpcUrl = `http://127.0.0.1:${anvilPort}`;
  await waitFor(async () => {
    try {
      const response = await fetch(rpcUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "eth_chainId", params: [] }),
      });
      return response.ok && privateKeys.length >= 3;
    } catch {
      return false;
    }
  }, "anvil ready");
  return { rpcUrl, privateKeys };
}

async function deployChain(rpcUrl, privateKeys) {
  const publicClient = createPublicClient({ transport: http(rpcUrl) });
  const deployer = privateKeyToAccount(privateKeys[0]);
  const payer = privateKeyToAccount(privateKeys[1]);
  const member = privateKeyToAccount(privateKeys[2]);
  const deployerClient = createWalletClient({ account: deployer, transport: http(rpcUrl) });
  const payerClient = createWalletClient({ account: payer, transport: http(rpcUrl) });
  const memberClient = createWalletClient({ account: member, transport: http(rpcUrl) });
  const mockUsdc = artifact("MockUsdc.sol", "MockUsdc");
  const distributorArtifact = artifact("RevenueDistributor.sol", "RevenueDistributor");

  const tokenHash = await deployerClient.deployContract({ abi: mockUsdc.abi, bytecode: bytecode(mockUsdc) });
  const tokenReceipt = await publicClient.waitForTransactionReceipt({ hash: tokenHash });
  const token = getAddress(tokenReceipt.contractAddress);
  const distributorHash = await deployerClient.deployContract({
    abi: distributorArtifact.abi,
    bytecode: bytecode(distributorArtifact),
    args: [token],
  });
  const distributorReceipt = await publicClient.waitForTransactionReceipt({ hash: distributorHash });
  const distributor = getAddress(distributorReceipt.contractAddress);

  await publicClient.waitForTransactionReceipt({
    hash: await deployerClient.writeContract({
      address: token,
      abi: mockUsdc.abi,
      functionName: "mint",
      args: [payer.address, totalRevenueMinor],
    }),
  });
  await publicClient.waitForTransactionReceipt({
    hash: await payerClient.writeContract({
      address: token,
      abi: mockUsdc.abi,
      functionName: "approve",
      args: [distributor, totalRevenueMinor],
    }),
  });
  await publicClient.waitForTransactionReceipt({
    hash: await payerClient.writeContract({
      address: distributor,
      abi: distributorArtifact.abi,
      functionName: "pay",
      args: [totalRevenueMinor],
    }),
  });

  return {
    publicClient,
    deployerClient,
    memberClient,
    tokenAbi: mockUsdc.abi,
    distributorAbi: distributorArtifact.abi,
    token,
    distributor,
    payer: payer.address,
    member: member.address,
  };
}

async function executeClaim(chain, batch, claim) {
  const amount = BigInt(claim.amountMinor);
  await chain.publicClient.waitForTransactionReceipt({
    hash: await chain.deployerClient.writeContract({
      address: chain.distributor,
      abi: chain.distributorAbi,
      functionName: "setDistributionRoot",
      args: [period, batch.merkleRoot, totalRevenueMinor],
    }),
  });
  const balanceBefore = await chain.publicClient.readContract({
    address: chain.token,
    abi: chain.tokenAbi,
    functionName: "balanceOf",
    args: [chain.member],
  });
  const txHash = await chain.memberClient.writeContract({
      address: chain.distributor,
      abi: chain.distributorAbi,
      functionName: "claim",
      args: [period, claim.accountId, chain.member, amount, claim.proof, claim.authorization],
  });
  const receipt = await chain.publicClient.waitForTransactionReceipt({ hash: txHash });
  const balanceAfter = await chain.publicClient.readContract({
    address: chain.token,
    abi: chain.tokenAbi,
    functionName: "balanceOf",
    args: [chain.member],
  });
  const claimEvents = parseEventLogs({
    abi: chain.distributorAbi,
    logs: receipt.logs,
    eventName: "Claimed",
  });
  const transferEvents = parseEventLogs({
    abi: chain.tokenAbi,
    logs: receipt.logs,
    eventName: "Transfer",
  });
  const claimEventMatched = claimEvents.some((event) => {
    return event.args?.period === period
      && event.args?.accountId === claim.accountId
      && getAddress(event.args?.recipient) === getAddress(chain.member)
      && event.args?.amount === amount;
  });
  const transferEventMatched = transferEvents.some((event) => {
    return getAddress(event.args?.from) === getAddress(chain.distributor)
      && getAddress(event.args?.to) === getAddress(chain.member)
      && event.args?.amount === amount;
  });
  return {
    txHash,
    balanceBefore,
    balanceAfter,
    confirmation: {
      receiptStatus: receipt.status,
      blockNumber: receipt.blockNumber.toString(),
      gasUsed: receipt.gasUsed.toString(),
      logCount: receipt.logs.length,
      claimEventMatched,
      transferEventMatched,
    },
  };
}

async function seedProject(ownerAccountId) {
  const sql = `
insert into projects (id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
  inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at)
values ('root-project', 'ROOT', '${ownerAccountId}', 'root', null, 'Root', 'Root', 'Root',
  'unlimited', null, 0, 'active', '{}'::jsonb, now(), now());
insert into projects (id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
  inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at)
values ('proj-test', 'MF260525PRJCHAIN01', '${ownerAccountId}', 'child', 'root-project', 'Testchain App', 'Testchain App',
  'Verify local chain settlement', 'unlimited', null, 0, 'active', '{}'::jsonb, now(), now());
`;
  run("docker", ["exec", "-i", dbName, "psql", "-U", "postgres", "-d", "monopolyfun", "-v", "ON_ERROR_STOP=1"], {
    cwd: repoRoot,
    input: sql,
    encoding: "utf8",
    stdio: ["pipe", "pipe", "pipe"],
  });
}

class ApiClient {
  constructor(baseUrl, session = null) {
    this.baseUrl = baseUrl;
    this.session = session ?? new ApiSession();
  }

  as(auth) {
    return new ApiClient(this.baseUrl, auth.session);
  }

  async register(handle, passwordValue) {
    // 每个测试账号保留独立 Cookie/CSRF，避免后续 owner/worker 操作串号。
    const client = new ApiClient(this.baseUrl);
    const response = await client.post("/api/v1/auth/register", { handle, password: passwordValue });
    return { account: response.account, session: client.session };
  }

  async post(path, body) {
    return this.request("POST", path, body);
  }

  async request(method, path, body) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers: this.session.headers(body !== undefined),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    this.session.capture(response);
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    if (!response.ok) {
      const error = new Error(`${method} ${path} failed`);
      error.status = response.status;
      error.body = payload;
      throw error;
    }
    return payload;
  }
}

class ApiSession {
  constructor() {
    this.cookies = new Map();
  }

  capture(response) {
    const setCookies = typeof response.headers.getSetCookie === "function"
      ? response.headers.getSetCookie()
      : splitSetCookie(response.headers.get("set-cookie"));
    for (const cookie of setCookies) {
      const [pair] = cookie.split(";");
      const index = pair.indexOf("=");
      if (index > 0) {
        this.cookies.set(pair.slice(0, index), pair.slice(index + 1));
      }
    }
  }

  headers(hasBody) {
    const headers = { Accept: "application/json" };
    if (hasBody) {
      headers["Content-Type"] = "application/json";
    }
    const cookie = [...this.cookies.entries()].map(([key, value]) => `${key}=${value}`).join("; ");
    if (cookie) {
      headers.Cookie = cookie;
    }
    const csrf = this.cookies.get("MONOPOLYFUN_CSRF");
    if (csrf) {
      headers["X-CSRF-Token"] = decodeURIComponent(csrf);
    }
    return headers;
  }
}

function splitSetCookie(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,=]+=[^;,]+)/g).map((item) => item.trim()).filter(Boolean);
}

function artifact(source, name) {
  return JSON.parse(readFileSync(resolve(repoRoot, "out", source, `${name}.json`), "utf8"));
}

function bytecode(value) {
  return value.bytecode?.object ?? value.bytecode;
}

function buildContractsIfNeeded() {
  const requiredArtifacts = [
    resolve(repoRoot, "out", "MockUsdc.sol", "MockUsdc.json"),
    resolve(repoRoot, "out", "RevenueDistributor.sol", "RevenueDistributor.json"),
  ];
  const contractSources = [
    resolve(repoRoot, "contracts", "src", "testchain", "MockUsdc.sol"),
    resolve(repoRoot, "contracts", "src", "testchain", "RevenueDistributor.sol"),
  ];
  const newestSourceTime = Math.max(...contractSources.map((path) => statSync(path).mtimeMs));
  if (!forceForgeBuild && requiredArtifacts.every((path) => {
    try {
      return Boolean(readFileSync(path, "utf8")) && statSync(path).mtimeMs >= newestSourceTime;
    } catch {
      return false;
    }
  })) {
    return;
  }
  // 中文注释：测试链 smoke 默认复用 Foundry artifact，缺失或显式强制时再编译。
  requireCommand("forge");
  run("forge", ["build"], { cwd: repoRoot, stdio: "inherit" });
}

function resultMarkdown(threadId) {
  return [
    "---",
    "packetType: work_result",
    `workThreadId: ${threadId}`,
    "---",
    "# Result",
    "",
    "## Summary",
    "Connected local testchain revenue claim.",
    "",
    "## Evidence",
    "- PR: http://localhost:3001/monopolyfun/testchain/pulls/1",
    "- Test: scripts/testchain/revenue-distribution-smoke.mjs passed",
    "",
    "## Changed Files",
    "- contracts/src/testchain/RevenueDistributor.sol",
    "- scripts/testchain/revenue-distribution-smoke.mjs",
    "",
  ].join("\n");
}

function requireCommand(command) {
  const result = spawnSync("which", [command], { encoding: "utf8" });
  if (result.status !== 0) {
    throw new Error(`${command} is required`);
  }
}

async function ensurePortFree(port) {
  const result = spawnSync("lsof", ["-tiTCP:" + port, "-sTCP:LISTEN"], { encoding: "utf8" });
  if (result.stdout.trim()) {
    throw new Error(`port ${port} is already in use`);
  }
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: repoRoot, encoding: "utf8", ...options });
  if (result.status !== 0) {
    const error = new Error(`${command} ${args.join(" ")} failed`);
    error.stdout = result.stdout;
    error.stderr = result.stderr;
    throw error;
  }
  return result;
}

async function waitFor(check, label, timeoutMs = 60000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await check()) {
      return;
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 500));
  }
  throw new Error(`Timed out waiting for ${label}`);
}

function suffix() {
  return String(Date.now()).slice(-6);
}

function cleanup() {
  for (const child of children.reverse()) {
    if (!child.killed) {
      child.kill("SIGTERM");
    }
  }
  spawnSync("docker", ["rm", "-f", dbName], { cwd: repoRoot, stdio: "ignore" });
}

function printJson(value) {
  process.stdout.write(`${JSON.stringify(value, null, 2)}\n`);
}

function errorPayload(error) {
  return {
    name: error?.name ?? "Error",
    message: error instanceof Error ? error.message : String(error),
    status: error?.status,
    body: error?.body,
    stdout: error?.stdout,
    stderr: error?.stderr,
  };
}

await main();
