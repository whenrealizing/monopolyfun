"use client";

import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import type {ComponentPropsWithoutRef, ElementRef} from "react";
import {forwardRef} from "react";

import {cn} from "@/lib/utils";

const TooltipProvider = TooltipPrimitive.Provider;
const Tooltip = TooltipPrimitive.Root;
const TooltipTrigger = TooltipPrimitive.Trigger;

const TooltipContent = forwardRef<
  ElementRef<typeof TooltipPrimitive.Content>,
  ComponentPropsWithoutRef<typeof TooltipPrimitive.Content>
>(function TooltipContent({ className, sideOffset = 8, ...props }, ref) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        ref={ref}
        sideOffset={sideOffset}
        className={cn(
          "z-50 max-w-[280px] animate-popover-in rounded-[12px] border border-[var(--border)] bg-[rgb(24,25,27)] px-3 py-2.5 text-xs leading-5 text-[var(--muted-foreground)] shadow-[var(--shadow-md)]",
          className,
        )}
        {...props}
      />
    </TooltipPrimitive.Portal>
  );
});

export { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider };
