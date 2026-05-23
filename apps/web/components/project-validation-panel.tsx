"use client";

import { useEffect, useMemo, useState, useSyncExternalStore, useTransition, type ReactNode } from "react";
import {
  Bug,
  CheckCircle2,
  ClipboardCheck,
  Hand,
  ListChecks,
  RefreshCcw,
  ShieldAlert,
  UploadCloud,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import {
  claimProjectValidationTask,
  listProjectValidationLaunches,
  listProjectValidationProofs,
  listProjectValidationRewards,
  listProjectValidationReviewQueue,
  listProjectValidationTasks,
  reviewProjectValidationProof,
  submitProjectValidationProof,
  type ValidationLaunch,
  type ValidationProof,
  type ValidationReviewQueueItem,
  type ValidationReward,
  type ValidationTask,
} from "@/lib/api";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";
import { cn } from "@/lib/utils";

type TextDraft = Record<string, string>;
type TaskKind = "normal" | "bug" | "review" | "dispute";
type ReviewResult = "accept" | "request_changes" | "hold";

type TaskWithLaunch = {
  task: ValidationTask;
  launch: ValidationLaunch;
  proofs: ValidationProof[];
  rewards: ValidationReward[];
};

const launchStatusLabels: Record<string, string> = {
  draft: "准备中",
  live: "任务列表",
  reviewing: "待验证",
  settled: "已结算",
};

const taskStatusLabels: Record<string, string> = {
  open: "可领取",
  claimed: "进行中",
  working: "进行中",
  proof_submitted: "待验证",
  accepted: "已确认",
  changes_requested: "需补充",
  settled: "虚拟股份已发放",
  cancelled: "已取消",
};

const rewardStatusLabels: Record<string, string> = {
  estimated: "预估",
  pending: "待发虚拟股份",
  settled: "虚拟股份已发放",
  held: "复核中",
};

const proofStatusLabels: Record<string, string> = {
  submitted: "待验证",
  accepted: "已确认",
  changes_requested: "需补充",
  held: "复核中",
};

const taskKindOptions: Array<{ value: TaskKind; label: string; icon: ReactNode; deliverable: string }> = [
  { value: "normal", label: "普通任务", icon: <ListChecks className="h-4 w-4" />, deliverable: "完成结果、PR、文档、数据或部署链接" },
  { value: "bug", label: "Bug 反馈", icon: <Bug className="h-4 w-4" />, deliverable: "复现步骤、影响范围、修复结果或截图链接" },
  { value: "review", label: "Review", icon: <ClipboardCheck className="h-4 w-4" />, deliverable: "Review 结论、问题清单、通过依据或修改意见" },
  { value: "dispute", label: "异议", icon: <ShieldAlert className="h-4 w-4" />, deliverable: "异议原因、关联任务、证据和处理结论" },
];

export function ProjectValidationPanel({ projectNo }: { projectNo: string }) {
  const toast = useToast();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [isPending, startTransition] = useTransition();
  const [launches, setLaunches] = useState<ValidationLaunch[]>([]);
  const [tasksByLaunch, setTasksByLaunch] = useState<Record<string, ValidationTask[]>>({});
  const [proofsByLaunch, setProofsByLaunch] = useState<Record<string, ValidationProof[]>>({});
  const [reviewQueue, setReviewQueue] = useState<ValidationReviewQueueItem[]>([]);
  const [rewards, setRewards] = useState<ValidationReward[]>([]);
  const [proofDrafts, setProofDrafts] = useState<Record<string, TextDraft>>({});
  const [reviewDrafts, setReviewDrafts] = useState<Record<string, TextDraft>>({});

  useEffect(() => {
    if (session) {
      void loadValidation();
    }
    // 中文注释：任务中心按当前登录身份读取任务、成果、验证和虚拟股份状态，保证列表和我的任务共用同一份事实。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accountId, projectNo]);

  const rewardsByLaunch = useMemo(() => {
    return rewards.reduce<Record<string, ValidationReward[]>>((acc, reward) => {
      acc[reward.launchId] = [...(acc[reward.launchId] ?? []), reward];
      return acc;
    }, {});
  }, [rewards]);

  const allTasks = useMemo<TaskWithLaunch[]>(() => {
    return launches.flatMap((launch) => {
      const launchProofs = proofsByLaunch[launch.id] ?? [];
      const launchRewards = rewardsByLaunch[launch.id] ?? [];
      return (tasksByLaunch[launch.id] ?? []).map((task) => ({
        task,
        launch,
        proofs: launchProofs.filter((proof) => proof.taskId === task.id),
        rewards: launchRewards.filter((reward) => reward.taskId === task.id),
      }));
    });
  }, [launches, proofsByLaunch, rewardsByLaunch, tasksByLaunch]);

  const myTaskGroups = useMemo(() => {
    const accountId = session?.accountId;
    if (!accountId) {
      return { claimed: [], created: [], submitted: [] } satisfies Record<string, TaskWithLaunch[]>;
    }
    return {
      claimed: allTasks.filter(({ task }) => task.claimedByAccountId === accountId),
      created: allTasks.filter(({ task }) => task.createdByAccountId === accountId),
      submitted: allTasks.filter(({ proofs }) => proofs.some((proof) => proof.submittedByAccountId === accountId)),
      };
  }, [allTasks, session?.accountId]);

  const myTaskStats = [
    { label: "我领取的", value: myTaskGroups.claimed.length },
    { label: "我创建的", value: myTaskGroups.created.length },
    { label: "我提交的", value: myTaskGroups.submitted.length },
    { label: "待我验证", value: reviewQueue.filter((item) => item.submittedByAccountId !== session?.accountId).length },
  ];

  if (!session) {
    // 中文注释：任务领取、成果提交和验证都需要账号身份，登录态缺失时给出单一入口说明。
    return (
      <section className="rounded-[12px] bg-[var(--surface)] p-4 text-sm text-[var(--muted-foreground)]">
        登录后查看我的任务、提交成果并处理待发虚拟股份。
      </section>
    );
  }

  function loadValidation() {
    startTransition(async () => {
      try {
        const nextLaunches = await listProjectValidationLaunches(projectNo);
        const [nextRewards, nextReviewQueue] = await Promise.all([
          listProjectValidationRewards(projectNo).catch(() => []),
          listProjectValidationReviewQueue(projectNo).catch(() => []),
        ]);
        const taskEntries = await Promise.all(nextLaunches.map(async (launch) => [launch.id, await listProjectValidationTasks(projectNo, launch.id).catch(() => [])] as const));
        const proofEntries = await Promise.all(nextLaunches.map(async (launch) => [launch.id, await listProjectValidationProofs(projectNo, launch.id).catch(() => [])] as const));
        setLaunches(nextLaunches);
        setTasksByLaunch(Object.fromEntries(taskEntries));
        setProofsByLaunch(Object.fromEntries(proofEntries));
        setReviewQueue(nextReviewQueue);
        setRewards(nextRewards);
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function claimTask(taskId: string) {
    startTransition(async () => {
      try {
        await claimProjectValidationTask(projectNo, taskId);
        await loadValidation();
        toast.notify({ tone: "success", title: "任务已领取" });
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function submitProof(task: ValidationTask) {
    const draft = proofDrafts[task.id] ?? {};
    const summary = draft.summary?.trim();
    if (!summary) {
      toast.notify({ tone: "error", title: "请填写成果摘要。" });
      return;
    }
    startTransition(async () => {
      try {
        await submitProjectValidationProof(projectNo, task.id, {
          summary,
          evidenceItems: [{
            kind: draft.kind?.trim() || "link",
            url: draft.url?.trim() || undefined,
            description: draft.description?.trim() || summary,
          }],
          linkedProofRequestIds: task.linkedProofRequestIds,
          notes: draft.notes?.trim() || undefined,
          metadata: { submittedFrom: "project_validation_panel" },
        });
        setProofDrafts((current) => ({ ...current, [task.id]: {} }));
        await loadValidation();
        toast.notify({ tone: "success", title: "成果待验证" });
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  function reviewProof(proofId: string, result: ReviewResult, validationMode: "ordinary" | "staked" = "ordinary") {
    const draft = reviewDrafts[proofId] ?? {};
    const stakedShares = Number(draft.stakedShares ?? 0);
    if (validationMode === "staked" && (!Number.isFinite(stakedShares) || stakedShares <= 0)) {
      toast.notify({ tone: "error", title: "请填写大于 0 的质押股份。" });
      return;
    }
    startTransition(async () => {
      try {
        await reviewProjectValidationProof(projectNo, proofId, {
          result,
          reason: draft.reason?.trim() || undefined,
          validationMode,
          stakedShares: validationMode === "staked" ? stakedShares : undefined,
          requestedEvidence: lines(draft.requestedEvidence).map((item) => ({ description: item })),
          riskFlags: lines(draft.riskFlags),
          scoreInputs: {
            source: "project_validation",
            result,
            validationMode,
          },
          metadata: { reviewedFrom: "project_validation_panel" },
        });
        setReviewDrafts((current) => ({ ...current, [proofId]: {} }));
        await loadValidation();
        toast.notify({ tone: "success", title: result === "accept" ? "验证已记录" : "补充要求已记录" });
      } catch (error) {
        toast.notifyError(error, "ui.agent.action.failed");
      }
    });
  }

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="grid grid-cols-2 gap-2 sm:flex">
          {myTaskStats.map((item) => (
            <span key={item.label} className="rounded-full bg-[var(--surface-2)] px-3 py-1.5 text-xs font-semibold text-[var(--muted-foreground)]">
              {item.label} <span className="text-[var(--foreground)]">{item.value}</span>
            </span>
          ))}
        </div>
        <Button type="button" variant="outline" size="sm" onClick={loadValidation} disabled={isPending} aria-label="刷新我的任务">
          <RefreshCcw className={cn("h-4 w-4", isPending ? "animate-spin" : null)} />
          刷新
        </Button>
      </div>
      <MyTaskBoard
        groups={myTaskGroups}
        reviewQueue={reviewQueue}
        proofDrafts={proofDrafts}
        reviewDrafts={reviewDrafts}
        currentAccountId={session.accountId}
        isPending={isPending}
        onClaim={claimTask}
        onProofDraftChange={(taskId, patch) => setProofDrafts((current) => ({ ...current, [taskId]: { ...(current[taskId] ?? {}), ...patch } }))}
        onSubmitProof={submitProof}
        onReviewDraftChange={(proofId, patch) => setReviewDrafts((current) => ({ ...current, [proofId]: { ...(current[proofId] ?? {}), ...patch } }))}
        onReview={reviewProof}
      />
    </section>
  );
}

function MyTaskBoard(props: {
  groups: { claimed: TaskWithLaunch[]; created: TaskWithLaunch[]; submitted: TaskWithLaunch[] };
  reviewQueue: ValidationReviewQueueItem[];
  proofDrafts: Record<string, TextDraft>;
  reviewDrafts: Record<string, TextDraft>;
  currentAccountId: string;
  isPending: boolean;
  onClaim: (taskId: string) => void;
  onProofDraftChange: (taskId: string, patch: TextDraft) => void;
  onSubmitProof: (task: ValidationTask) => void;
  onReviewDraftChange: (proofId: string, patch: TextDraft) => void;
  onReview: (proofId: string, result: ReviewResult, validationMode?: "ordinary" | "staked") => void;
}) {
  const reviewItems = props.reviewQueue.filter((item) => item.submittedByAccountId !== props.currentAccountId);
  const queues = buildUniqueTaskQueues(props.groups, props.currentAccountId, reviewItems);
  return (
    <div className="grid gap-3 lg:grid-cols-3">
      <TaskListSection
        title="需要我处理"
        items={queues.actionNeeded}
        proofDrafts={props.proofDrafts}
        reviewDrafts={props.reviewDrafts}
        currentAccountId={props.currentAccountId}
        isPending={props.isPending}
        emptyText="当前没有需要你立即处理的任务。"
        onClaim={props.onClaim}
        onProofDraftChange={props.onProofDraftChange}
        onSubmitProof={props.onSubmitProof}
        onReviewDraftChange={props.onReviewDraftChange}
        onReview={props.onReview}
      >
        <ReviewQueueBlock
          title="待我验证"
          items={reviewItems}
          drafts={props.reviewDrafts}
          currentAccountId={props.currentAccountId}
          isPending={props.isPending}
          onDraftChange={props.onReviewDraftChange}
          onReview={props.onReview}
        />
      </TaskListSection>
      <TaskListSection
        title="等待别人处理"
        items={queues.waiting}
        proofDrafts={props.proofDrafts}
        reviewDrafts={props.reviewDrafts}
        currentAccountId={props.currentAccountId}
        isPending={props.isPending}
        emptyText="当前没有等待中的任务。"
        onClaim={props.onClaim}
        onProofDraftChange={props.onProofDraftChange}
        onSubmitProof={props.onSubmitProof}
        onReviewDraftChange={props.onReviewDraftChange}
        onReview={props.onReview}
      />
      <TaskListSection
        title="历史记录"
        items={queues.history}
        proofDrafts={props.proofDrafts}
        reviewDrafts={props.reviewDrafts}
        currentAccountId={props.currentAccountId}
        isPending={props.isPending}
        emptyText="当前没有历史记录。"
        onClaim={props.onClaim}
        onProofDraftChange={props.onProofDraftChange}
        onSubmitProof={props.onSubmitProof}
        onReviewDraftChange={props.onReviewDraftChange}
        onReview={props.onReview}
      />
    </div>
  );
}

function buildUniqueTaskQueues(groups: { claimed: TaskWithLaunch[]; created: TaskWithLaunch[]; submitted: TaskWithLaunch[] }, accountId: string, reviewItems: ValidationReviewQueueItem[]) {
  const tasks = new Map<string, TaskWithLaunch>();
  for (const entry of [...groups.claimed, ...groups.created, ...groups.submitted]) {
    tasks.set(entry.task.id, entry);
  }
  const reviewTaskIds = new Set(reviewItems.map((item) => item.proof.taskId));
  const actionNeeded: TaskWithLaunch[] = [];
  const waiting: TaskWithLaunch[] = [];
  const history: TaskWithLaunch[] = [];
  for (const entry of tasks.values()) {
    const status = entry.task.status;
    const canSubmit = ["claimed", "working", "changes_requested"].includes(status) && entry.task.claimedByAccountId === accountId;
    const canClaim = status === "open";
    const terminal = ["accepted", "settled", "cancelled"].includes(status);
    // 中文注释：同一任务只进入一个队列，避免创建人、领取人、提交人身份造成重复卡片。
    if (!reviewTaskIds.has(entry.task.id) && (canSubmit || canClaim)) {
      actionNeeded.push(entry);
    } else if (terminal) {
      history.push(entry);
    } else {
      waiting.push(entry);
    }
  }
  return { actionNeeded, waiting, history };
}

function TaskListSection(props: TaskListProps & { title: string; children?: ReactNode }) {
  return (
    <PanelBox icon={<ClipboardCheck className="h-4 w-4" />} title={props.title} className="h-full">
      <TaskList {...props} />
      {props.children}
    </PanelBox>
  );
}

type TaskListProps = {
  items: TaskWithLaunch[];
  proofDrafts: Record<string, TextDraft>;
  reviewDrafts: Record<string, TextDraft>;
  currentAccountId: string;
  isPending: boolean;
  emptyText: string;
  onClaim: (taskId: string) => void;
  onProofDraftChange: (taskId: string, patch: TextDraft) => void;
  onSubmitProof: (task: ValidationTask) => void;
  onReviewDraftChange: (proofId: string, patch: TextDraft) => void;
  onReview: (proofId: string, result: ReviewResult, validationMode?: "ordinary" | "staked") => void;
};

function TaskList(props: TaskListProps) {
  if (props.items.length === 0) {
    return <div className="rounded-[8px] bg-[var(--background)] px-3 py-2 text-xs leading-5 text-[var(--muted-foreground)]">{props.emptyText}</div>;
  }
  return (
    <div className="grid gap-2">
      {props.items.map((entry) => (
        <TaskCard
          key={entry.task.id}
          entry={entry}
          proofDraft={props.proofDrafts[entry.task.id] ?? {}}
          reviewDrafts={props.reviewDrafts}
          currentAccountId={props.currentAccountId}
          isPending={props.isPending}
          onClaim={() => props.onClaim(entry.task.id)}
          onProofDraftChange={(patch) => props.onProofDraftChange(entry.task.id, patch)}
          onSubmitProof={() => props.onSubmitProof(entry.task)}
          onReviewDraftChange={props.onReviewDraftChange}
          onReview={props.onReview}
        />
      ))}
    </div>
  );
}

function TaskCard(props: {
  entry: TaskWithLaunch;
  proofDraft: TextDraft;
  reviewDrafts: Record<string, TextDraft>;
  currentAccountId: string;
  isPending: boolean;
  onClaim: () => void;
  onProofDraftChange: (patch: TextDraft) => void;
  onSubmitProof: () => void;
  onReviewDraftChange: (proofId: string, patch: TextDraft) => void;
  onReview: (proofId: string, result: ReviewResult, validationMode?: "ordinary" | "staked") => void;
}) {
  const { task, launch, proofs, rewards } = props.entry;
  const kind = getTaskKindOption(getTaskKind(task));
  const canClaim = task.status === "open";
  const canSubmit = ["claimed", "working", "changes_requested"].includes(task.status) && task.claimedByAccountId === props.currentAccountId;
  const pendingRewards = rewards.filter((reward) => reward.status === "pending").length;
  return (
    <details className="group rounded-[8px] bg-[var(--surface-2)] p-3" open={canSubmit || proofs.some((proof) => proof.status === "submitted")}>
      <summary className="cursor-pointer list-none">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="flex items-center gap-1 rounded-full bg-[var(--surface-control)] px-2 py-1 text-[11px] font-black text-[var(--muted-foreground)]">
                {kind.icon}
                {kind.label}
              </span>
              <StatusPill label={taskStatusLabels[task.status] ?? task.status} />
              {pendingRewards > 0 ? <StatusPill label={`${pendingRewards} 条待发虚拟股份`} /> : null}
            </div>
            <div className="mt-2 truncate text-sm font-black text-[var(--foreground)]">{task.title}</div>
            <div className="mt-1 line-clamp-2 text-xs leading-5 text-[var(--muted-foreground)]">{task.intent || task.deliverable}</div>
          </div>
          <div className="flex shrink-0 flex-wrap gap-2">
            {canClaim ? (
              <Button type="button" size="sm" variant="outline" onClick={(event) => { event.preventDefault(); props.onClaim(); }} loading={props.isPending}>
                <Hand className="h-4 w-4" />
                接任务
              </Button>
            ) : null}
            <span className="rounded-[8px] border border-[var(--border)] px-3 py-2 text-xs font-semibold text-[var(--muted-foreground)]">查看详情</span>
          </div>
        </div>
      </summary>

      <div className="mt-3 grid gap-3 border-t border-[var(--border)] pt-3">
        <div className="grid gap-2 md:grid-cols-4">
          <MetricBox label="任务状态" value={taskStatusLabels[task.status] ?? task.status} />
          <MetricBox label="任务来源" value={launchStatusLabels[launch.status] ?? launch.title} />
          <MetricBox label="创建人" value={shortAccount(task.createdByAccountId)} />
          <MetricBox label="领取人" value={task.claimedByAccountId ? shortAccount(task.claimedByAccountId) : "待领取"} />
        </div>

        <div className="grid gap-2 md:grid-cols-2">
          <InfoBlock title="任务说明" content={task.deliverable} />
          <InfoBlock title="确认标准" content={task.acceptanceCriteria.length > 0 ? task.acceptanceCriteria.join("\n") : "按任务说明验证"} />
        </div>

        <StatusFlow activeStatus={task.status} />

        {canClaim ? (
          <div className="rounded-[8px] bg-[var(--background)] px-3 py-2 text-xs leading-5 text-[var(--muted-foreground)]">
            接任务后可以在这里提交成果。
          </div>
        ) : null}

        {canSubmit ? (
          <SubmitProofBox
            draft={props.proofDraft}
            isPending={props.isPending}
            onDraftChange={props.onProofDraftChange}
            onSubmit={props.onSubmitProof}
          />
        ) : null}

        {proofs.length > 0 ? (
          <div className="grid gap-2">
            <div className="text-xs font-black text-[var(--muted-foreground)]">成果记录</div>
            {proofs.map((proof) => (
              <ProofRow
                key={proof.id}
                proof={proof}
                draft={props.reviewDrafts[proof.id] ?? {}}
                currentAccountId={props.currentAccountId}
                isPending={props.isPending}
                onDraftChange={(patch) => props.onReviewDraftChange(proof.id, patch)}
                onReview={(result, validationMode) => props.onReview(proof.id, result, validationMode)}
              />
            ))}
          </div>
        ) : null}

        {rewards.length > 0 ? (
          <div className="grid gap-2 md:grid-cols-3">
            {rewards.map((reward) => (
              <MetricBox key={reward.id} label={rewardStatusLabels[reward.status] ?? reward.status} value={reward.recipientAccountId ? shortAccount(reward.recipientAccountId) : "虚拟股份池"} />
            ))}
          </div>
        ) : null}
      </div>
    </details>
  );
}

function SubmitProofBox(props: {
  draft: TextDraft;
  isPending: boolean;
  onDraftChange: (patch: TextDraft) => void;
  onSubmit: () => void;
}) {
  return (
    <div className="grid gap-2 rounded-[8px] bg-[var(--background)] p-3 md:grid-cols-2">
      <TextArea value={props.draft.summary ?? ""} onChange={(summary) => props.onDraftChange({ summary })} placeholder="成果摘要" />
      <div className="grid gap-2">
        <TextInput value={props.draft.kind ?? ""} onChange={(kind) => props.onDraftChange({ kind })} placeholder="成果类型，例如 PR、截图、文档、链接" />
        <TextInput value={props.draft.url ?? ""} onChange={(url) => props.onDraftChange({ url })} placeholder="成果链接" />
        <Button type="button" size="sm" variant="outline" onClick={props.onSubmit} loading={props.isPending}>
          <UploadCloud className="h-4 w-4" />
          提交成果
        </Button>
      </div>
    </div>
  );
}

function ReviewQueueBlock(props: {
  title: string;
  items: ValidationReviewQueueItem[];
  drafts: Record<string, TextDraft>;
  currentAccountId: string;
  isPending: boolean;
  onDraftChange: (proofId: string, patch: TextDraft) => void;
  onReview: (proofId: string, result: ReviewResult, validationMode?: "ordinary" | "staked") => void;
}) {
  return (
    <div className="grid gap-2">
      <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
        <ClipboardCheck className="h-4 w-4" />
        {props.title}
      </div>
      {props.items.length === 0 ? (
        <div className="rounded-[8px] bg-[var(--background)] px-3 py-2 text-xs leading-5 text-[var(--muted-foreground)]">当前没有待处理验证。</div>
      ) : (
        props.items.map((item) => (
          <div key={item.proof.id} className="rounded-[8px] bg-[var(--background)] p-3">
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs">
              <span className="font-semibold text-[var(--foreground)]">{item.taskTitle}</span>
              <span className="text-[var(--muted-foreground)]">
                {validationProgressLabel(item.proof)}
              </span>
            </div>
            <ProofRow
              proof={item.proof}
              draft={props.drafts[item.proof.id] ?? {}}
              currentAccountId={props.currentAccountId}
              isPending={props.isPending}
              onDraftChange={(patch) => props.onDraftChange(item.proof.id, patch)}
              onReview={(result, validationMode) => props.onReview(item.proof.id, result, validationMode)}
            />
          </div>
        ))
      )}
    </div>
  );
}

function ProofRow(props: {
  proof: ValidationProof;
  draft: TextDraft;
  currentAccountId: string;
  isPending: boolean;
  onDraftChange: (patch: TextDraft) => void;
  onReview: (result: ReviewResult, validationMode?: "ordinary" | "staked") => void;
}) {
  const { proof, draft } = props;
  return (
    <div className="rounded-[8px] bg-[var(--surface-control)] p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-[var(--foreground)]">{proof.summary}</div>
          <div className="mt-1 text-xs text-[var(--muted-foreground)]">{proofStatusLabels[proof.status] ?? proof.status} / {shortAccount(proof.submittedByAccountId)}</div>
        </div>
        {proof.status === "accepted" ? <CheckCircle2 className="h-4 w-4 text-[var(--accent-green)]" /> : null}
      </div>
      <ValidationProgress proof={proof} />
      {proof.status === "submitted" && proof.submittedByAccountId === props.currentAccountId ? (
        <div className="mt-3 rounded-[8px] bg-[var(--background)] px-3 py-2 text-xs text-[var(--muted-foreground)]">等待验证</div>
      ) : null}
      {proof.status === "submitted" && proof.submittedByAccountId !== props.currentAccountId ? (
        <div className="mt-3 grid gap-2 md:grid-cols-2">
          <TextArea value={draft.reason ?? ""} onChange={(reason) => props.onDraftChange({ reason })} placeholder="验证备注，可留空" />
          <div className="grid gap-2">
            <TextInput value={draft.stakedShares ?? ""} onChange={(stakedShares) => props.onDraftChange({ stakedShares })} placeholder="质押虚拟股份数量" />
            <TextArea value={draft.requestedEvidence ?? ""} onChange={(requestedEvidence) => props.onDraftChange({ requestedEvidence })} placeholder="需要补充的成果，每行一条" />
            <TextArea value={draft.riskFlags ?? ""} onChange={(riskFlags) => props.onDraftChange({ riskFlags })} placeholder="风险标记，每行一条" />
            <div className="flex flex-wrap gap-2">
              <Button type="button" size="sm" onClick={() => props.onReview("accept", "ordinary")} loading={props.isPending}>普通确认</Button>
              <Button type="button" size="sm" variant="outline" onClick={() => props.onReview("accept", "staked")} loading={props.isPending}>质押确认</Button>
              <Button type="button" size="sm" variant="outline" onClick={() => props.onReview("request_changes")} loading={props.isPending}>需补充</Button>
              <Button type="button" size="sm" variant="outline" onClick={() => props.onReview("hold")} loading={props.isPending}>复核挂起</Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function ValidationProgress({ proof }: { proof: ValidationProof }) {
  const stats = proof.validationStats;
  if (!stats) {
    return null;
  }
  return (
    <div className="mt-3 grid gap-2 sm:grid-cols-2">
      <MetricBox label="参与验证人数" value={`${stats.participantCount} / ${stats.minParticipantCount}`} />
      <MetricBox label="有效验证人数" value={`${formatMetric(stats.effectiveValidationCount)} / ${formatMetric(stats.minEffectiveValidationCount)}`} />
      <MetricBox label="普通验证" value={`${stats.ordinaryValidationCount} 人`} />
      <MetricBox label="质押验证" value={`${stats.stakedValidationCount} 人，${formatMetric(stats.stakedShares)} 虚拟股份`} />
    </div>
  );
}

function InfoBlock({ title, content }: { title: string; content: string }) {
  return (
    <div className="rounded-[8px] bg-[var(--background)] p-3">
      <div className="text-xs font-black text-[var(--muted-foreground)]">{title}</div>
      <div className="mt-2 whitespace-pre-line text-xs leading-5 text-[var(--foreground)]">{content}</div>
    </div>
  );
}

function StatusFlow({ activeStatus }: { activeStatus: string }) {
  const steps = [
    { status: "open", label: "可领取" },
    { status: "claimed", label: "进行中" },
    { status: "proof_submitted", label: "待验证" },
    { status: "changes_requested", label: "需补充" },
    { status: "held", label: "复核中" },
    { status: "accepted", label: "已确认" },
    { status: "pending", label: "待发虚拟股份" },
    { status: "settled", label: "已发放" },
  ];
  const activeIndex = resolveStatusStepIndex(activeStatus);
  return (
    <div className="grid gap-1 rounded-[8px] bg-[var(--background)] p-2 sm:grid-cols-4 lg:grid-cols-8">
      {steps.map((step, index) => (
        <div
          key={step.status}
          className={cn(
            "rounded-[6px] px-2 py-1 text-center text-[11px] font-semibold",
            index <= activeIndex ? "bg-[var(--surface-control)] text-[var(--foreground)]" : "text-[var(--muted-foreground)]",
          )}
        >
          {step.label}
        </div>
      ))}
    </div>
  );
}

function PanelBox({ icon, title, children, className }: { icon: ReactNode; title: string; children: ReactNode; className?: string }) {
  return (
    <div className={cn("rounded-[8px] bg-[var(--surface-2)] p-3", className)}>
      <div className="mb-2 flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
        {icon}
        {title}
      </div>
      <div className="grid gap-2">{children}</div>
    </div>
  );
}

function StatusPill({ label }: { label: string }) {
  return <span className="rounded-full bg-[var(--surface-control)] px-2 py-1 text-[11px] font-semibold text-[var(--muted-foreground)]">{label}</span>;
}

function MetricBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[8px] bg-[var(--background)] px-3 py-2">
      <div className="text-[11px] text-[var(--muted-foreground)]">{label}</div>
      <div className="mt-1 truncate text-sm font-semibold text-[var(--foreground)]">{value}</div>
    </div>
  );
}

const inputClassName = "min-h-9 w-full rounded-[8px] border border-[var(--border)] bg-[var(--background)] px-3 py-2 text-xs font-semibold text-[var(--foreground)] placeholder:text-[var(--muted-foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]";

function TextInput({ value, onChange, placeholder }: { value: string; onChange: (value: string) => void; placeholder: string }) {
  return <input className={inputClassName} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />;
}

function TextArea({ value, onChange, placeholder }: { value: string; onChange: (value: string) => void; placeholder: string }) {
  return <textarea className={cn(inputClassName, "min-h-20 font-normal leading-5")} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />;
}

function getTaskKind(task: ValidationTask): TaskKind {
  const metadataKind = typeof task.metadata.taskType === "string" ? task.metadata.taskType : "";
  const tagKind = task.tags.find((tag) => isTaskKind(tag)) ?? "";
  const kind = metadataKind || tagKind;
  return isTaskKind(kind) ? kind : "normal";
}

function getTaskKindOption(kind: TaskKind) {
  return taskKindOptions.find((option) => option.value === kind) ?? taskKindOptions[0];
}

function isTaskKind(value: string): value is TaskKind {
  return ["normal", "bug", "review", "dispute"].includes(value);
}

function resolveStatusStepIndex(status: string) {
  if (status === "open") return 0;
  if (["claimed", "working"].includes(status)) return 1;
  if (["proof_submitted", "submitted"].includes(status)) return 2;
  if (["changes_requested", "held"].includes(status)) return 4;
  if (status === "accepted") return 5;
  if (status === "settled") return 7;
  return 0;
}

function shortAccount(accountId: string) {
  return accountId.length > 12 ? `${accountId.slice(0, 6)}...${accountId.slice(-4)}` : accountId;
}

function lines(value: string | undefined) {
  return (value ?? "").split(/\n+/).map((item) => item.trim()).filter(Boolean);
}

function validationProgressLabel(proof: ValidationProof) {
  const stats = proof.validationStats;
  if (!stats) {
    return "待验证";
  }
  return `参与 ${stats.participantCount}/${stats.minParticipantCount}，有效 ${formatMetric(stats.effectiveValidationCount)}/${formatMetric(stats.minEffectiveValidationCount)}`;
}

function formatMetric(value: unknown) {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed.toFixed(2) : "0.00";
}
