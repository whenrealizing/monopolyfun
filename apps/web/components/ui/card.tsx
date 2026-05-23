import * as React from "react";

import {cn} from "@/lib/utils";

export type CardSize = "sm" | "md" | "lg";

const cardSizeMap: Record<CardSize, string> = {
  sm: "16px",
  md: "20px",
  lg: "24px",
};

export function Card({
  className,
  style,
  size = "md",
  ...props
}: React.HTMLAttributes<HTMLDivElement> & { size?: CardSize }) {
  const cardStyle = {
    ...(style ?? {}),
    ["--card-padding" as string]: cardSizeMap[size],
  } as React.CSSProperties;

  return (
    <div
      className={cn(
        "rounded-[6px] bg-[var(--background)] text-[var(--card-foreground)]",
        className,
      )}
      style={cardStyle}
      {...props}
    />
  );
}

export function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex flex-col space-y-1.5 p-[var(--card-padding)]", className)} {...props} />;
}

export function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn("text-xl font-normal", className)} {...props} />;
}

export function CardDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("text-xs text-[var(--muted-foreground)]", className)} {...props} />;
}

export function CardContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("p-[var(--card-padding)] pt-0", className)} {...props} />;
}
