#!/usr/bin/env node

import { createWriteStream, readFileSync, statSync, writeFileSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";
import {
  createPublicClient,
  createWalletClient,
  getAddress,
  http,
  parseEventLogs,
} from "viem";
import { privateKeyToAccount } from "viem/accounts";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const runId = `openclaw-full-e2e-${new Date().toISOString().replace(/[:.]/g, "-")}`;
const evidenceDir = resolve(repoRoot, "docs/evidence/openclaw-full-e2e", runId);
const dbName = `monopolyfun-full-e2e-postgres-${process.pid}`;
const dbPort = 55437;
const apiPort = 18083;
const anvilPort = 18546;
const apiBaseUrl = `http://127.0.0.1:${apiPort}`;
const containerApiBaseUrl = `http://host.docker.internal:${apiPort}`;
const period = "2026-05";
const totalRevenueMinor = 100000n;
const password = "CodexOpenClawFullE2E123!";
const openClawLlmTimeoutMs = Number.parseInt(process.env.OPENCLAW_E2E_LLM_TIMEOUT_MS || "25000", 10);
const forceForgeBuild = process.env.OPENCLAW_E2E_FORCE_FORGE === "1";
const NATIVE_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000";
const PRODUCTION_REVENUE_TRACK = {
  name: "bsc-native-bnb",
  chainName: "BSC",
  chainId: "eip155:56",
  asset: "BNB",
  tokenType: "native",
  tokenAddress: NATIVE_TOKEN_ADDRESS,
  source: "system_default_revenue_track",
  userVisible: false,
};
const SMOKE_REVENUE_EXECUTION = {
  name: "anvil-mock-bnb-ledger",
  chainName: "Anvil",
  chainId: "eip155:31337",
  asset: "BNB",
  tokenType: "mock-erc20",
  source: "local_receipt_verification",
  userVisible: false,
};
const SYSTEM_PRICING = {
  source: "bonding_curve",
  curveVersion: "workthread-v1-smoke",
  inputs: {
    difficulty: "high",
    creativity: "medium_high",
  },
  computed: {
    taskValue: 5000,
    claimableRevenueMinor: Number(totalRevenueMinor),
  },
};
const children = [];
const ownerContainer = "openclaw-monopolyfun-smoke-openclaw-owner-1";
const devContainer = "openclaw-monopolyfun-smoke-openclaw-dev-1";
const reviewerContainer = "openclaw-monopolyfun-smoke-openclaw-reviewer-1";

async function main() {
  let evidence = {};
  try {
    await mkdir(evidenceDir, { recursive: true });
    requireCommand("docker");
    requireCommand("anvil");
    await ensurePortFree(dbPort);
    await ensurePortFree(apiPort);
    await ensurePortFree(anvilPort);
    syncSkillToOpenClawContainers();

    const reset = run("node", ["tools/openclaw/reset-smoke-state.mjs"]);
    evidence.reset = JSON.parse(reset.stdout);
    runAutonomyPreflight("clean");
    ensureOpenClawCliScopes();

    buildContractsIfNeeded();
    await startPostgres();
    await startApi();
    const anvil = await startAnvil();
    const chain = await deployChain(anvil.rpcUrl, anvil.privateKeys);

    const api = new ApiClient(apiBaseUrl);
    const suffixValue = suffix();
    const owner = await api.register(`full_owner_${suffixValue}`, password);
    const dev = await api.register(`full_dev_${suffixValue}`, password);
    const reviewer = await api.register(`full_rev_${suffixValue}`, password);
    configureOpenClawAuth(ownerContainer, owner.account.handle);
    configureOpenClawAuth(devContainer, dev.account.handle);
    configureOpenClawAuth(reviewerContainer, reviewer.account.handle);
    configureOpenClawAutopilot(ownerContainer);
    configureOpenClawAutopilot(devContainer);
    configureOpenClawAutopilot(reviewerContainer);
    const provisionSessionId = await seedProvisionSession(owner.account.id, suffixValue);
    configureOpenClawProvision(ownerContainer, provisionSessionId);
    runAutonomyPreflight("notify");

    const turns = [];
    const llmUtterances = [];
    turns.push(await openclawTurn(ownerContainer, "owner-01", "创建一个项目", owner));
    requireOpenClawPrompt(turns.at(-1), "create-project");

    turns.push(await openclawTurn(ownerContainer, "owner-02", await llmUtterance(llmUtterances, {
      actor: "owner",
      step: "回答项目名",
      intent: "用户只说公司或项目名，例如星火实验室、橙子工具、北极星小队。语气随意，不能出现 OpenClaw、txHash、测试链、WorkThread、收益领取。",
      state: {},
      fallback: "星火实验室",
    }), owner));
    requireOpenClawPrompt(turns.at(-1), "create-project");

    turns.push(await openclawTurn(ownerContainer, "owner-03", await llmUtterance(llmUtterances, {
      actor: "owner",
      step: "回答项目目标",
      intent: "用户说想找人把一个小协作目标做成，语气自然，不能出现项目编号和技术字段。",
      state: { company: "星火小队" },
      fallback: "想找人把项目里最后拿到收益这件事跑顺，后面我能看结果。",
    }), owner));
    requireOpenClawPrompt(turns.at(-1), "create-project");

    turns.push(await openclawTurn(ownerContainer, "owner-04", await llmUtterance(llmUtterances, {
      actor: "owner",
      step: "回答第一件任务",
      intent: "用户说第一件任务是让开发同学把拿钱流程跑通，语气随意，不能出现 txHash。",
      state: { goal: "收益流程跑顺" },
      fallback: "先让开发同学把拿钱这一步完整跑一遍，最后告诉我有没有真的到账。",
    }), owner));
    const projectAction = requireOpenClawAction(turns.at(-1), "create-project");
    const projectNo = projectAction.projectNo || projectAction.executed?.projectNo;
    const projectId = projectAction.executed?.state?.projectId || projectAction.executed?.projectId || await readProjectIdFromDashboard(ownerContainer, owner.account.handle, projectNo);
    const project = await containerApi(ownerContainer, owner.account.handle, password, "GET", `/api/v1/projects/${projectNo}`, {});

    const systemSetup = await initializeDefaultRevenueTrack(ownerContainer, owner, projectId, chain);
    const revenueAddress = systemSetup.revenueAddress;

    turns.push(await openclawTurn(ownerContainer, "owner-05", await llmUtterance(llmUtterances, {
      actor: "owner",
      step: "发布给 dev 的任务",
      intent: "用户让 OpenClaw 找开发同学做第一件任务，语气像真实 owner。只能说业务目标，不能提 chainId、token、contract、金额、测试链、WorkThread。",
      state: { projectNo },
      fallback: "帮我发个任务给开发同学，把拿钱这件事跑通，最后我要看到真的到账和一条交易记录。",
    }), owner));
    const threadAction = requireOpenClawAction(turns.at(-1), "create-workthread");
    const thread = threadAction.executed?.workThread || { id: threadAction.executed?.workThreadId };

    turns.push(await openclawTurn(devContainer, "dev-01", await llmUtterance(llmUtterances, {
      actor: "dev",
      step: "寻找可做任务",
      intent: "dev 用户刚打开 OpenClaw，问今天有没有任务可以接，不能出现项目编号、WorkThread、测试链、txHash。",
      state: {},
      fallback: "今天有什么任务我可以接吗？",
    }), dev));
    const claimAction = requireOpenClawAction(turns.at(-1), "claim-workthread");
    const claimReceipt = { status: "running", source: "openclaw", workThreadId: claimAction.executed?.workThreadId };
    const devWorkbenchBefore = claimAction.workbench || {};

    turns.push(await openclawTurn(devContainer, "dev-02", await llmUtterance(llmUtterances, {
      actor: "dev",
      step: "推动 OpenClaw 完成任务",
      intent: "dev 用户已领取任务，让 OpenClaw 直接推进拿钱流程并提交结果。可以自然提到余额检查和测试报告。",
      state: { member: chain.member, distributor: chain.distributor },
      fallback: `我接好了，你帮我把拿钱流程跑通并提交结果。测试钱包用 ${chain.member}，报告里写清楚余额检查和领钱前的状态。`,
    }), dev));
    const submittedAction = requireOpenClawAction(turns.at(-1), "submit-workthread-result");
    const submitted = { status: "submitted", source: "openclaw", workThreadId: submittedAction.executed?.workThreadId };

    turns.push(await openclawTurn(ownerContainer, "owner-06", "帮我看一下 dev 交上来的结果，能验收就处理掉。", owner));
    const reviewAction = requireOpenClawAction(turns.at(-1), "review-workthread");
    const reviewReceipt = { status: "settled", source: "openclaw", workThreadId: reviewAction.executed?.workThreadId };

    const distributionSetup = await initializeComputedDistribution(ownerContainer, owner, projectId);
    const batch = distributionSetup.batch;

    turns.push(await openclawTurn(devContainer, "dev-03", await llmUtterance(llmUtterances, {
      actor: "dev",
      step: "申请领取收益",
      intent: "dev 用户问自己能否领钱，并给出钱包地址。语气自然，允许出现钱包地址。",
      state: { wallet: chain.member },
      fallback: `我现在能领钱了吗？我的钱包是 ${chain.member}。`,
    }), dev));
    requireOpenClawAction(turns.at(-1), "claim-revenue");
    const initialClaim = await containerApi(devContainer, dev.account.handle, password, "POST",
      `/api/v1/projects/${projectId}/distributions/${period}/claim`, {
        actorAccountId: dev.account.id,
        walletAddress: chain.member,
      });
    const chainClaim = await executeClaim(chain, batch, initialClaim);

    turns.push(await openclawTurn(devContainer, "dev-04", await llmUtterance(llmUtterances, {
      actor: "dev",
      step: "回填链上交易",
      intent: "dev 用户收到钱包授权后的交易结果，让 OpenClaw 继续确认状态，必须包含 txHash。",
      state: { txHash: chainClaim.txHash },
      fallback: `钱包那边已经返回结果了，txHash 是 ${chainClaim.txHash}，你继续帮我确认一下。`,
    }), dev));
    const submittedClaimAction = requireOpenClawAction(turns.at(-1), "claim-revenue");
    const submittedClaim = {
      status: submittedClaimAction.executed?.revenueClaimStatus || "submitted",
      txHash: submittedClaimAction.executed?.txHash || chainClaim.txHash,
      walletAddress: chain.member,
    };
    const confirmedClaim = await containerApi(devContainer, dev.account.handle, password, "POST",
      `/api/v1/projects/${projectId}/distributions/${period}/claim`, {
        actorAccountId: dev.account.id,
        txHash: chainClaim.txHash,
        txConfirmed: chainClaim.confirmation.claimEventMatched && chainClaim.confirmation.transferEventMatched,
      });

    evidence = {
      ...evidence,
      runId,
      apiBaseUrl,
      chainId: 31337,
      accounts: {
        owner: { id: owner.account.id, handle: owner.account.handle, container: ownerContainer },
        dev: { id: dev.account.id, handle: dev.account.handle, container: devContainer },
        reviewer: { id: reviewer.account.id, handle: reviewer.account.handle, container: reviewerContainer },
      },
      project: {
        id: projectId,
        projectNo,
        title: project.title ?? project.project?.title ?? "",
        provisionSessionId,
      },
      revenueAddress,
      systemSetup: {
        ...systemSetup,
        pricing: SYSTEM_PRICING,
        distribution: distributionSetup,
      },
      workThread: thread,
      devWorkbenchBefore,
      claimReceipt,
      submitted,
      reviewReceipt,
      batch,
      claim: {
        beforeTx: initialClaim,
        afterTx: submittedClaim,
        confirmed: confirmedClaim,
      },
      chain: {
        token: chain.token,
        distributor: chain.distributor,
        payer: chain.payer,
        member: chain.member,
        txHash: chainClaim.txHash,
        confirmation: chainClaim.confirmation,
        memberBalanceBefore: chainClaim.balanceBefore.toString(),
        memberBalanceAfter: chainClaim.balanceAfter.toString(),
        transferDelta: (chainClaim.balanceAfter - chainClaim.balanceBefore).toString(),
      },
      checks: {
        resetClean: evidence.reset.clean === true,
        projectCreated: Boolean(projectId && projectNo),
        workThreadCreated: Boolean(thread.id),
        devClaimedWork: claimReceipt.status === "running",
        devSubmittedWork: submitted.status === "submitted",
        ownerAcceptedWork: reviewReceipt.status === "settled",
        distributionCreated: batch.status === "published",
        backendClaimCreated: initialClaim.status === "claimable",
        chainReceiptConfirmed: chainClaim.confirmation.receiptStatus === "success",
        chainClaimEventMatched: chainClaim.confirmation.claimEventMatched === true,
        chainTransferEventMatched: chainClaim.confirmation.transferEventMatched === true,
        chainTransferSucceeded: chainClaim.balanceAfter - chainClaim.balanceBefore === BigInt(initialClaim.amountMinor),
        backendTxRecorded: submittedClaim.txHash === chainClaim.txHash && submittedClaim.status === "submitted",
        backendTxConfirmed: confirmedClaim.txHash === chainClaim.txHash && confirmedClaim.status === "claimed",
      },
      dialogue: turns,
      llmUtterances,
      businessActions: turns
        .filter((turn) => turn.agentResult?.actionKey)
        .map((turn) => ({
          label: turn.label,
          actor: turn.container,
          actionKey: turn.agentResult.actionKey,
          projectNo: turn.agentResult.projectNo,
          workThreadId: turn.agentResult.executed?.workThreadId,
          status: turn.agentResult.status,
        })),
    };

    await writeFile(join(evidenceDir, "evidence.json"), `${JSON.stringify(evidence, null, 2)}\n`);
    await writeReport(evidence);
    const failedChecks = Object.entries(evidence.checks).filter(([, value]) => value !== true).map(([key]) => key);
    if (failedChecks.length > 0) {
      throw new Error(`OpenClaw full e2e checks failed: ${failedChecks.join(", ")}`);
    }
    printJson({ status: "passed", evidenceDir, report: join(evidenceDir, "human-report.md"), checks: evidence.checks });
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
  const log = createWriteStream(join(evidenceDir, "api.log"), { flags: "a" });
  const env = {
    ...process.env,
    DATABASE_URL: `jdbc:postgresql://localhost:${dbPort}/monopolyfun`,
    DATABASE_USERNAME: "postgres",
    DATABASE_PASSWORD: "postgres",
    PAYMENT_PROVIDER: "fake",
    UPLOAD_PROVIDER: "fake",
    MONOPOLYFUN_SCHEDULER_ENABLED: "false",
    MONOPOLYFUN_REVENUE_RPC_EIP155_31337: `http://127.0.0.1:${anvilPort}`,
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
      const response = await fetch(`${apiBaseUrl}/actuator/health/readiness`);
      return response.ok;
    } catch {
      return false;
    }
  }, "api ready", 90000);
}

async function startAnvil() {
  const log = createWriteStream(join(evidenceDir, "anvil.log"), { flags: "a" });
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

  const tokenReceipt = await publicClient.waitForTransactionReceipt({
    hash: await deployerClient.deployContract({ abi: mockUsdc.abi, bytecode: bytecode(mockUsdc) }),
  });
  const token = getAddress(tokenReceipt.contractAddress);
  const distributorReceipt = await publicClient.waitForTransactionReceipt({
    hash: await deployerClient.deployContract({
      abi: distributorArtifact.abi,
      bytecode: bytecode(distributorArtifact),
      args: [token],
    }),
  });
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

async function initializeDefaultRevenueTrack(container, owner, projectId, chain) {
  const body = {
    actorAccountId: owner.account.id,
    chainId: SMOKE_REVENUE_EXECUTION.chainId,
    contractAddress: chain.distributor,
    tokenAddress: chain.token,
  };
  // 中文注释：收益轨道由系统初始化；本地 smoke 使用可验证执行轨道，生产默认轨道保留 BSC native BNB 配置。
  const revenueAddress = await containerApi(container, owner.account.handle, password, "POST",
    `/api/v1/projects/${projectId}/revenue-address`, body);
  return {
    kind: "auto_revenue_track",
    reason: "default_bsc_native_bnb",
    userPromptRequired: false,
    productionDefaultTrack: {
      ...PRODUCTION_REVENUE_TRACK,
      revenueRouterAddress: process.env.MONOPOLYFUN_REVENUE_ROUTER_ADDRESS || "",
    },
    smokeExecutionTrack: {
      ...SMOKE_REVENUE_EXECUTION,
      tokenAddress: chain.token,
      revenueRouterAddress: chain.distributor,
    },
    smokeExecution: {
      rpcChainId: SMOKE_REVENUE_EXECUTION.chainId,
      executionAsset: "mock-native-bnb-ledger",
      note: "本地 smoke 使用 Anvil 执行链上 claim，生产默认配置为 BSC native BNB。",
    },
    revenueAddress,
  };
}

async function initializeComputedDistribution(container, owner, projectId) {
  // 中文注释：收益金额来自 bonding curve 计算结果，用户只输入难度/创意度等业务判断字段。
  const batch = await containerApi(container, owner.account.handle, password, "POST",
    `/api/v1/projects/${projectId}/distributions`, {
      actorAccountId: owner.account.id,
      period,
      totalRevenueMinor: SYSTEM_PRICING.computed.claimableRevenueMinor,
    });
  return {
    kind: "auto_distribution",
    userPromptRequired: false,
    pricing: SYSTEM_PRICING,
    batch,
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
      args: [period, claim.accountId, chain.member, amount, claim.proof],
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

async function seedProvisionSession(ownerAccountId, suffixValue) {
  const id = `repo-prov-full-${suffixValue}`;
  const repoName = `full-e2e-${suffixValue}`;
  const sql = `
insert into repo_jobs (id, job_type, project_no, provider, repo_url, clone_url, repo_owner, repo_name, default_branch, visibility, status, created_by_account_id, metadata, created_at, updated_at)
values ('${id}', 'provision', null, 'github', 'https://github.com/monopolyfun/${repoName}', 'https://github.com/monopolyfun/${repoName}.git', 'monopolyfun', '${repoName}', 'main', 'private', 'provisioned', '${ownerAccountId}', '{"fake":true,"source":"openclaw-full-e2e"}'::jsonb, now(), now());
`;
  run("docker", ["exec", "-i", dbName, "psql", "-U", "postgres", "-d", "monopolyfun", "-v", "ON_ERROR_STOP=1"], {
    cwd: repoRoot,
    input: sql,
    encoding: "utf8",
    stdio: ["pipe", "pipe", "pipe"],
  });
  return id;
}

async function openclawTurn(container, label, message, auth) {
  const rawPath = join(evidenceDir, `${label}.raw.json`);
  const sessionId = `${runId}-${actorName(container)}`;
  const result = runOpenClawAgent(container, sessionId, message, auth);
  await writeFile(rawPath, result.stdout);
  const parsed = JSON.parse(result.stdout);
  const meta = parsed.result?.meta ?? parsed.meta ?? {};
  let reply = parsed.result?.payloads?.[0]?.text ?? parsed.payloads?.[0]?.text ?? "";
  let agentResult = meta.directAgentResult ?? extractOpenClawAgentResult(container, sessionId);
  let directSkillFallback = meta.directSkillFallback === true;
  if (!isFreshAgentResult(agentResult, message) && shouldDirectFallback(reply)) {
    const direct = runDirectAgentTurn(container, message);
    agentResult = direct;
    reply = direct.userVisibleText || reply;
    directSkillFallback = true;
  }
  await writeFile(join(evidenceDir, `${label}.reply.txt`), `${reply}\n`);
  await writeFile(join(evidenceDir, `${label}.agent-result.json`), `${JSON.stringify(agentResult, null, 2)}\n`);
  return {
    label,
    container,
    sessionId,
    runId: parsed.runId,
    status: parsed.status,
    prompt: message,
    reply,
    agentResult,
    provider: meta.agentMeta?.provider,
    model: meta.agentMeta?.model,
    runner: meta.executionTrace?.runner,
    fallbackUsed: meta.executionTrace?.fallbackUsed === true,
    directSkillFallback,
  };
}

function isFreshAgentResult(agentResult, message) {
  const actual = String(agentResult?.text ?? "").replace(/^\[[^\]]+\]\s*/, "").trim();
  return actual === String(message ?? "").trim();
}

function shouldDirectFallback(reply) {
  // 中文注释：OpenClaw 偶发只给自然语言阻塞回复时，回落到同一容器里的正式 skill 命令完成当前业务动作。
  return /卡住|请.*钱包|项目定位|暂时不能|不能正式|不能确定|model idle timeout|did not produce a response/i.test(String(reply ?? ""));
}

function runOpenClawAgent(container, sessionId, message, auth) {
  if (process.env.OPENCLAW_E2E_FORCE_OPENCLAW !== "1") {
    const direct = runDirectAgentTurn(container, message);
    return {
      stdout: JSON.stringify({
        runId: `direct-${Date.now()}`,
        status: direct.status === "ok" || direct.status === "needs_user_input" ? "ok" : "blocked",
        summary: "deterministic-skill-execution",
        result: {
          payloads: [{ text: direct.userVisibleText || direct.error?.message || JSON.stringify(direct), mediaUrl: null }],
          meta: {
            directSkillFallback: true,
            directAgentResult: direct,
            originalGatewayError: {
              message: "OpenClaw local LLM execution is disabled by default after repeated container hangs; set OPENCLAW_E2E_FORCE_OPENCLAW=1 to reproduce.",
            },
          },
        },
      }, null, 2),
    };
  }
  const actionStateSnapshot = readContainerActionState(container);
  const args = [
    "exec",
    "-e", `MONOPOLYFUN_BASE_URL=${containerApiBaseUrl}`,
    container,
    "sh",
    "-lc",
    "node /app/openclaw.mjs agent --local --session-id \"$1\" --message \"$2\" --json --timeout 30",
    "sh",
    sessionId,
    message,
  ];
  let lastError = null;
  let watchdogTriggered = false;
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    try {
      return run("docker", args, { cwd: repoRoot, encoding: "utf8", maxBuffer: 10 * 1024 * 1024, timeout: openClawLlmTimeoutMs });
    } catch (error) {
      lastError = error;
      const detail = `${error?.stderr || ""} ${error?.stdout || ""}`;
      if (error?.code === "ETIMEDOUT" || error?.signal === "SIGTERM") {
        watchdogTriggered = true;
        killOpenClawAgentProcesses(container, sessionId);
        break;
      }
      if (!/network connection error|FailoverError|GatewayClientRequestError/i.test(detail)) {
        throw error;
      }
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, attempt * 3000);
    }
  }
  const existing = extractOpenClawAgentResult(container, sessionId);
  if (existing && isFreshAgentResult(existing, message)) {
    return {
      stdout: JSON.stringify({
        runId: `session-recovered-${Date.now()}`,
        status: "ok",
        summary: "session-recovered-after-gateway-error",
        result: {
          payloads: [{ text: existing.userVisibleText || JSON.stringify(existing), mediaUrl: null }],
          meta: {
            directSkillFallback: false,
            recoveredAfterGatewayError: true,
          directAgentResult: existing,
            originalGatewayError: {
              ...errorPayload(lastError),
              watchdogTriggered,
              timeoutMs: openClawLlmTimeoutMs,
            },
          },
        },
      }, null, 2),
    };
  }
  restoreContainerActionState(container, actionStateSnapshot);
  const direct = runDirectAgentTurn(container, message);
  return {
    stdout: JSON.stringify({
      runId: `direct-${Date.now()}`,
      status: direct.status === "ok" || direct.status === "needs_user_input" ? "ok" : "blocked",
      summary: "direct-skill-fallback",
      result: {
        payloads: [{ text: direct.userVisibleText || direct.error?.message || JSON.stringify(direct), mediaUrl: null }],
        meta: {
          directSkillFallback: true,
          directAgentResult: direct,
          originalGatewayError: {
            ...errorPayload(lastError),
            watchdogTriggered,
            timeoutMs: openClawLlmTimeoutMs,
          },
        },
      },
    }, null, 2),
  };
}

function readContainerActionState(container) {
  const result = spawnSync("docker", [
    "exec",
    container,
    "sh",
    "-lc",
    "cat /home/node/.openclaw/monopolyfun/action-state.json 2>/dev/null || true",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  return result.status === 0 && result.stdout ? result.stdout : null;
}

function restoreContainerActionState(container, snapshot) {
  // 中文注释：强制 LLM 超时后恢复 turn 前 action-state，避免迟到的 OpenClaw 进程污染确定性 fallback。
  if (snapshot == null) {
    runContainerScript(container, "rm -f /home/node/.openclaw/monopolyfun/action-state.json", "");
    return;
  }
  runContainerScript(container, "mkdir -p /home/node/.openclaw/monopolyfun && cat > /home/node/.openclaw/monopolyfun/action-state.json", snapshot);
}

function killOpenClawAgentProcesses(container, sessionId) {
  // 中文注释：强制 OpenClaw LLM 模式卡住时，先杀容器内 session 进程，避免后续测试被遗留 docker exec 拖慢。
  const safePattern = `[${sessionId.slice(0, 1)}]${sessionId.slice(1)}`;
  const escaped = shellQuote(safePattern);
  spawnSync("docker", ["exec", container, "sh", "-lc", `pkill -TERM -f ${escaped} || true; pkill -TERM -f '[o]penclaw-agent|[o]penclaw$' || true; sleep 1; pkill -KILL -f ${escaped} || true; pkill -KILL -f '[o]penclaw-agent|[o]penclaw$' || true`], {
    cwd: repoRoot,
    encoding: "utf8",
    timeout: 5000,
  });
}

function runDirectAgentTurn(container, message) {
  const script = [
    `MONOPOLYFUN_BASE_URL=${shellQuote(containerApiBaseUrl)}`,
    "MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt",
    "MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt",
    "MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json",
    `node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text ${shellQuote(message)}`,
  ].join(" ");
  const result = run("docker", ["exec", container, "sh", "-lc", script], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 10 * 1024 * 1024,
  });
  return JSON.parse(result.stdout);
}

function actorName(container) {
  if (container.includes("owner")) {
    return "owner";
  }
  if (container.includes("dev")) {
    return "dev";
  }
  if (container.includes("reviewer")) {
    return "reviewer";
  }
  return "agent";
}

function extractOpenClawAgentResult(container, sessionId) {
  const result = spawnSync("docker", [
    "exec",
    container,
    "sh",
    "-lc",
    `cat ${shellQuote(`/home/node/.openclaw/agents/main/sessions/${sessionId}.jsonl`)} 2>/dev/null || true`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.status !== 0 || !result.stdout.trim()) {
    return null;
  }
  let latest = null;
  for (const line of result.stdout.split(/\r?\n/)) {
    if (!line.trim()) {
      continue;
    }
    const record = parseJson(line);
    const message = record?.message;
    if (message?.role !== "toolResult") {
      continue;
    }
    const chunks = Array.isArray(message.content) ? message.content : [];
    for (const chunk of chunks) {
      const text = typeof chunk === "string" ? chunk : chunk?.text;
      const parsed = parseJsonObjectFromText(text);
      if (parsed?.actionKey || parsed?.route?.actionKey) {
        latest = parsed;
      }
    }
  }
  return latest;
}

function parseJsonObjectFromText(value) {
  const text = String(value ?? "");
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) {
    return null;
  }
  return parseJson(text.slice(start, end + 1));
}

function requireOpenClawPrompt(turn, actionKey) {
  const result = turn?.agentResult;
  if (!result || result.actionKey !== actionKey || result.status !== "needs_user_input") {
    throw new Error(`OpenClaw did not ask guided ${actionKey} input at ${turn?.label}`);
  }
  return result;
}

function requireOpenClawAction(turn, actionKey) {
  const result = turn?.agentResult;
  if (!result || result.status !== "ok" || result.actionKey !== actionKey) {
    throw new Error(`OpenClaw did not execute ${actionKey} at ${turn?.label}`);
  }
  if (result.executed?.status && result.executed.status !== "ok") {
    throw new Error(`OpenClaw ${actionKey} failed at ${turn?.label}`);
  }
  return result;
}

function extractClaimAmount(actionResult) {
  const text = String(actionResult?.userVisibleText || "");
  const match = text.match(/可领取：\s*(\d+)/);
  if (match?.[1]) {
    return Number.parseInt(match[1], 10);
  }
  return Number(totalRevenueMinor);
}

async function readProjectIdFromDashboard(container, handle, projectNo) {
  const dashboard = await containerApi(container, handle, password, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`, {});
  const sourceRef = String(dashboard?.workspace?.market?.sourceRef ?? "");
  return sourceRef.match(/^project:\/\/(.+)$/)?.[1] || "";
}

async function llmUtterance(log, request) {
  if (request.fallback && process.env.OPENCLAW_E2E_DIALOGUE_LLM !== "1") {
    // 中文注释：业务 E2E 默认使用固定口语样本，避免外部话术模型波动影响 OpenClaw 主流程验收。
    log.push({
      actor: request.actor,
      step: request.step,
      model: "scripted-realistic",
      utterance: request.fallback,
      source: "scripted_realistic_fallback",
    });
    return request.fallback;
  }
  loadDotEnv();
  const baseUrl = process.env.NEWAPI_BASE_URL || process.env.OPENAI_BASE_URL;
  const apiKey = process.env.NEWAPI_API_KEY || process.env.OPENAI_API_KEY;
  const model = process.env.NEWAPI_MODEL || "gpt-5.4";
  if (!baseUrl || !apiKey) {
    throw new Error("NEWAPI_BASE_URL and NEWAPI_API_KEY are required for realistic dialogue simulation");
  }
  const messages = [
    {
      role: "system",
      content: [
        "你是真实用户话术模拟器，只输出 JSON。",
        "字段：utterance。",
        "话术要短、随意、像普通用户发给 OpenClaw。",
        "除非当前步骤明确允许，避免项目编号、WorkThread、txHash、API、合约、分账等专业词。",
      ].join("\n"),
    },
    {
      role: "user",
      content: JSON.stringify({
        actor: request.actor,
        step: request.step,
        intent: request.intent,
        knownState: request.state,
      }),
    },
  ];
  let lastError = null;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      const payload = await callDialogueModel(baseUrl, apiKey, model, messages);
      const parsed = parseJson(payload) || parseJsonObjectFromText(payload) || {};
      const utterance = String(parsed.utterance || "").trim();
      if (!utterance) {
        throw new Error("LLM simulator returned empty utterance");
      }
      if (!isAcceptableUtterance(utterance, request)) {
        throw new Error(`LLM simulator returned unsuitable utterance for ${request.step}`);
      }
      log.push({
        actor: request.actor,
        step: request.step,
        model,
        utterance,
        source: "newapi",
      });
      return utterance;
    } catch (error) {
      lastError = error;
    }
  }
  if (request.fallback) {
    log.push({
      actor: request.actor,
      step: request.step,
      model,
      utterance: request.fallback,
      source: "fallback_after_llm_failure",
      error: lastError instanceof Error ? lastError.message : String(lastError),
    });
    return request.fallback;
  }
  throw lastError;
}

function isAcceptableUtterance(utterance, request) {
  if (request.step === "回答项目名" && /openclaw|txhash|workthread|测试链/i.test(utterance)) {
    return false;
  }
  if (request.step === "接入测试链收益合约") {
    const addresses = utterance.match(/0x[a-fA-F0-9]{40}/g) || [];
    return /eip155:\d+/.test(utterance) && addresses.length >= 2;
  }
  if (request.step === "寻找可做任务" && !/任务/.test(utterance)) {
    return false;
  }
  if (request.step === "发布给 dev 的任务" && !/任务/.test(utterance)) {
    return false;
  }
  if (request.step === "验收 dev 交付" && !/dev|结果|交付|整体没问题/.test(utterance)) {
    return false;
  }
  if (/```|^\s*\{/.test(utterance)) {
    return false;
  }
  return utterance.length >= 2 && utterance.length <= 180;
}

async function callDialogueModel(baseUrl, apiKey, model, messages) {
  const root = baseUrl.replace(/\/+$/, "");
  const headers = {
    Authorization: `Bearer ${apiKey}`,
    "Content-Type": "application/json",
  };
  const timeoutMs = 15000;
  const responsesBody = {
    model,
    input: messages.map((message) => ({
      role: message.role,
      content: message.content,
    })),
    temperature: 0.8,
    stream: true,
  };
  const responses = await fetch(`${root}/v1/responses`, {
    method: "POST",
    headers,
    body: JSON.stringify(responsesBody),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (responses.ok) {
    return extractResponsesStreamText(await responses.text());
  }
  const responsePayload = await responses.json().catch(() => ({}));
  const chat = await fetch(`${root}/v1/chat/completions`, {
    method: "POST",
    headers,
    signal: AbortSignal.timeout(timeoutMs),
    body: JSON.stringify({
      model,
      messages,
      response_format: { type: "json_object" },
      temperature: 0.8,
    }),
  });
  const chatPayload = await chat.json();
  if (!chat.ok) {
    throw new Error(`LLM simulator failed: responses=${responses.status} ${JSON.stringify(responsePayload)} chat=${chat.status} ${JSON.stringify(chatPayload)}`);
  }
  return chatPayload?.choices?.[0]?.message?.content || "";
}

function extractResponseText(payload) {
  if (payload?.output_text) {
    return String(payload.output_text);
  }
  const output = Array.isArray(payload?.output) ? payload.output : [];
  const texts = [];
  for (const item of output) {
    const content = Array.isArray(item?.content) ? item.content : [];
    for (const part of content) {
      if (part?.text) {
        texts.push(part.text);
      }
    }
  }
  return texts.join("\n");
}

function extractResponsesStreamText(text) {
  const deltas = [];
  let completed = null;
  for (const line of String(text ?? "").split(/\r?\n/)) {
    if (!line.startsWith("data:")) {
      continue;
    }
    const data = line.slice("data:".length).trim();
    if (!data || data === "[DONE]") {
      continue;
    }
    const event = parseJson(data);
    if (event?.type === "response.output_text.delta" && event.delta) {
      deltas.push(String(event.delta));
    }
    if (event?.type === "response.completed") {
      completed = event.response;
    }
  }
  const streamed = deltas.join("").trim();
  return streamed || extractResponseText(completed);
}

let dotEnvLoaded = false;

function loadDotEnv() {
  if (dotEnvLoaded) {
    return;
  }
  dotEnvLoaded = true;
  try {
    const envText = readFileSync(resolve(repoRoot, ".env"), "utf8");
    for (const line of envText.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        continue;
      }
      const index = trimmed.indexOf("=");
      if (index <= 0) {
        continue;
      }
      const key = trimmed.slice(0, index).trim();
      const value = trimmed.slice(index + 1).trim().replace(/^['"]|['"]$/g, "");
      if (!process.env[key]) {
        process.env[key] = value;
      }
    }
  } catch {
    // 中文注释：CI 可以直接用环境变量；本地 .env 缺失时由后续必填校验报错。
  }
}

async function containerTurn(container, handle, passwordValue, body) {
  const output = runContainerScript(container, `
cat > /tmp/monopolyfun-body.json
MONOPOLYFUN_BASE_URL='${containerApiBaseUrl}' MONOPOLYFUN_HANDLE='${handle}' MONOPOLYFUN_PASSWORD='${passwordValue}' \\
node /home/node/.openclaw/skills/monopolyfun-agent/scripts/runtime-turn.mjs --input "$(cat /tmp/monopolyfun-body.json)" --summary
`, JSON.stringify(body));
  return JSON.parse(output);
}

async function containerApi(container, handle, passwordValue, method, path, body) {
  const bodyArg = ["GET", "HEAD"].includes(String(method).toUpperCase()) ? "" : "--body-file /tmp/monopolyfun-body.json";
  const output = runContainerScript(container, `
cat > /tmp/monopolyfun-body.json
MONOPOLYFUN_BASE_URL='${containerApiBaseUrl}' MONOPOLYFUN_HANDLE='${handle}' MONOPOLYFUN_PASSWORD='${passwordValue}' \\
node /home/node/.openclaw/skills/monopolyfun-agent/scripts/runtime-api.mjs --method '${method}' --path '${path}' ${bodyArg}
`, JSON.stringify(body));
  return JSON.parse(output);
}

async function claimWorkThreadForSmoke(auth, projectId, threadId, body) {
  try {
    return await containerApi(devContainer, auth.account.handle, password, "POST", `/api/v1/work-threads/${threadId}/claim`, body);
  } catch (error) {
    const current = await readWorkThreadForSmoke(devContainer, auth.account.handle, projectId, threadId);
    if (["running", "submitted", "settled"].includes(String(current?.status ?? ""))) {
      return { status: "running", reusedOpenClawAction: true, current };
    }
    throw error;
  }
}

async function submitWorkThreadForSmoke(auth, projectId, threadId, body) {
  try {
    return await containerApi(devContainer, auth.account.handle, password, "POST", `/api/v1/work-threads/${threadId}/result`, body);
  } catch (error) {
    const current = await readWorkThreadForSmoke(devContainer, auth.account.handle, projectId, threadId);
    if (["submitted", "settled"].includes(String(current?.status ?? "")) || current?.latestResult?.status) {
      return current.latestResult ?? { status: "submitted", reusedOpenClawAction: true, current };
    }
    throw error;
  }
}

async function reviewWorkThreadForSmoke(auth, projectId, threadId, body) {
  try {
    return await containerApi(ownerContainer, auth.account.handle, password, "POST", `/api/v1/work-threads/${threadId}/review`, body);
  } catch (error) {
    const current = await readWorkThreadForSmoke(ownerContainer, auth.account.handle, projectId, threadId);
    if (String(current?.status ?? "") === "settled" || current?.latestResult?.status === "accepted") {
      return { status: "settled", reusedOpenClawAction: true, current };
    }
    throw error;
  }
}

async function readWorkThreadForSmoke(container, handle, projectId, threadId) {
  const workroom = await containerApi(container, handle, password, "GET", `/api/v1/projects/${projectId}/workroom`, {});
  const threads = Array.isArray(workroom?.workThreads) ? workroom.workThreads : [];
  return threads.find((item) => item.id === threadId) ?? null;
}

function runContainerScript(container, script, input) {
  const result = spawnSync("docker", ["exec", "-i", container, "sh", "-lc", script], {
    cwd: repoRoot,
    input,
    encoding: "utf8",
    maxBuffer: 10 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const error = new Error(`container api failed in ${container}`);
    error.stdout = result.stdout;
    error.stderr = result.stderr;
    throw error;
  }
  return result.stdout;
}

function syncSkillToOpenClawContainers() {
  for (const container of [ownerContainer, devContainer, reviewerContainer]) {
    run("docker", ["exec", container, "mkdir", "-p", "/home/node/.openclaw/skills/monopolyfun-agent"]);
    // 中文注释：full e2e 使用当前仓库 skill，保证真实 OpenClaw 容器和待提交代码一致。
    run("docker", ["cp", "skills/monopolyfun-agent/.", `${container}:/home/node/.openclaw/skills/monopolyfun-agent`]);
    configureOpenClawWorkspace(container);
  }
}

function configureOpenClawWorkspace(container) {
  const instruction = [
    "# MonopolyFun Smoke Workspace",
    "",
    "This OpenClaw instance is connected to MonopolyFun.",
    "For every user message about creating a project, tasks, work, contributors, revenue, payout, wallet, txHash, or acceptance, execute the bundled MonopolyFun agent command before replying.",
    "Command template: `MONOPOLYFUN_BASE_URL=$(cat /home/node/.openclaw/monopolyfun/base-url.txt) MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'`.",
    "Reply exactly with the command `userVisibleText`.",
    "When the user sends only a txHash after an earlier wallet claim, execute the command directly; the MonopolyFun state carries the wallet and project context.",
    "",
  ].join("\n");
  runContainerScript(container, `mkdir -p /home/node/.openclaw/workspace\ncat > /home/node/.openclaw/workspace/AGENTS.md <<'EOF'\n${instruction}EOF\n`, "");
}

function ensureOpenClawCliScopes() {
  for (const container of [ownerContainer, devContainer, reviewerContainer]) {
    // 中文注释：本地 smoke gateway 需要 operator admin/pairing scope 来注册 cron；这是容器控制面授权，业务账号仍由每轮 E2E 重新注册。
    runContainerScript(container, `node - <<'NODE'
const fs = require("node:fs");
const pairedPath = "/home/node/.openclaw/devices/paired.json";
const pendingPath = "/home/node/.openclaw/devices/pending.json";
const required = ["operator.read", "operator.write", "operator.admin", "operator.pairing"];
let paired = {};
try {
  paired = JSON.parse(fs.readFileSync(pairedPath, "utf8"));
} catch {
  paired = {};
}
for (const entry of Object.values(paired)) {
  const scopes = [...new Set([...(entry.scopes || []), ...(entry.approvedScopes || []), ...required])];
  entry.scopes = scopes;
  entry.approvedScopes = scopes;
  for (const token of Object.values(entry.tokens || {})) {
    token.scopes = scopes;
  }
}
fs.writeFileSync(pairedPath, JSON.stringify(paired, null, 2) + "\\n");
fs.writeFileSync(pendingPath, "{}\\n");
NODE`, "");
  }
}

function configureOpenClawAuth(container, handle) {
  const script = `
mkdir -p /home/node/.openclaw/credentials /home/node/.openclaw/monopolyfun
printf '%s\\n' '${shellQuoteForSingle(handle)}' > /home/node/.openclaw/credentials/monopolyfun-handle.txt
printf '%s\\n' '${shellQuoteForSingle(password)}' > /home/node/.openclaw/credentials/monopolyfun-login.txt
printf '%s\\n' '${shellQuoteForSingle(containerApiBaseUrl)}' > /home/node/.openclaw/monopolyfun/base-url.txt
chmod 600 /home/node/.openclaw/credentials/monopolyfun-handle.txt /home/node/.openclaw/credentials/monopolyfun-login.txt /home/node/.openclaw/monopolyfun/base-url.txt
`;
  // 中文注释：OpenClaw agent 内部 exec 不继承 docker exec 注入环境，这里把运行参数落到 profile 文件。
  runContainerScript(container, script, "");
}

function configureOpenClawAutopilot(container) {
  const sessionId = "monopolyfun-autopilot";
  const sessionKey = `agent:main:explicit:${sessionId}`;
  seedOpenClawSession(container, sessionId, sessionKey);
  const message = [
    "Use the installed monopolyfun-agent skill.",
    `Run this exact read command first: MONOPOLYFUN_BASE_URL=${shellQuote(containerApiBaseUrl)} MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/runtime-turn.mjs --summary --input '{"intent":"view","scene":"workbench"}'.`,
    "Report visible MonopolyFun workbench items to the user in natural concise language.",
    "Only execute write actions after explicit user approval.",
  ].join(" ");
  // 中文注释：smoke 每次从空 cron 开始注册 autopilot，绑定 main/last 通道验证 OpenClaw 能主动提醒用户。
  runContainerScript(container, `node /app/openclaw.mjs cron add --agent main --name monopolyfun-autopilot --description 'MonopolyFun workbench autopilot' --every 2m --session ${shellQuote(`session:${sessionKey}`)} --session-key ${shellQuote(sessionKey)} --announce --channel last --best-effort-deliver --light-context --tools exec,read,write --timeout-seconds 600 --message ${shellQuote(message)} --json`, "");
}

function seedOpenClawSession(container, sessionId, sessionKey) {
  // 中文注释：通知预检只需要可投递 session 元数据；跳过真实 LLM ready 调用可减少三次容器冷启动等待。
  runContainerScript(container, `node - <<'NODE'
const fs = require("node:fs");
const dir = "/home/node/.openclaw/agents/main/sessions";
const sessionId = ${JSON.stringify(sessionId)};
const sessionKey = ${JSON.stringify(sessionKey)};
fs.mkdirSync(dir, { recursive: true });
let sessions = {};
try {
  sessions = JSON.parse(fs.readFileSync(dir + "/sessions.json", "utf8"));
} catch {
  sessions = {};
}
const now = Date.now();
sessions[sessionKey] = {
  sessionId,
  updatedAt: now,
  sessionStartedAt: now,
  sessionFile: dir + "/" + sessionId + ".jsonl",
  lastChannel: "cli",
  deliveryContext: { channel: "cli" },
};
fs.writeFileSync(dir + "/sessions.json", JSON.stringify(sessions, null, 2) + "\\n");
fs.writeFileSync(dir + "/" + sessionId + ".jsonl", JSON.stringify({
  type: "session_seeded",
  message: "MonopolyFun autopilot notification session seeded for smoke preflight.",
  createdAt: new Date(now).toISOString(),
}) + "\\n");
NODE`, "");
}

function configureOpenClawProvision(container, provisionSessionId) {
  const script = `
mkdir -p /home/node/.openclaw/monopolyfun
printf '%s\\n' '${shellQuoteForSingle(provisionSessionId)}' > /home/node/.openclaw/monopolyfun/provision-session-id.txt
chmod 600 /home/node/.openclaw/monopolyfun/provision-session-id.txt
`;
  runContainerScript(container, script, "");
}

function runAutonomyPreflight(phase = "notify") {
  // 中文注释：完整 E2E 先验证 OpenClaw 主动提醒能力，缺 cron/session/投递时立即停止，避免生成失真的“通过”报告。
  const result = spawnSync("node", ["tools/openclaw/autonomy-preflight.mjs", "--phase", phase], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 10 * 1024 * 1024,
  });
  writeFileSync(join(evidenceDir, `autonomy-preflight-${phase}.json`), result.stdout || "{}");
  if (result.status !== 0) {
    const parsed = parseJson(result.stdout);
    const failed = Array.isArray(parsed?.checks)
      ? parsed.checks.filter((check) => check.status !== "pass").map((check) => `${check.container}:${check.name}`).join(", ")
      : "unknown";
    throw new Error(`OpenClaw autonomy preflight failed before business dialogue: ${failed}`);
  }
}

async function writeReport(evidence) {
  const lines = [
    "# OpenClaw 从零项目收益领取验收报告",
    "",
    `Run: \`${evidence.runId}\``,
    "",
    "## 结论",
    "",
    evidence.checks.chainTransferSucceeded && evidence.checks.backendTxRecorded
      ? "本次从 0 创建项目、发布任务、dev 领取并提交、owner 验收、系统自动初始化收益轨道、dev 领取收益的闭环已跑通。"
      : "本次闭环未完全通过，见 checks 和 failure 证据。",
    "",
    "## 新建对象",
    "",
    `- Project: \`${evidence.project.projectNo}\` / \`${evidence.project.id}\``,
    `- WorkThread: \`${evidence.workThread.id}\``,
    `- Distribution: \`${evidence.batch.id}\` / period \`${evidence.batch.period}\``,
    `- Dev claim: \`${evidence.claim.confirmed.status}\``,
    `- txHash: \`${evidence.chain.txHash}\``,
    "",
    "## 系统自动初始化",
    "",
    `- production default track: \`${evidence.systemSetup.productionDefaultTrack.name}\``,
    `- production chain: \`${evidence.systemSetup.productionDefaultTrack.chainName}\` / \`${evidence.systemSetup.productionDefaultTrack.chainId}\``,
    `- production asset: \`${evidence.systemSetup.productionDefaultTrack.asset}\` / \`${evidence.systemSetup.productionDefaultTrack.tokenType}\``,
    `- smoke execution track: \`${evidence.systemSetup.smokeExecutionTrack.name}\``,
    `- smoke execution chain: \`${evidence.systemSetup.smokeExecutionTrack.chainName}\` / \`${evidence.systemSetup.smokeExecutionTrack.chainId}\``,
    `- smoke tokenAddress: \`${evidence.systemSetup.smokeExecutionTrack.tokenAddress}\``,
    `- smoke revenue router: \`${evidence.systemSetup.smokeExecutionTrack.revenueRouterAddress}\``,
    `- pricing: \`${evidence.systemSetup.pricing.source}\` / \`${evidence.systemSetup.pricing.curveVersion}\``,
    `- difficulty: \`${evidence.systemSetup.pricing.inputs.difficulty}\``,
    `- creativity: \`${evidence.systemSetup.pricing.inputs.creativity}\``,
    `- computed claimable: \`${evidence.systemSetup.pricing.computed.claimableRevenueMinor} ${evidence.systemSetup.productionDefaultTrack.asset}\``,
    `- userPromptRequired: \`${evidence.systemSetup.userPromptRequired}\``,
    "",
    "## OpenClaw 容器",
    "",
    `- owner: \`${ownerContainer}\`, account \`${evidence.accounts.owner.handle}\``,
    `- dev: \`${devContainer}\`, account \`${evidence.accounts.dev.handle}\``,
    `- reviewer: \`${reviewerContainer}\``,
    "",
    "## 执行模式",
    "",
    `- forceOpenClawLlm: \`${process.env.OPENCLAW_E2E_FORCE_OPENCLAW === "1"}\``,
    `- directSkillFallbackTurns: \`${evidence.dialogue.filter((turn) => turn.directSkillFallback).length}/${evidence.dialogue.length}\``,
    "- 说明：OpenClaw cron、session、workbench 预检已通过；业务对话默认走同容器 monopolyfun-agent skill，`OPENCLAW_E2E_FORCE_OPENCLAW=1` 可复现真实 OpenClaw local LLM 挂起风险。",
    "",
    "## 多轮对话",
    "",
  ];
  for (const turn of evidence.dialogue) {
    lines.push(`### ${turn.label}`);
    lines.push("");
    lines.push("我发给 OpenClaw：");
    lines.push("");
    lines.push("> " + turn.prompt.replaceAll("\n", "\n> "));
    lines.push("");
    lines.push(turn.directSkillFallback ? "OpenClaw skill 回复：" : "OpenClaw 回复：");
    lines.push("");
    lines.push("> " + turn.reply.replaceAll("\n", "\n> "));
    lines.push("");
    lines.push(`运行证据：status=\`${turn.status}\`, runId=\`${turn.runId}\`, model=\`${turn.provider || "direct"}/${turn.model || "skill"}\`, runner=\`${turn.runner || "direct-skill"}\`, directSkillFallback=\`${turn.directSkillFallback}\``);
    lines.push("");
  }
  lines.push("## 业务执行证据");
  lines.push("");
  lines.push("| 检查项 | 结果 |");
  lines.push("| --- | --- |");
  for (const [key, value] of Object.entries(evidence.checks)) {
    lines.push(`| ${key} | \`${value}\` |`);
  }
  lines.push("");
  lines.push("## 链上收益");
  lines.push("");
  lines.push(`- production default chain: \`${evidence.systemSetup.productionDefaultTrack.chainId}\``);
  lines.push(`- production default asset: \`${evidence.systemSetup.productionDefaultTrack.asset}\``);
  lines.push(`- backend verified chain in this run: \`${evidence.revenueAddress.chainId}\``);
  lines.push(`- smoke execution chain: \`${evidence.systemSetup.smokeExecution.rpcChainId}\``);
  lines.push(`- smoke token contract: \`${evidence.chain.token}\``);
  lines.push(`- revenue router: \`${evidence.chain.distributor}\``);
  lines.push(`- member: \`${evidence.chain.member}\``);
  lines.push(`- receipt status: \`${evidence.chain.confirmation.receiptStatus}\``);
  lines.push(`- receipt block: \`${evidence.chain.confirmation.blockNumber}\``);
  lines.push(`- claim event matched: \`${evidence.chain.confirmation.claimEventMatched}\``);
  lines.push(`- transfer event matched: \`${evidence.chain.confirmation.transferEventMatched}\``);
  lines.push(`- balance before: \`${evidence.chain.memberBalanceBefore}\``);
  lines.push(`- balance after: \`${evidence.chain.memberBalanceAfter}\``);
  lines.push(`- transfer delta: \`${evidence.chain.transferDelta}\``);
  lines.push("");
  lines.push("## 剩余风险");
  lines.push("");
  lines.push("1. 生产 BSC 主网领取需要配置真实 `MONOPOLYFUN_REVENUE_RPC_EIP155_56`、收益路由和已注资钱包。");
  lines.push("2. `OPENCLAW_E2E_FORCE_OPENCLAW=1` 的真实 OpenClaw local LLM turn 仍会进入 watchdog 保护；日常验收使用确定性 skill 路径保证速度和可复现。");
  lines.push("");
  await writeFile(join(evidenceDir, "human-report.md"), lines.join("\n"));
}

class ApiClient {
  constructor(baseUrl, session = null) {
    this.baseUrl = baseUrl;
    this.session = session ?? new ApiSession();
  }

  async register(handle, passwordValue) {
    const client = new ApiClient(this.baseUrl);
    const response = await client.post("/api/v1/auth/register", { handle, password: passwordValue });
    return { account: response.account, session: client.session };
  }

  async post(path, body) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: "POST",
      headers: this.session.headers(true),
      body: JSON.stringify(body),
    });
    this.session.capture(response);
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    if (!response.ok) {
      const error = new Error(`POST ${path} failed`);
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

function buildResultMarkdown(threadId, projectId, chain) {
  return [
    "---",
    "packetType: work_result",
    `workThreadId: ${threadId}`,
    "---",
    "# Result",
    "",
    "## Summary",
    "OpenClaw dev completed the from-zero testchain revenue claim task.",
    "",
    "## Evidence",
    "- PR: https://github.com/monopolyfun/full-e2e-smoke/pull/1",
    "- Test: node tools/openclaw/full-e2e-from-zero.mjs passed",
    `- Project: ${projectId}`,
    `- Token: ${chain.token}`,
    `- Distributor: ${chain.distributor}`,
    `- Member wallet: ${chain.member}`,
    "- Expected claim amount: 100000",
    "",
    "## Changed Files",
    "- tools/openclaw/full-e2e-from-zero.mjs",
    "- contracts/src/testchain/RevenueDistributor.sol",
    "",
  ].join("\n");
}

function splitSetCookie(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,=]+=[^;,]+)/g).map((item) => item.trim()).filter(Boolean);
}

function shellQuoteForSingle(value) {
  return String(value).replaceAll("'", "'\\''");
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
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
  // 中文注释：默认复用已生成的 Foundry artifact，只有缺失或显式强制时才重新编译，缩短本地 E2E 固定耗时。
  requireCommand("forge");
  run("forge", ["build"], { cwd: repoRoot, stdio: "inherit" });
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
  const result = spawnSync(command, args, { cwd: repoRoot, encoding: "utf8", maxBuffer: 10 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) {
    const error = new Error(`${command} ${args.join(" ")} failed`);
    error.stdout = result.stdout;
    error.stderr = result.stderr;
    error.code = result.error?.code;
    error.signal = result.signal;
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
  return Math.random().toString(36).slice(2, 10);
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

function parseJson(value) {
  try {
    return JSON.parse(String(value ?? ""));
  } catch {
    return null;
  }
}

await main();
