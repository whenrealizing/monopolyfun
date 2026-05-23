"use client";

import { CheckCircle2, GitPullRequest, RotateCcw, ShieldCheck, SlidersHorizontal, UploadCloud } from "lucide-react";
import { useMemo, useState, useTransition } from "react";

import {
  runProjectAgentAction,
  type ProjectAgentActionCard,
  type ProjectAgentInbox,
} from "@/lib/api";
import { projectActionUi } from "@/lib/project-action-registry";
import { cn } from "@/lib/utils";

type JsonRecord = Record<string, unknown>;

export function ProjectAgentProtocolPanel({
  projectNo,
  initialInbox,
}: {
  projectNo: string;
  initialInbox: ProjectAgentInbox | null;
}) {
  const [inbox, setInbox] = useState(initialInbox);
  const [activeCardId, setActiveCardId] = useState(initialInbox?.cards[0]?.cardId ?? "");
  const [message, setMessage] = useState<string | null>(null);
  const activeCard = useMemo(
    () => inbox?.cards.find((card) => card.cardId === activeCardId) ?? inbox?.cards[0] ?? null,
    [activeCardId, inbox],
  );

  if (!inbox) {
    return (
      <section className="rounded-[12px] bg-[var(--surface-2)] px-4 py-3 text-sm text-[var(--muted-foreground)]">
        Agent 协议当前无法读取。
      </section>
    );
  }

  return (
    <section className="grid gap-4 xl:grid-cols-[minmax(260px,360px)_minmax(0,1fr)]">
      <div className="space-y-3">
        <div className="rounded-[12px] bg-[var(--surface-2)] px-4 py-3">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold text-[var(--foreground)]">{String(inbox.project.goal ?? inbox.project.projectNo ?? "Agent Protocol")}</div>
              <div className="mt-1 text-xs text-[var(--muted-foreground)]">
                {String(inbox.agentState.level ?? "new")} · {inbox.cards.length} actions
              </div>
            </div>
            <ShieldCheck className="h-5 w-5 text-[var(--accent-green)]" />
          </div>
        </div>
        <div className="grid gap-2">
          {inbox.cards.map((card) => (
            <AgentActionButton
              key={card.cardId}
              card={card}
              active={activeCard?.cardId === card.cardId}
              onClick={() => {
                setActiveCardId(card.cardId);
                setMessage(null);
              }}
            />
          ))}
          {inbox.cards.length === 0 ? (
            <div className="rounded-[10px] border border-[rgba(255,255,255,0.08)] bg-[var(--background)] px-3 py-4 text-sm text-[var(--muted-foreground)]">
              当前没有可执行 action。
            </div>
          ) : null}
        </div>
      </div>

      <div className="min-w-0 rounded-[12px] bg-[var(--surface-2)] p-4">
        {activeCard ? (
          <AgentActionForm
            projectNo={projectNo}
            card={activeCard}
            message={message}
            onMessage={setMessage}
            onInbox={(nextInbox) => {
              setInbox(nextInbox);
              setActiveCardId(nextInbox.cards[0]?.cardId ?? "");
            }}
          />
        ) : (
          <div className="text-sm text-[var(--muted-foreground)]">等待 action。</div>
        )}
      </div>
    </section>
  );
}

function AgentActionButton({ card, active, onClick }: { card: ProjectAgentActionCard; active: boolean; onClick: () => void }) {
  const ui = projectActionUi(card.type);
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "grid min-h-20 gap-2 rounded-[10px] border px-3 py-3 text-left transition",
        active
          ? "border-[rgba(72,108,230,0.5)] bg-[rgba(72,108,230,0.12)]"
          : "border-[rgba(255,255,255,0.08)] bg-[var(--background)] hover:bg-[var(--surface-control)]",
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <ActionIcon type={card.type} />
        <span className="truncate text-sm font-semibold text-[var(--foreground)]">{ui.label}</span>
      </div>
      <div className="line-clamp-1 text-xs text-[var(--muted-foreground)]">{card.title}</div>
      <div className="flex flex-wrap gap-1.5 text-[11px] text-[var(--muted-foreground)]">
        <span className="rounded-full bg-[var(--surface-control)] px-2 py-0.5">{ui.group}</span>
        {card.packId ? <span className="rounded-full bg-[var(--surface-control)] px-2 py-0.5">{card.packId}</span> : null}
      </div>
    </button>
  );
}

function AgentActionForm({
  projectNo,
  card,
  message,
  onMessage,
  onInbox,
}: {
  projectNo: string;
  card: ProjectAgentActionCard;
  message: string | null;
  onMessage: (message: string | null) => void;
  onInbox: (inbox: ProjectAgentInbox) => void;
}) {
  const [pending, startTransition] = useTransition();
  const [decision, setDecision] = useState("accepted");
  const [reason, setReason] = useState("");
  const [scores, setScores] = useState({ scope: 80, complexity: 80, leverage: 80, evidence: 80 });
  const [payloadText, setPayloadText] = useState(() => JSON.stringify(defaultPayload(card), null, 2));
  const ui = projectActionUi(card.type);

  function submit() {
    startTransition(async () => {
      try {
        // 中文注释：前端只提交当前 card 的最小 payload，服务端返回新的 inbox 作为下一步事实。
        const result = await runProjectAgentAction(projectNo, {
          actionType: card.type,
          cardId: card.cardId,
          payload: payloadFor(card, { decision, reason, scores, payloadText }),
        });
        onMessage(`${result.actionType} · ${result.status}`);
        onInbox(result.inbox);
      } catch (error) {
        onMessage(error instanceof Error ? error.message : "Action failed");
      }
    });
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex min-w-0 items-center gap-2">
            <ActionIcon type={card.type} />
            <h2 className="truncate text-lg font-semibold text-[var(--foreground)]">{ui.label}</h2>
          </div>
          <div className="mt-1 text-xs text-[var(--muted-foreground)]">{card.title}</div>
        </div>
        {card.context.sharePool ? (
          <span className="rounded-full bg-[rgba(72,230,174,0.12)] px-3 py-1 text-xs font-semibold text-[var(--accent-green)]">
            {String(card.context.sharePool)} shares
          </span>
        ) : null}
      </div>

      <ContextGrid context={card.context} />

      {card.type === "score_review" ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {(["scope", "complexity", "leverage", "evidence"] as const).map((key) => (
            <label key={key} className="grid gap-1 text-xs font-semibold text-[var(--muted-foreground)]">
              {key}
              <input
                type="number"
                min={0}
                max={100}
                value={scores[key]}
                onChange={(event) => setScores({ ...scores, [key]: Number(event.target.value) })}
                className="h-10 rounded-[8px] border border-[rgba(255,255,255,0.1)] bg-[var(--background)] px-3 text-sm text-[var(--foreground)] outline-none focus:border-[var(--ring)]"
              />
            </label>
          ))}
        </div>
      ) : null}

      {["result_review", "final_review"].includes(card.type) ? (
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1 text-xs font-semibold text-[var(--muted-foreground)]">
            decision
            <select
              value={decision}
              onChange={(event) => setDecision(event.target.value)}
              className="h-10 rounded-[8px] border border-[rgba(255,255,255,0.1)] bg-[var(--background)] px-3 text-sm text-[var(--foreground)] outline-none focus:border-[var(--ring)]"
            >
              <option value="accepted">accepted</option>
              <option value="rejected">rejected</option>
            </select>
          </label>
          {card.requiredFields.includes("reviewedHeadSha") ? (
            <label className="grid gap-1 text-xs font-semibold text-[var(--muted-foreground)]">
              reviewedHeadSha
              <input
                value={String(card.context.codeHeadSha ?? "")}
                readOnly
                className="h-10 rounded-[8px] border border-[rgba(255,255,255,0.1)] bg-[var(--background)] px-3 text-sm text-[var(--foreground)]"
              />
            </label>
          ) : null}
        </div>
      ) : null}

      {["submit_pack", "revise_pack"].includes(card.type) ? (
        <details className="rounded-[8px] bg-[var(--background)] p-3">
          <summary className="cursor-pointer text-xs font-semibold text-[var(--muted-foreground)]">调试 payload</summary>
          <textarea
            value={payloadText}
            onChange={(event) => setPayloadText(event.target.value)}
            rows={12}
            className="mt-3 w-full resize-y rounded-[8px] border border-[rgba(255,255,255,0.1)] bg-[var(--surface-2)] p-3 font-mono text-xs leading-5 text-[var(--foreground)] outline-none focus:border-[var(--ring)]"
          />
        </details>
      ) : (
        <label className="grid gap-1 text-xs font-semibold text-[var(--muted-foreground)]">
          reason
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            className="w-full resize-y rounded-[8px] border border-[rgba(255,255,255,0.1)] bg-[var(--background)] p-3 text-sm leading-6 text-[var(--foreground)] outline-none focus:border-[var(--ring)]"
          />
        </label>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={submit}
          disabled={pending}
          className="inline-flex h-10 items-center gap-2 rounded-[8px] bg-[var(--foreground)] px-4 text-sm font-semibold text-[var(--background)] disabled:opacity-50"
        >
          <CheckCircle2 className="h-4 w-4" />
          {pending ? "提交中" : ui.label}
        </button>
        {message ? <div className="text-xs font-semibold text-[var(--muted-foreground)]">{message}</div> : null}
      </div>
    </div>
  );
}

function payloadFor(
  card: ProjectAgentActionCard,
  state: {
    decision: string;
    reason: string;
    scores: { scope: number; complexity: number; leverage: number; evidence: number };
    payloadText: string;
  },
) {
  if (card.type === "submit_pack" || card.type === "revise_pack") {
    return JSON.parse(state.payloadText) as JsonRecord;
  }
  if (card.type === "score_review") {
    return { packId: card.packId, choice: "endorse", scores: state.scores, reason: state.reason || "verified" };
  }
  if (card.type === "support_candidate") {
    return { taskId: card.context.taskId, prNumber: card.context.prNumber, reason: state.reason || "supported" };
  }
  if (card.type === "final_review_candidate") {
    return { taskId: card.context.taskId, prNumber: card.context.prNumber, decision: state.decision, reason: state.reason || "reviewed" };
  }
  if (card.type === "skip_candidate") {
    return { candidateId: card.packId, reasonCode: "agent_skip", reason: state.reason || "skipped", ttlMinutes: 30 };
  }
  return {
    packId: card.packId,
    decision: state.decision,
    reason: state.reason || "reviewed",
    reviewedHeadSha: card.context.codeHeadSha,
  };
}

function defaultPayload(card: ProjectAgentActionCard): JsonRecord {
  if (card.type === "revise_pack") {
    return {
      packId: card.packId,
      reason: "修复已提出的问题",
      implementation: { type: "evidence", summary: "更新后的实现说明" },
      artifacts: [{ kind: "result", summary: "更新后的验证证据" }],
    };
  }
  return {
    title: "ProposalPack",
    summary: "本次实现的简短摘要",
    work: { objective: "目标", boundaries: ["范围"], acceptanceCriteria: ["验收标准"], contextRefs: ["上下文引用"] },
    implementation: { type: "evidence", summary: "实现说明" },
    artifacts: [{ kind: "result", summary: "证据摘要" }],
    initialImpact: { scope: 70, complexity: 70, leverage: 70, evidence: 70 },
  };
}

function ContextGrid({ context }: { context: JsonRecord }) {
  const items = Object.entries(context).slice(0, 8);
  if (items.length === 0) return null;
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {items.map(([key, value]) => (
        <div key={key} className="min-w-0 rounded-[8px] bg-[var(--background)] px-3 py-2">
          <div className="text-[11px] font-semibold text-[var(--muted-foreground)]">{key}</div>
          <div className="mt-1 truncate text-sm text-[var(--foreground)]">{String(value)}</div>
        </div>
      ))}
    </div>
  );
}

function ActionIcon({ type }: { type: string }) {
  const className = "h-4 w-4 shrink-0 text-[var(--accent-blue)]";
  if (type === "submit_pack") return <UploadCloud className={className} />;
  if (type === "revise_pack") return <RotateCcw className={className} />;
  if (type === "score_review") return <SlidersHorizontal className={className} />;
  if (type === "final_review") return <ShieldCheck className={className} />;
  return <GitPullRequest className={className} />;
}
