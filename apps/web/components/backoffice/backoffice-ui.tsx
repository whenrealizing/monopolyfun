import Link from "next/link";
import {Boxes, CreditCard, FileSearch, Gauge, LucideIcon, ShieldAlert,} from "lucide-react";
import type {ReactNode} from "react";

import {Collapsible, CollapsibleContent, CollapsibleTrigger} from "@/components/ui/collapsible";
import {EmptyState} from "@/components/ui/page-layout";
import {cn} from "@/lib/utils";

export type BackofficeRouteKey = "home" | "audit" | "risk" | "payments" | "assets";

type StatusTone = "default" | "success" | "warning" | "danger" | "info";

const routes: Array<{ key: BackofficeRouteKey; href: string; label: string; icon: LucideIcon }> = [
  { key: "home", href: "/backoffice", label: "总览", icon: Gauge },
  { key: "risk", href: "/backoffice/risk", label: "风控", icon: ShieldAlert },
  { key: "payments", href: "/backoffice/payments", label: "支付", icon: CreditCard },
  { key: "assets", href: "/backoffice/assets", label: "附件", icon: Boxes },
  { key: "audit", href: "/backoffice/audit", label: "审计", icon: FileSearch },
];

const valueLabels: Record<string, string> = {
  active: "正常",
  approved: "已通过",
  authorized: "已授权",
  banned: "已封禁",
  cancelled: "已取消",
  captured: "已入账",
  completed: "已完成",
  disputed: "争议中",
  failed: "失败",
  frozen: "已冻结",
  high: "高",
  local: "本地存储",
  medium: "中",
  normal: "正常",
  okx_direct_pay: "OKX 链上支付",
  pending: "待处理",
  quarantined: "已隔离",
  refunded: "已退款",
  success: "成功",
  uploaded: "已上传",
  verified: "已验证",
  "application/json": "JSON 文件",
  "application/pdf": "PDF 文件",
  "image/jpeg": "JPEG 图片",
  "image/png": "PNG 图片",
  "text/plain": "文本文件",
};

const toneClassMap: Record<StatusTone, string> = {
  default: "border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted-foreground)]",
  success: "border-[rgba(23,138,93,0.28)] bg-[rgba(23,138,93,0.10)] text-[var(--accent-green)]",
  warning: "border-[rgba(240,180,95,0.32)] bg-[rgba(240,180,95,0.12)] text-[var(--warning)]",
  danger: "border-[rgba(194,75,57,0.30)] bg-[rgba(194,75,57,0.10)] text-[var(--accent-red)]",
  info: "border-[rgba(49,94,251,0.28)] bg-[rgba(49,94,251,0.10)] text-[var(--accent-blue)]",
};

function normalize(value: unknown) {
  return String(value ?? "").trim().toLowerCase();
}

export function textValue(value: unknown, labels: Record<string, string> = valueLabels, emptyValue = "空") {
  if (value === undefined || value === null || value === "") return emptyValue;
  const raw = String(value);
  return labels[normalize(raw)] ?? raw.replaceAll("_", " ");
}

export function statusTone(value: unknown): StatusTone {
  const key = normalize(value);
  if (["success", "active", "normal", "verified", "uploaded", "captured", "authorized", "approved", "completed"].includes(key)) return "success";
  if (["pending", "review", "medium", "draft"].includes(key)) return "warning";
  if (["failed", "high", "frozen", "banned", "quarantined", "disputed"].includes(key)) return "danger";
  if (["refunded", "cancelled"].includes(key)) return "info";
  return "default";
}

export function totalCount(counts: Record<string, number> | null | undefined) {
  return Object.values(counts ?? {}).reduce((sum, value) => sum + value, 0);
}

export function amountText(amountMinor: number | undefined, currency: string | undefined) {
  const value = (amountMinor ?? 0) / 100;
  const code = (currency || "USD").trim().toUpperCase();
  if (canUseIntlCurrency(code)) {
    return new Intl.NumberFormat("zh-CN", {
      style: "currency",
      currency: code,
      minimumFractionDigits: 2,
    }).format(value);
  }
  // 中文注释：链上资产符号如 USDC 不是 ISO 法币代码，后台金额展示用数值加资产符号保持稳定。
  return `${new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)} ${code}`;
}

function canUseIntlCurrency(currency: string) {
  try {
    new Intl.NumberFormat("zh-CN", { style: "currency", currency }).format(0);
    return true;
  } catch {
    return false;
  }
}

export function bytesText(bytes: number | undefined) {
  const size = bytes ?? 0;
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function shortRef(value: string | undefined | null) {
  if (!value) return "空";
  return value.length > 18 ? `${value.slice(0, 8)}...${value.slice(-6)}` : value;
}

export function BackofficeWorkspace({
  active,
  title,
  description,
  summary,
  children,
}: {
  active: BackofficeRouteKey;
  title: string;
  description?: string;
  summary?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="w-full space-y-4">
      <section className="bg-[var(--background)]">
        <div className="pb-3">
          <h1 className="text-[32px] font-normal leading-tight text-[var(--foreground)]">{title}</h1>
          {description ? <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--muted-foreground)]">{description}</p> : null}
        </div>
        {summary ? <div className="mb-3">{summary}</div> : null}
        <nav className="flex gap-2 overflow-x-auto rounded-[12px] bg-[var(--background)] p-1 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
          {routes.map((route) => {
            const Icon = route.icon;
            const selected = active === route.key;
            return (
              <Link
                key={route.key}
                href={route.href}
                className={cn(
                  "flex min-h-10 min-w-[92px] items-center justify-center gap-2 rounded-[10px] px-3 text-sm font-normal transition hover:bg-[var(--surface-2)]",
                  selected ? "bg-[var(--primary)] text-[var(--primary-foreground)]" : "text-[var(--muted-foreground)]",
                )}
              >
                <Icon className="h-4 w-4" />
                {route.label}
              </Link>
            );
          })}
        </nav>
      </section>
      {children}
    </div>
  );
}

export function Panel({ title, description, children, className }: { title: string; description?: string; children: ReactNode; className?: string }) {
  return (
    <section className={cn("overflow-hidden rounded-[12px] bg-[var(--background)]", className)}>
      <div className="bg-[var(--background)] px-4 py-3">
        <div className="text-sm font-normal text-[var(--foreground)]">{title}</div>
        {description ? <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{description}</div> : null}
      </div>
      {children}
    </section>
  );
}

export function Metric({ label, value, icon: Icon }: { label: string; value: number | string; icon: LucideIcon }) {
  return (
    <div className="rounded-[12px] bg-[var(--background)] p-4">
      <div className="flex items-center justify-center gap-2">
        <Icon className="h-4 w-4 text-[var(--accent-blue)]" />
        <span className="text-sm font-normal text-[var(--muted-foreground)]">{label}</span>
      </div>
      <div className="mt-3 text-center text-2xl font-normal text-[var(--foreground)]">{value}</div>
    </div>
  );
}

export function StatusPill({ value, tone }: { value: unknown; tone?: StatusTone }) {
  return <span className={cn("inline-flex min-h-7 items-center rounded-[8px] border px-2.5 py-1 text-xs font-medium", toneClassMap[tone ?? statusTone(value)])}>{textValue(value)}</span>;
}

export function SimpleRow({
  title,
  meta,
  status,
  href,
}: {
  title: ReactNode;
  meta: ReactNode;
  status?: unknown;
  href?: string;
}) {
  const content = (
    <>
      <div className="min-w-0">
        <div className="truncate text-sm font-normal text-[var(--foreground)]">{title}</div>
        <div className="mt-1 text-xs font-normal text-[var(--muted-foreground)]">{meta}</div>
      </div>
      <div className="flex shrink-0 items-center gap-3 sm:justify-end">
        {status ? <StatusPill value={status} /> : null}
      </div>
    </>
  );
  if (href) {
    return (
      <Link
        href={href}
        className="grid gap-2 border-b border-[var(--border)] px-4 py-2.5 transition hover:bg-[var(--surface-2)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)] last:border-b-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
      >
        {content}
      </Link>
    );
  }
  return (
    <div className="grid gap-2 border-b border-[var(--border)] px-4 py-2.5 last:border-b-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
      {content}
    </div>
  );
}

export function EmptyLine({ children }: { children: string }) {
  return (
    <div className="p-3">
      <EmptyState compact title={children} />
    </div>
  );
}

export function Evidence({ value }: { value: unknown }) {
  // 中文注释：证据区默认折叠，页面主信息保持短，排查时仍能看到后端原始载荷。
  return (
    <Collapsible className="bg-[var(--background)] px-5 py-3">
      <CollapsibleTrigger className="group flex w-full cursor-pointer items-center justify-between gap-3 text-left text-xs font-bold text-[var(--muted-foreground)] transition hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]">
        <span>查看证据</span>
        <span className="text-[11px] font-black text-[var(--accent-blue)] group-data-[state=open]:hidden">展开</span>
        <span className="hidden text-[11px] font-black text-[var(--accent-blue)] group-data-[state=open]:inline">收起</span>
      </CollapsibleTrigger>
      <CollapsibleContent className="data-[state=closed]:animate-[mf-collapsible-up_180ms_ease-out] data-[state=open]:animate-[mf-collapsible-down_180ms_ease-out]">
        <pre className="mt-2 max-h-64 overflow-auto bg-[var(--surface-1)] p-3 text-xs leading-6 text-[var(--foreground)]">{JSON.stringify(value ?? {}, null, 2)}</pre>
      </CollapsibleContent>
    </Collapsible>
  );
}
