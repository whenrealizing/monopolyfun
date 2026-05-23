"use client";

import { Link, useRouter } from "@/i18n/navigation";
import { useMemo, useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { ArrowRight, CheckCircle2, ClipboardPaste, PackageCheck } from "lucide-react";

import { useAuthGate } from "@/components/auth-required-gate";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/page-layout";
import { useToast } from "@/components/ui/toast";
import { claimPostItemWithDeliveryInput, type PostItem } from "@/lib/api";
import { shouldOpenPaymentRequired } from "@/lib/order-routing";
import { cn } from "@/lib/utils";

const EVM_ADDRESS_PATTERN = /^0x[0-9a-fA-F]{40}$/;

function itemCanBeClaimed(item: PostItem) {
  return item.status === "open" || item.status === "released";
}

function paymentStatusLabel(item: PostItem, t: ReturnType<typeof useTranslations>) {
  if (!item.activeOrderNo || item.settlementType !== "money" || !item.activeOrderPaymentRequired) {
    return null;
  }
  const status = String(item.activeOrderPaymentStatus ?? "missing").toLowerCase();
  return t.has(`paymentStatus.${status}`) ? t(`paymentStatus.${status}`) : t("paymentStatus.pending");
}

function paymentStatusTone(item: PostItem) {
  return String(item.activeOrderPaymentStatus ?? "").toLowerCase() === "captured" ? "success" : "warning";
}

export function RequestItemPicker({
  ownerHandle,
  returnTo,
  items,
}: {
  ownerHandle?: string | null;
  returnTo: string;
  items: PostItem[];
}) {
  const t = useTranslations("Orders.requestItemPicker");
  const toast = useToast();
  const router = useRouter();
  const { session, requireSession } = useAuthGate();
  const [selectedId, setSelectedId] = useState(items.find(itemCanBeClaimed)?.id ?? items[0]?.id ?? "");
  const [buyerNote, setBuyerNote] = useState("");
  const [paymentRecipient, setPaymentRecipient] = useState("");
  const [directDeliveryPhone, setDirectDeliveryPhone] = useState("");
  const [isPending, startTransition] = useTransition();
  const selectedItem = useMemo(() => items.find((item) => item.id === selectedId) ?? items[0], [items, selectedId]);
  const normalizedOwnerHandle = ownerHandle?.replace(/^@+/, "").toLowerCase();
  const normalizedSessionHandle = session?.handle?.replace(/^@+/, "").toLowerCase();
  // 中文注释：公开 Request 接单入口只用发布人 handle 避免自接单，账号主键只在提交命令时取当前会话。
  const isOwner = !!normalizedOwnerHandle && normalizedOwnerHandle === normalizedSessionHandle;
  const canClaimSelected = selectedItem ? itemCanBeClaimed(selectedItem) && !isOwner : false;
  const activeOrderBelongsToSession = Boolean(session?.accountId && selectedItem?.claimedByAccountId === session.accountId);
  const requiresOkxRecipient = canClaimSelected && selectedItem?.settlementType === "money";
  const paymentRecipientValid = !requiresOkxRecipient || EVM_ADDRESS_PATTERN.test(paymentRecipient.trim());
  const directDeliveryReady = !isInstantDeliveryMode(selectedItem?.deliveryMode) || /^1[3-9]\d{9}$/.test(directDeliveryPhone.replace(/\D/g, ""));
  const selectedPaymentLabel = selectedItem ? paymentStatusLabel(selectedItem, t) : null;

  async function pastePaymentRecipient() {
    if (typeof navigator === "undefined" || !navigator.clipboard?.readText) {
      toast.notify({ tone: "error", title: t("errors.clipboardReadFailed") });
      return;
    }
    try {
      const text = await navigator.clipboard.readText();
      setPaymentRecipient(text.trim());
    } catch {
      toast.notify({ tone: "error", title: t("errors.clipboardReadFailed") });
    }
  }

  function submit() {
    if (!selectedItem) return;
    const activeSession = requireSession(returnTo);
    if (!activeSession) return;
    if (selectedItem.activeOrderNo && !canClaimSelected) {
      if (activeOrderBelongsToSession) router.push(`/orders/${encodeURIComponent(selectedItem.activeOrderNo)}`);
      return;
    }
    if (!paymentRecipientValid) {
      toast.notify({ tone: "error", title: t("errors.invalidRecipient") });
      return;
    }

    startTransition(async () => {
      try {
        // 中文注释：request 的卖方在接单时确定，执行人钱包随 claim 写入订单快照供买方付款。
        const deliveryInput = isInstantDeliveryMode(selectedItem.deliveryMode)
          ? { phone: directDeliveryPhone.replace(/\D/g, ""), amount: selectedItem.budgetAmount ?? selectedItem.priceAmount ?? 0 }
          : undefined;
        const receipt = await claimPostItemWithDeliveryInput(selectedItem.id, activeSession.accountId, buyerNote, deliveryInput, requiresOkxRecipient ? paymentRecipient.trim() : undefined);
        const orderNo = typeof receipt.payload?.orderNo === "string" ? receipt.payload.orderNo : "";
        const paymentRequired = shouldOpenPaymentRequired(receipt, activeSession.accountId, selectedItem.settlementType);
        router.push(`/orders/${encodeURIComponent(orderNo)}${paymentRequired ? "?payment=required" : ""}`);
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      }
    });
  }

  if (items.length === 0) {
    return (
      <EmptyState compact title={t("empty")} description={t("emptyDescription")} />
    );
  }

  return (
    <section className="bg-[var(--background)] p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-black text-[var(--foreground)]">{t("heading")}</h2>
          <p className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{t("description")}</p>
        </div>
        {isOwner ? <span className="mf-chip">{t("ownerView")}</span> : null}
      </div>

      <div className="mt-4 grid items-start gap-3 lg:grid-cols-[minmax(0,1fr)_420px]">
        <div className="grid auto-rows-max content-start gap-2">
          {items.map((item) => {
            const active = item.id === selectedItem?.id;
            const claimable = itemCanBeClaimed(item);
            const paymentLabel = paymentStatusLabel(item, t);
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setSelectedId(item.id)}
                className={cn(
                  "min-h-[96px] w-full rounded-[10px] p-3 text-left transition",
                  active
                    ? "bg-[var(--surface-selected)] shadow-[inset_0_0_0_1px_var(--primary)]"
                    : "bg-[var(--surface-1)] hover:bg-[var(--surface-2)]",
                )}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-black text-[var(--foreground)]">{item.title}</div>
                    {item.summary ? <div className="mt-1 line-clamp-2 text-xs leading-5 text-[var(--muted-foreground)]">{item.summary}</div> : null}
                  </div>
                  <div className="flex shrink-0 flex-wrap justify-end gap-1">
                    <StatusChip label={t.has(`status.${item.status}`) ? t(`status.${item.status}`) : t("status.unavailable")} tone={claimable ? "success" : "default"} />
                    {paymentLabel ? <StatusChip label={paymentLabel} tone={paymentStatusTone(item)} /> : null}
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-2 text-sm font-black text-[var(--foreground)]">
                  <span>{formatItemMoney(item.budgetAmount ?? item.priceAmount ?? 0, item.currency)}</span>
                  {session?.accountId && item.claimedByAccountId === session.accountId && item.activeOrderNo ? <span>{t("hasOrder")}</span> : null}
                </div>
              </button>
            );
          })}
        </div>

        {selectedItem ? (
          <aside className="rounded-[12px] bg-[var(--surface-control)] p-4 lg:sticky lg:top-20 lg:self-start">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.14em] text-[var(--muted-foreground)]">
                  <CheckCircle2 className="h-4 w-4 text-[var(--muted-foreground)]" />
                  {t("selected")}
                </div>
                <div className="mt-2 break-words text-base font-black text-[var(--foreground)]">{selectedItem.title}</div>
                <div className="mt-1 text-sm font-semibold text-[var(--muted-foreground)]">
                  {formatItemMoney(selectedItem.budgetAmount ?? selectedItem.priceAmount ?? 0, selectedItem.currency)}
                </div>
              </div>
              <div className="flex shrink-0 flex-wrap justify-end gap-1">
                <StatusChip label={t.has(`status.${selectedItem.status}`) ? t(`status.${selectedItem.status}`) : t("status.unavailable")} tone={canClaimSelected ? "success" : "default"} />
                {selectedPaymentLabel ? <StatusChip label={selectedPaymentLabel} tone={paymentStatusTone(selectedItem)} /> : null}
              </div>
            </div>
            {canClaimSelected ? (
              <div className="mt-4 grid gap-3">
                <DealConfirmation item={selectedItem} t={t} />
                {!session ? <div className="bg-[var(--surface-2)] px-3 py-2 text-xs leading-5 text-[var(--muted-foreground)]">{t("loginPrompt")}</div> : null}
                {requiresOkxRecipient ? (
                  <div className="grid gap-2">
                    <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">{t("recipient.label")}</span>
                    <span className="text-xs font-semibold leading-5 text-[var(--muted-foreground)]">{t("recipient.hint")}</span>
                    <div className="flex gap-2">
                      <input
                        className={cn(
                          "mf-control-field h-11 min-w-0 flex-1 px-3",
                          !paymentRecipientValid && paymentRecipient.length > 0 && "border-[rgba(245,98,98,0.55)] bg-[rgba(245,98,98,0.08)] focus:border-[rgba(245,98,98,0.75)] focus-visible:border-[rgba(245,98,98,0.75)]",
                        )}
                        value={paymentRecipient}
                        onChange={(event) => setPaymentRecipient(event.target.value)}
                        placeholder="0x..."
                        aria-invalid={!paymentRecipientValid && paymentRecipient.length > 0}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        className="h-11 min-h-11 shrink-0 border-transparent bg-[var(--surface-2)] px-3 hover:bg-[rgb(30,31,33)]"
                        onClick={pastePaymentRecipient}
                      >
                        <ClipboardPaste className="h-4 w-4" />
                        {t("recipient.paste")}
                      </Button>
                    </div>
                    {!paymentRecipientValid && paymentRecipient.length > 0 ? (
                      <p className="rounded-[8px] border border-[rgba(245,98,98,0.3)] bg-[rgba(245,98,98,0.08)] px-3 py-2 text-sm font-normal text-[rgb(255,170,170)]">
                        {t("errors.invalidRecipient")}
                      </p>
                    ) : EVM_ADDRESS_PATTERN.test(paymentRecipient.trim()) ? (
                      <p className="flex min-h-5 items-center gap-1.5 text-xs leading-5 text-[var(--success)]">
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        {t("recipient.valid")}
                      </p>
                    ) : null}
                  </div>
                ) : null}
                {isInstantDeliveryMode(selectedItem.deliveryMode) ? (
                  <label className="grid gap-2">
                    <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">{t("instantDelivery.phoneLabel")}</span>
                    <input
                      className={cn("mf-control-field w-full px-3", !directDeliveryReady && directDeliveryPhone.length > 0 && "border-[rgba(245,98,98,0.48)]")}
                      value={directDeliveryPhone}
                      onChange={(event) => setDirectDeliveryPhone(event.target.value)}
                      placeholder="13800138000"
                      inputMode="tel"
                    />
                    <span className="text-xs font-semibold leading-5 text-[var(--muted-foreground)]">{selectedItem.deliverySlaLabel || t("instantDelivery.slaFallback")}</span>
                  </label>
                ) : null}
                {isStockFulfillmentMode(selectedItem.deliveryMode) ? (
                  <div className="grid gap-2 rounded-[8px] border border-[rgba(72,230,174,0.24)] bg-[rgba(72,230,174,0.08)] p-3">
                    <div className="flex items-center gap-2 text-sm font-black text-[var(--foreground)]">
                      <PackageCheck className="h-4 w-4 text-[var(--accent-green)]" />
                      {t("stockDelivery.heading")}
                    </div>
                    <span className="text-xs font-semibold leading-5 text-[var(--muted-foreground)]">{t("stockDelivery.description")}</span>
                  </div>
                ) : null}
                <textarea
                  className="mf-control-field min-h-20 w-full resize-none px-3 py-3 text-sm leading-6"
                  value={buyerNote}
                  onChange={(event) => setBuyerNote(event.target.value)}
                  placeholder={selectedItem.buyerNotePlaceholder || t("buyerNotePlaceholder")}
                />
                <Button variant="primary" className="h-11 w-full" loading={isPending} disabled={isPending || (!!session && (!paymentRecipientValid || !directDeliveryReady))} onClick={submit}>
                  {session ? t("claim") : t("loginToClaim")}
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </div>
            ) : selectedItem.activeOrderNo && activeOrderBelongsToSession ? (
              <div className="mt-4">
                <Button asChild variant="outline" className="w-full">
                  <Link href={`/orders/${encodeURIComponent(selectedItem.activeOrderNo)}`}>{t("viewOrder")}</Link>
                </Button>
              </div>
            ) : (
              <div className="mt-4 flex items-start gap-2 text-sm leading-6 text-[var(--muted-foreground)]">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[var(--muted-foreground)]" />
                {t("waitingState")}
              </div>
            )}
          </aside>
        ) : null}
      </div>

    </section>
  );
}

function DealConfirmation({ item, t }: { item: PostItem; t: ReturnType<typeof useTranslations> }) {
  const acceptanceText = item.acceptanceCriteria?.length ? item.acceptanceCriteria.join("\n") : item.acceptanceSpec;
  return (
    <div className="grid gap-3 text-xs leading-5 text-[var(--muted-foreground)]">
      <InfoBlock label={t("confirm.deliverable")} value={item.deliverableSpec} />
      <InfoBlock label={t("confirm.acceptance")} value={acceptanceText} />
      <InfoBlock label={t("confirm.paymentMethod")} value={item.settlementType === "money" ? "OKX Direct Pay" : t("confirm.shares")} />
      {isStockFulfillmentMode(item.deliveryMode) ? <InfoBlock label={t("stockDelivery.heading")} value={t("stockDelivery.confirmValue")} /> : null}
    </div>
  );
}

function isInstantDeliveryMode(value?: string) {
  return value === "instant_delivery" || value === "instant_fulfillment";
}

function isStockFulfillmentMode(value?: string) {
  return value === "stock_fulfillment";
}

function InfoBlock({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">{label}</div>
      <div className="mt-2 whitespace-pre-line text-sm leading-6 text-[var(--foreground)]">{value}</div>
    </div>
  );
}

function StatusChip({ label, tone }: { label: string; tone?: "default" | "success" | "warning" }) {
  return (
    <span
      className={cn(
        "rounded-full px-2 py-1 text-[10px] font-medium uppercase",
        tone === "success"
          ? "bg-[rgba(72,230,174,0.14)] text-[var(--success)]"
          : tone === "warning"
            ? "bg-[rgba(240,180,95,0.16)] text-[var(--warning)]"
            : "bg-[rgba(255,255,255,0.06)] text-[var(--muted-foreground)]",
      )}
    >
      {label}
    </span>
  );
}

function formatItemMoney(amount: number, currency: string | undefined) {
  const resolvedCurrency = currency ?? "USD";
  const value = Number.isInteger(amount) ? amount.toFixed(0) : amount.toFixed(2);
  return resolvedCurrency === "USD" ? `$${value}` : `${value} ${resolvedCurrency}`;
}
