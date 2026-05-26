#!/usr/bin/env node

import {
  DEFAULT_AGENT_ID,
  DEFAULT_JOB_ID,
  appendJsonl,
  findCronJob,
  listCronJobs,
  resolveOfficialDeliveryRoute,
  loadSessions,
  resolvePreferredReportSession,
  resolveAdapterDir,
  resolveOpenClawHome,
  runOpenClawCron,
  scriptPaths,
  sessionDigest,
} from "./openclaw-adapter-lib.mjs";
import { buildFailurePayload, formatHelp, parseArgs, printJson, readOption } from "./runtime-session.mjs";

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/openclaw-session-bind.mjs [--job monopolyfun-autopilot] [--agent main]",
    "",
    "options:",
    "  --job             cron job id or name, default monopolyfun-autopilot",
    "  --agent           OpenClaw agent id, default main",
    "  --openclaw-home   OpenClaw home, default ~/.openclaw",
    "  --openclaw-bin    OpenClaw CLI binary, default openclaw",
    "  --url             Gateway websocket url",
    "  --token           Gateway token",
    "  --dry-run         only print selected session",
  ]));
  process.exit(0);
}

try {
  const { skillDir } = scriptPaths(import.meta.url);
  const openclawHome = resolveOpenClawHome(flags);
  const adapterDir = resolveAdapterDir(openclawHome);
  const agentId = readOption(flags, "agent", { defaultValue: DEFAULT_AGENT_ID });
  const jobId = readOption(flags, "job", { defaultValue: DEFAULT_JOB_ID });
  const openclawBin = readOption(flags, "openclaw-bin", {
    envKeys: ["OPENCLAW_BIN"],
    defaultValue: "openclaw",
  });

  const { sessions } = await loadSessions(openclawHome, agentId);
  const latest = resolvePreferredReportSession(sessions, { agentId });
  const selected = sessionDigest(latest);
  const route = resolveOfficialDeliveryRoute(latest);
  if (!selected) {
    const payload = {
      ok: false,
      action: "blocked",
      reason: "no-deliverable-session",
      hint: "请先在 OpenClaw 任一可投递通道发送一条消息，再重新运行绑定。",
      checkedAt: new Date().toISOString(),
    };
    await appendJsonl(`${adapterDir}/session-bind.jsonl`, payload);
    printJson(payload);
    process.exit(2);
  }

  if (flags.has("dry-run")) {
    const payload = { ok: true, action: "dry-run", selected, route, checkedAt: new Date().toISOString() };
    await appendJsonl(`${adapterDir}/session-bind.jsonl`, payload);
    printJson(payload);
    process.exit(0);
  }

  const jobs = await listCronJobs({ openclawBin, flags });
  const job = findCronJob(jobs, jobId);
  if (!job) {
    const payload = {
      ok: false,
      action: "blocked",
      reason: "cron-job-missing",
      job: jobId,
      selected,
      checkedAt: new Date().toISOString(),
    };
    await appendJsonl(`${adapterDir}/session-bind.jsonl`, payload);
    printJson(payload);
    process.exit(3);
  }

  const delivery = job.delivery && typeof job.delivery === "object" ? job.delivery : {};
  const deliveryAligned = route.kind === "official"
    ? delivery.mode === "announce"
      && delivery.channel === route.channel
      && delivery.to === route.to
      && (route.accountId ? delivery.accountId === route.accountId : true)
      && delivery.bestEffort === true
    : !delivery.mode || delivery.mode === "none";
  const alreadyBound = job.sessionTarget === selected.sessionTarget
    && (job.sessionKey || "") === selected.sessionKey
    && deliveryAligned;
  if (!alreadyBound) {
    // 中文注释：官方支持的外部通道走 OpenClaw delivery，网页等内部会话保留上下文并关闭官方投递。
    const deliveryArgs = route.kind === "official"
      ? [
          "--announce",
          "--channel",
          route.channel,
          "--to",
          route.to,
          ...(route.accountId ? ["--account", route.accountId] : []),
          "--best-effort-deliver",
        ]
      : ["--no-deliver"];
    await runOpenClawCron({
      openclawBin,
      flags,
      args: [
        "edit",
        job.id,
        "--session",
        selected.sessionTarget,
        "--session-key",
        selected.sessionKey,
        ...deliveryArgs,
      ],
    });
  }

  const payload = {
    ok: true,
    action: alreadyBound ? "noop" : "rebind",
    jobId: job.id,
    skillDir,
    selected,
    route,
    previous: {
      sessionTarget: job.sessionTarget,
      sessionKey: job.sessionKey,
      delivery: job.delivery,
    },
    checkedAt: new Date().toISOString(),
  };
  await appendJsonl(`${adapterDir}/session-bind.jsonl`, payload);
  printJson(payload);
} catch (error) {
  const payload = buildFailurePayload(error, {
    status: "blocked",
    phase: "openclaw_session_bind",
  });
  printJson(payload);
  process.exit(1);
}
