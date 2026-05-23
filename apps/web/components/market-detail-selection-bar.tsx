"use client";

import { useRouter } from "@/i18n/navigation";
import { useMemo, useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { ArrowRight, Box, ClipboardCheck, CreditCard, Minus, PackageCheck, Plus, type LucideIcon } from "lucide-react";

import { useAuthGate } from "@/components/auth-required-gate";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { claimPostItemWithDeliveryInput, type SettlementType } from "@/lib/api";
import { shouldOpenPaymentRequired } from "@/lib/order-routing";
import { cn } from "@/lib/utils";

export type DetailSelectableItem = {
  id: string;
  title: string;
  priceLabel: string;
  subtitle: string;
  detail?: string;
  statusLabel: string;
  statusTone?: "default" | "success" | "warning";
  settlementType?: SettlementType;
  disabled?: boolean;
  activeOrderNo?: string;
  claimedByAccountId?: string;
  activeOrderPaymentRequired?: boolean;
  activeOrderPaymentStatus?: string;
  paymentStatusLabel?: string;
  buyerNotePlaceholder?: string;
  deliverableSpec?: string;
  acceptanceSpec?: string;
  acceptanceCriteria?: string[];
  paymentLabel?: string;
  recipientLabel?: string;
  deliveryMode?: string;
  deliveryProvider?: string;
  deliverySlaLabel?: string;
  deliveryFailurePolicy?: string;
  priceAmount?: number | null;
  budgetAmount?: number | null;
  inventoryLabel?: string;
};

export function MarketDetailSelectionBar({
  kind,
  ownerHandle,
  returnTo,
  initialItems,
}: {
  kind: "offer" | "request" | "project";
  ownerHandle?: string | null;
  returnTo: string;
  initialItems: DetailSelectableItem[];
}) {
  const t = useTranslations("Orders.marketSelection");
  const toast = useToast();
  const router = useRouter();
  const { session, requireSession } = useAuthGate();
  const firstSelectableItem = useMemo(
    () => initialItems.find((item) => !item.disabled) ?? initialItems.find((item) => item.activeOrderNo) ?? initialItems[0],
    [initialItems],
  );
  const [selectedId, setSelectedId] = useState(firstSelectableItem?.id ?? "");
  const [buyerNote, setBuyerNote] = useState("");
  const [directDeliveryPhone, setDirectDeliveryPhone] = useState("");
  const [isPending, startTransition] = useTransition();
  const selectedItem = useMemo(
    () => initialItems.find((item) => item.id === selectedId) ?? firstSelectableItem,
    [firstSelectableItem, initialItems, selectedId],
  );
  const showSelectionGrid = initialItems.length > 1;
  const normalizedOwnerHandle = ownerHandle?.replace(/^@+/, "").toLowerCase();
  const normalizedSessionHandle = session?.handle?.replace(/^@+/, "").toLowerCase();
  // 中文注释：公开详情选择器以 owner handle 判断当前用户身份，避免依赖公开响应中的内部账号 id。
  const isOwner = !!normalizedOwnerHandle && normalizedOwnerHandle === normalizedSessionHandle;
  const activeOrderBelongsToSession = Boolean(session?.accountId && selectedItem?.claimedByAccountId === session.accountId);
  const canUseSelectedActiveOrder = Boolean(selectedItem?.activeOrderNo && activeOrderBelongsToSession);
  const notePlaceholder = kind === "project"
    ? t("projectNotePlaceholder")
    : selectedItem?.buyerNotePlaceholder || t("buyerNotePlaceholder");
  const directDeliveryPhoneDigits = directDeliveryPhone.replace(/\D/g, "");
  const directDeliveryNeedsPhone = isInstantDeliveryMode(selectedItem?.deliveryMode);
  const directDeliveryReady = !directDeliveryNeedsPhone || /^1[3-9]\d{9}$/.test(directDeliveryPhoneDigits);
  const directDeliveryPhoneError = directDeliveryNeedsPhone && directDeliveryPhone.length > 0 && !directDeliveryReady;
  const showActionPanel = !(isOwner && kind !== "project");
  const quantity = 1;

  function submit() {
    if (!selectedItem) return;
    const activeSession = requireSession(returnTo);
    if (!activeSession) {
      return;
    }
    if (selectedItem.activeOrderNo && selectedItem.disabled && canUseSelectedActiveOrder) {
      router.push(`/orders/${encodeURIComponent(selectedItem.activeOrderNo)}`);
      return;
    }
    startTransition(async () => {
      try {
        const deliveryInput = isInstantDeliveryMode(selectedItem.deliveryMode)
          ? { phone: directDeliveryPhoneDigits, amount: selectedItem.priceAmount ?? selectedItem.budgetAmount ?? 0 }
          : undefined;
        const receipt = await claimPostItemWithDeliveryInput(selectedItem.id, activeSession.accountId, buyerNote, deliveryInput);
        const orderNo = typeof receipt.payload?.orderNo === "string" ? receipt.payload.orderNo : "";
        if (!orderNo) {
          toast.notify({ tone: "error", title: t("errors.orderMissing") });
          return;
        }
        const paymentRequired = shouldOpenPaymentRequired(receipt, activeSession.accountId, selectedItem.settlementType);
        router.push(`/orders/${encodeURIComponent(orderNo)}${paymentRequired ? "?payment=required" : ""}`);
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      }
    });
  }

  function moveSelection(offset: number) {
    if (initialItems.length < 2 || !selectedItem) return;
    const currentIndex = initialItems.findIndex((item) => item.id === selectedItem.id);
    const nextIndex = (currentIndex + offset + initialItems.length) % initialItems.length;
    setSelectedId(initialItems[nextIndex]?.id ?? selectedItem.id);
  }

  // 中文注释：Project owner 可以自领任务推进，offer/request owner 继续只保留创建入口。
  const fallbackActionLabel = kind === "offer" ? t("action.buy") : kind === "project" ? t("action.joinProject") : t("action.claimRequest");
  const actionLabel = isOwner && kind !== "project"
    ? t("action.disabled")
    : selectedItem?.disabled
    ? canUseSelectedActiveOrder ? t("action.viewOrder") : t("action.disabled")
    : !selectedItem
      ? fallbackActionLabel
    : kind === "offer"
      ? selectedItem.settlementType === "money" ? t("action.payToBuy") : t("action.buy")
      : kind === "project"
        ? isOwner ? t("action.claimTask") : t("action.joinProject")
        : t("action.claimRequest");

  const actionDisabled = isPending || !selectedItem || (isOwner && kind !== "project") || (!!selectedItem.disabled && !canUseSelectedActiveOrder) || !directDeliveryReady;
  const showLoginPrompt = !session && !!selectedItem && !selectedItem.disabled;

  return (
    <section className="bg-[var(--background)] py-2">
      <div className={cn("grid items-start gap-3", showActionPanel && "lg:grid-cols-[minmax(0,1fr)_420px]")}>
        <div className="grid gap-3">
          <section className="rounded-[12px] border border-[var(--border)] bg-[var(--surface-1)] p-4">
            <div>
              <h2 className="text-base font-semibold text-[var(--foreground)]">{t(`title.${kind}`)}</h2>
              <p className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{t(`description.${kind}`)}</p>
            </div>

            {showSelectionGrid ? (
          <div
            role="radiogroup"
            aria-label={t(`title.${kind}`)}
            className="mt-4 grid auto-rows-max content-start gap-2"
            onKeyDown={(event) => {
              if (event.key === "ArrowRight" || event.key === "ArrowDown") {
                event.preventDefault();
                moveSelection(1);
              }
              if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
                event.preventDefault();
                moveSelection(-1);
              }
            }}
          >
          {initialItems.map((item) => {
            const active = item.id === selectedItem?.id;
            return (
              <button
                key={item.id}
                type="button"
                role="radio"
                aria-checked={active}
                aria-disabled={item.disabled && !item.activeOrderNo ? true : undefined}
                aria-label={`${item.title} ${item.priceLabel} ${item.statusLabel}`}
                onClick={() => setSelectedId(item.id)}
                className={cn(
                  "grid min-h-[92px] grid-cols-[24px_44px_minmax(0,1fr)_auto] items-center gap-3 rounded-[10px] border p-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
                  item.disabled && !item.activeOrderNo && "opacity-70",
                  active
                    ? "border-[var(--primary)] bg-[var(--surface-selected)]"
                    : "border-[var(--border)] bg-[var(--surface-control)] hover:bg-[var(--surface-control-hover)]",
                )}
              >
                <span className={cn("flex h-5 w-5 items-center justify-center rounded-full border", active ? "border-[var(--primary)]" : "border-[var(--muted-foreground)]")}>
                  {active ? <span className="h-2.5 w-2.5 rounded-full bg-[var(--primary)]" /> : null}
                </span>
                <span className="flex h-11 w-11 items-center justify-center rounded-[10px] bg-[rgba(72,108,230,0.18)] text-[var(--primary)]">
                  <Box className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium text-[var(--foreground)]">{item.title}</span>
                  <span className="mt-1 block line-clamp-2 text-xs leading-5 text-[var(--muted-foreground)]">{item.subtitle}</span>
                </span>
                <span className="grid justify-items-end gap-1">
                  <span className="text-sm font-semibold text-[var(--foreground)]">{item.priceLabel}</span>
                  <span className="text-xs text-[var(--muted-foreground)]">{item.inventoryLabel ?? item.statusLabel}</span>
                </span>
              </button>
            );
          })}
          </div>
            ) : selectedItem ? (
              <div className="mt-4 grid min-h-[92px] grid-cols-[44px_minmax(0,1fr)_auto] items-center gap-3 rounded-[10px] border border-[var(--primary)] bg-[var(--surface-selected)] p-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-[10px] bg-[rgba(72,108,230,0.18)] text-[var(--primary)]">
                  <Box className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium text-[var(--foreground)]">{selectedItem.title}</span>
                  <span className="mt-1 block line-clamp-2 text-xs leading-5 text-[var(--muted-foreground)]">{selectedItem.subtitle}</span>
                </span>
                <span className="grid justify-items-end gap-1">
                  <span className="text-sm font-semibold text-[var(--foreground)]">{selectedItem.priceLabel}</span>
                  <span className="text-xs text-[var(--muted-foreground)]">{selectedItem.inventoryLabel ?? selectedItem.statusLabel}</span>
                </span>
              </div>
            ) : null}
          </section>

          {selectedItem ? (
            <section className="rounded-[12px] border border-[var(--border)] bg-[var(--surface-1)] p-4">
              <h2 className="text-base font-semibold text-[var(--foreground)]">{t("notes.title")}</h2>
              <div className="mt-4 grid gap-3 text-xs leading-5 text-[var(--muted-foreground)]">
                <ConfirmCell icon={PackageCheck} label={t("confirm.deliverable")} value={selectedItem.deliverableSpec ?? selectedItem.detail ?? t("confirm.deliverableFallback")} />
                <ConfirmCell
                  icon={ClipboardCheck}
                  label={t("confirm.acceptance")}
                  value={selectedItem.acceptanceCriteria?.length ? selectedItem.acceptanceCriteria.join("\n") : selectedItem.acceptanceSpec ?? t("confirm.acceptanceFallback")}
                />
                <ConfirmCell icon={Box} label={t("notes.quantity")} value={t("notes.quantityValue")} />
              </div>
              {isInstantDeliveryMode(selectedItem.deliveryMode) ? (
                <div className="mt-3 grid gap-2 rounded-[8px] border border-[rgba(72,230,174,0.24)] bg-[rgba(72,230,174,0.08)] p-3">
                  <div className="text-xs font-medium text-[var(--foreground)]">{t("instantDelivery.heading")}</div>
                  <input
                    className={cn("mf-control-field w-full px-3", !directDeliveryReady && directDeliveryPhone.length > 0 && "border-[rgba(245,98,98,0.48)]")}
                    value={directDeliveryPhone}
                    onChange={(event) => setDirectDeliveryPhone(event.target.value)}
                    placeholder={t("instantDelivery.phonePlaceholder")}
                    inputMode="tel"
                    aria-invalid={directDeliveryPhoneError || undefined}
                  />
                  {directDeliveryPhoneError ? <div className="text-xs leading-5 text-[var(--danger)]">{t("instantDelivery.phoneError")}</div> : null}
                </div>
              ) : null}
              <textarea
                className="mf-control-field mt-3 min-h-20 w-full resize-none px-3 py-2 text-sm leading-6"
                value={buyerNote}
                onChange={(event) => setBuyerNote(event.target.value)}
                placeholder={notePlaceholder}
                maxLength={200}
              />
            </section>
          ) : null}
        </div>

        {showActionPanel ? (
        <aside className="rounded-[12px] border border-[var(--border)] bg-[var(--surface-1)] p-4 lg:sticky lg:top-20 lg:self-start">
          <div className="flex items-center justify-between gap-3 border-b border-[var(--border)] pb-4">
            <h2 className="text-base font-semibold text-[var(--foreground)]">{t("order.title")}</h2>
          </div>

          <div className="mt-4 grid gap-3 text-sm">
            <OrderRow label={t("order.selected")} value={selectedItem?.title ?? t("selectPlaceholder")} />
            <OrderRow label={t("order.unitPrice")} value={selectedItem?.priceLabel ?? t("action.disabled")} />
            <div className="flex items-center justify-between gap-3">
              <span className="text-[var(--muted-foreground)]">{t("order.quantity")}</span>
              <QuantityStepper quantity={quantity} t={t} />
            </div>
          </div>

          <div className="mt-4 flex items-end justify-between gap-3 border-t border-[var(--border)] pt-4">
            <span className="text-sm font-medium text-[var(--foreground)]">{t("order.total")}</span>
            <span className="break-words text-2xl font-black leading-none text-[rgb(255,79,36)]">{selectedItem?.priceLabel ?? "--"}</span>
          </div>

          {selectedItem && kind !== "project" ? <PaymentMethodChoice item={selectedItem} t={t} /> : null}
          <div className="mt-3 grid gap-2">
            <Button
              variant="primary"
              className="h-11 w-full"
              loading={isPending}
              disabled={actionDisabled}
              onClick={submit}
            >
              {actionLabel}
              <ArrowRight className="h-4 w-4" />
            </Button>
            {showLoginPrompt ? <div className="text-xs leading-5 text-[var(--muted-foreground)]">{t("loginPrompt")}</div> : null}
          </div>
        </aside>
        ) : null}
      </div>
    </section>
  );
}

function OrderRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="text-[var(--muted-foreground)]">{label}</span>
      <span className="max-w-[230px] break-words text-right font-medium text-[var(--foreground)]">{value}</span>
    </div>
  );
}

function QuantityStepper({ quantity, t }: { quantity: number; t: ReturnType<typeof useTranslations> }) {
  return (
    <div className="inline-flex h-9 items-center rounded-[10px] border border-[var(--border)] bg-[var(--surface-control)]">
      <button type="button" className="flex h-9 w-9 items-center justify-center text-[var(--muted-foreground)] opacity-50" disabled aria-label={t("order.decreaseQuantity")}>
        <Minus className="h-4 w-4" />
      </button>
      <span className="min-w-8 text-center text-sm font-medium text-[var(--foreground)]">{quantity}</span>
      <button type="button" className="flex h-9 w-9 items-center justify-center text-[var(--muted-foreground)] opacity-50" disabled aria-label={t("order.increaseQuantity")}>
        <Plus className="h-4 w-4" />
      </button>
    </div>
  );
}

function PaymentMethodChoice({ item, t }: { item: DetailSelectableItem; t: ReturnType<typeof useTranslations> }) {
  const isMoneySettlement = item.settlementType === "money";
  const label = item.paymentLabel ?? (isMoneySettlement ? t("payment.okxDirectPay") : t("confirm.shares"));
  const description = isMoneySettlement ? t("payment.okxDescription") : t("payment.sharesDescription");

  return (
    <div className="mt-4 grid gap-2">
      <div className="flex items-center gap-2 text-sm font-medium text-[var(--foreground)]">
        <CreditCard className="h-4 w-4 text-[var(--muted-foreground)]" />
        {t("confirm.paymentMethod")}
      </div>
      <div role="radiogroup" aria-label={t("confirm.paymentMethod")} className="grid gap-2">
        <button
          type="button"
          role="radio"
          aria-checked="true"
          className="flex w-full items-center gap-3 rounded-[10px] border border-[var(--primary)] bg-[var(--surface-selected)] p-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
        >
          <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-[var(--primary)]">
            <span className="h-2 w-2 rounded-full bg-[var(--primary)]" />
          </span>
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[9px] bg-[var(--background)] text-sm font-black tracking-wide text-[var(--foreground)]">OKX</span>
          <span className="min-w-0 flex-1">
            <span className="flex flex-wrap items-center gap-2">
              <span className="font-medium text-[var(--foreground)]">{label}</span>
            </span>
            <span className="mt-1 block text-xs leading-5 text-[var(--muted-foreground)]">{description}</span>
          </span>
        </button>
      </div>
    </div>
  );
}

function ConfirmCell({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="grid min-w-0 grid-cols-[18px_minmax(0,1fr)] gap-2">
      <Icon className="mt-0.5 h-4 w-4 text-[var(--muted-foreground)]" />
      <div className="min-w-0">
        <div className="font-medium text-[var(--foreground)]">{label}</div>
        <div className="mt-0.5 line-clamp-3 break-words">{value}</div>
      </div>
    </div>
  );
}

function isInstantDeliveryMode(value?: string) {
  return value === "instant_delivery" || value === "instant_fulfillment";
}
