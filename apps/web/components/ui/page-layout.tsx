import * as React from "react";

import {cn} from "@/lib/utils";

export type PageWidth = "reading" | "content" | "wide" | "full";
export type PageSectionTone = "default" | "subtle" | "muted";
export type PageSectionSize = "sm" | "md" | "lg" | "flush";

const pageWidthClassMap: Record<PageWidth, string> = {
  reading: "max-w-3xl",
  content: "max-w-[980px]",
  wide: "max-w-[1180px]",
  full: "w-full",
};

const pageSectionToneClassMap: Record<PageSectionTone, string> = {
  default: "bg-[var(--background)]",
  subtle: "bg-[var(--background)]",
  muted: "bg-[var(--background)]",
};

const pageSectionSizeClassMap: Record<PageSectionSize, string> = {
  sm: "px-4 py-3 sm:px-5",
  md: "px-4 py-4 sm:px-5 sm:py-5",
  lg: "px-5 py-5 sm:px-6 sm:py-6",
  flush: "p-0",
};

export function PageContainer({
  className,
  width = "wide",
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  width?: PageWidth;
}) {
  return <div className={cn("mx-auto w-full space-y-3 sm:space-y-4", pageWidthClassMap[width], className)} {...props} />;
}

export function PageIntro({
  className,
  heading,
  description,
  action,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  heading: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className={cn("flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between", className)} {...props}>
      <div className="min-w-0 space-y-2">
        <h1 className="text-xl font-medium leading-7 text-[var(--foreground)]">
          {heading}
        </h1>
        {description ? (
          <p className="max-w-3xl text-sm leading-6 text-[var(--muted-foreground)]">
            {description}
          </p>
        ) : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

export function PageSection({
  className,
  tone = "default",
  size = "md",
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  tone?: PageSectionTone;
  size?: PageSectionSize;
}) {
  return (
    <section
      className={cn("overflow-hidden rounded-[6px]", pageSectionToneClassMap[tone], pageSectionSizeClassMap[size], className)}
      {...props}
    />
  );
}

export function PageSectionHeader({
  className,
  heading,
  description,
  action,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  heading: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className={cn("flex flex-wrap items-start justify-between gap-3", className)} {...props}>
      <div className="min-w-0 space-y-1">
        <div className="text-base font-medium text-[var(--foreground)]">{heading}</div>
        {description ? <p className="text-sm leading-6 text-[var(--muted-foreground)]">{description}</p> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

export function EmptyState({
  className,
  icon,
  title,
  description,
  action,
  compact = false,
}: React.HTMLAttributes<HTMLDivElement> & {
  icon?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  compact?: boolean;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center rounded-[8px] bg-[var(--background)] px-5 text-center",
        compact ? "py-6" : "py-12 sm:py-14",
        className,
      )}
    >
      {icon ? <div className="mb-3 text-[var(--muted-foreground)]">{icon}</div> : null}
      <div className="text-sm font-medium text-[var(--foreground)]">{title}</div>
      {description ? <div className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted-foreground)]">{description}</div> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function ErrorState({
  className,
  icon,
  title,
  description,
  action,
  compact = false,
}: React.HTMLAttributes<HTMLDivElement> & {
  icon?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  compact?: boolean;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center rounded-[8px] border border-[rgba(213,84,63,0.32)] bg-[rgba(213,84,63,0.08)] px-5 text-center",
        compact ? "py-6" : "py-12 sm:py-14",
        className,
      )}
    >
      {icon ? <div className="mb-3 text-[var(--destructive)]">{icon}</div> : null}
      <div className="text-sm font-medium text-[var(--foreground)]">{title}</div>
      {description ? <div className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted-foreground)]">{description}</div> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function LoadingState({
  className,
  title,
  description,
}: React.HTMLAttributes<HTMLDivElement> & {
  title?: React.ReactNode;
  description?: React.ReactNode;
}) {
  return (
    <div className={cn("rounded-[8px] bg-[var(--background)] px-5 py-6", className)}>
      <div className="space-y-3">
        {title ? <div className="text-sm font-medium text-[var(--foreground)]">{title}</div> : null}
        {description ? <div className="text-sm leading-6 text-[var(--muted-foreground)]">{description}</div> : null}
        <div className="grid gap-2">
          <SkeletonBlock className="h-3 w-2/3" />
          <SkeletonBlock className="h-3 w-full" />
          <SkeletonBlock className="h-3 w-4/5" />
        </div>
      </div>
    </div>
  );
}

export function SkeletonBlock({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("animate-pulse rounded-full bg-[var(--surface-control-hover)]", className)} {...props} />;
}

export function SkeletonText({
  className,
  lines = 1,
}: React.HTMLAttributes<HTMLDivElement> & {
  lines?: number;
}) {
  return (
    <div className={cn("grid gap-2", className)}>
      {Array.from({ length: lines }).map((_, index) => (
        <SkeletonBlock
          key={index}
          className={cn(
            "h-3",
            index === lines - 1 && lines > 1 ? "w-4/5" : "w-full",
          )}
        />
      ))}
    </div>
  );
}

export function SkeletonAvatar({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <SkeletonBlock className={cn("h-10 w-10 shrink-0 rounded-full", className)} {...props} />;
}

export function SkeletonBadge({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <SkeletonBlock className={cn("h-7 w-20 rounded-full", className)} {...props} />;
}

export function SkeletonField({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("grid gap-2", className)} {...props}>
      <SkeletonBlock className="h-3 w-24" />
      <SkeletonBlock className="h-10 w-full rounded-[10px]" />
    </div>
  );
}

export function SkeletonPanel({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-[12px] bg-[var(--background)] p-4", className)} {...props} />;
}

export function SkeletonListRow({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("grid gap-2 rounded-[12px] bg-[var(--background)] px-3 py-3", className)} {...props}>
      <div className="flex flex-wrap gap-2">
        <SkeletonBadge className="w-16" />
        <SkeletonBadge className="w-20" />
      </div>
      <SkeletonBlock className="h-4 w-2/3" />
      <SkeletonText lines={2} className="max-w-2xl" />
    </div>
  );
}
