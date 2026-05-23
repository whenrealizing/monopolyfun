"use client";

import { useEffect, useState, useTransition } from "react";
import { CheckCircle2, RotateCcw } from "lucide-react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/page-layout";
import { ShareApprovalPanelSkeleton } from "@/components/page-skeletons";
import { useToast } from "@/components/ui/toast";
import { approveShareRelease, formatDate, listPendingShareReleases, type ShareReleaseRequest } from "@/lib/api";

export function ShareReleaseApprovalPanel() {
  const t = useTranslations("Shares.approvals");
  const toast = useToast();
  const tokenLabels = t.raw("tokens") as Record<string, string>;
  const [items, setItems] = useState<ShareReleaseRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [isPending, startTransition] = useTransition();

  function load() {
    setLoading(true);
    setError(false);
    listPendingShareReleases()
      .then(setItems)
      .catch(() => {
        setItems([]);
        setError(true);
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    queueMicrotask(load);
  }, []);

  function approve(requestId: string) {
    startTransition(async () => {
      try {
        await approveShareRelease(requestId);
        toast.notify({ tone: "success", title: t("approved") });
        load();
      } catch (approveError) {
        toast.notifyError(approveError, "ui.order.command.failed");
      }
    });
  }

  return (
    <section className="space-y-3 bg-[var(--background)] p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-black text-[var(--foreground)]">{t("title")}</div>
          <p className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{t("description")}</p>
        </div>
        <Button variant="outline" size="sm" loading={loading} disabled={loading || isPending} onClick={load}>
          <RotateCcw className="h-4 w-4" />
          {t("refresh")}
        </Button>
      </div>

      {loading ? (
        <ShareApprovalPanelSkeleton />
      ) : error ? (
        <EmptyState compact title={t("error")} />
      ) : items.length > 0 ? (
        <div className="grid gap-2">
          {items.map((item) => (
            <div key={item.id} className="grid gap-3 bg-[var(--background)] px-3 py-3 sm:grid-cols-[minmax(0,1fr)_auto]">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-black text-[var(--foreground)]">{t("amount", { amount: item.amount.toLocaleString() })}</span>
                  <span className="mf-chip">{localizeToken(item.status, tokenLabels)}</span>
                  <span className="mf-chip">{localizeToken(item.issuerType, tokenLabels)}</span>
                </div>
                <div className="mt-1 text-xs font-semibold leading-5 text-[var(--muted-foreground)]">
                  {t("meta", {
                    accountId: shortRef(item.accountId),
                    marketId: shortRef(item.marketId),
                    date: item.createdAt ? formatDate(item.createdAt) : t("pendingDate"),
                  })}
                </div>
                <div className="mt-1 text-xs font-semibold leading-5 text-[var(--muted-foreground)]">
                  {t("roles", {
                    approved: formatRoles(item.approvedRoleCodes, tokenLabels, t("none")),
                    required: formatRoles(item.requiredRoleCodes, tokenLabels, t("none")),
                  })}
                </div>
              </div>
              <Button variant="primary" size="sm" loading={isPending} disabled={isPending} onClick={() => approve(item.id)}>
                <CheckCircle2 className="h-4 w-4" />
                {t("approve")}
              </Button>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState compact title={t("empty")} />
      )}
    </section>
  );
}

function localizeToken(value: string, labels: Record<string, string>) {
  return labels[value] ?? value.replace(/[_-]+/g, " ");
}

function formatRoles(roleCodes: string[], labels: Record<string, string>, emptyLabel: string) {
  return roleCodes.map((roleCode) => localizeToken(roleCode, labels)).join(" / ") || emptyLabel;
}

function shortRef(value: string | undefined | null) {
  if (!value) return "-";
  const clean = value.trim();
  return clean.length > 12 ? `#${clean.slice(-8)}` : clean;
}
