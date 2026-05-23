import type { ReactNode } from "react";
import { Link } from "@/i18n/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { BadgeCheck, Github, Home, Search, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { EmptyState, PageSection } from "@/components/ui/page-layout";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SurfaceFilterMenu } from "@/components/surface-filter-menu";
import { SurfaceSortMenu } from "@/components/surface-sort-menu";
import {
  buildSurfaceOwnerIdentity,
  MARKET_SURFACE_META,
  surfaceAccentStyle,
} from "@/components/market-card-primitives";
import type { Account, OfferPost, PostItemSummary, ProjectPost, RequestPost } from "@/lib/api";
import { offerHref, projectHref, requestHref } from "@/lib/business-routes";
import { cn } from "@/lib/utils";

export type FeedItem =
  | ({ kind: "offer"; href: string } & OfferPost)
  | ({ kind: "request"; href: string } & RequestPost)
  | ({ kind: "project"; href: string } & ProjectPost);

export type FeedKind = "all" | FeedItem["kind"];
export type FeedSort = "recent" | "oldest" | "title";

const KIND_FILTERS: Array<{
  kind: FeedKind;
  labelKey: string;
  shortLabelKey: string;
}> = [
  { kind: "all", labelKey: "filters.all", shortLabelKey: "filters.allShort" },
  { kind: "offer", labelKey: "filters.offer", shortLabelKey: "filters.offerShort" },
  { kind: "request", labelKey: "filters.request", shortLabelKey: "filters.requestShort" },
  { kind: "project", labelKey: "filters.project", shortLabelKey: "filters.projectShort" },
];

const SORT_FILTERS: Array<{
  sort: FeedSort;
  labelKey: string;
  shortLabelKey: string;
}> = [
  { sort: "recent", labelKey: "sort.recent", shortLabelKey: "sortShort.recent" },
  { sort: "oldest", labelKey: "sort.oldest", shortLabelKey: "sortShort.oldest" },
];

type MarketSurfaceTranslator = Awaited<ReturnType<typeof getTranslations>>;

function amountLabel(item: FeedItem, t: MarketSurfaceTranslator) {
  if (item.kind === "offer") {
    const fallback = item.priceAmount ? formatMarketMoney(item.priceAmount, item.currency) : t("amount.pricePending");
    return item.itemSummary ? formatSummaryAmount(item.itemSummary, fallback) : fallback;
  }
  if (item.kind === "request") {
    const fallback = item.budgetAmount ? formatMarketMoney(item.budgetAmount, item.currency) : t("amount.budgetPending");
    return item.itemSummary ? formatSummaryAmount(item.itemSummary, fallback) : fallback;
  }
  return t("amount.projectSlot");
}

function formatMarketMoney(amount: number, currency: string) {
  const value = Number.isInteger(amount) ? amount.toFixed(0) : amount.toFixed(2);
  return currency === "USD" ? `$${value}` : `${value} ${currency}`;
}

function formatSummaryAmount(summary: PostItemSummary, fallback: string) {
  if (summary.minAmount == null || summary.maxAmount == null) return fallback;
  const currency = summary.currency ?? "USD";
  if (summary.minAmount === summary.maxAmount) return formatMarketMoney(summary.minAmount, currency);
  return `${formatMarketMoney(summary.minAmount, currency)}-${formatMarketMoney(summary.maxAmount, currency)}`;
}

function inventoryLabel(item: FeedItem, t: MarketSurfaceTranslator) {
  if (item.kind === "offer") {
    if (item.itemSummary?.totalQuantity) return t("inventory.remaining", { count: item.itemSummary.remainingQuantity ?? 0 });
    return item.stockTotal ? t("inventory.remaining", { count: Math.max(item.stockTotal - item.stockSold, 0) }) : t("inventory.unlimited");
  }
  if (item.kind === "request") {
    if (item.itemSummary?.totalQuantity) return t("inventory.claimable", { remaining: item.itemSummary.remainingQuantity ?? 0, total: item.itemSummary.totalQuantity });
    return item.stockTotal ? t("inventory.claimable", { remaining: Math.max(item.stockTotal - item.stockFilled, 0), total: item.stockTotal }) : t("inventory.unlimitedSlots");
  }
  return item.stockTotal ? t("inventory.joined", { joined: item.stockSold, total: item.stockTotal }) : t("inventory.unlimitedSlots");
}

function settlementLabel(item: FeedItem, t: MarketSurfaceTranslator) {
  if (item.kind === "project") return t("card.settlement.shares");
  if (item.paymentMethod === "shares") return t("card.settlement.shares");
  if (item.paymentMethod === "okx_direct_pay") return t("card.settlement.cash");
  return null;
}

function compactMetaLabel(...items: Array<string | null | undefined>) {
  return items.filter((item) => item && item.trim()).join(" · ");
}

function summaryText(item: FeedItem) {
  return item.kind === "project" ? item.summary : item.description;
}

function actionLabel(item: FeedItem, t: MarketSurfaceTranslator) {
  if (item.kind === "offer") return t("card.actions.offer");
  if (item.kind === "request") return t("card.actions.request");
  return t("card.actions.project");
}

function feedItemKey(item: FeedItem) {
  if (item.kind === "offer") return `${item.kind}:${item.offerNo}`;
  if (item.kind === "request") return `${item.kind}:${item.requestNo}`;
  return `${item.kind}:${item.projectNo}`;
}

function relativeDateLabel(value: string, locale: string) {
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;
  const diffSeconds = Math.round((timestamp - Date.now()) / 1000);
  const absSeconds = Math.abs(diffSeconds);
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ["year", 60 * 60 * 24 * 365],
    ["month", 60 * 60 * 24 * 30],
    ["day", 60 * 60 * 24],
    ["hour", 60 * 60],
    ["minute", 60],
  ];
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  const [unit, seconds] = units.find(([, unitSeconds]) => absSeconds >= unitSeconds) ?? ["minute", 60];
  return formatter.format(Math.round(diffSeconds / seconds), unit);
}

function ownerBadge(item: FeedItem, owner: ReturnType<typeof buildSurfaceOwnerIdentity>, t: MarketSurfaceTranslator) {
  if (owner.verified) return { icon: Github, label: t("card.owner.githubVerified") };
  if (item.kind === "project" && item.projectLevel === "root") return { icon: ShieldCheck, label: t("card.owner.coreProject") };
  return null;
}

export function toFeedItems(offers: OfferPost[], requests: RequestPost[], projects: ProjectPost[]): FeedItem[] {
  return [
    ...offers.map((offer) => ({ ...offer, kind: "offer" as const, href: offerHref(offer) })),
    ...requests.map((request) => ({ ...request, kind: "request" as const, href: requestHref(request) })),
    ...projects.map((project) => ({ ...project, kind: "project" as const, href: projectHref(project) })),
  ].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
}

export function countByKind(items: FeedItem[]) {
  return {
    all: items.length,
    offer: items.filter((item) => item.kind === "offer").length,
    request: items.filter((item) => item.kind === "request").length,
    project: items.filter((item) => item.kind === "project").length,
  };
}

export function filterItems(items: FeedItem[], kind: FeedKind) {
  return kind === "all" ? items : items.filter((item) => item.kind === kind);
}

export function sortItems(items: FeedItem[], sort: FeedSort) {
  return [...items].sort((left, right) => {
    if (sort === "oldest") return Date.parse(left.updatedAt) - Date.parse(right.updatedAt);
    if (sort === "title") return left.title.localeCompare(right.title);
    return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
  });
}

export async function SurfaceStatGrid({
  items,
  accountCount,
}: {
  items: FeedItem[];
  accountCount: number;
}) {
  const t = await getTranslations("MarketSurface");
  const counts = countByKind(items);
  const stats = [
    { label: t("stats.all"), value: counts.all, tone: "var(--primary)" },
    { label: t("stats.offers"), value: counts.offer, tone: "var(--market-opportunity-offer)" },
    { label: t("stats.requests"), value: counts.request, tone: "var(--market-opportunity-request)" },
    { label: t("stats.projects"), value: counts.project, tone: "var(--market-opportunity-project-work)" },
    { label: t("stats.accounts"), value: accountCount, tone: "var(--accent-blue)" },
  ];

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {stats.map((stat) => (
        <div key={stat.label} className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] p-4">
          <div className="text-[11px] font-medium uppercase tracking-[0.12em] text-[var(--muted-foreground)]">{stat.label}</div>
          <div className="mt-3 text-[1.6rem] font-medium leading-none" style={{ color: stat.tone }}>
            {stat.value}
          </div>
        </div>
      ))}
    </div>
  );
}

export function SurfaceSectionHeader({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
      <div className="space-y-1.5">
        <h2 className="text-xl font-medium leading-7 text-[var(--foreground)]">{title}</h2>
        <p className="max-w-3xl text-sm leading-7 text-[var(--muted-foreground)]">{description}</p>
      </div>
      {action ? <div className="flex shrink-0 flex-wrap gap-2">{action}</div> : null}
    </div>
  );
}

export function SurfaceStageSection({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <PageSection tone="subtle" size="lg" className={cn("mf-page-panel", className)}>
      <div className="space-y-5">{children}</div>
    </PageSection>
  );
}

export async function SurfaceFilterChips({
  activeKind,
  hrefBuilder,
}: {
  activeKind: FeedKind;
  hrefBuilder: (kind: FeedKind) => string;
}) {
  const t = await getTranslations("MarketSurface");
  return (
    <>
      <div className="md:hidden">
        <SurfaceFilterMenu
          activeKind={activeKind}
          label={t("filters.label")}
          options={KIND_FILTERS.map((filter) => ({
            kind: filter.kind,
            label: t(filter.labelKey),
            shortLabel: t(filter.shortLabelKey),
            href: hrefBuilder(filter.kind),
          }))}
        />
      </div>
      <div className="hidden min-w-0 flex-1 overflow-x-auto md:flex [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
        <div className="flex gap-2">
          {KIND_FILTERS.map((filter) => (
            <Link
              key={filter.kind}
              href={hrefBuilder(filter.kind)}
              className={cn("om-chip", activeKind === filter.kind ? "om-chip-active" : null)}
            >
              <span>{t(filter.shortLabelKey)}</span>
            </Link>
          ))}
        </div>
      </div>
    </>
  );
}

export async function SurfaceSortChips({
  activeSort,
  hrefBuilder,
}: {
  activeSort: FeedSort;
  hrefBuilder: (sort: FeedSort) => string;
}) {
  const t = await getTranslations("MarketSurface");
  return (
    <>
      <div className="md:hidden">
        <SurfaceSortMenu
          activeSort={activeSort}
          label={t("sort.label")}
          options={SORT_FILTERS.map((filter) => ({
            sort: filter.sort,
            label: t(filter.labelKey),
            shortLabel: t(filter.shortLabelKey),
            href: hrefBuilder(filter.sort),
          }))}
        />
      </div>
      <div className="hidden shrink-0 gap-1.5 md:flex">
        {SORT_FILTERS.map((filter) => (
          <Link
            key={filter.sort}
            href={hrefBuilder(filter.sort)}
            className={cn("om-chip", activeSort === filter.sort ? "om-chip-active" : null)}
          >
            <span>{t(filter.labelKey)}</span>
          </Link>
        ))}
      </div>
    </>
  );
}

export function SurfaceFilterDivider() {
  return <span className="inline-block w-px shrink-0 self-stretch bg-[var(--border)]" />;
}

export async function SurfaceToolbar({
  defaultKind,
  defaultSort,
  defaultQuery,
}: {
  defaultKind: FeedKind;
  defaultSort: "recent" | "oldest" | "title";
  defaultQuery: string;
}) {
  const t = await getTranslations("MarketSurface");
  return (
    <form action="/" noValidate className="grid gap-3 rounded-[12px] border border-[var(--border)] bg-[var(--surface-panel)] p-3 lg:grid-cols-[160px_minmax(0,1fr)_160px_auto]">
      <label className="grid gap-2">
        <span className="text-[11px] font-medium uppercase tracking-[0.12em] text-[var(--muted-foreground)]">{t("toolbar.section")}</span>
        <Select name="kind" defaultValue={defaultKind}>
          <SelectTrigger className="h-11">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("filters.all")}</SelectItem>
            <SelectItem value="offer">{t("filters.offer")}</SelectItem>
            <SelectItem value="request">{t("filters.request")}</SelectItem>
            <SelectItem value="project">{t("filters.project")}</SelectItem>
          </SelectContent>
        </Select>
      </label>

      <label className="grid gap-2">
        <span className="text-[11px] font-medium uppercase tracking-[0.12em] text-[var(--muted-foreground)]">{t("toolbar.search")}</span>
        <div className="relative">
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <input
            name="q"
            defaultValue={defaultQuery}
            placeholder={t("toolbar.searchPlaceholder")}
            className="mf-control-field h-11 w-full pl-11 pr-4"
          />
        </div>
      </label>

      <label className="grid gap-2">
        <span className="text-[11px] font-medium uppercase tracking-[0.12em] text-[var(--muted-foreground)]">{t("toolbar.sort")}</span>
        <Select name="sort" defaultValue={defaultSort}>
          <SelectTrigger className="h-11">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="recent">{t("sort.recent")}</SelectItem>
            <SelectItem value="oldest">{t("sort.oldest")}</SelectItem>
            <SelectItem value="title">{t("sort.title")}</SelectItem>
          </SelectContent>
        </Select>
      </label>

      <div className="flex items-end gap-2">
        <Button type="submit" variant="primary" className="h-11 px-5">
          {t("toolbar.apply")}
        </Button>
        <Button asChild variant="outline" className="h-11 px-5">
          <Link href="/">{t("toolbar.reset")}</Link>
        </Button>
      </div>
    </form>
  );
}

export async function OpportunityRail({
  items,
  accountsById,
}: {
  items: FeedItem[];
  accountsById: Record<string, Account>;
}) {
  const t = await getTranslations("MarketSurface");
  if (items.length === 0) {
    return <SurfaceEmptyState title={t("empty.featuredTitle")} description={t("empty.featuredDescription")} />;
  }

  return (
    <div className="-mx-2 overflow-x-auto px-2 pb-2 pt-1 [scroll-snap-type:x_mandatory] [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
      <div className="flex gap-2.5 pr-10">
        {items.map((item) => (
          <div key={feedItemKey(item)} className="w-[min(68vw,320px)] shrink-0 sm:w-[min(82vw,360px)]">
            <OpportunityCard item={item} accountsById={accountsById} variant="featured" />
          </div>
        ))}
      </div>
    </div>
  );
}

export async function OpportunityGrid({
  items,
  accountsById,
}: {
  items: FeedItem[];
  accountsById: Record<string, Account>;
}) {
  const t = await getTranslations("MarketSurface");
  if (items.length === 0) {
    return <SurfaceEmptyState title={t("empty.filteredTitle")} description={t("empty.filteredDescription")} />;
  }

  return (
    <div className="grid grid-cols-2 gap-x-2 gap-y-3 sm:grid-cols-[repeat(auto-fill,minmax(min(100%,240px),1fr))] sm:gap-x-3 sm:gap-y-5">
      {items.map((item) => (
        <OpportunityCard key={feedItemKey(item)} item={item} accountsById={accountsById} />
      ))}
    </div>
  );
}

async function OpportunityCard({
  item,
  accountsById,
  variant = "grid",
}: {
  item: FeedItem;
  accountsById: Record<string, Account>;
  variant?: "featured" | "grid";
}) {
  const t = await getTranslations("MarketSurface");
  const locale = await getLocale();
  const meta = MARKET_SURFACE_META[item.kind];
  // 中文注释：公开市场卡片只用公开 handle 补齐人物展示，避免把内部账号主键当作对外身份入口。
  const ownerId = item.kind === "project" ? item.ownerHandle : item.actorHandle;
  const owner = buildSurfaceOwnerIdentity(ownerId, accountsById);
  const updatedAt = relativeDateLabel(item.updatedAt, locale);
  const summary = summaryText(item);
  const badge = ownerBadge(item, owner, t);
  const OwnerBadgeIcon = badge?.icon;
  const featured = variant === "featured";
  const amount = amountLabel(item, t);
  const inventory = inventoryLabel(item, t);
  const settlement = settlementLabel(item, t);
  const metaLabel = compactMetaLabel(inventory, settlement);

  if (featured) {
    return (
      <article
        className={cn(
          "group relative isolate flex h-[208px] flex-col overflow-hidden rounded-[10px] border border-[var(--border-strong)] bg-[var(--background)] p-3.5 transition duration-200 hover:scale-[1.01] hover:border-[color-mix(in_srgb,var(--opportunity-accent)_72%,var(--border-strong))] sm:h-[212px]",
        )}
        style={{
          ...surfaceAccentStyle(meta.accent),
          ...(item.kind === "project" ? { borderColor: "var(--primary)", borderWidth: "1px" } : {}),
        }}
      >
        <Link href={item.href} className="absolute inset-0 z-0" aria-label={actionLabel(item, t)} />
        <div className="pointer-events-none relative z-10 flex items-center justify-between gap-3">
          <span
            className="inline-flex h-6 min-w-11 items-center justify-center rounded-[7px] px-2 text-[11px] font-normal"
            style={{
              backgroundColor: "color-mix(in srgb, var(--opportunity-accent) 13%, transparent)",
              color: "var(--opportunity-accent)",
            }}
          >
            {meta.heading}
          </span>
          <span className="truncate text-[11px] font-normal text-[var(--muted-foreground)]">{updatedAt}</span>
        </div>

        <div className="pointer-events-none relative z-10 mt-5 min-w-0">
          <h3 className="flex min-h-[23px] min-w-0 items-center text-[19px] font-medium leading-[1.18] text-[var(--foreground)]">
            <span className="min-w-0 flex-1 truncate">{item.title}</span>
            {badge && OwnerBadgeIcon ? (
              <span className="ml-2 inline-flex h-6 max-w-[45%] shrink-0 items-center gap-1 align-middle text-[11px] font-normal text-[var(--muted-foreground)]">
                <span className="truncate">{badge.label}</span>
                <BadgeCheck className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--opportunity-accent)" }} />
              </span>
            ) : null}
          </h3>
          <div
            aria-hidden={item.kind === "project"}
            className={cn(
              "mt-3 truncate text-[22px] font-black leading-none text-[var(--foreground)]",
              item.kind === "project" ? "invisible" : null,
            )}
          >
            {item.kind === "project" ? "\u00a0" : amount}
          </div>
          <p className="mt-2.5 line-clamp-2 min-h-[40px] text-[13px] leading-5 text-[var(--muted-foreground)]">
            {summary || t("card.noSummary")}
          </p>
        </div>

        <div className="relative z-10 mt-auto flex items-center justify-between gap-3 pt-4 text-xs font-normal text-[var(--muted-foreground)]">
          <OwnerHandle owner={owner} />
          {metaLabel ? <span className="pointer-events-none shrink-0 truncate">{metaLabel}</span> : null}
        </div>
      </article>
    );
  }

  return (
    <article
      className={cn(
        "group relative isolate flex h-[208px] flex-col overflow-hidden rounded-[10px] border border-[var(--border-strong)] bg-[var(--background)] p-2.5 transition duration-200 hover:scale-[1.01] hover:border-[color-mix(in_srgb,var(--opportunity-accent)_72%,var(--border-strong))] hover:bg-[linear-gradient(180deg,color-mix(in_srgb,var(--opportunity-accent)_5%,transparent),transparent_58%),var(--background)] sm:h-[224px] sm:p-3.5",
      )}
      style={{
        ...surfaceAccentStyle(meta.accent),
        ...(item.kind === "project" ? { borderColor: "var(--primary)", borderWidth: "1px" } : {}),
      }}
    >
      <Link href={item.href} className="absolute inset-0 z-0" aria-label={actionLabel(item, t)} />
      <div className="pointer-events-none relative z-10 flex items-center justify-between gap-3">
        <span
          className="inline-flex h-6 min-w-9 items-center justify-center rounded-[7px] px-1.5 text-[14px] font-normal sm:min-w-11 sm:px-2 sm:text-[11px]"
          style={{
            backgroundColor: "color-mix(in srgb, var(--opportunity-accent) 13%, transparent)",
            color: "var(--opportunity-accent)",
          }}
        >
          {meta.heading}
        </span>
        <span className="truncate text-[14px] font-normal text-[var(--muted-foreground)] sm:text-[11px]">{updatedAt}</span>
      </div>

      <div className="pointer-events-none relative z-10 mt-3 min-w-0 sm:mt-4">
        <h3 className="flex min-h-[20px] min-w-0 items-center text-[16px] font-medium leading-[1.2] text-[var(--foreground)] sm:min-h-[21px] sm:text-[17px] sm:leading-[1.22]">
          <span className="min-w-0 flex-1 truncate">{item.title}</span>
          {badge && OwnerBadgeIcon ? (
            <span className="ml-1.5 hidden h-6 max-w-[42%] shrink-0 items-center gap-1 align-middle text-[11px] font-normal text-[var(--muted-foreground)] sm:inline-flex">
              <span className="truncate">{badge.label}</span>
              <BadgeCheck className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--opportunity-accent)" }} />
            </span>
          ) : null}
        </h3>
        <div
          aria-hidden={item.kind === "project"}
          className={cn(
            "mt-2 truncate text-[16px] font-black leading-none text-[var(--foreground)] sm:mt-3 sm:text-[20px]",
            item.kind === "project" ? "invisible" : null,
          )}
        >
          {item.kind === "project" ? "\u00a0" : amount}
        </div>
        {summary ? (
          <p className="mt-1.5 line-clamp-2 min-h-[40px] text-[14px] leading-5 text-[var(--muted-foreground)] sm:mt-2 sm:text-[13px]">{summary}</p>
        ) : (
          <p className="mt-1.5 min-h-[40px] text-[14px] leading-5 text-[var(--muted-foreground)] sm:mt-2 sm:text-[13px]">{t("card.noSummary")}</p>
        )}
      </div>

      <div className="pointer-events-none relative z-10 mt-2 flex min-w-0 items-center gap-2 text-[14px] font-normal text-[var(--muted-foreground)] sm:mt-3 sm:text-xs">
        <span className="truncate">{metaLabel}</span>
      </div>

      <div className="relative z-10 mt-auto flex items-center gap-1.5 pt-2 sm:gap-2 sm:pt-3">
        <span
          className="pointer-events-none flex h-3.5 w-3.5 shrink-0 items-center justify-center overflow-hidden rounded-full text-[7px] font-normal text-white sm:h-4 sm:w-4 sm:text-[8px]"
          style={{ background: `linear-gradient(135deg, hsl(${owner.hue} 76% 48%), hsl(${(owner.hue + 36) % 360} 72% 36%))` }}
        >
          {owner.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={owner.avatarUrl} alt="" className="h-full w-full object-cover" />
          ) : owner.initials}
        </span>
        <OwnerHandle owner={owner} />
        <span className="sr-only">{actionLabel(item, t)}</span>
      </div>
    </article>
  );
}

function OwnerHandle({ owner }: { owner: ReturnType<typeof buildSurfaceOwnerIdentity> }) {
  if (!owner.profileHref) {
    return (
      <span className="min-w-0 truncate text-[14px] font-normal leading-4" style={{ color: "var(--muted-foreground)" }}>
        {owner.handle}
      </span>
    );
  }
  return (
    <Link
      href={owner.profileHref}
      className="mf-owner-handle pointer-events-auto relative z-20 inline-flex min-w-0 max-w-full truncate text-[14px] font-normal leading-4"
      style={{ color: "var(--muted-foreground)" }}
    >
      {owner.handle}
    </Link>
  );
}

function SurfaceEmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return <EmptyState title={title} description={description} />;
}

export async function HomeSurfaceActions() {
  const t = await getTranslations("MarketSurface");
  return (
    <Button asChild variant="primary">
      <Link href="/publish?type=trade">{t("actions.publish")}</Link>
    </Button>
  );
}

export async function MarketSurfaceActions() {
  const t = await getTranslations("MarketSurface");
  return (
    <>
      <Button asChild variant="primary">
        <Link href="/publish?type=trade">{t("actions.publish")}</Link>
      </Button>
      <Button asChild variant="outline">
        <Link href="/">
          <Home className="h-4 w-4" />
          {t("actions.backHome")}
        </Link>
      </Button>
    </>
  );
}
