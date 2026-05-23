"use client";

import {type CSSProperties, type ReactNode, useEffect, useState, useSyncExternalStore, useTransition} from "react";
import {useRouter} from "@/i18n/navigation";
import {useLocale, useTranslations} from "next-intl";
import {
    AlertTriangle,
    Boxes,
    ChevronRight,
    Code2,
    CreditCard,
    ExternalLink,
    EyeOff,
    HandCoins,
    PackageCheck,
    ShieldCheck,
    UserRoundCheck,
    UsersRound,
} from "lucide-react";

import {type GlobalStateKind, GlobalStatePage, RetryButton} from "@/components/global-state-page";
import {Button} from "@/components/ui/button";
import {EmptyState} from "@/components/ui/page-layout";
import {useToast} from "@/components/ui/toast";
import {
    acceptProjectInvite,
    ApiRequestError,
    declineProjectInvite,
    dismissWorkbenchItem,
    listWorkbenchItems,
    type WorkbenchItem
} from "@/lib/api";
import {readStoredSession, subscribeSession} from "@/lib/client-preferences";
import {presentError} from "@/lib/error-messages";
import {projectWorkbenchGroup} from "@/lib/project-action-registry";
import {cn} from "@/lib/utils";

type WorkbenchFilter = "all" | "project" | "bought" | "sold" | "published" | "reviewing" | "approving";
type WorkbenchLoadError = {
  kind: GlobalStateKind;
  title: string;
  description: string;
};

export function WorkbenchPanel() {
  const t = useTranslations("Workbench");
  const stateT = useTranslations("State");
  const locale = useLocale() as "zh-CN" | "en";
  const toast = useToast();
  const router = useRouter();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [items, setItems] = useState<WorkbenchItem[]>([]);
  const [loadError, setLoadError] = useState<WorkbenchLoadError | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [activeActionId, setActiveActionId] = useState<string | null>(null);
  const [isLoadingWorkbench, setIsLoadingWorkbench] = useState(false);
  const [hasLoadedWorkbench, setHasLoadedWorkbench] = useState(false);
  const [filter, setFilter] = useState<WorkbenchFilter>("all");
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    if (session) {
      startTransition(() => {
        setItems([]);
        setLoadError(null);
        setActionError(null);
        setHasLoadedWorkbench(false);
      });
      void loadWorkbench({ preserveOnError: false });
    }
    // 中文注释：工作台绑定当前 session，账号切换后立即重算可见待办和隐藏状态。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accountId]);

  async function loadWorkbench(options: { preserveOnError?: boolean } = {}) {
    const preserveOnError = options.preserveOnError ?? hasLoadedWorkbench;
    setIsLoadingWorkbench(true);
    try {
      const nextItems = await listWorkbenchItems();
      startTransition(() => {
        setLoadError(null);
        setActionError(null);
        setHasLoadedWorkbench(true);
        setItems(nextItems);
      });
    } catch (caught) {
      const nextError = resolveWorkbenchLoadError(caught, t, stateT);
      startTransition(() => {
        if (preserveOnError) {
          setActionError(nextError.description);
          return;
        }
        setItems([]);
        setLoadError(nextError);
      });
    } finally {
      setIsLoadingWorkbench(false);
    }
  }

  async function dismissItem(itemId: string) {
    setActiveActionId(`dismiss:${itemId}`);
    try {
      setActionError(null);
      await dismissWorkbenchItem(itemId);
      await loadWorkbench();
      toast.notify({ tone: "success", title: t("actionCompleted") });
    } catch (caught) {
      setActionError(presentError(caught, "ui.agent.action.failed").message);
    } finally {
      setActiveActionId(null);
    }
  }

  async function openItem(itemId: string, item?: WorkbenchItem) {
    if (item?.targetHref) {
      router.push(item.targetHref);
      return;
    }
    setActionError(t("errors.actionFailed"));
  }

  async function executeDirectAction(actionId: string, itemId: string) {
    setActiveActionId(`${actionId}:${itemId}`);
    try {
      setActionError(null);
      if (actionId === "accept_project_invite") {
        await acceptProjectInvite(itemId);
      } else if (actionId === "decline_project_invite") {
        await declineProjectInvite(itemId);
      } else {
        throw new Error(`Unsupported direct workbench action: ${actionId}`);
      }
      await loadWorkbench();
      toast.notify({ tone: "success", title: t("actionCompleted") });
    } catch (caught) {
      setActionError(presentError(caught, "ui.agent.action.failed").message);
    } finally {
      setActiveActionId(null);
    }
  }

  const tokenLabels = t.raw("tokens") as Record<string, string>;
  const factLabels = t.raw("facts") as Record<string, string>;
  const filterOptions = workbenchFilterOptions(t.raw("filters") as Record<WorkbenchFilter, string>);
  const filterCounts = workbenchFilterCounts(items);
  const filteredItems = items.filter((item) => filter === "all" || workbenchItemMatchesFilter(item, filter));
  const groupedItems = groupWorkbenchItems(filteredItems, locale);

  if (!session) {
    return null;
  }

  if (loadError) {
    return (
      <GlobalStatePage
        kind={loadError.kind}
        title={loadError.title}
        description={loadError.description}
        primaryAction={<RetryButton onClick={() => void loadWorkbench()} label={stateT("actions.retry")} />}
      />
    );
  }

  return (
    <section className="min-w-0 bg-[var(--background)]">
      <div className="px-1 pb-4 pt-1">
        <div className="text-[22px] font-semibold leading-7 text-[var(--foreground)]">{t("heading")}</div>
      </div>

      <div className="flex gap-2 overflow-x-auto px-1 pb-4 pt-2 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
        {filterOptions.map((option) => (
          <button
            key={option.id}
            type="button"
            className={cn(
              "mf-chip shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
              filter === option.id ? "mf-chip-active" : null,
            )}
            onClick={() => setFilter(option.id)}
          >
            <span>{option.label}</span>
            <span className={cn(
              "min-w-5 rounded-full px-1.5 text-center text-[11px] leading-5",
              filter === option.id ? "bg-[rgba(255,255,255,0.16)] text-[var(--primary-foreground)]" : "bg-[var(--surface-2)] text-[var(--muted-foreground)]",
            )}>
              {filterCounts[option.id]}
            </span>
          </button>
        ))}
      </div>

      <div className="space-y-1.5">
        {actionError ? (
          <div className="mb-3 rounded-[8px] border border-[rgba(213,84,63,0.28)] bg-[rgba(213,84,63,0.1)] px-3 py-2 text-sm leading-6 text-[var(--foreground)]">
            {actionError}
          </div>
        ) : null}
        {isLoadingWorkbench && !hasLoadedWorkbench ? (
          <WorkbenchListSkeleton />
        ) : filteredItems.length > 0 ? (
          groupedItems.map((group) => (
            <div key={group.id} className="space-y-2">
              {groupedItems.length > 1 || filter === "project" ? (
                <div className="px-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--muted-foreground)]">
                  {group.label} · {group.items.length}
                </div>
              ) : null}
              {group.items.map((item) => {
            const urgencyTone = workbenchUrgencyTone(item.urgency);
            const targetFact = workbenchFact(item, "target");
            const showUrgency = !workbenchItemIsOrderRelated(item, targetFact);
            const visibleActions = workbenchVisibleActions(item.actions ?? []);
            const visibleFacts = workbenchVisibleFacts(item);
            const displayTitle = workbenchDisplayTitle(item, tokenLabels);
            const selected = {
              ...item,
              actions: visibleActions,
            };
            return (
              <article
                key={item.id}
                className={cn(
                  "group relative isolate overflow-hidden rounded-[10px] border border-[var(--border-strong)] bg-[var(--background)] p-3.5 transition duration-200 hover:border-[color-mix(in_srgb,var(--workbench-accent)_72%,var(--border-strong))] hover:bg-[linear-gradient(180deg,color-mix(in_srgb,var(--workbench-accent)_5%,transparent),transparent_58%),var(--background)] lg:hover:scale-[1.01]",
                )}
                style={workbenchAccentStyle(item)}
              >
                <div className="grid w-full min-w-0 gap-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
                  <button type="button" className="grid min-w-0 gap-3 text-left lg:grid-cols-[minmax(0,1fr)_minmax(220px,0.5fr)] lg:items-center" onClick={() => void openItem(item.id, item)}>
                    <div className="grid min-w-0 gap-3 sm:flex sm:items-center">
                      <div className="flex min-w-0 items-start gap-3 sm:items-center">
                        <WorkbenchIconTile item={item} urgencyTone={urgencyTone} />
                        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1.5 sm:hidden">
                          <WorkbenchPill className={workbenchDomainTone(item.domain).className}>{localizeWorkbenchToken(item.domain, tokenLabels)}</WorkbenchPill>
                          <WorkbenchPill className={workbenchActionTone(item.actionKind).className}>{localizeWorkbenchToken(item.actionKind, tokenLabels)}</WorkbenchPill>
                          {showUrgency ? (
                            <WorkbenchPill className={urgencyTone.className}>
                              {urgencyTone.icon ? <AlertTriangle className="h-3 w-3" /> : null}
                              {localizeWorkbenchToken(item.urgency, tokenLabels)}
                            </WorkbenchPill>
                          ) : null}
                        </div>
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="hidden flex-wrap items-center gap-1.5 sm:flex">
                          <WorkbenchPill className={workbenchDomainTone(item.domain).className}>{localizeWorkbenchToken(item.domain, tokenLabels)}</WorkbenchPill>
                          <WorkbenchPill className={workbenchActionTone(item.actionKind).className}>{localizeWorkbenchToken(item.actionKind, tokenLabels)}</WorkbenchPill>
                          {showUrgency ? (
                            <WorkbenchPill className={urgencyTone.className}>
                              {urgencyTone.icon ? <AlertTriangle className="h-3 w-3" /> : null}
                              {localizeWorkbenchToken(item.urgency, tokenLabels)}
                            </WorkbenchPill>
                          ) : null}
                        </div>
                        <div className="mt-2 line-clamp-1 text-[15px] font-semibold leading-5 text-[var(--foreground)]">{displayTitle}</div>
                        <div className="mt-1 line-clamp-1 text-sm leading-5 text-[var(--muted-foreground)]">{item.description}</div>
                      </div>
                    </div>

                    <div className="min-w-0 space-y-1 text-sm leading-5 text-[var(--muted-foreground)]">
                      {visibleFacts.map((fact) => (
                        <div key={`${item.id}:${fact.key}:${fact.value}`} className="truncate">
                          <span>{factLabel(fact.key, factLabels)}</span>
                          <span>：</span>
                          {formatFactValue(fact.value, tokenLabels)}
                        </div>
                      ))}
                    </div>
                  </button>

                  <div className="flex items-center justify-between gap-2 border-t border-[var(--border)] pt-3 lg:justify-end lg:border-0 lg:pt-0">
                    {selected.actions.length > 0 ? (
                      <div className="flex flex-wrap items-center gap-2 lg:justify-end">
                        {selected.actions.map((action) => renderWorkbenchAction(action, selected, isPending, activeActionId, openItem, dismissItem, executeDirectAction, actionLabels(t)))}
                      </div>
                    ) : null}
                    <button type="button" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] text-[var(--muted-foreground)] transition-colors hover:bg-[var(--surface-control)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]" aria-label={t("viewDetails")} title={t("viewDetails")} onClick={() => void openItem(item.id, item)}>
                      <ChevronRight className="h-5 w-5" />
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
            </div>
          ))
        ) : (
          <div className="rounded-[8px] border border-dashed border-[var(--border-strong)] px-2 py-8">
            <EmptyState
              compact
              title={t("empty")}
              description={emptyDescription(filter, t, items.length)}
            />
          </div>
        )}
      </div>
    </section>
  );
}

function prettifyToken(value: string): string {
  if (!value) return "-";
  return value.replace(/[_-]+/g, " ").replace(/\s+/g, " ").trim();
}

function localizeWorkbenchToken(value: string | undefined, labels: Record<string, string>) {
  // 中文注释：agent 返回字段保持英文 token，页面只在展示层做中文标签映射。
  if (!value) return "-";
  return labels[value] ?? prettifyToken(value);
}

function factLabel(value: string, labels: Record<string, string>) {
  return labels[value] ?? prettifyToken(value);
}

function workbenchFilterOptions(labels: Record<WorkbenchFilter, string>) {
  return (["all", "project", "bought", "sold", "published", "reviewing", "approving"] as WorkbenchFilter[]).map((id) => ({
    id,
    label: labels[id],
  }));
}

function workbenchFilterCounts(items: WorkbenchItem[]): Record<WorkbenchFilter, number> {
  return {
    all: items.length,
    project: items.filter((item) => workbenchItemMatchesFilter(item, "project")).length,
    bought: items.filter((item) => workbenchItemMatchesFilter(item, "bought")).length,
    sold: items.filter((item) => workbenchItemMatchesFilter(item, "sold")).length,
    published: items.filter((item) => workbenchItemMatchesFilter(item, "published")).length,
    reviewing: items.filter((item) => workbenchItemMatchesFilter(item, "reviewing")).length,
    approving: items.filter((item) => workbenchItemMatchesFilter(item, "approving")).length,
  };
}

function workbenchItemMatchesFilter(item: WorkbenchItem, filter: WorkbenchFilter) {
  const lane = item.lane;
  const reason = item.reason;
  if (filter === "project") {
    return item.domain === "project"
      || String(reason ?? "").includes("project")
      || String(reason ?? "").includes("proof")
      || String(reason ?? "").includes("share_release")
      || lane === "ecosystem"
      || lane === "worker"
      || lane === "reviewer"
      || lane === "settlement";
  }
  if (item.filterTags?.length) {
    return item.filterTags.includes(filter);
  }
  if (filter === "bought") {
    return lane === "payer" || reason === "complete_money_payment" || reason === "lead_accept_or_dispute";
  }
  if (filter === "sold") {
    return lane === "fulfiller" || lane === "worker" || reason === "submit_worker_proof" || reason === "auto_delivery_pending";
  }
  if (filter === "published") {
    return lane === "lead" || lane === "ecosystem" || reason === "expand_offer_supply" || reason === "promote_request" || reason === "promote_project";
  }
  if (filter === "reviewing") {
    return lane === "reviewer" || lane === "review" || reason === "review_disputed_order";
  }
  if (filter === "approving") {
    return lane === "settlement" || reason === "share_release_approval";
  }
  return true;
}

function groupWorkbenchItems(items: WorkbenchItem[], locale: "zh-CN" | "en") {
  const groups = new Map<string, { id: string; label: string; items: WorkbenchItem[] }>();
  for (const item of items) {
    const ui = projectWorkbenchGroup(item);
    const id = ui.group;
    const label = locale === "zh-CN" ? projectGroupLabelZh(ui.group) : ui.group;
    const current = groups.get(id) ?? { id, label, items: [] };
    current.items.push(item);
    groups.set(id, current);
  }
  return [...groups.values()];
}

function projectGroupLabelZh(group: string) {
  const labels: Record<string, string> = {
    "Project delivery": "Project 交付",
    "Project review": "Project 验收",
    "Project validation": "Project 验证",
    "Project feedback": "Project 反馈",
    "Project rewards": "Project 奖励",
    "Project maintenance": "Project 维护",
    "General workbench": "通用待办",
  };
  return labels[group] ?? group;
}

function emptyDescription(filter: WorkbenchFilter, t: ReturnType<typeof useTranslations>, totalCount: number) {
  if (totalCount === 0) return undefined;
  if (filter === "all") return t("emptyDescription");
  return t("filteredEmptyDescription");
}

function workbenchUrgencyTone(urgency: string | undefined) {
  if (urgency === "urgent") {
    return {
      className: "border border-[rgba(213,84,63,0.32)] bg-[rgba(213,84,63,0.1)] text-[var(--accent-red)]",
      iconTileClass: "bg-[rgba(213,84,63,0.13)] text-[var(--accent-red)]",
      icon: true,
    };
  }
  if (urgency === "attention") {
    return {
      className: "border border-[rgba(240,180,95,0.28)] bg-[rgba(240,180,95,0.12)] text-[var(--warning)]",
      iconTileClass: "bg-[color-mix(in_srgb,var(--workbench-accent)_13%,transparent)] text-[var(--workbench-accent)]",
      icon: false,
    };
  }
  return {
    className: "border border-[var(--border)] bg-[var(--surface-control)] text-[var(--muted-foreground)]",
    iconTileClass: "bg-[color-mix(in_srgb,var(--workbench-accent)_13%,transparent)] text-[var(--workbench-accent)]",
    icon: false,
  };
}

function workbenchDomainTone(domain: string | undefined) {
  void domain;
  return {
    className: "border border-[var(--border)] bg-[var(--background)] text-[var(--foreground)]",
  };
}

function workbenchActionTone(actionKind: string | undefined) {
  void actionKind;
  return {
    className: "border border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted-foreground)]",
  };
}

function WorkbenchPill({ className, children }: { className: string; children: ReactNode }) {
  return (
    <span className={cn("inline-flex h-6 items-center gap-1 rounded-[7px] px-2 text-[11px] font-normal leading-none", className)}>
      {children}
    </span>
  );
}

function WorkbenchIconTile({ item, urgencyTone }: { item: WorkbenchItem; urgencyTone: ReturnType<typeof workbenchUrgencyTone> }) {
  return (
    <span className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-[7px]", urgencyTone.iconTileClass)}>
      {workbenchItemIcon(item)}
    </span>
  );
}

function workbenchAccentStyle(item: WorkbenchItem) {
  return { "--workbench-accent": workbenchAccent(item) } as CSSProperties & Record<"--workbench-accent", string>;
}

function workbenchAccent(item: WorkbenchItem) {
  const token = `${item.domain ?? ""} ${item.actionKind ?? ""} ${item.reason ?? ""} ${item.lane ?? ""}`.toLowerCase();
  if (token.includes("payment") || token.includes("money") || token.includes("payer")) return "var(--market-opportunity-offer)";
  if (token.includes("delivery") || token.includes("proof") || token.includes("fulfill") || token.includes("worker")) return "var(--market-opportunity-project-work)";
  if (token.includes("review") || token.includes("accept") || token.includes("dispute")) return "var(--primary)";
  if (token.includes("approval") || token.includes("settlement") || token.includes("share")) return "var(--market-opportunity-request)";
  if (token.includes("invite") || token.includes("role") || token.includes("member")) return "var(--market-opportunity-project)";
  if (token.includes("ci") || token.includes("code") || token.includes("repo")) return "var(--accent-blue)";
  return "var(--primary)";
}

function workbenchItemIcon(item: WorkbenchItem) {
  const token = `${item.domain ?? ""} ${item.actionKind ?? ""} ${item.reason ?? ""} ${item.lane ?? ""}`.toLowerCase();
  const className = "h-5 w-5";
  if (token.includes("payment") || token.includes("money") || token.includes("payer")) return <CreditCard className={className} />;
  if (token.includes("delivery") || token.includes("proof") || token.includes("fulfill") || token.includes("worker")) return <PackageCheck className={className} />;
  if (token.includes("review") || token.includes("accept") || token.includes("dispute")) return <ShieldCheck className={className} />;
  if (token.includes("approval") || token.includes("settlement") || token.includes("share")) return <HandCoins className={className} />;
  if (token.includes("invite") || token.includes("role") || token.includes("member")) return <UserRoundCheck className={className} />;
  if (token.includes("ci") || token.includes("code") || token.includes("repo")) return <Code2 className={className} />;
  if (token.includes("project")) return <UsersRound className={className} />;
  return <Boxes className={className} />;
}

function workbenchVisibleActions(actions: RenderableWorkbenchAction[]) {
  return actions.filter((action) => {
    if (action.id === "open" || action.id === "dismiss" || action.id === "claim_work_item") {
      return false;
    }
    if (action.mode === "form") {
      return false;
    }
    return true;
  });
}

function workbenchFact(item: WorkbenchItem, key: string) {
  return item.summaryFacts?.find((fact) => fact.key === key)?.value;
}

function workbenchDisplayTitle(item: WorkbenchItem, tokenLabels: Record<string, string>) {
  const itemTitle = workbenchFact(item, "itemTitle");
  if (itemTitle && workbenchItemIsOrderRelated(item, workbenchFact(item, "target"))) {
    return `${localizeWorkbenchToken(item.reason, tokenLabels)}：${itemTitle}`;
  }
  return item.title;
}

function workbenchVisibleFacts(item: WorkbenchItem) {
  const facts = item.summaryFacts ?? [];
  const isOrderRelated = workbenchItemIsOrderRelated(item, workbenchFact(item, "target"));
  if (isOrderRelated) {
    return facts.filter((fact) => ["amount", "orderNo", "role"].includes(fact.key)).slice(0, 3);
  }
  const targetFact = facts.find((fact) => fact.key === "target");
  return [
    ...(targetFact ? [targetFact] : []),
    ...facts.filter((fact) => fact.key !== "target").slice(0, 2),
  ];
}

function formatFactValue(value: string, labels: Record<string, string>) {
  const [type, id] = value.split(":");
  if (id && type) {
    return `${localizeWorkbenchToken(type, labels)}：${id}`;
  }
  return localizeWorkbenchToken(value, labels);
}

function workbenchItemIsOrderRelated(item: WorkbenchItem, targetFact?: string) {
  const token = `${targetFact ?? ""} ${item.domain ?? ""} ${item.actionKind ?? ""} ${item.reason ?? ""} ${item.lane ?? ""}`.toLowerCase();
  return token.includes("order") || token.includes("订单");
}

function WorkbenchListSkeleton() {
  return (
    <div className="space-y-1.5" aria-hidden="true">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-2)] px-4 py-3">
          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(220px,0.5fr)_80px] lg:items-center">
            <div className="flex items-center gap-4">
              <div className="h-12 w-12 rounded-[10px] bg-[var(--surface-3)]" />
              <div className="min-w-0 flex-1">
                <div className="flex gap-2">
                  <div className="h-6 w-16 rounded-[12px] bg-[var(--surface-3)]" />
                  <div className="h-6 w-20 rounded-[12px] bg-[var(--surface-3)]" />
                  <div className="h-6 w-16 rounded-[12px] bg-[var(--surface-3)]" />
                </div>
                <div className="mt-3 h-5 w-2/5 rounded-[6px] bg-[var(--surface-3)]" />
                <div className="mt-2 h-4 w-3/5 rounded-[6px] bg-[var(--surface-3)]" />
              </div>
            </div>
            <div className="space-y-2">
              <div className="h-4 w-3/4 rounded-[6px] bg-[var(--surface-3)]" />
              <div className="h-4 w-2/3 rounded-[6px] bg-[var(--surface-3)]" />
            </div>
            <div className="h-9 w-9 rounded-[8px] bg-[var(--surface-3)] lg:ml-auto" />
          </div>
        </div>
      ))}
    </div>
  );
}

function resolveWorkbenchLoadError(error: unknown, t: ReturnType<typeof useTranslations>, stateT: ReturnType<typeof useTranslations>): WorkbenchLoadError {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return { kind: "unauthorized", title: stateT("unauthorized.title"), description: t("errors.loginRequired") };
    }
    if (error.status === 403) {
      return { kind: "forbidden", title: stateT("forbidden.title"), description: t("errors.forbidden") };
    }
    return { kind: "error", title: t("loadFailed"), description: t("errors.loadFailed") };
  }
  if (error instanceof Error && error.message.trim()) {
    return { kind: "error", title: t("loadFailed"), description: t("errors.actionFailed") };
  }
  return { kind: "error", title: t("loadFailed"), description: t("errors.loadFailed") };
}

type RenderableWorkbenchAction = {
  id: string;
  label: string;
  mode?: string;
  requiredInputs?: string[];
  targetHref?: string;
  destructive?: boolean;
};

type WorkbenchActionLabels = {
  openToHandle: string;
  fillToSubmit: string;
  viewDetails: string;
};

function actionLabels(t: ReturnType<typeof useTranslations>): WorkbenchActionLabels {
  return {
    openToHandle: t("openToHandle"),
    fillToSubmit: t("fillToSubmit"),
    viewDetails: t("viewDetails"),
  };
}

function renderWorkbenchAction(
  action: RenderableWorkbenchAction,
  item: WorkbenchItem,
  isPending: boolean,
  activeActionId: string | null,
  openItem: (itemId: string, item?: WorkbenchItem) => Promise<void>,
  dismissItem: (itemId: string) => Promise<void>,
  executeDirectAction: (actionId: string, itemId: string) => Promise<void>,
  labels: WorkbenchActionLabels,
) {
  const canExecuteDirectly = action.mode === "direct" && action.requiredInputs?.length === 0;
  const opensDetails = action.id !== "open" && action.id !== "dismiss" && !canExecuteDirectly;
  const actionKey = opensDetails ? `open:${item.id}` : `${action.id}:${item.id}`;
  const busy = activeActionId === actionKey;
  const disabled = isPending || Boolean(activeActionId);
  const navigationItem = { ...item, targetHref: action.targetHref ?? item.targetHref };
  // 中文注释：需要额外输入的业务动作先进入详情页，由详情页表单收集完整参数。
  if (action.id === "open") {
    return (
      <Button key={action.id} variant="primary" size="sm" loading={busy} disabled={disabled} onClick={() => void openItem(item.id, navigationItem)}>
        <ExternalLink className="h-4 w-4" />
        {action.mode === "navigate" ? labels.viewDetails : action.label}
      </Button>
    );
  }
  if (action.id === "dismiss") {
    return (
      <Button key={action.id} variant="outline" size="sm" loading={busy} disabled={disabled} onClick={() => void dismissItem(item.id)}>
        <EyeOff className="h-4 w-4" />
        {action.label}
      </Button>
    );
  }
  if (canExecuteDirectly) {
    return (
      <Button key={action.id} variant={action.destructive ? "danger" : "primary"} size="sm" loading={busy} disabled={disabled} onClick={() => void executeDirectAction(action.id, item.id)}>
        <ExternalLink className="h-4 w-4" />
        {action.label}
      </Button>
    );
  }
  return (
    <Button
      key={action.id}
      variant="outline"
      size="sm"
      loading={busy}
      disabled={disabled}
      onClick={() => void openItem(item.id, navigationItem)}
    >
      <ExternalLink className="h-4 w-4" />
      {action.label} · {action.mode === "form" ? labels.fillToSubmit : labels.openToHandle}
    </Button>
  );
}
