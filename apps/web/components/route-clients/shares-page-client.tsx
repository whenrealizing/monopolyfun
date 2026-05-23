"use client";

import {useEffect, useState, useTransition} from "react";
import {useLocale, useTranslations} from "next-intl";
import {useSearchParams} from "next/navigation";
import {Landmark, TriangleAlert} from "lucide-react";

import {Link} from "@/i18n/navigation";
import {listAccountSharesLedger, listMarketSharesLedger, type SharesLedgerEntry} from "@/lib/api";
import {ClientOnlyMount} from "@/components/client-only-mount";
import {ShareReleaseApprovalPanel} from "@/components/share-release-approval-panel";
import {SharesSkeleton} from "@/components/page-skeletons";
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {EmptyState, ErrorState, PageContainer, PageIntro, PageSection} from "@/components/ui/page-layout";

type SharesTranslator = ReturnType<typeof useTranslations>;

export function SharesPageClient() {
  const t = useTranslations("Shares");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const accountId = searchParams.get("accountId")?.trim() ?? "";
  const marketId = searchParams.get("marketId")?.trim() ?? "";
  const [entries, setEntries] = useState<SharesLedgerEntry[]>([]);
  const [error, setError] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [, startTransition] = useTransition();

  useEffect(() => {
    let active = true;
    // 中文注释：查询条件变化时先进入骨架态，状态重置放入 transition 降低同步渲染压力。
    startTransition(() => {
      setLoaded(false);
      setError(false);
    });
    const operation = accountId
      ? listAccountSharesLedger(accountId, { limit: 50 })
      : marketId
        ? listMarketSharesLedger(marketId, { limit: 50 })
        : Promise.resolve([]);
    operation
      .then((nextEntries) => {
        if (!active) return;
        startTransition(() => {
          setEntries(nextEntries);
          setError(false);
          setLoaded(true);
        });
      })
      .catch(() => {
        if (!active) return;
        startTransition(() => {
          setEntries([]);
          setError(true);
          setLoaded(true);
        });
      });
    return () => {
      active = false;
    };
  }, [accountId, marketId]);

  return (
    <ClientOnlyMount fallback={<SharesSkeleton />}>
      <PageContainer>
        <PageSection tone="subtle" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
          <PageIntro
            heading={t("heading")}
            description={t("description")}
          />
        </PageSection>

        <PageSection tone="default" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
          <ShareReleaseApprovalPanel />
        </PageSection>

        <PageSection tone="default" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
          <form action={`/${locale}/shares`} noValidate className="grid gap-3 md:grid-cols-[1fr_1fr_auto]">
            <input name="accountId" defaultValue={accountId} className="mf-control-field px-3" placeholder={t("placeholders.accountId")} />
            <input name="marketId" defaultValue={marketId} className="mf-control-field px-3" placeholder={t("placeholders.marketId")} />
            <Button type="submit" variant="primary">{t("search")}</Button>
          </form>
          <div className="mt-4 divide-y divide-[var(--border)] bg-[var(--background)]">
            {!loaded ? (
              <div className="space-y-3 p-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={index} className="h-14 animate-pulse rounded-[6px] bg-[var(--surface-1)]" />
                ))}
              </div>
            ) : error ? (
              <ErrorState
                compact
                className="m-3 rounded-[6px] border-0 bg-[var(--surface-1)]"
                icon={<TriangleAlert className="h-7 w-7" />}
                title={t("error.title")}
                description={t("error.description")}
              />
            ) : entries.length > 0 ? entries.map((entry) => (
              <div key={entry.id} className="grid gap-2 px-4 py-3 md:grid-cols-[1fr_auto_auto]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-[var(--foreground)]">{t("holderRef", { ref: shortRef(entry.accountId) })}</span>
                    <Badge variant="outline">{reasonLabel(entry.reason, t)}</Badge>
                  </div>
                  <div className="mt-1 text-xs text-[var(--muted-foreground)]">
                    {t("entryMeta", { account: shortRef(entry.accountId), market: shortRef(entry.marketId), date: entry.createdAt })}
                  </div>
                </div>
                <div className="text-sm font-semibold text-[var(--foreground)]">{t("amount", { amount: entry.amount })}</div>
                <div className="text-xs text-[var(--muted-foreground)]">{t("entryRef", { ref: shortRef(entry.id) })}</div>
              </div>
            )) : (
              <EmptyState
                className="m-3 rounded-[6px] border-0 bg-[var(--surface-1)]"
                icon={<Landmark className="h-8 w-8" />}
                title={t("empty.title")}
                action={(
                  <Button asChild variant="outline" size="sm">
                    <Link href="/accounts">{t("empty.accountsLink")}</Link>
                  </Button>
                )}
              />
            )}
          </div>
        </PageSection>
      </PageContainer>
    </ClientOnlyMount>
  );
}

function shortRef(value: string | undefined | null) {
  if (!value) return "-";
  const clean = value.trim();
  return clean.length > 12 ? `#${clean.slice(-8)}` : clean;
}

function reasonLabel(reason: string | undefined | null, t: SharesTranslator) {
  const key = String(reason ?? "").toLowerCase();
  return key && t.has(`reasons.${key}`) ? t(`reasons.${key}`) : t("reasons.fallback");
}
