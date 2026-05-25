"use client";

import {useMemo, useState, useSyncExternalStore, useTransition, type ReactNode} from "react";
import {Banknote, CheckCircle2, ClipboardList, GitPullRequest, Hand, RefreshCcw, Send, WalletCards, XCircle} from "lucide-react";

import {Button} from "@/components/ui/button";
import {useToast} from "@/components/ui/toast";
import {
  claimDistribution,
  claimWorkThread,
  createDistributionBatch,
  createWorkThread,
  getProjectWorkroom,
  reviewWorkThread,
  submitWorkThreadResult,
  upsertProjectRevenueAddress,
  type WorkThread,
  type WorkThreadOverview,
} from "@/lib/api";
import {readStoredSession, subscribeSession} from "@/lib/client-preferences";
import {cn} from "@/lib/utils";

type DraftMap = Record<string, Record<string, string>>;

const statusLabels: Record<string, string> = {
  open: "可领取",
  running: "进行中",
  submitted: "待验收",
  settled: "已结算",
  rejected: "已拒绝",
};

export function ProjectWorkroomPanel({projectNo, initialOverview}: { projectNo: string; initialOverview: WorkThreadOverview | null }) {
  const toast = useToast();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [isPending, startTransition] = useTransition();
  const [overview, setOverview] = useState(initialOverview);
  const [createDraft, setCreateDraft] = useState({
    title: "",
    goal: "",
    deliverables: "PR link\nTest summary\nChanged files",
    acceptanceCriteria: "PR merged or ready for review\nTests pass\nScope matches task",
    taskValue: "3000",
    bountyAmountMinor: "0",
    bountyToken: "USDC",
    repoRef: "",
    issueUrl: "",
  });
  const [resultDrafts, setResultDrafts] = useState<DraftMap>({});
  const [reviewDrafts, setReviewDrafts] = useState<DraftMap>({});
  const [revenueDraft, setRevenueDraft] = useState({chainId: "8453", contractAddress: "", tokenAddress: ""});
  const [distributionDraft, setDistributionDraft] = useState({period: currentPeriod(), totalRevenueMinor: ""});
  const [walletDrafts, setWalletDrafts] = useState<DraftMap>({});

  const stats = useMemo(() => {
    const threads = overview?.workThreads ?? [];
    return {
      open: threads.filter((thread) => thread.status === "open").length,
      running: threads.filter((thread) => thread.status === "running").length,
      submitted: threads.filter((thread) => thread.status === "submitted").length,
      settled: threads.filter((thread) => thread.status === "settled").length,
    };
  }, [overview?.workThreads]);

  function load() {
    startTransition(async () => {
      try {
        setOverview(await getProjectWorkroom(projectNo));
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function createThread() {
    const taskValue = Number(createDraft.taskValue);
    if (!createDraft.title.trim() || !createDraft.goal.trim() || !Number.isFinite(taskValue) || taskValue < 1 || taskValue > 10000) {
      toast.notify({tone: "error", title: "请填写标题、目标和 1-10000 的任务价值。"});
      return;
    }
    startTransition(async () => {
      try {
        await createWorkThread(projectNo, {
          title: createDraft.title.trim(),
          goal: createDraft.goal.trim(),
          deliverables: lines(createDraft.deliverables),
          acceptanceCriteria: lines(createDraft.acceptanceCriteria),
          taskValue,
          bountyAmountMinor: Number(createDraft.bountyAmountMinor || 0),
          bountyToken: createDraft.bountyToken.trim() || "USDC",
          repoRef: createDraft.repoRef.trim() || undefined,
          issueUrl: createDraft.issueUrl.trim() || undefined,
        });
        setCreateDraft((current) => ({...current, title: "", goal: "", issueUrl: ""}));
        await load();
        toast.notify({tone: "success", title: "任务已发布"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function claim(thread: WorkThread) {
    startTransition(async () => {
      try {
        await claimWorkThread(thread.id);
        await load();
        toast.notify({tone: "success", title: "任务已领取"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function submitResult(thread: WorkThread) {
    const draft = resultDrafts[thread.id] ?? {};
    const changedFiles = lines(draft.changedFiles);
    if (!draft.summary?.trim() || !draft.prUrl?.trim() || !draft.testSummary?.trim() || changedFiles.length === 0) {
      toast.notify({tone: "error", title: "请填写摘要、PR、测试和文件列表。"});
      return;
    }
    startTransition(async () => {
      try {
        await submitWorkThreadResult(thread.id, {
          summary: draft.summary.trim(),
          prUrl: draft.prUrl.trim(),
          testSummary: draft.testSummary.trim(),
          changedFiles,
          evidenceRefs: lines(draft.evidenceRefs),
        });
        setResultDrafts((current) => ({...current, [thread.id]: {}}));
        await load();
        toast.notify({tone: "success", title: "结果已提交"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function review(thread: WorkThread, decision: "accept" | "resubmit" | "reject") {
    const draft = reviewDrafts[thread.id] ?? {};
    const reason = draft.reason?.trim() || (decision === "accept" ? "Result matches acceptance criteria" : "Please resubmit with requested evidence");
    startTransition(async () => {
      try {
        await reviewWorkThread(thread.id, {decision, reason});
        setReviewDrafts((current) => ({...current, [thread.id]: {}}));
        await load();
        toast.notify({tone: "success", title: decision === "accept" ? "股份已结算" : "验收已记录"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function saveRevenueAddress() {
    startTransition(async () => {
      try {
        await upsertProjectRevenueAddress(projectNo, revenueDraft);
        await load();
        toast.notify({tone: "success", title: "收款地址已保存"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function createDistribution() {
    const amount = Number(distributionDraft.totalRevenueMinor);
    if (!distributionDraft.period.trim() || !Number.isFinite(amount) || amount <= 0) {
      toast.notify({tone: "error", title: "请填写周期和收入金额。"});
      return;
    }
    startTransition(async () => {
      try {
        await createDistributionBatch(projectNo, {period: distributionDraft.period.trim(), totalRevenueMinor: amount});
        setDistributionDraft((current) => ({...current, totalRevenueMinor: ""}));
        await load();
        toast.notify({tone: "success", title: "分红批次已发布"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function claimRevenue(period: string) {
    const draft = walletDrafts[period] ?? {};
    if (!draft.walletAddress?.trim()) {
      toast.notify({tone: "error", title: "请填写领取钱包。"});
      return;
    }
    startTransition(async () => {
      try {
        await claimDistribution(projectNo, period, {walletAddress: draft.walletAddress.trim(), txHash: draft.txHash?.trim() || undefined});
        await load();
        toast.notify({tone: "success", title: "Claim 已生成"});
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  if (!session) {
    return (
      <section className="rounded-[12px] bg-[var(--surface)] p-4 text-sm text-[var(--muted-foreground)]">
        登录后进入 Workroom，领取任务、提交结果、结算股份和领取分红。
      </section>
    );
  }

  const threads = overview?.workThreads ?? [];
  const owner = Boolean(overview?.owner);

  return (
    <section className="space-y-4">
      <div className="grid gap-2 md:grid-cols-5">
        <Metric icon={<ClipboardList className="h-4 w-4" />} label="可领取" value={stats.open} />
        <Metric icon={<Hand className="h-4 w-4" />} label="进行中" value={stats.running} />
        <Metric icon={<GitPullRequest className="h-4 w-4" />} label="待验收" value={stats.submitted} />
        <Metric icon={<CheckCircle2 className="h-4 w-4" />} label="已结算" value={stats.settled} />
        <Metric icon={<WalletCards className="h-4 w-4" />} label="我的股份" value={overview?.myRewards.totalShares ?? 0} />
      </div>

      <div className="flex justify-end">
        <Button size="sm" variant="outline" onClick={load} loading={isPending}>
          <RefreshCcw className="h-4 w-4" />
          刷新
        </Button>
      </div>

      {owner ? (
        <Panel title="发布 GitHub Bounty" icon={<ClipboardList className="h-4 w-4" />}>
          <div className="grid gap-3 lg:grid-cols-2">
            <Field label="任务标题" value={createDraft.title} onChange={(value) => setCreateDraft((current) => ({...current, title: value}))} />
            <Field label="Task Value 1-10000" value={createDraft.taskValue} onChange={(value) => setCreateDraft((current) => ({...current, taskValue: value}))} />
            <TextField label="目标" value={createDraft.goal} onChange={(value) => setCreateDraft((current) => ({...current, goal: value}))} />
            <TextField label="交付物" value={createDraft.deliverables} onChange={(value) => setCreateDraft((current) => ({...current, deliverables: value}))} />
            <TextField label="验收标准" value={createDraft.acceptanceCriteria} onChange={(value) => setCreateDraft((current) => ({...current, acceptanceCriteria: value}))} />
            <div className="grid gap-3">
              <Field label="Repo Ref" value={createDraft.repoRef} onChange={(value) => setCreateDraft((current) => ({...current, repoRef: value}))} />
              <Field label="Issue URL" value={createDraft.issueUrl} onChange={(value) => setCreateDraft((current) => ({...current, issueUrl: value}))} />
              <Field label="Bounty Minor" value={createDraft.bountyAmountMinor} onChange={(value) => setCreateDraft((current) => ({...current, bountyAmountMinor: value}))} />
            </div>
          </div>
          <div className="mt-3 flex justify-end">
            <Button variant="primary" onClick={createThread} loading={isPending}>
              <Send className="h-4 w-4" />
              发布任务
            </Button>
          </div>
        </Panel>
      ) : null}

      <Panel title="任务池" icon={<Hand className="h-4 w-4" />}>
        <div className="grid gap-3">
          {threads.length ? threads.map((thread) => (
            <ThreadRow
              key={thread.id}
              thread={thread}
              currentAccountId={session.accountId ?? ""}
              owner={owner}
              isPending={isPending}
              resultDraft={resultDrafts[thread.id] ?? {}}
              reviewDraft={reviewDrafts[thread.id] ?? {}}
              onClaim={() => claim(thread)}
              onSubmit={() => submitResult(thread)}
              onReview={(decision) => review(thread, decision)}
              onResultDraft={(patch) => setResultDrafts((current) => ({...current, [thread.id]: {...(current[thread.id] ?? {}), ...patch}}))}
              onReviewDraft={(patch) => setReviewDrafts((current) => ({...current, [thread.id]: {...(current[thread.id] ?? {}), ...patch}}))}
            />
          )) : (
            <div className="rounded-[8px] bg-[var(--surface-control)] p-3 text-sm text-[var(--muted-foreground)]">当前项目暂无 WorkThread。</div>
          )}
        </div>
      </Panel>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Panel title="股份与分红" icon={<Banknote className="h-4 w-4" />}>
          <div className="grid gap-2 md:grid-cols-3">
            <Metric label="Claimable" value={formatMinor(overview?.myRewards.claimableAmountMinor ?? 0, overview?.myRewards.claimableToken ?? "USDC")} />
            <Metric label="Bounty" value={formatMinor(overview?.myRewards.bountyAmountMinor ?? 0, overview?.myRewards.bountyToken ?? "USDC")} />
            <Metric label="收款地址" value={overview?.revenueAddress?.contractAddress ? shortAddress(overview.revenueAddress.contractAddress) : "待设置"} />
          </div>

          {owner ? (
            <div className="mt-4 grid gap-3 lg:grid-cols-2">
              <div className="rounded-[8px] bg-[var(--surface-control)] p-3">
                <div className="text-xs font-semibold text-[var(--foreground)]">项目收款地址</div>
                <div className="mt-3 grid gap-2">
                  <Field label="Chain ID" value={revenueDraft.chainId} onChange={(value) => setRevenueDraft((current) => ({...current, chainId: value}))} />
                  <Field label="收款地址" value={revenueDraft.contractAddress} onChange={(value) => setRevenueDraft((current) => ({...current, contractAddress: value}))} />
                  <Field label="Token Address" value={revenueDraft.tokenAddress} onChange={(value) => setRevenueDraft((current) => ({...current, tokenAddress: value}))} />
                  <Button size="sm" onClick={saveRevenueAddress} loading={isPending}>保存地址</Button>
                </div>
              </div>
              <div className="rounded-[8px] bg-[var(--surface-control)] p-3">
                <div className="text-xs font-semibold text-[var(--foreground)]">发布分红批次</div>
                <div className="mt-3 grid gap-2">
                  <Field label="Period" value={distributionDraft.period} onChange={(value) => setDistributionDraft((current) => ({...current, period: value}))} />
                  <Field label="Revenue Minor" value={distributionDraft.totalRevenueMinor} onChange={(value) => setDistributionDraft((current) => ({...current, totalRevenueMinor: value}))} />
                  <Button size="sm" onClick={createDistribution} loading={isPending}>发布分红</Button>
                </div>
              </div>
            </div>
          ) : null}

          <div className="mt-4 grid gap-2">
            {(overview?.distributions ?? []).map((batch) => (
              <div key={batch.id} className="grid gap-3 rounded-[8px] bg-[var(--surface-control)] p-3 lg:grid-cols-[minmax(0,1fr)_minmax(220px,320px)] lg:items-center">
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-[var(--foreground)]">{batch.period}</div>
                  <div className="mt-1 text-xs text-[var(--muted-foreground)]">
                    收入 {formatMinor(batch.totalRevenueMinor, batch.token)} · 快照 {batch.totalSnapshotShares.toLocaleString()} shares · 可领 {formatMinor(batch.myClaimableAmountMinor, batch.token)}
                  </div>
                </div>
                <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                  <Field label="钱包" value={walletDrafts[batch.period]?.walletAddress ?? ""} onChange={(value) => setWalletDrafts((current) => ({...current, [batch.period]: {...(current[batch.period] ?? {}), walletAddress: value}}))} />
                  <Button size="sm" onClick={() => claimRevenue(batch.period)} loading={isPending} disabled={batch.myClaimableAmountMinor <= 0}>Claim</Button>
                </div>
              </div>
            ))}
          </div>
        </Panel>

        <Panel title="Cap Table" icon={<WalletCards className="h-4 w-4" />}>
          <div className="grid gap-2">
            {(overview?.contributors ?? []).map((member) => (
              <div key={member.accountId} className="rounded-[8px] bg-[var(--surface-control)] p-3">
                <div className="truncate text-sm font-semibold text-[var(--foreground)]">{member.accountId}</div>
                <div className="mt-2 grid grid-cols-3 gap-2 text-xs text-[var(--muted-foreground)]">
                  <span>{member.totalShares.toLocaleString()} shares</span>
                  <span>{member.settledCount} tasks</span>
                  <span>{formatMinor(member.bountyAmountMinor, member.bountyToken)}</span>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </section>
  );
}

function ThreadRow({
  thread,
  currentAccountId,
  owner,
  isPending,
  resultDraft,
  reviewDraft,
  onClaim,
  onSubmit,
  onReview,
  onResultDraft,
  onReviewDraft,
}: {
  thread: WorkThread;
  currentAccountId: string;
  owner: boolean;
  isPending: boolean;
  resultDraft: Record<string, string>;
  reviewDraft: Record<string, string>;
  onClaim: () => void;
  onSubmit: () => void;
  onReview: (decision: "accept" | "resubmit" | "reject") => void;
  onResultDraft: (patch: Record<string, string>) => void;
  onReviewDraft: (patch: Record<string, string>) => void;
}) {
  const assignee = thread.assigneeAccountId === currentAccountId;
  return (
    <div className="rounded-[8px] bg-[var(--surface-control)] p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-[var(--foreground)]">{thread.title}</span>
            <span className={cn("rounded-full px-2 py-0.5 text-[11px]", thread.status === "submitted" ? "bg-[rgba(238,185,73,0.15)] text-[var(--accent-yellow)]" : "bg-[var(--background)] text-[var(--muted-foreground)]")}>
              {statusLabels[thread.status] ?? thread.status}
            </span>
          </div>
          <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">
            Task Value {thread.taskValue.toLocaleString()} · Bounty {formatMinor(thread.bountyAmountMinor, thread.bountyToken)} · {thread.threadNo}
          </div>
        </div>
        {thread.status === "open" ? (
          <Button size="sm" onClick={onClaim} loading={isPending}>
            <Hand className="h-4 w-4" />
            领取
          </Button>
        ) : null}
      </div>

      <p className="mt-3 whitespace-pre-line text-sm leading-6 text-[var(--muted-foreground)]">{thread.goal}</p>

      {thread.latestResult ? (
        <a href={thread.latestResult.prUrl} target="_blank" rel="noreferrer" className="mt-3 inline-flex items-center gap-2 text-xs font-semibold text-[var(--accent-blue)] hover:underline">
          <GitPullRequest className="h-3.5 w-3.5" />
          {thread.latestResult.summary}
        </a>
      ) : null}

      {thread.status === "running" && assignee ? (
        <div className="mt-3 grid gap-2 lg:grid-cols-2">
          <TextField label="结果摘要" value={resultDraft.summary ?? ""} onChange={(value) => onResultDraft({summary: value})} />
          <Field label="PR URL" value={resultDraft.prUrl ?? ""} onChange={(value) => onResultDraft({prUrl: value})} />
          <Field label="测试说明" value={resultDraft.testSummary ?? ""} onChange={(value) => onResultDraft({testSummary: value})} />
          <TextField label="修改文件" value={resultDraft.changedFiles ?? ""} onChange={(value) => onResultDraft({changedFiles: value})} />
          <div className="lg:col-span-2 flex justify-end">
            <Button size="sm" variant="primary" onClick={onSubmit} loading={isPending}>
              <Send className="h-4 w-4" />
              提交结果
            </Button>
          </div>
        </div>
      ) : null}

      {thread.status === "submitted" && owner ? (
        <div className="mt-3 grid gap-2">
          <TextField label="验收说明" value={reviewDraft.reason ?? ""} onChange={(value) => onReviewDraft({reason: value})} />
          <div className="flex flex-wrap justify-end gap-2">
            <Button size="sm" variant="primary" onClick={() => onReview("accept")} loading={isPending}>
              <CheckCircle2 className="h-4 w-4" />
              通过
            </Button>
            <Button size="sm" variant="outline" onClick={() => onReview("resubmit")} loading={isPending}>
              <RefreshCcw className="h-4 w-4" />
              重新提交
            </Button>
            <Button size="sm" variant="danger" onClick={() => onReview("reject")} loading={isPending}>
              <XCircle className="h-4 w-4" />
              拒绝
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function Panel({title, icon, children}: { title: string; icon: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-[12px] bg-[var(--background)] p-4">
      <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--foreground)]">
        {icon}
        {title}
      </div>
      {children}
    </section>
  );
}

function Metric({label, value, icon}: { label: string; value: string | number; icon?: ReactNode }) {
  return (
    <div className="rounded-[8px] bg-[var(--background)] p-3">
      <div className="flex items-center gap-2 text-[11px] font-semibold text-[var(--muted-foreground)]">
        {icon}
        {label}
      </div>
      <div className="mt-2 truncate text-sm font-semibold text-[var(--foreground)]">{value}</div>
    </div>
  );
}

function Field({label, value, onChange}: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="grid gap-1 text-xs font-semibold text-[var(--muted-foreground)]">
      {label}
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 rounded-[8px] border border-[var(--border)] bg-[var(--background)] px-3 text-sm font-normal text-[var(--foreground)] outline-none focus:ring-2 focus:ring-[var(--ring)]"
      />
    </label>
  );
}

function TextField({label, value, onChange}: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="grid gap-1 text-xs font-semibold text-[var(--muted-foreground)]">
      {label}
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        rows={4}
        className="min-h-24 rounded-[8px] border border-[var(--border)] bg-[var(--background)] px-3 py-2 text-sm font-normal leading-6 text-[var(--foreground)] outline-none focus:ring-2 focus:ring-[var(--ring)]"
      />
    </label>
  );
}

function lines(value: string | undefined) {
  return (value ?? "").split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

function formatMinor(value: number, token: string) {
  return `${(value / 100).toLocaleString(undefined, {maximumFractionDigits: 2})} ${token}`;
}

function shortAddress(value: string) {
  return value.length > 12 ? `${value.slice(0, 6)}...${value.slice(-4)}` : value;
}

function currentPeriod() {
  return new Date().toISOString().slice(0, 7);
}
