import type { ProjectCommercialization } from "@/lib/api";

type ProjectContributionLedgerPanelProps = {
  commercialization: ProjectCommercialization | null;
  labels: {
    title: string;
    description: string;
    status: Record<string, string>;
    metrics: {
      tasks: string;
      results: string;
      accepted: string;
      virtualSharePool: string;
      claimed: string;
      virtualShares: string;
    };
  };
};

export function ProjectContributionLedgerPanel({ commercialization, labels }: ProjectContributionLedgerPanelProps) {
  if (!commercialization) {
    return null;
  }

  // 中文注释：第一版只展示虚拟股份和贡献事实，方向和收入留在后续扩展层。
  const activeGroup = commercialization.leadingDirection ?? commercialization.directions[0] ?? null;
  const accepted = commercialization.proofStats.acceptedProofs;
  const submitted = commercialization.proofStats.submittedProofs;
  const virtualSharePool = commercialization.currentDistribution.eligibleShareMinted.toLocaleString();

  return (
    <section className="space-y-3 bg-[var(--background)] p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-medium text-[var(--foreground)]">{labels.title}</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{labels.description}</p>
        </div>
        <span className="rounded-full bg-[var(--surface-control)] px-2.5 py-1 text-[11px] text-[var(--muted-foreground)]">
          {labels.status[commercialization.currentDistribution.status] ?? commercialization.currentDistribution.status}
        </span>
      </div>

      <div className="grid gap-2 md:grid-cols-4">
        <Metric label={labels.metrics.tasks} value={String(commercialization.proofStats.totalTasks)} />
        <Metric label={labels.metrics.results} value={String(submitted)} />
        <Metric label={labels.metrics.accepted} value={String(accepted)} />
        <Metric label={labels.metrics.virtualSharePool} value={virtualSharePool} />
      </div>

      {activeGroup ? (
        <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_260px]">
          <div className="min-w-0 bg-[var(--surface-control)] p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-medium text-[var(--foreground)]">{activeGroup.statement}</span>
              <span className="rounded-full bg-[var(--background)] px-2 py-0.5 text-[11px] text-[var(--muted-foreground)]">
                {labels.status[activeGroup.status] ?? activeGroup.status}
              </span>
            </div>
            {activeGroup.successMetric ? (
              <p className="mt-2 text-xs leading-5 text-[var(--muted-foreground)]">{activeGroup.successMetric}</p>
            ) : null}
          </div>
          <div className="grid grid-cols-3 gap-2 bg-[var(--surface-control)] p-3">
            <Metric label={labels.metrics.claimed} value={String(activeGroup.claimedCount)} compact />
            <Metric label={labels.metrics.accepted} value={String(activeGroup.acceptedCount)} compact />
            <Metric label={labels.metrics.virtualShares} value={virtualSharePool} compact />
          </div>
        </div>
      ) : null}
    </section>
  );
}

function Metric({ label, value, compact = false }: { label: string; value: string; compact?: boolean }) {
  return (
    <div className={compact ? "min-w-0" : "bg-[var(--surface-control)] p-3"}>
      <div className="truncate text-[11px] font-medium text-[var(--muted-foreground)]">{label}</div>
      <div className="mt-1 truncate text-sm font-medium text-[var(--foreground)]">{value}</div>
    </div>
  );
}
