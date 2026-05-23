"use client";

import * as PopoverPrimitive from "@radix-ui/react-popover";
import * as React from "react";

import {cn} from "@/lib/utils";

const Popover = PopoverPrimitive.Root;
const PopoverTrigger = PopoverPrimitive.Trigger;
const PopoverAnchor = PopoverPrimitive.Anchor;

const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(function PopoverContent({ className, align = "start", sideOffset = 8, ...props }, ref) {
  return (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        ref={ref}
        align={align}
        sideOffset={sideOffset}
        className={cn(
          "z-50 rounded-[12px] border border-[var(--border)] bg-[var(--surface-panel)] p-3 text-[var(--foreground)] shadow-2xl outline-none data-[state=closed]:animate-overlay-out data-[state=open]:animate-overlay-in",
          className,
        )}
        {...props}
      />
    </PopoverPrimitive.Portal>
  );
});

export { Popover, PopoverAnchor, PopoverContent, PopoverTrigger };
