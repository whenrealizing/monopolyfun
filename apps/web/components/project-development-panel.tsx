import { AlertCircle, CheckCircle2, ExternalLink, GitBranch, GitPullRequest, RadioTower } from "lucide-react";
import type { ReactNode } from "react";

import { type ProjectCiCheck, type ProjectPrCiStatus, type ProjectRepoBinding } from "@/lib/api";
import { cn } from "@/lib/utils";

export function ProjectDevelopmentPanel({
  repoBindings,
  prCiStatus,
}: {
  repoBindings: ProjectRepoBinding[];
  prCiStatus: ProjectPrCiStatus | null;
}) {
  const pullRequests = prCiStatus?.pullRequests ?? [];
  const checks = prCiStatus?.checks ?? [];
  const failedChecks = checks.filter((check) => isFailedCheck(check));
  const healthyChecks = checks.filter((check) => !isFailedCheck(check));

  return (
    <section className="space-y-3 rounded-[10px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.18)] p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-sm font-black text-[var(--foreground)]">
            <RadioTower className="h-4 w-4 text-[var(--accent-blue)]" />
            代码记录回流
          </div>
          <p className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">
            Forgejo 合并请求和自动检查进入项目记录后，会在待办里生成处理项。
          </p>
        </div>
        <span className={cn("mf-chip", failedChecks.length ? "border-[rgba(245,98,98,0.3)] text-[var(--accent-red)]" : "border-[rgba(72,230,174,0.28)] text-[var(--accent-green)]")}>
          {failedChecks.length ? `${failedChecks.length} 个失败检查` : `${healthyChecks.length} 个检查`}
        </span>
      </div>

      <div className="grid gap-3 xl:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
        <div className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.14)] p-3">
          <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
            <GitBranch className="h-3.5 w-3.5" />
            仓库绑定
          </div>
          <div className="mt-3 grid gap-2">
            {repoBindings.length ? repoBindings.map((binding) => (
              <a
                key={binding.id}
                href={binding.repoUrl}
                target="_blank"
                rel="noreferrer"
                className="group min-w-0 rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(255,255,255,0.03)] px-3 py-2 transition hover:border-[rgba(72,108,230,0.34)]"
              >
                <div className="flex min-w-0 items-center justify-between gap-2">
                  <span className="truncate text-xs font-black text-[var(--foreground)]">{binding.repoOwner}/{binding.repoName}</span>
                  <ExternalLink className="h-3.5 w-3.5 shrink-0 text-[var(--muted-foreground)] group-hover:text-[var(--foreground)]" />
                </div>
                <div className="mt-1 truncate text-[11px] font-semibold text-[var(--muted-foreground)]">
                  {binding.provider ?? "forgejo"} · {binding.defaultBranch ?? "main"}
                </div>
              </a>
            )) : (
              <div className="text-xs leading-5 text-[var(--muted-foreground)]">当前项目暂无仓库绑定。</div>
            )}
          </div>
        </div>

        <div className="grid gap-3 lg:grid-cols-2">
          <DevelopmentList
            title="合并请求"
            icon={<GitPullRequest className="h-3.5 w-3.5" />}
            empty="暂无合并请求事件。"
            items={pullRequests.map((pullRequest) => ({
              key: String(pullRequest.id ?? pullRequest.prUrl ?? pullRequest.prNumber),
              title: pullRequest.prNumber ? `PR #${pullRequest.prNumber}` : "PR",
              subtitle: [pullRequest.state, pullRequest.branchName, pullRequest.headSha].filter(Boolean).join(" · "),
              href: pullRequest.prUrl,
            }))}
          />
          <DevelopmentList
            title="自动检查"
            icon={<CheckCircle2 className="h-3.5 w-3.5" />}
            empty="暂无自动检查事件。"
            items={checks.map((check) => ({
              key: String(check.id ?? check.detailsUrl ?? check.checkName),
              title: check.checkName ?? "自动检查",
              subtitle: [check.status, check.conclusion, check.headSha].filter(Boolean).join(" · "),
              href: check.detailsUrl,
              danger: isFailedCheck(check),
            }))}
          />
        </div>
      </div>
    </section>
  );
}

function DevelopmentList({
  title,
  icon,
  empty,
  items,
}: {
  title: string;
  icon: ReactNode;
  empty: string;
  items: Array<{ key: string; title: string; subtitle?: string; href?: string | null; danger?: boolean }>;
}) {
  return (
    <div className="rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(0,0,0,0.14)] p-3">
      <div className="flex items-center gap-2 text-xs font-black text-[var(--muted-foreground)]">
        {icon}
        {title}
      </div>
      <div className="mt-3 grid gap-2">
        {items.length ? items.map((item) => {
          const content = (
            <>
              <div className="flex min-w-0 items-center justify-between gap-2">
                <span className="truncate text-xs font-black text-[var(--foreground)]">{item.title}</span>
                {item.danger ? <AlertCircle className="h-3.5 w-3.5 shrink-0 text-[var(--accent-red)]" /> : <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-[var(--accent-green)]" />}
              </div>
              <div className="mt-1 truncate text-[11px] font-semibold text-[var(--muted-foreground)]">{item.subtitle || "等待状态"}</div>
            </>
          );
          return item.href ? (
            <a
              key={item.key}
              href={item.href}
              target="_blank"
              rel="noreferrer"
              className="min-w-0 rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(255,255,255,0.03)] px-3 py-2 transition hover:border-[rgba(72,108,230,0.34)]"
            >
              {content}
            </a>
          ) : (
            <div key={item.key} className="min-w-0 rounded-[8px] border border-[rgba(255,255,255,0.08)] bg-[rgba(255,255,255,0.03)] px-3 py-2">
              {content}
            </div>
          );
        }) : (
          <div className="text-xs leading-5 text-[var(--muted-foreground)]">{empty}</div>
        )}
      </div>
    </div>
  );
}

function isFailedCheck(check: ProjectCiCheck) {
  const conclusion = String(check.conclusion ?? "").toLowerCase();
  const status = String(check.status ?? "").toLowerCase();
  // 中文注释：失败态直接驱动待办，页面同步暴露同一风险信号。
  return ["failure", "failed", "timed_out", "cancelled", "action_required"].includes(conclusion) || status === "failed";
}
