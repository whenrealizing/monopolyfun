"use client";

import type { ReactNode } from "react";
import { BadgeCheck, Clock3, Crown, Flame, Github, ShieldCheck, Sprout } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { IdentityBadge } from "@/lib/api";

function badgeIcon(icon: IdentityBadge["icon"]): ReactNode {
  switch (icon) {
    case "github":
      return <Github className="h-3.5 w-3.5" />;
    case "crown":
      return <Crown className="h-3.5 w-3.5" />;
    case "flame":
      return <Flame className="h-3.5 w-3.5" />;
    case "clock":
      return <Clock3 className="h-3.5 w-3.5" />;
    case "sprout":
      return <Sprout className="h-3.5 w-3.5" />;
    default:
      return <ShieldCheck className="h-3.5 w-3.5" />;
  }
}

function badgeVariant(kind: IdentityBadge["kind"]) {
  return kind === "verified" ? "success" : "outline";
}

export function IdentityBadgeList({
  badges,
  compact = false,
}: {
  badges: IdentityBadge[];
  compact?: boolean;
}) {
  if (badges.length === 0) return null;
  const visibleBadges = badges.filter((badge, index, list) => (
    list.findIndex((candidate) => candidate.kind === badge.kind && candidate.code === badge.code) === index
  ));

  return (
    <div className="flex flex-wrap items-center gap-2">
      {visibleBadges.map((badge) => (
        <Badge key={`${badge.kind}:${badge.code}`} variant={badgeVariant(badge.kind)} className={compact ? "px-2 py-0.5 text-[10px]" : undefined}>
          {badgeIcon(badge.icon)}
          <span>{badge.label}</span>
          {badge.kind === "verified" ? <BadgeCheck className="h-3 w-3" /> : null}
        </Badge>
      ))}
    </div>
  );
}
