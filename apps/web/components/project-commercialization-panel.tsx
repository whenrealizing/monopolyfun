import type { ProjectCommercialization } from "@/lib/api";

type ProjectContributionLedgerPanelProps = {
  commercialization: ProjectCommercialization | null;
  accountsById?: Record<string, { displayName: string; handle: string }>;
  labels: {
    title: string;
    description: string;
    status: Record<string, string>;
    ledgerTitle: string;
    contributorsTitle: string;
    empty: string;
    metrics: {
      tasks: string;
      results: string;
      accepted: string;
      virtualSharePool: string;
      claimed: string;
      virtualShares: string;
      contributors: string;
      ledgerEntries: string;
      weight: string;
    };
    sourceTypes: Record<string, string>;
    roles: Record<string, string>;
  };
};

export function ProjectContributionLedgerPanel({ commercialization, accountsById = {}, labels }: ProjectContributionLedgerPanelProps) {
  if (!commercialization) {
    return null;
  }

  // 中文注释：治理页直接展示商业化聚合里的统一贡献账本，避免页面层重新拼接任务、验证和奖励事实。
  const activeGroup = commercialization.leadingDirection ?? commercialization.directions[0] ?? null;
  const accepted = commercialization.proofStats.acceptedProofs;
  const submitted = commercialization.proofStats.submittedProofs;
  const virtualSharePool = commercialization.currentDistribution.eligibleShareMinted.toLocaleString();
  const recentEntries = commercialization.contributionLedger.slice(0, 6);

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

      <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="space-y-2 bg-[var(--surface-control)] p-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-xs font-medium text-[var(--foreground)]">{labels.ledgerTitle}</h3>
            <span className="text-[11px] text-[var(--muted-foreground)]">
              {commercialization.contributionLedger.length.toLocaleString()} {labels.metrics.ledgerEntries}
            </span>
          </div>
          {recentEntries.length > 0 ? (
            <div className="grid gap-2">
              {recentEntries.map((entry) => (
                <div key={entry.id} className="grid gap-2 bg-[var(--background)] p-3 md:grid-cols-[minmax(0,1fr)_80px_80px]">
                  <div className="min-w-0">
                    <div className="truncate text-xs font-medium text-[var(--foreground)]">{accountLabel(entry.accountId, accountsById)}</div>
                    <div className="mt-1 flex flex-wrap gap-1.5 text-[11px] text-[var(--muted-foreground)]">
                      <span>{labels.sourceTypes[entry.sourceType] ?? entry.sourceType}</span>
                      <span>{labels.roles[entry.contributionRole] ?? entry.contributionRole}</span>
                    </div>
                  </div>
                  <Metric label={labels.metrics.virtualShares} value={entry.shares.toLocaleString()} compact />
                  <Metric label={labels.metrics.weight} value={formatWeight(entry.contributionWeight)} compact />
                </div>
              ))}
            </div>
          ) : (
            <div className="bg-[var(--background)] p-3 text-xs text-[var(--muted-foreground)]">{labels.empty}</div>
          )}
        </div>

        <div className="space-y-2 bg-[var(--surface-control)] p-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-xs font-medium text-[var(--foreground)]">{labels.contributorsTitle}</h3>
            <span className="text-[11px] text-[var(--muted-foreground)]">
              {commercialization.contributors.length.toLocaleString()} {labels.metrics.contributors}
            </span>
          </div>
          <div className="grid gap-2">
            {commercialization.contributors.slice(0, 5).map((contributor) => (
              <div key={contributor.accountId} className="grid grid-cols-[minmax(0,1fr)_80px] gap-2 bg-[var(--background)] p-3">
                <div className="min-w-0">
                  <div className="truncate text-xs font-medium text-[var(--foreground)]">{accountLabel(contributor.accountId, accountsById)}</div>
                  <div className="mt-1 text-[11px] text-[var(--muted-foreground)]">{contributor.settledCount.toLocaleString()} {labels.metrics.ledgerEntries}</div>
                </div>
                <Metric label={labels.metrics.virtualShares} value={contributor.totalShares.toLocaleString()} compact />
              </div>
            ))}
          </div>
        </div>
      </div>
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

function accountLabel(accountId: string, accountsById: Record<string, { displayName: string; handle: string }>) {
  const account = accountsById[accountId];
  return account ? `${account.displayName} ${account.handle}` : accountId;
}

function formatWeight(value: number) {
  return Number.isInteger(value) ? value.toLocaleString() : value.toLocaleString(undefined, { maximumFractionDigits: 2 });
}
