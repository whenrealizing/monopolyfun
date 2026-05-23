import * as React from "react";
import {Slot} from "@radix-ui/react-slot";
import {cva, type VariantProps} from "class-variance-authority";
import {LoaderCircle} from "lucide-react";

import {cn} from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[12px] text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--background)] disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50",
  {
    variants: {
      variant: {
        default:
          "border border-[var(--border)] bg-[var(--surface-control)] text-[var(--foreground)] shadow-sm hover:bg-[rgb(30,31,33)]",
        primary:
          "border border-[var(--primary)] bg-[var(--primary)] text-[var(--primary-foreground)] shadow-[0_10px_26px_rgba(72,108,230,0.24)] hover:bg-[var(--primary-hover)]",
        secondary:
          "border border-[var(--border)] bg-[var(--muted)] text-[var(--foreground)] hover:bg-[rgb(30,31,33)]",
        outline:
          "border border-[var(--border)] bg-transparent text-[var(--foreground)] hover:bg-[rgb(30,31,33)]",
        ghost:
          "text-[var(--muted-foreground)] hover:bg-[rgb(30,31,33)] hover:text-[var(--foreground)]",
        danger:
          "border border-[rgba(213,84,63,0.42)] bg-transparent text-[rgb(255,170,160)] shadow-sm hover:border-[rgba(213,84,63,0.62)] hover:bg-[rgba(213,84,63,0.08)]",
      },
      size: {
        default: "h-10 px-4 py-2",
        sm: "h-10 px-3 text-sm",
        lg: "h-11 px-5",
        icon: "h-10 w-10 rounded-[12px]",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
}

export function Button({ className, variant, size, asChild = false, loading = false, disabled, children, ...props }: ButtonProps) {
  const resolvedDisabled = disabled || loading;
  const classes = cn(buttonVariants({ variant, size, className }), loading ? "cursor-wait opacity-70" : null);

  if (asChild) {
    return (
      <Slot
        className={classes}
        aria-busy={loading || undefined}
        data-loading={loading || undefined}
        {...props}
      >
        {children}
      </Slot>
    );
  }

  return (
    <button
      className={classes}
      aria-busy={loading || undefined}
      data-loading={loading || undefined}
      disabled={resolvedDisabled}
      {...props}
    >
      {loading ? <LoaderCircle className="h-4 w-4 shrink-0 animate-spin" aria-hidden="true" /> : null}
      {children}
    </button>
  );
}

export { buttonVariants };
