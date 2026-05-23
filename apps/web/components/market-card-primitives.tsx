import type { CSSProperties } from "react";
import type { LucideIcon } from "lucide-react";
import { Box, FolderKanban, Search } from "lucide-react";

import type { Account, PublicAccount } from "@/lib/api";
import { cn } from "@/lib/utils";

export type MarketSurfaceKind = "offer" | "request" | "project";

export const MARKET_SURFACE_META: Record<MarketSurfaceKind, {
  label: string;
  heading: string;
  accent: string;
  tint: string;
  icon: LucideIcon;
}> = {
  offer: {
    label: "供给",
    heading: "供给",
    accent: "var(--market-opportunity-offer)",
    tint: "#48e6ae",
    icon: Box,
  },
  request: {
    label: "需求",
    heading: "需求",
    accent: "var(--market-opportunity-request)",
    tint: "#f0b45f",
    icon: Search,
  },
  project: {
    label: "项目",
    heading: "项目",
    accent: "var(--market-opportunity-project-work)",
    tint: "#5ec8ff",
    icon: FolderKanban,
  },
};

export function surfaceAccentStyle(accent: string): CSSProperties & Record<"--opportunity-accent", string> {
  return { "--opportunity-accent": accent };
}

function stableSeed(value: string) {
  return Array.from(value).reduce((sum, char) => sum + char.charCodeAt(0), 0);
}

function normalizeOwnerLookupKey(value: string | null | undefined) {
  return String(value ?? "").replace(/^@+/, "").trim().toLowerCase();
}

function findSurfaceOwnerAccount(ownerId: string, accountsById: Record<string, Account | PublicAccount>) {
  const normalizedOwnerId = normalizeOwnerLookupKey(ownerId);
  return accountsById[ownerId]
    ?? accountsById[normalizedOwnerId]
    ?? Object.values(accountsById).find((account) => {
      const displayHandle = account.displaySkin?.displayHandle;
      return normalizeOwnerLookupKey(account.id) === normalizedOwnerId
        || normalizeOwnerLookupKey(account.handle) === normalizedOwnerId
        || normalizeOwnerLookupKey(displayHandle) === normalizedOwnerId;
    });
}

export function buildSurfaceMediaStyle(kind: MarketSurfaceKind, id: string): CSSProperties {
  const meta = MARKET_SURFACE_META[kind];
  const seed = stableSeed(`${kind}:${id}`);
  const angle = 118 + (seed % 48);
  const x = 18 + (seed % 54);
  const y = 16 + ((seed >> 2) % 50);

  // 中文注释：封面渐变只由条目 kind 和 id 决定，这样列表卡与详情头图会稳定共用同一视觉记忆点。
  return {
    backgroundImage: [
      "linear-gradient(180deg, rgba(0,0,0,0) 30%, rgba(0,0,0,0.78) 100%)",
      `radial-gradient(circle at ${x}% ${y}%, color-mix(in srgb, ${meta.tint} 52%, white), transparent 22%)`,
      `linear-gradient(${angle}deg, color-mix(in srgb, ${meta.tint} 36%, #243228), #111214 48%, #050505 100%)`,
      "repeating-linear-gradient(135deg, rgba(255,255,255,0.08) 0 1px, transparent 1px 18px)",
    ].join(", "),
  };
}

export function buildSurfaceOwnerIdentity(ownerId: string | null | undefined, accountsById: Record<string, Account | PublicAccount>) {
  if (!ownerId) {
    return {
      displayName: "Market member",
      handle: "@member",
      platformHandle: "@member",
      initials: "MF",
      hue: 216,
      summary: "",
      verified: false,
      avatarUrl: null,
      profileUrl: null,
      profileHref: null,
      themeKey: "native",
    };
  }
  const account = findSurfaceOwnerAccount(ownerId, accountsById);
  const identity = account?.displaySkin;
  const displayName = identity?.displayName ?? account?.displayName ?? "Market member";
  const handleSource = identity?.displayHandle ?? account?.handle;
  const normalizedHandle = handleSource ? handleSource.replace(/^@+/, "") : ownerId.slice(0, 6);
  const handle = `@${normalizedHandle}`;
  const initials = displayName
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  return {
    displayName,
    handle,
    platformHandle: account?.handle ? `@${account.handle.replace(/^@+/, "")}` : `@${ownerId.slice(0, 6)}`,
    initials: initials || "MF",
    hue: stableSeed(ownerId) % 360,
    summary: account?.agentSummary ?? "",
    verified: identity?.source === "verified_identity",
    avatarUrl: identity?.avatarUrl ?? null,
    profileUrl: identity?.profileUrl ?? null,
    profileHref: account ? `/profiles/${encodeURIComponent(normalizedHandle)}` : null,
    themeKey: identity?.themeKey ?? "native",
  };
}

export function formatSurfaceDateLabel(value: string, locale = "zh-CN") {
  return new Date(value).toLocaleDateString(locale, { month: "short", day: "numeric" });
}

export function SurfaceHeroCard({
  kind,
  id,
  title,
  amountLabel,
  label,
  className,
  aspectClassName,
  titleClassName,
}: {
  kind: MarketSurfaceKind;
  id: string;
  title: string;
  amountLabel: string;
  label?: string;
  className?: string;
  aspectClassName?: string;
  titleClassName?: string;
}) {
  const meta = MARKET_SURFACE_META[kind];
  const Icon = meta.icon;

  return (
    <div
      className={cn("om-card-media relative isolate overflow-hidden", aspectClassName ?? "aspect-[1.2/1]", className)}
      style={buildSurfaceMediaStyle(kind, id)}
    >
      <div
        aria-hidden="true"
        className="absolute -right-8 -top-8 h-28 w-28 rounded-full opacity-45 blur-2xl"
        style={{ backgroundColor: meta.accent }}
      />
      <div aria-hidden="true" className="om-card-media-sheen" />

      <div className="absolute left-4 top-4 flex items-center gap-2">
        <span
          className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[11px] font-medium text-white shadow-[0_8px_18px_rgba(0,0,0,0.2)]"
          style={{ backgroundColor: "color-mix(in srgb, var(--opportunity-accent) 44%, rgba(0,0,0,0.54))" }}
        >
          <Icon className="h-3.5 w-3.5 shrink-0" />
          {label ?? meta.label}
        </span>
      </div>

      <div className="absolute right-4 top-4">
        <span className="rounded-full bg-[rgba(0,0,0,0.46)] px-3 py-1.5 text-[11px] font-medium text-white shadow-[0_8px_18px_rgba(0,0,0,0.2)]">
          {amountLabel}
        </span>
      </div>

      <div className="absolute bottom-4 left-4 right-4">
        <h3
          className={cn(
            "line-clamp-2 font-normal leading-[0.94] tracking-normal text-white drop-shadow-[0_2px_8px_rgba(0,0,0,0.45)]",
            titleClassName ?? "text-[20px]",
          )}
        >
          {title}
        </h3>
      </div>
    </div>
  );
}
