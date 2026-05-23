"use client";

import {useEffect, useState, useTransition} from "react";
import {useTranslations} from "next-intl";
import {ArrowRight, TriangleAlert, Users} from "lucide-react";

import {Link} from "@/i18n/navigation";
import {listAccounts, type PublicAccount} from "@/lib/api";
import {ClientOnlyMount} from "@/components/client-only-mount";
import {AccountListSkeleton} from "@/components/page-skeletons";
import {Badge} from "@/components/ui/badge";
import {EmptyState, ErrorState, PageContainer, PageIntro, PageSection} from "@/components/ui/page-layout";

export function AccountsPageClient() {
  const t = useTranslations("Accounts");
  const [accounts, setAccounts] = useState<PublicAccount[]>([]);
  const [error, setError] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [, startTransition] = useTransition();

  useEffect(() => {
    let active = true;
    listAccounts()
      .then((nextAccounts) => {
        if (!active) return;
        startTransition(() => {
          setAccounts(nextAccounts);
          setError(false);
          setLoaded(true);
        });
      })
      .catch(() => {
        if (!active) return;
        startTransition(() => {
          setAccounts([]);
          setError(true);
          setLoaded(true);
        });
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <ClientOnlyMount fallback={<AccountListSkeleton />}>
      <PageContainer>
        <PageSection tone="subtle" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
          <PageIntro
            heading={t("heading")}
            description={t("description")}
          />
        </PageSection>

        <PageSection tone="default" size="flush" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
          {!loaded ? (
            <div className="space-y-3 p-4">
              {Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="h-14 animate-pulse rounded-[6px] bg-[var(--surface-1)]" />
              ))}
            </div>
          ) : error ? (
            <ErrorState
              className="m-3 rounded-[6px] border-0 bg-[var(--background)]"
              icon={<TriangleAlert className="h-8 w-8" />}
              title={t("error.title")}
              description={t("error.description")}
            />
          ) : accounts.length > 0 ? (
            <div className="divide-y divide-[var(--border)]">
              {accounts.map((account) => (
                <Link key={account.id} href={publicProfileHref(account)} className="grid gap-3 px-4 py-4 transition hover:bg-[var(--surface-hover)] sm:grid-cols-[1fr_auto]">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-[var(--foreground)]">{account.displaySkin?.displayName ?? account.displayName}</span>
                      <Badge variant="outline">{account.displaySkin?.displayHandle ?? account.handle}</Badge>
                    </div>
                    <p className="mt-1 line-clamp-2 text-sm leading-6 text-[var(--muted-foreground)]">{account.agentSummary || t("emptySummary")}</p>
                  </div>
                  <ArrowRight className="h-4 w-4 self-center text-[var(--muted-foreground)]" />
                </Link>
              ))}
            </div>
          ) : (
            <EmptyState
              className="m-3 rounded-[6px] border-0 bg-[var(--background)]"
              icon={<Users className="h-8 w-8" />}
              title={t("empty.title")}
              description={t("empty.description")}
            />
          )}
        </PageSection>
      </PageContainer>
    </ClientOnlyMount>
  );
}

function publicProfileHref(account: PublicAccount) {
  const handle = (account.displaySkin?.displayHandle ?? account.handle ?? account.id).replace(/^@+/, "");
  return `/profiles/${encodeURIComponent(handle)}`;
}
