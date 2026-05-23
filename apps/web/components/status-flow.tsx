import { useTranslations } from "next-intl";
import { Check } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

export type StatusFlowStep = {
  id: string;
  label: string;
  helper?: string;
};

export function StatusFlow({
  steps,
  currentIndex,
  showCurrentHelper = true,
}: {
  steps: StatusFlowStep[];
  currentIndex: number;
  showCurrentHelper?: boolean;
}) {
  // 中文注释：外部状态可能来自多条业务路径，这里收敛到可渲染区间，保证状态线始终指向一个明确步骤。
  const boundedIndex = Math.min(Math.max(currentIndex, 0), Math.max(steps.length - 1, 0));

  return (
    <div className="overflow-x-auto pb-1">
      <ol className="flex min-w-max items-start">
        {steps.map((step, index) => {
          const completed = index < boundedIndex;
          const current = index === boundedIndex;
          const last = index === steps.length - 1;
          return (
            <li
              key={step.id}
              className="flex min-w-[132px] items-start"
            >
              <div className="min-w-0">
                <div className="flex items-center">
                  <span
                    className={cn(
                      "box-border flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-[11px] font-medium transition-colors",
                      current
                        ? "border-[var(--primary)] bg-[var(--primary)] text-white"
                        : completed
                          ? "border-[rgb(128,255,196)] bg-[var(--background)] text-[rgb(128,255,196)]"
                          : "border-[var(--border)] bg-[var(--background)] text-[var(--muted-foreground)]",
                    )}
                  >
                    {completed ? <Check className="h-3.5 w-3.5" /> : index + 1}
                  </span>
                  {!last ? (
                    <span
                      className={cn(
                        "mx-2 h-px w-20 shrink-0",
                        completed ? "bg-[rgba(118,255,189,0.5)]" : "bg-[var(--border)]",
                      )}
                    />
                  ) : null}
                </div>
                <div className={cn("mt-2 text-sm leading-5", current ? "font-medium text-[var(--foreground)]" : "text-[var(--muted-foreground)]")}>
                  {step.label}
                </div>
                {showCurrentHelper && current && step.helper ? <div className="mt-1 max-w-28 text-xs leading-5 text-[var(--muted-foreground)]">{step.helper}</div> : null}
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

export function CurrentStatePanel({
  title,
  description,
  action,
  children,
  compact = false,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children?: ReactNode;
  compact?: boolean;
}) {
  const t = useTranslations("Common");
  if (compact) {
    return (
      <section className="inline-flex max-w-full rounded-full bg-[rgba(72,108,230,0.12)] px-3 py-1.5">
        <div className="truncate text-sm font-medium leading-5 text-[var(--foreground)]">{title}</div>
      </section>
    );
  }

  return (
    <section className="rounded-[12px] bg-[rgba(72,108,230,0.08)] px-4 py-3">
      <div className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{t("currentState")}</div>
      <div className="mt-2 text-lg font-medium leading-6 text-[var(--foreground)]">{title}</div>
      {action ? <div className="mt-2 text-sm leading-6 text-[var(--accent-blue)]">{action}</div> : null}
      {description ? <div className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">{description}</div> : null}
      {children ? <div className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">{children}</div> : null}
    </section>
  );
}
