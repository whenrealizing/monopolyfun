"use client";

import { ArrowDownUp, Check } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import type { FeedSort } from "@/components/market-surface";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

export type SurfaceSortOption = {
  sort: FeedSort;
  label: string;
  shortLabel: string;
  href: string;
};

export function SurfaceSortMenu({
  activeSort,
  options,
  label,
}: {
  activeSort: FeedSort;
  options: SurfaceSortOption[];
  label: string;
}) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const closeTimerRef = useRef<number | null>(null);
  const activeOption = options.find((option) => option.sort === activeSort) ?? options[0];

  const requestClose = useCallback(() => {
    if (!open || closing) return;
    setClosing(true);
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    closeTimerRef.current = window.setTimeout(() => {
      setOpen(false);
      setClosing(false);
      closeTimerRef.current = null;
    }, 140);
  }, [closing, open]);

  useEffect(() => () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
  }, []);

  useEffect(() => {
    if (!open) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") requestClose();
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, requestClose]);

  return (
    <div className="relative shrink-0">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        className={cn("om-chip", open ? "om-chip-active" : null)}
        onClick={() => {
          if (open) {
            requestClose();
            return;
          }
          setOpen(true);
        }}
      >
        <ArrowDownUp className="h-3.5 w-3.5" />
        <span>{activeOption?.shortLabel ?? label}</span>
      </button>
      {open ? (
        <div className={cn("absolute right-0 top-[calc(100%+8px)] z-30 w-[168px]", closing ? "animate-popover-out" : "animate-popover-in")}>
          <div className="rounded-[12px] border border-[var(--border)] bg-[rgb(24,25,27)] p-1.5 shadow-[var(--shadow-md)]">
            {options.map((option) => {
              const active = option.sort === activeSort;
              return (
                <Link
                  key={option.sort}
                  href={option.href}
                  className={cn(
                    "flex h-10 items-center gap-3 rounded-[12px] px-3 text-[14px] leading-5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
                    active ? "bg-[var(--surface-selected)] text-[var(--foreground)]" : "text-[var(--muted-foreground)] hover:bg-[rgb(33,34,37)] hover:text-[var(--foreground)]",
                  )}
                  onClick={requestClose}
                >
                  <span className="min-w-0 flex-1 truncate">{option.label}</span>
                  {active ? <Check className="h-4 w-4 shrink-0 text-[var(--primary)]" /> : null}
                </Link>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
