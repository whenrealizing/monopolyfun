"use client";

import {ChevronLeft, ChevronRight} from "lucide-react";
import * as React from "react";
import {DayPicker} from "react-day-picker";

import {cn} from "@/lib/utils";

export type CalendarProps = React.ComponentProps<typeof DayPicker>;

export function Calendar({ className, classNames, showOutsideDays = true, ...props }: CalendarProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      className={cn("p-0", className)}
      classNames={{
        root: "text-sm",
        months: "flex flex-col gap-4",
        month: "space-y-3",
        month_caption: "relative flex h-9 items-center justify-center",
        caption_label: "text-sm font-black text-[var(--foreground)]",
        nav: "absolute inset-x-0 top-0 flex items-center justify-between",
        button_previous:
          "inline-flex h-9 w-9 items-center justify-center rounded-[10px] text-[var(--muted-foreground)] transition hover:bg-[var(--surface-control)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
        button_next:
          "inline-flex h-9 w-9 items-center justify-center rounded-[10px] text-[var(--muted-foreground)] transition hover:bg-[var(--surface-control)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
        weekdays: "grid grid-cols-7 gap-1",
        weekday: "flex h-8 items-center justify-center text-[11px] font-black uppercase text-[var(--muted-foreground)]",
        week: "mt-1 grid grid-cols-7 gap-1",
        day: "relative flex h-9 w-9 items-center justify-center rounded-[10px] text-sm font-semibold text-[var(--foreground)] transition hover:bg-[var(--surface-control)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
        day_button: "h-9 w-9 rounded-[10px]",
        selected: "bg-[var(--primary)] text-[var(--primary-foreground)] hover:bg-[var(--primary)]",
        today: "text-[var(--accent-blue)]",
        outside: "text-[var(--muted-foreground)] opacity-45",
        disabled: "pointer-events-none text-[var(--muted-foreground)] opacity-35",
        hidden: "invisible",
        ...classNames,
      }}
      components={{
        Chevron: ({ orientation }) => (
          orientation === "left" ? <ChevronLeft className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />
        ),
      }}
      {...props}
    />
  );
}
