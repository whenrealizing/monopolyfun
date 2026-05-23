"use client";

import { useMemo, useState, useTransition } from "react";
import { BookOpenCheck, CheckCircle2, GitCommit, PencilLine, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useRouter } from "@/i18n/navigation";
import {
  approveProjectMemoryEntry,
  createProjectMemoryEntry,
  supersedeProjectMemoryEntry,
  type ProjectAgentContext,
  type ProjectMemoryEntry,
  type ProjectMemoryOverview,
  type ProjectMemorySource,
} from "@/lib/api";

const kindLabels: Record<string, string> = {
  identity: "项目身份",
  positioning: "定位",
  audience: "用户",
  strategy: "策略",
  voice: "表达",
  experiment: "实验",
  result: "结果",
  lesson: "经验",
  priority: "优先级",
  risk_boundary: "风险边界",
};

export function ProjectMemoryPanel({
  projectNo,
  overview,
  agentContext,
}: {
  projectNo: string;
  overview: ProjectMemoryOverview | null;
  agentContext: ProjectAgentContext | null;
}) {
  const router = useRouter();
  const toast = useToast();
  const [isPending, startTransition] = useTransition();
  const sources = overview?.sources ?? [];
  const entries = overview?.entries ?? [];
  const events = overview?.events ?? [];
  const firstSource = sources[0]?.sourceId ?? "";
  const [draft, setDraft] = useState({
    kind: "lesson",
    content: "",
    sourceRefsText: firstSource,
    confidence: "0.8",
  });
  const activeEntries = entries.filter((entry) => entry.status === "active");
  const proposedEntries = entries.filter((entry) => entry.status === "proposed");
  const latestSourceEvent = events.find((event) => event.eventType === "source_review_ready");
  const contextCount = useMemo(() => Object.values(agentContext?.memory ?? {}).reduce((sum, list) => sum + list.length, 0), [agentContext]);

  function submitMemoryDraft() {
    const sourceRefs = draft.sourceRefsText.split(/[,\s]+/).map((item) => item.trim()).filter(Boolean);
    if (sourceRefs.length === 0 || draft.content.trim().length < 6) {
      toast.notify({ tone: "error", title: "请填写来源引用和至少 6 个字的平台记忆。" });
      return;
    }
    startTransition(async () => {
      try {
        await createProjectMemoryEntry(projectNo, {
          kind: draft.kind,
          content: draft.content.trim(),
          sourceRefs,
          confidence: Number(draft.confidence) || 0.8,
          visibility: "team",
          riskLevel: draft.kind === "risk_boundary" || draft.kind === "priority" ? "approval_required" : "normal",
          originEventType: latestSourceEvent?.eventType,
          originEventId: latestSourceEvent?.id,
          maintenanceReason: "team_memory_from_source_review",
        });
        toast.notify({ tone: "success", title: "项目记忆已提交，等待审批。" });
        setDraft((current) => ({ ...current, content: "" }));
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      }
    });
  }

  function reviewEntry(entry: ProjectMemoryEntry, action: "approve" | "supersede") {
    startTransition(async () => {
      try {
        const command = action === "approve" ? approveProjectMemoryEntry : supersedeProjectMemoryEntry;
        await command(projectNo, entry.memoryId);
        toast.notify({ tone: "success", title: action === "approve" ? "项目记忆已进入智能体上下文。" : "项目记忆已标记为被替换。" });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      }
    });
  }

  return (
    <section className="space-y-3 rounded-[10px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.18)] p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-sm font-black text-[var(--foreground)]">
            <BookOpenCheck className="h-4 w-4 text-[var(--accent-blue)]" />
            项目记忆
          </div>
          <p className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">
            系统自动维护来源，平台维护记忆。智能体上下文只读取已审批的有效记忆。
          </p>
        </div>
        <span className="mf-chip border-[rgba(72,230,174,0.28)] text-[var(--accent-green)]">
          {contextCount} 条上下文
        </span>
      </div>

      <div className="grid gap-3 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <div className="space-y-3">
          <MemorySyncBlock overview={overview} />
          <SourceList sources={sources} />
        </div>

        <div className="space-y-3">
          <div className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.14)] p-3">
            <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
              <PencilLine className="h-3.5 w-3.5" />
              平台维护记忆
            </div>
            <div className="mt-3 grid gap-2">
              <select
                className="h-9 rounded-[8px] border border-[var(--border)] bg-[var(--background)] px-3 text-xs font-semibold text-[var(--foreground)]"
                value={draft.kind}
                onChange={(event) => setDraft((current) => ({ ...current, kind: event.target.value }))}
              >
                {Object.entries(kindLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
              <input
                className="h-9 rounded-[8px] border border-[var(--border)] bg-[var(--background)] px-3 text-xs font-semibold text-[var(--foreground)]"
                value={draft.sourceRefsText}
                onChange={(event) => setDraft((current) => ({ ...current, sourceRefsText: event.target.value }))}
                placeholder="来源引用，例如 src_xxx"
              />
              <textarea
                className="min-h-24 rounded-[8px] border border-[var(--border)] bg-[var(--background)] px-3 py-2 text-xs leading-5 text-[var(--foreground)]"
                value={draft.content}
                onChange={(event) => setDraft((current) => ({ ...current, content: event.target.value }))}
                placeholder="平台确认后的项目记忆"
              />
              <Button type="button" size="sm" onClick={submitMemoryDraft} disabled={isPending || sources.length === 0}>
                提交平台记忆
              </Button>
            </div>
          </div>

          <EntryList title="待审批记忆" entries={proposedEntries} onReview={reviewEntry} isPending={isPending} />
          <EntryList title="有效记忆" entries={activeEntries} />
        </div>
      </div>
    </section>
  );
}

function MemorySyncBlock({ overview }: { overview: ProjectMemoryOverview | null }) {
  const root = overview?.latestRoot;
  return (
    <div className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.14)] p-3">
      <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
        <GitCommit className="h-3.5 w-3.5" />
        同步状态
      </div>
      {root ? (
        <div className="mt-3 grid gap-1.5 text-xs text-[var(--muted-foreground)]">
          <div className="font-black text-[var(--foreground)]">{root.repoOwner}/{root.repoName}</div>
          <div>{root.branch} · {root.commitSha}</div>
          <div className="break-all">rootHash: {root.rootHash}</div>
          <div>{root.syncStatus} · {root.syncedAt ?? "等待同步"}</div>
        </div>
      ) : (
        <div className="mt-3 text-xs leading-5 text-[var(--muted-foreground)]">当前还没有同步 root，source 可先进入平台复盘。</div>
      )}
    </div>
  );
}

function SourceList({ sources }: { sources: ProjectMemorySource[] }) {
  return (
    <div className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.14)] p-3">
      <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
        <ShieldCheck className="h-3.5 w-3.5" />
        自动收集来源
      </div>
      <div className="mt-3 grid gap-2">
        {sources.length ? sources.slice(0, 6).map((source) => (
          <div key={source.id} className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(255,255,255,0.03)] px-3 py-2">
            <div className="truncate text-xs font-black text-[var(--foreground)]">{source.sourceId}</div>
            <div className="mt-1 truncate text-[11px] font-semibold text-[var(--muted-foreground)]">{source.kind} · {source.syncStatus}</div>
          </div>
        )) : (
          <div className="text-xs leading-5 text-[var(--muted-foreground)]">已通过证据、PR/CI 和复盘会自动形成来源。</div>
        )}
      </div>
    </div>
  );
}

function EntryList({
  title,
  entries,
  onReview,
  isPending,
}: {
  title: string;
  entries: ProjectMemoryEntry[];
  onReview?: (entry: ProjectMemoryEntry, action: "approve" | "supersede") => void;
  isPending?: boolean;
}) {
  return (
    <div className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.14)] p-3">
      <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
        <CheckCircle2 className="h-3.5 w-3.5" />
        {title}
      </div>
      <div className="mt-3 grid gap-2">
        {entries.length ? entries.slice(0, 6).map((entry) => (
          <div key={entry.id} className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(255,255,255,0.03)] px-3 py-2">
            <div className="flex min-w-0 items-center justify-between gap-2">
              <span className="truncate text-xs font-black text-[var(--foreground)]">{kindLabels[entry.kind] ?? entry.kind}</span>
              <span className="mf-chip">{entry.confidence}</span>
            </div>
            <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{entry.content}</div>
            <div className="mt-2 truncate text-[11px] font-semibold text-[var(--muted-foreground)]">来源引用：{entry.sourceRefs.join(", ")}</div>
            {onReview ? (
              <div className="mt-2 flex flex-wrap gap-2">
                <Button type="button" size="sm" onClick={() => onReview(entry, "approve")} disabled={isPending}>批准</Button>
                <Button type="button" size="sm" variant="outline" onClick={() => onReview(entry, "supersede")} disabled={isPending}>替换</Button>
              </div>
            ) : null}
          </div>
        )) : (
          <div className="text-xs leading-5 text-[var(--muted-foreground)]">暂无记录。</div>
        )}
      </div>
    </div>
  );
}
