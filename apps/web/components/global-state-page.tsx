import Link from "next/link";
import type { ReactNode } from "react";
import { ArrowLeft, LogIn, RotateCcw, SearchX, ShieldAlert, TriangleAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import { PageContainer, PageIntro, PageSection } from "@/components/ui/page-layout";
import { cn } from "@/lib/utils";

export type GlobalStateKind = "error" | "notFound" | "unauthorized" | "forbidden";

const stateIconMap: Record<GlobalStateKind, ReactNode> = {
  error: <TriangleAlert className="h-5 w-5" />,
  notFound: <SearchX className="h-5 w-5" />,
  unauthorized: <LogIn className="h-5 w-5" />,
  forbidden: <ShieldAlert className="h-5 w-5" />,
};

export function GlobalStatePage({
  kind = "error",
  title,
  description,
  primaryAction,
  secondaryAction,
  note,
  className,
}: {
  kind?: GlobalStateKind;
  title: string;
  description: string;
  primaryAction: ReactNode;
  secondaryAction?: ReactNode;
  note?: ReactNode;
  className?: string;
}) {
  return (
    <PageContainer width="reading" className={cn("pb-10", className)}>
      <PageSection tone="subtle" size="lg" className="space-y-5">
        {/* 中文注释：所有页面级异常共用同一视觉骨架，路由只传入状态类型和业务文案。 */}
        <div className="flex h-11 w-11 items-center justify-center rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] text-[var(--muted-foreground)]">
          {stateIconMap[kind]}
        </div>
        <PageIntro heading={title} description={description} />
        {note ? (
          <div className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3 text-sm leading-6 text-[var(--muted-foreground)]">
            {note}
          </div>
        ) : null}
        <div className="flex flex-wrap gap-2">
          {primaryAction}
          {secondaryAction}
        </div>
      </PageSection>
    </PageContainer>
  );
}

export function MarketHomeButton({ label = "返回首页" }: { label?: string }) {
  return (
    <Button asChild variant="primary" size="sm">
      <Link href="/">
        <ArrowLeft className="h-3.5 w-3.5" />
        {label}
      </Link>
    </Button>
  );
}

export function LoginButton({ href = "/login?auth=login", label = "登录" }: { href?: string; label?: string }) {
  return (
    <Button asChild variant="primary" size="sm">
      <Link href={href}>
        <LogIn className="h-3.5 w-3.5" />
        {label}
      </Link>
    </Button>
  );
}

export function RetryButton({ onClick, label = "重新加载" }: { onClick: () => void; label?: string }) {
  return (
    <Button type="button" variant="outline" size="sm" onClick={onClick}>
      <RotateCcw className="h-3.5 w-3.5" />
      {label}
    </Button>
  );
}
