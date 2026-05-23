import * as React from "react";
import {cva, type VariantProps} from "class-variance-authority";

import {cn} from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-[12px] border px-3 py-1 text-[11px] font-semibold tracking-[0.01em] backdrop-blur-[10px] transition-colors",
  {
    variants: {
      variant: {
        default: "border-[rgba(72,108,230,0.3)] bg-[rgba(72,108,230,0.16)] text-[var(--accent-blue)]",
        success: "border-[rgba(72,230,174,0.3)] bg-[rgba(72,230,174,0.14)] text-[var(--accent-green)]",
        warning: "border-[rgba(240,180,95,0.34)] bg-[rgba(240,180,95,0.13)] text-[#f0b45f]",
        danger: "border-[rgba(213,84,63,0.38)] bg-[rgba(213,84,63,0.13)] text-[var(--accent-red)]",
        outline: "border-[var(--border)] bg-[var(--surface-control)] text-[var(--muted-foreground)]",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}
