"use client";

import { Link } from "@/i18n/navigation";
import { useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";

import { CurrentStatePanel } from "@/components/status-flow";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";

export type RequestCurrentStateView = {
  title: string;
  orderNo?: string;
  paymentOwnerHandle?: string;
};

function paymentStateForViewer(state: RequestCurrentStateView, viewerHandle: string | null | undefined, t: ReturnType<typeof useTranslations>) {
  if (!state.orderNo || !state.paymentOwnerHandle) return state;
  // 中文注释：需求详情是公开页，付款缺口按公开 handle 区分当前用户和发布人。
  if (viewerHandle && normalizeHandle(viewerHandle) === normalizeHandle(state.paymentOwnerHandle)) {
    return {
      ...state,
      title: t("viewerPayment.title"),
    };
  }
  return {
    ...state,
    title: t("publisherPayment.title"),
  };
}

export function RequestCurrentStatePanel({ state }: { state: RequestCurrentStateView }) {
  const t = useTranslations("Orders.requestCurrentState");
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const visibleState = paymentStateForViewer(state, session?.handle, t);
  const canOpenPaymentOrder = Boolean(
    visibleState.orderNo
      && visibleState.paymentOwnerHandle
      && session?.handle
      && normalizeHandle(session.handle) === normalizeHandle(visibleState.paymentOwnerHandle),
  );

  return (
    <CurrentStatePanel
      title={visibleState.title}
      action={canOpenPaymentOrder ? (
        <Link className="font-medium underline-offset-4 hover:underline" href={`/orders/${encodeURIComponent(visibleState.orderNo!)}`}>
          {t("openPaymentOrder")}
        </Link>
      ) : undefined}
    />
  );
}

function normalizeHandle(value: string) {
  return value.replace(/^@+/, "").trim().toLowerCase();
}
