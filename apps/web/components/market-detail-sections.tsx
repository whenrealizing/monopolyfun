import type { ElementType, ReactNode } from "react";
import { getTranslations } from "next-intl/server";
import { ChevronDown } from "lucide-react";

import { SurfaceHeroCard, type MarketSurfaceKind } from "@/components/market-card-primitives";
import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Link } from "@/i18n/navigation";

export async function MarketIntroSection({
  kind,
  id,
  title,
  summary,
  amountLabel,
  tags,
}: {
  kind: MarketSurfaceKind;
  id: string;
  title: string;
  summary: string;
  amountLabel: string;
  tags: string[];
}) {
  const t = await getTranslations("MarketSurface");
  return (
    <section className="grid gap-5 lg:grid-cols-[360px_minmax(0,1fr)]">
      <SurfaceHeroCard
        kind={kind}
        id={id}
        title={title}
        amountLabel={amountLabel}
        label={t(`filters.${kind}Short`)}
        aspectClassName="aspect-[1.42/1] min-h-[240px]"
        titleClassName="text-[32px]"
      />
      <div className="space-y-4">
        <div className="space-y-3">
          <h1 className="text-[32px] font-normal leading-tight text-[var(--foreground)]">{title}</h1>
          <p className="max-w-3xl whitespace-pre-line text-sm leading-7 text-[var(--muted-foreground)]">{summary}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {tags.map((tag) => (
            <Badge key={tag} variant="outline">{tag}</Badge>
          ))}
        </div>
      </div>
    </section>
  );
}

export function MarketOperationsRow({
  items,
}: {
  items: Array<{ icon: ElementType; label: string; value: string; helper?: string }>;
}) {
  return (
    <section className="grid gap-2 bg-[var(--background)] p-3 sm:grid-cols-3">
      {items.map((item) => (
        <WorkspaceMetric key={item.label} {...item} />
      ))}
    </section>
  );
}

export async function MarketPublisherSection({
  title,
  description,
  name,
  handle,
  initials,
  hue,
  avatarUrl,
  summary,
  badges,
  profileHref,
}: {
  title?: string;
  description?: string;
  name: string;
  handle: string;
  initials: string;
  hue: number;
  avatarUrl?: string | null;
  summary?: string;
  badges: string[];
  profileHref?: string | null;
}) {
  const t = await getTranslations("MarketDetailShared");
  const resolvedTitle = title ?? t("publisherTitle");
  return (
    <DetailBlock title={resolvedTitle} description={description ?? (title ? undefined : t("publisherDescription"))}>
      <ProfileIdentityLink href={profileHref}>
        <span
          className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-full text-lg font-medium text-white ring-1 ring-[rgba(255,255,255,0.12)]"
          style={{ background: `linear-gradient(135deg, hsl(${hue} 76% 48%), hsl(${(hue + 36) % 360} 72% 36%))` }}
        >
          {avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={avatarUrl} alt="" className="h-full w-full object-cover" />
          ) : initials}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-lg font-normal text-[var(--foreground)]">{name}</span>
          <span className="mt-1 block truncate text-sm text-[var(--muted-foreground)]">{handle}</span>
        </span>
      </ProfileIdentityLink>
      {summary ? <p className="mt-4 whitespace-pre-line text-sm leading-6 text-[var(--muted-foreground)]">{summary}</p> : null}
      {badges.length > 0 ? (
        <div className="mt-4 flex flex-wrap gap-2">
          {badges.map((badge) => (
            <Badge key={badge} variant="outline">{badge}</Badge>
          ))}
        </div>
      ) : null}
    </DetailBlock>
  );
}

function ProfileIdentityLink({ href, children }: { href?: string | null; children: ReactNode }) {
  const className = "flex items-center gap-3 transition hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]";
  return href ? <Link href={href} className={className}>{children}</Link> : <div className={className}>{children}</div>;
}

export function DetailBlock({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section className="bg-[var(--background)] p-4">
      <div className="space-y-1.5">
        <h2 className="text-sm font-medium text-[var(--foreground)]">{title}</h2>
        {description ? <p className="text-sm leading-6 text-[var(--muted-foreground)]">{description}</p> : null}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function BaseInfoGrid({
  items,
}: {
  items: Array<{ label: string; value: string }>;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item) => (
        <div key={item.label} className="bg-[var(--background)] px-3 py-3">
          <div className="text-[11px] font-medium uppercase tracking-[0.14em] text-[var(--muted-foreground)]">{item.label}</div>
          <div className="mt-2 text-sm text-[var(--foreground)]">{item.value}</div>
        </div>
      ))}
    </div>
  );
}

export function AdvancedInfoDetails({
  title,
  description,
  items,
}: {
  title: string;
  description?: string;
  items: Array<{ label: string; value: string }>;
}) {
  return (
    <Collapsible className="bg-[var(--surface-1)]">
      <CollapsibleTrigger className="group flex w-full items-center justify-between gap-3 px-3 py-3 text-left text-sm font-medium text-[var(--foreground)] transition hover:bg-[var(--surface-hover)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]">
        <span>{title}</span>
        <ChevronDown className="h-4 w-4 text-[var(--muted-foreground)] transition-transform group-data-[state=open]:rotate-180" />
      </CollapsibleTrigger>
      <CollapsibleContent className="px-3 pb-3 data-[state=closed]:animate-[mf-collapsible-up_180ms_ease-out] data-[state=open]:animate-[mf-collapsible-down_180ms_ease-out]">
        {description ? <p className="text-sm leading-6 text-[var(--muted-foreground)]">{description}</p> : null}
        <div className="mt-3">
          <BaseInfoGrid items={items} />
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function WorkspaceMetric({
  icon: Icon,
  label,
  value,
  helper,
}: {
  icon: ElementType;
  label: string;
  value: string;
  helper?: string;
}) {
  return (
    <div className="bg-[var(--background)] px-4 py-3">
      <div className="flex items-center gap-2 text-[11px] font-medium uppercase tracking-[0.14em] text-[var(--muted-foreground)]">
        <Icon className="h-4 w-4" />
        <span>{label}</span>
      </div>
      <div className="mt-2 text-xl font-medium text-[var(--foreground)]">{value}</div>
      {helper ? <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{helper}</div> : null}
    </div>
  );
}
