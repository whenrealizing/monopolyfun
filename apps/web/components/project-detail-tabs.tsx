"use client";

import { useState, type ReactNode } from "react";

import { cn } from "@/lib/utils";

export type ProjectDetailTab = {
  id: string;
  label: string;
  content: ReactNode;
};

export function ProjectDetailTabs({ tabs }: { tabs: ProjectDetailTab[] }) {
  const [activeId, setActiveId] = useState(tabs[0]?.id ?? "");
  const activeTab = tabs.find((tab) => tab.id === activeId) ?? tabs[0];

  return (
    <section className="bg-[var(--background)]">
      <div className="flex gap-2 overflow-x-auto px-1 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
        {tabs.map((tab) => {
          const active = tab.id === activeTab?.id;
          return (
            <button
              key={tab.id}
              type="button"
              className={cn(
                "relative h-10 shrink-0 px-4 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
                active
                  ? "text-[var(--foreground)] after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-[var(--primary)]"
                  : "text-[var(--muted-foreground)] hover:text-[var(--foreground)]",
              )}
              aria-pressed={active}
              onClick={() => setActiveId(tab.id)}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div className="pt-4">{activeTab?.content}</div>
    </section>
  );
}
