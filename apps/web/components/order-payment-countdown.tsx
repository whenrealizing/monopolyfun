"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";

import { formatDate } from "@/lib/api";

function remainingMs(dueAt: string) {
  const dueTime = new Date(dueAt).getTime();
  if (!Number.isFinite(dueTime)) return null;
  return Math.max(0, dueTime - Date.now());
}

function formatRemaining(ms: number) {
  const totalSeconds = Math.ceil(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function OrderPaymentCountdown({ dueAt }: { dueAt?: string | null }) {
  const t = useTranslations("Orders.detail.paymentCountdown");
  const normalizedDueAt = typeof dueAt === "string" && dueAt.trim() ? dueAt.trim() : "";
  const [remaining, setRemaining] = useState<number | null>(null);

  useEffect(() => {
    if (!normalizedDueAt) return;
    const update = () => setRemaining(remainingMs(normalizedDueAt));
    update();
    const timer = window.setInterval(update, 1000);
    return () => window.clearInterval(timer);
  }, [normalizedDueAt]);

  if (!normalizedDueAt || remaining === null) return null;

  return (
    <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">
      {remaining > 0
        ? t("remaining", { time: formatRemaining(remaining), date: formatDate(normalizedDueAt) })
        : t("expired", { date: formatDate(normalizedDueAt) })}
    </div>
  );
}
