import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

export function ProfileIdentityHero({
  displayName,
  handle,
  avatarUrl,
  summary,
  emptySummary,
  badges,
  actions,
  stats,
  cover = false,
  compact = false,
  avatarClassName,
}: {
  displayName: string;
  handle: string;
  avatarUrl?: string | null;
  summary?: string | null;
  emptySummary?: string;
  badges?: ReactNode;
  actions?: ReactNode;
  stats?: Array<{ label: string; value: string }>;
  cover?: boolean;
  compact?: boolean;
  avatarClassName?: string;
}) {
  const normalizedHandle = handle.replace(/^@+/, "");
  const summaryText = summary?.trim() || emptySummary;

  return (
    <section className={cn("bg-[var(--background)]", cover ? "overflow-hidden rounded-[12px] border border-[var(--border)]" : null)}>
      {cover ? <div className="h-20 bg-[var(--muted)]/25" /> : null}
      <div className={cn(cover ? "px-5 pb-5 pt-4 sm:px-6" : null)}>
        <div className={cn("space-y-5", cover ? "-mt-10" : null)}>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className={cn("flex min-w-0 flex-col gap-4 sm:flex-row sm:items-start", cover ? "pt-10" : null)}>
              <ProfileAvatar
                displayName={displayName}
                handle={normalizedHandle}
                avatarUrl={avatarUrl}
                compact={compact}
                className={avatarClassName}
              />
              <div className="min-w-0 pt-0.5">
                <h1 className="truncate text-[20px] font-normal leading-7 text-[var(--foreground)]">
                  {displayName}
                </h1>
                <div className="mt-1 text-[14px] font-normal text-[var(--muted-foreground)]">@{normalizedHandle}</div>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  {badges}
                </div>
                {summaryText ? (
                  <p className="mt-2 max-w-3xl whitespace-pre-line text-[14px] font-normal leading-6 text-[var(--muted-foreground)]">
                    {summaryText}
                  </p>
                ) : null}
              </div>
            </div>
            {actions ? <div className="flex shrink-0 flex-wrap gap-2 sm:justify-end">{actions}</div> : null}
          </div>
          {stats?.length ? <ProfileStats stats={stats} /> : null}
        </div>
      </div>
    </section>
  );
}

function ProfileAvatar({
  displayName,
  handle,
  avatarUrl,
  compact,
  className,
}: {
  displayName: string;
  handle: string;
  avatarUrl?: string | null;
  compact: boolean;
  className?: string;
}) {
  const fallback = displayName.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || handle.slice(0, 2).toUpperCase() || "MF";
  return (
    <span className={cn(
      "flex shrink-0 items-center justify-center overflow-hidden rounded-full border-2 border-[var(--border)] bg-[rgba(72,108,230,0.16)] text-[rgb(218,226,255)] shadow-[var(--shadow-sm)]",
      compact ? "h-20 w-20 text-[20px] sm:h-24 sm:w-24" : "h-20 w-20 border-4 border-[var(--background)] text-[20px]",
      className,
    )}>
      {avatarUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={avatarUrl} alt="" className="h-full w-full object-cover" />
      ) : fallback}
    </span>
  );
}

function ProfileStats({ stats }: { stats: Array<{ label: string; value: string }> }) {
  return (
    <div className="flex flex-wrap justify-start gap-x-10 gap-y-3 pt-1">
      {stats.map((stat) => (
        <div key={stat.label} className="min-w-16 text-center">
          <div className="text-[20px] font-normal leading-7 text-[var(--foreground)]">{stat.value}</div>
          <div className="mt-1 text-[12px] font-normal text-[var(--muted-foreground)]">{stat.label}</div>
        </div>
      ))}
    </div>
  );
}
