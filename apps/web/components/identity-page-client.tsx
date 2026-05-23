"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState, useSyncExternalStore, useTransition, type ComponentProps, type ComponentType, type ReactNode } from "react";
import {
  ArrowRight,
  Award,
  BadgeCheck,
  Camera,
  Check,
  CheckCircle2,
  ChevronDown,
  CircleDashed,
  Fingerprint,
  Github,
  Info,
  KeyRound,
  ListChecks,
  Package,
  ShieldCheck,
  ShoppingBag,
  Star,
  TriangleAlert,
  User,
  Wallet,
  Youtube,
} from "lucide-react";

import { Button as BaseButton } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorState, PageContainer } from "@/components/ui/page-layout";
import { Badge as BaseBadge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/toast";
import { IdentitySkeleton } from "@/components/page-skeletons";
import { ProfileIdentityHero } from "@/components/profile-identity-hero";
import {
  beginIdentityVerification,
  completeIdentityVerification,
  formatDate,
  getIdentityPage,
  type IdentityBadge,
  type IdentityCertifier,
  type IdentityDisplaySkin,
  type IdentityPage,
  type Order,
  type SharesLedgerEntry,
  type ShareSettlementHold,
  updateIdentityDisplaySkin,
  updateIdentityProfile,
} from "@/lib/api";
import { isApiStatus } from "@/lib/api-error";
import { orderHref } from "@/lib/business-routes";
import { readStoredSession, saveSession, subscribeSession, type ClientSession } from "@/lib/client-preferences";
import { UiError } from "@/lib/error-messages";
import { formatMajorMoney } from "@/lib/format-money";
import { orderRoleLabel } from "@/lib/order-display";
import { cn } from "@/lib/utils";

type PrimarySection = "market" | "badges" | "assets" | "identity";
type OrderStageId = "payment" | "delivery" | "acceptance" | "dispute" | "complete";
type OrderStatusFilter = "all" | "active" | "mine" | "dispute" | "complete";
type MarketTab = "all" | "sell" | "buy" | "projects";
type BadgeTab = "overview" | "verification";
type AssetTab = "system" | "other";
type SecondaryTab = MarketTab | BadgeTab | AssetTab;
type IconComponent = ComponentType<{ className?: string }>;

const primarySections: Array<{ id: PrimarySection; label: string; icon: IconComponent }> = [
  { id: "market", label: "市场", icon: ShoppingBag },
  { id: "badges", label: "徽章", icon: Award },
  { id: "assets", label: "资产", icon: Wallet },
  { id: "identity", label: "身份", icon: User },
];

const marketTabs: Array<{ id: MarketTab; label: string; icon: IconComponent }> = [
  { id: "all", label: "全部", icon: ListChecks },
  { id: "sell", label: "卖", icon: Package },
  { id: "buy", label: "买", icon: ShoppingBag },
  { id: "projects", label: "项目", icon: Star },
];

const marketStatusFilters: Array<{ id: OrderStatusFilter; label: string }> = [
  { id: "all", label: "全部状态" },
  { id: "active", label: "进行中" },
  { id: "mine", label: "待我处理" },
  { id: "dispute", label: "争议中" },
  { id: "complete", label: "已完成" },
];

const badgeTabs: Array<{ id: BadgeTab; label: string; icon: IconComponent }> = [
  { id: "overview", label: "总览", icon: Award },
  { id: "verification", label: "认证", icon: ShieldCheck },
];

const assetTabs: Array<{ id: AssetTab; label: string; icon: IconComponent }> = [
  { id: "system", label: "系统项目虚拟股份", icon: Wallet },
  { id: "other", label: "其他项目虚拟股份", icon: ListChecks },
];

const defaultSecondaryTab: Record<PrimarySection, SecondaryTab> = {
  market: "all",
  badges: "overview",
  assets: "system",
  identity: "all",
};

function Button({ style, ...props }: ComponentProps<typeof BaseButton>) {
  return <BaseButton {...props} style={{ ...style, fontWeight: 400 }} />;
}

function Badge({ style, ...props }: ComponentProps<typeof BaseBadge>) {
  return <BaseBadge {...props} style={{ ...style, fontWeight: 400 }} />;
}

export function IdentityPageClient() {
  const toast = useToast();
  const searchParams = useSearchParams();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [identity, setIdentity] = useState<IdentityPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeCertifierId, setActiveCertifierId] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const activeSection = normalizePrimarySection(searchParams.get("section"));
  const activeTab = normalizeSecondaryTab(activeSection, searchParams.get("tab"));
  const marketStatusFilter = normalizeOrderStatusFilter(searchParams.get("status"));
  const focusedCertifierId = searchParams.get("certifier");

  useEffect(() => {
    let cancelled = false;

    if (!session) {
      queueMicrotask(() => {
        if (cancelled) return;
        setIdentity(null);
      });
      return () => {
        cancelled = true;
      };
    }

    getIdentityPage()
      .then((result) => {
        if (cancelled) return;
        setIdentity(result);
        setError(null);
      })
      .catch((fetchError) => {
        if (cancelled) return;
        if (isApiStatus(fetchError, [404])) {
          // 中文注释：身份主接口缺失或未部署时保留已登录账号页，避免把登录成功误展示成资源不存在。
          const fallbackIdentity = buildSessionIdentityPage(session);
          setIdentity(fallbackIdentity);
          setError(null);
          return;
        }

        setError(fetchError instanceof Error ? fetchError.message : "加载身份中心失败。");
      });

    return () => {
      cancelled = true;
    };
  }, [session]);

  const pendingChallenges = useMemo(() => (
    new Map((identity?.challenges ?? []).filter((challenge) => challenge.status === "pending").map((challenge) => [challenge.certifierId, challenge]))
  ), [identity?.challenges]);

  const linkedAccounts = useMemo(() => (
    new Map((identity?.profile.linkedAccounts ?? []).map((account) => [account.certifierId, account]))
  ), [identity?.profile.linkedAccounts]);

  const loading = Boolean(session) && !identity && !error;

  const handleBeginVerification = (certifier: IdentityCertifier) => {
    if (certifier.verificationMethod === "public_proof") {
      window.location.assign(identityHref("badges", "verification", { certifier: certifier.id }));
      return;
    }
    setActiveCertifierId(certifier.id);
    setError(null);
    startTransition(async () => {
      try {
        const result = await beginIdentityVerification(certifier.id);
        if (!result.actionUrl) {
          window.location.assign(identityHref("badges", "verification", { certifier: certifier.id }));
          return;
        }
        window.location.assign(result.actionUrl);
      } catch (verificationError) {
        toast.notifyError(verificationError, "identity.verification.start.failed");
        setActiveCertifierId(null);
      }
    });
  };

  const handleIdentityReloaded = (nextIdentity: IdentityPage) => {
    setIdentity(nextIdentity);
    setActiveCertifierId(null);
  };

  const handleProfileUpdated = (nextIdentity: IdentityPage) => {
    setIdentity(nextIdentity);
    if (!session) return;
    saveSession({
      ...session,
      displayName: nextIdentity.profile.displaySkin.displayName,
      handle: nextIdentity.profile.displaySkin.displayHandle,
    });
  };

  const handleDisplaySkinSelected = (skin: IdentityDisplaySkin) => {
    setError(null);
    if (skin.source !== "native" && !skin.verified) {
      toast.notifyError(new UiError("identity.skin.verify_before_use", {
        provider: providerLabel(skin.provider),
        handle: skin.displayHandle.replace(/^@+/, ""),
      }));
      return;
    }
    startTransition(async () => {
      try {
        const nextIdentity = await updateIdentityDisplaySkin({
          source: skin.source === "native" ? "native" : "verified_identity",
          certifierId: skin.certifierId,
        });
        setIdentity(nextIdentity);
        if (!session) return;
        saveSession({
          ...session,
          displayName: nextIdentity.profile.displaySkin.displayName,
          handle: nextIdentity.profile.displaySkin.displayHandle,
        });
      } catch (caught) {
        toast.notifyError(caught, "identity.skin.update.failed");
      }
    });
  };

  if (!session) {
    return (
      <PageContainer className="space-y-0">
        <PlainState
          title="身份中心"
          description="登录后可查看市场行为、徽章、虚拟股份资产和个人资料。"
        />
      </PageContainer>
    );
  }

  if (loading) {
    return <IdentitySkeleton />;
  }

  if (!identity) {
    return (
      <PageContainer className="space-y-0">
        <PlainState
          title="身份中心"
          description={error ?? "当前无法加载身份中心数据。"}
        />
      </PageContainer>
    );
  }

  // 中文注释：URL 参数承载 tab 状态，四个一级分类共享同一份身份页后端读模型。
  // 中文注释：底层 Share 在个人中心统一展示为虚拟股份，系统项目固定归到 Root Project。
  const systemShares = identity.activity.sharesLedger.filter((entry) => entry.projectId === "project-root");
  const otherProjectShares = identity.activity.sharesLedger.filter((entry) => entry.projectId !== "project-root");
  const systemShareHolds = identity.activity.shareSettlementHolds.filter((entry) => entry.projectId === "project-root");
  const otherProjectShareHolds = identity.activity.shareSettlementHolds.filter((entry) => entry.projectId !== "project-root");
  const badgeTiles = buildBadgeTiles(identity, linkedAccounts, pendingChallenges);
  return (
    <PageContainer className="space-y-0">
      <div className="max-w-5xl space-y-4">
        <IdentityProfileStrip
          key={`${identity.profile.account.id}:${identity.profile.account.displayName}:${identity.profile.account.agentSummary ?? ""}:${nativeAvatarUrl(identity) ?? ""}`}
          identity={identity}
          isPending={isPending}
          activeCertifierId={activeCertifierId}
          onProfileUpdated={handleProfileUpdated}
          onBeginVerification={handleBeginVerification}
          onSelectDisplaySkin={handleDisplaySkinSelected}
        />
        <PrimaryTabs activeSection={activeSection} />
        <SecondaryTabs
          activeSection={activeSection}
          activeTab={activeTab}
          marketStatusFilter={marketStatusFilter}
          identity={identity}
          systemShares={systemShares}
          otherProjectShares={otherProjectShares}
          systemShareHolds={systemShareHolds}
          otherProjectShareHolds={otherProjectShareHolds}
        />
        {error ? (
          <div className="p-3">
            <ErrorState compact icon={<TriangleAlert className="h-5 w-5" />} title={error} />
          </div>
        ) : null}
        <IdentityContentPane
          identity={identity}
          activeSection={activeSection}
          activeTab={activeTab}
          marketStatusFilter={marketStatusFilter}
          badgeTiles={badgeTiles}
          linkedAccounts={linkedAccounts}
          pendingChallenges={pendingChallenges}
          systemShares={systemShares}
          otherProjectShares={otherProjectShares}
          systemShareHolds={systemShareHolds}
          otherProjectShareHolds={otherProjectShareHolds}
          focusedCertifierId={focusedCertifierId}
          activeCertifierId={activeCertifierId}
          isPending={isPending}
          onBeginVerification={handleBeginVerification}
          onSelectDisplaySkin={handleDisplaySkinSelected}
          onIdentityReloaded={handleIdentityReloaded}
        />
      </div>
    </PageContainer>
  );
}

function buildSessionIdentityPage(session: ClientSession): IdentityPage {
  return {
    profile: {
      account: {
        id: session.accountId,
        displayName: session.displayName,
        handle: session.handle,
        displaySkin: {
          source: "native",
          certifierId: null,
          provider: "monopolyfun",
          platformUserId: null,
          displayName: session.displayName,
          displayHandle: session.handle,
          avatarUrl: null,
          profileUrl: "/profile/me",
          themeKey: "native",
          verified: false,
          selected: true,
        },
      },
      verified: false,
      verifiedFactCount: 0,
      badges: [],
      linkedAccounts: [],
      displaySkin: {
        source: "native",
        certifierId: null,
        provider: "monopolyfun",
        platformUserId: null,
        displayName: session.displayName,
        displayHandle: session.handle,
        avatarUrl: null,
        profileUrl: "/profile/me",
        themeKey: "native",
        verified: false,
        selected: true,
      },
      displaySkinOptions: [{
        source: "native",
        certifierId: null,
        provider: "monopolyfun",
        platformUserId: null,
        displayName: session.displayName,
        displayHandle: session.handle,
        avatarUrl: null,
        profileUrl: "/profile/me",
        themeKey: "native",
        verified: false,
        selected: true,
      }],
    },
    activity: {
      myOffers: [],
      myRequests: [],
      myProjects: [],
      myOrders: [],
      sharesLedger: [],
      shareSettlementHolds: [],
      agentCapabilitySummary: {},
    },
    certifiers: [],
    challenges: [],
  };
}

function nativeAvatarUrl(identity: IdentityPage) {
  // 中文注释：资料编辑写入默认皮肤头像；当前选择外部皮肤时，表单仍回填本地账号头像。
  return identity.profile.displaySkinOptions.find((skin) => skin.source === "native")?.avatarUrl
    ?? (identity.profile.displaySkin.source === "native" ? identity.profile.displaySkin.avatarUrl : null);
}

function isHttpUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function IdentityProfileStrip({
  identity,
  isPending,
  activeCertifierId,
  onProfileUpdated,
  onBeginVerification,
  onSelectDisplaySkin,
}: {
  identity: IdentityPage;
  isPending: boolean;
  activeCertifierId: string | null;
  onProfileUpdated: (identity: IdentityPage) => void;
  onBeginVerification: (certifier: IdentityCertifier) => void;
  onSelectDisplaySkin: (skin: IdentityDisplaySkin) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState(identity.profile.account.displayName);
  const [agentSummary, setAgentSummary] = useState(identity.profile.account.agentSummary ?? "");
  const [avatarUrl, setAvatarUrl] = useState(nativeAvatarUrl(identity) ?? "");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const displaySkin = identity.profile.displaySkin;

  function openProfileEditor(open: boolean) {
    setEditing(open);
    setSaveError(null);
    if (!open) return;
    // 中文注释：编辑弹窗每次打开都回填最新身份读模型，避免保存失败后的旧草稿污染下一次编辑。
    setDisplayName(identity.profile.account.displayName);
    setAgentSummary(identity.profile.account.agentSummary ?? "");
    setAvatarUrl(nativeAvatarUrl(identity) ?? "");
  }

  async function submitProfileEdit() {
    const normalizedDisplayName = displayName.trim();
    if (!normalizedDisplayName) {
      setSaveError("展示名不能为空。");
      return;
    }
    const normalizedAvatarUrl = avatarUrl.trim();
    if (normalizedAvatarUrl && !isHttpUrl(normalizedAvatarUrl)) {
      setSaveError("头像 URL 需要以 http:// 或 https:// 开头。");
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      const nextIdentity = await updateIdentityProfile({
        displayName: normalizedDisplayName,
        agentSummary: agentSummary.trim() || undefined,
        avatarUrl: normalizedAvatarUrl || undefined,
      });
      onProfileUpdated(nextIdentity);
      setEditing(false);
    } catch (caught) {
      setSaveError(caught instanceof Error ? caught.message : "保存资料失败。");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="bg-[var(--background)]">
      <ProfileIdentityHero
        compact
        displayName={displaySkin.displayName}
        handle={displaySkin.displayHandle}
        avatarUrl={displaySkin.avatarUrl}
        summary={identity.profile.account.agentSummary}
        stats={[
          { label: "认证", value: String(identity.profile.verifiedFactCount) },
          { label: "卖", value: String(identity.activity.myOffers.length) },
          { label: "买", value: String(identity.activity.myRequests.length) },
          { label: "虚拟股份", value: String(identity.activity.sharesLedger.length + identity.activity.shareSettlementHolds.length) },
        ]}
        avatarClassName={cn(
          displaySkin.themeKey === "x"
            ? "border-[rgba(255,255,255,0.3)] bg-black"
            : displaySkin.themeKey === "github"
              ? "border-[rgba(255,255,255,0.22)] bg-[#24292f]"
              : "border-[rgba(72,108,230,0.48)]",
        )}
        badges={(
          <>
            {displaySkin.verified ? (
              <Badge variant="success">
                <BadgeCheck className="h-3.5 w-3.5" />
                {providerLabel(displaySkin.provider)} 皮肤
              </Badge>
            ) : (
              <Badge variant="outline">默认皮肤</Badge>
            )}
            <DisplaySkinSwitchButton
              identity={identity}
              isPending={isPending}
              activeCertifierId={activeCertifierId}
              onBeginVerification={onBeginVerification}
              onSelect={onSelectDisplaySkin}
            />
          </>
        )}
        actions={(
          <Button type="button" size="sm" variant="primary" className="h-9 rounded-[12px]" onClick={() => openProfileEditor(true)}>
            编辑资料
          </Button>
        )}
      />
      <Dialog open={editing} onOpenChange={openProfileEditor}>
        <DialogContent aria-describedby={undefined}>
          <DialogHeader>
            <DialogTitle>编辑资料</DialogTitle>
          </DialogHeader>
          <form
            noValidate
            className="grid gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              void submitProfileEdit();
            }}
          >
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]">
              <label className="grid gap-2">
                <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">展示名</span>
                <input
                  className="mf-control-field h-11 w-full px-3"
                  value={displayName}
                  maxLength={60}
                  onChange={(event) => {
                    setDisplayName(event.target.value);
                    setSaveError(null);
                  }}
                  required
                />
              </label>
              <label className="grid gap-2">
                <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">Handle</span>
                <input className="mf-control-field h-11 w-full px-3 opacity-70" value={`@${identity.profile.account.handle}`} readOnly />
              </label>
            </div>
            <label className="grid gap-2">
              <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">头像 URL</span>
              <div className="grid gap-3 sm:grid-cols-[64px_minmax(0,1fr)] sm:items-center">
                <div className="flex h-14 w-14 items-center justify-center overflow-hidden rounded-[14px] border border-[rgba(72,108,230,0.48)] bg-[rgba(72,108,230,0.16)] text-[rgb(218,226,255)]">
                  {avatarUrl.trim() ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={avatarUrl.trim()} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <Camera className="h-5 w-5" />
                  )}
                </div>
                <input
                  className="mf-control-field h-11 w-full px-3"
                  value={avatarUrl}
                  maxLength={500}
                  type="url"
                  inputMode="url"
                  placeholder="https://example.com/avatar.png"
                  onChange={(event) => {
                    setAvatarUrl(event.target.value);
                    setSaveError(null);
                  }}
                />
              </div>
            </label>
            <label className="grid gap-2">
              <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">简介</span>
              <textarea
                className="mf-control-field min-h-24 w-full resize-y px-3 py-3"
                value={agentSummary}
                maxLength={240}
                onChange={(event) => {
                  setAgentSummary(event.target.value);
                  setSaveError(null);
                }}
                placeholder="展示你当前可承接的工作、认证背景或协作偏好"
              />
            </label>
            {saveError ? (
              <div className="rounded-[8px] border border-[rgba(255,97,97,0.24)] bg-[rgba(255,97,97,0.08)] px-3 py-2 text-sm text-[var(--foreground)]" role="alert">
                {saveError}
              </div>
            ) : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => openProfileEditor(false)}>
                取消
              </Button>
              <Button type="submit" variant="primary" loading={saving}>
                保存资料
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </section>
  );
}

function DisplaySkinSwitchButton({
  identity,
  isPending,
  activeCertifierId,
  onBeginVerification,
  onSelect,
}: {
  identity: IdentityPage;
  isPending: boolean;
  activeCertifierId: string | null;
  onBeginVerification: (certifier: IdentityCertifier) => void;
  onSelect: (skin: IdentityDisplaySkin) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const options = buildDisplaySkinChooserOptions(identity);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  function chooseSkin(skin: IdentityDisplaySkin) {
    const certifier = certifierForSkin(identity, skin);
    setOpen(false);
    // 中文注释：顶部切换入口把待认证皮肤直接导向认证流程，避免用户只看到错误提示。
    if (skin.source !== "native" && !skin.verified && certifier) {
      onBeginVerification(certifier);
      return;
    }
    onSelect(skin);
  }

  function beginCertifier(certifier: IdentityCertifier) {
    setOpen(false);
    onBeginVerification(certifier);
  }

  return (
    <div ref={rootRef} className="relative">
      <Button
        type="button"
        size="sm"
        variant="outline"
        className="h-7 px-3 text-[11px]"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        切换
        <ChevronDown className={cn("h-3.5 w-3.5 transition-transform", open ? "rotate-180" : null)} />
      </Button>
      {open ? (
        <div
          role="menu"
          className="absolute left-0 top-full z-30 mt-2 w-[min(360px,calc(100vw-2rem))] rounded-[8px] border border-[var(--border)] bg-[var(--background)] p-2 shadow-[0_18px_42px_rgba(0,0,0,0.42)]"
        >
          <div className="px-2 pb-2 text-[11px] font-black uppercase text-[var(--muted-foreground)]">选择展示皮肤</div>
          <div className="grid gap-1">
            {options.map((option) => {
              if (option.kind === "certifier") {
                const certifier = option.certifier;
                const busy = isPending && activeCertifierId === certifier.id;
                return (
                  <button
                    key={`switch-certifier:${certifier.id}`}
                    type="button"
                    role="menuitem"
                    disabled={isPending}
                    onClick={() => beginCertifier(certifier)}
                    className="flex min-h-[58px] w-full items-center gap-3 rounded-[7px] px-2 py-2 text-left text-[var(--foreground)] hover:bg-[var(--muted)] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <SkinOptionIcon themeKey={certifier.provider} provider={certifier.provider} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-black">{providerLabel(certifier.provider)} 皮肤</span>
                      <span className="mt-0.5 block truncate text-xs font-semibold text-[var(--muted-foreground)]">待认证 · via @{identity.profile.account.handle}</span>
                    </span>
                    <span className="shrink-0 text-xs font-black text-[rgb(245,158,11)]">{busy ? "处理中" : "去认证"}</span>
                  </button>
                );
              }

              const skin = option.skin;
              const actionLabel = skin.selected ? "当前" : skin.source !== "native" && !skin.verified ? "去认证" : "切换";
              return (
                <button
                  key={`switch-skin:${skin.source}:${skin.certifierId ?? "native"}:${skin.platformUserId ?? "local"}`}
                  type="button"
                  role="menuitem"
                  disabled={isPending || skin.selected}
                  onClick={() => chooseSkin(skin)}
                  className={cn(
                    "flex min-h-[58px] w-full items-center gap-3 rounded-[7px] px-2 py-2 text-left text-[var(--foreground)] hover:bg-[var(--muted)] disabled:cursor-not-allowed disabled:opacity-60",
                    skin.selected ? "bg-[rgba(72,230,174,0.08)]" : null,
                  )}
                >
                  <SkinOptionIcon themeKey={skin.themeKey} provider={skin.provider} avatarUrl={skin.avatarUrl} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-black">{displaySkinMenuLabel(skin)}</span>
                    <span className="mt-0.5 block truncate text-xs font-semibold text-[var(--muted-foreground)]">@{skin.displayHandle.replace(/^@+/, "")}</span>
                  </span>
                  <span className="flex shrink-0 items-center gap-1 text-xs font-black text-[var(--muted-foreground)]">
                    {skin.selected ? <Check className="h-3.5 w-3.5 text-[rgb(72,230,174)]" /> : null}
                    {actionLabel}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function SkinOptionIcon({ themeKey, provider, avatarUrl }: { themeKey?: string | null; provider?: string | null; avatarUrl?: string | null }) {
  return (
    <span className={cn(
      "flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full border border-[var(--border)] text-[var(--foreground)]",
      themeKey === "x" || provider === "x" || provider === "twitter"
        ? "bg-black"
        : themeKey === "github" || provider === "github"
          ? "bg-[#24292f]"
          : "bg-[var(--surface-2)]",
    )}>
      {avatarUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={avatarUrl} alt="" className="h-full w-full object-cover" />
      ) : themeKey === "github" || provider === "github" ? (
        <Github className="h-4 w-4" />
      ) : themeKey === "youtube" || provider === "youtube" ? (
        <Youtube className="h-4 w-4" />
      ) : (
        <Fingerprint className="h-4 w-4" />
      )}
    </span>
  );
}

function PrimaryTabs({ activeSection }: { activeSection: PrimarySection }) {
  return (
    <div className="flex overflow-x-auto border-b border-[var(--border)] [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
      <div className="flex min-w-max">
        {primarySections.map((item) => {
          const active = item.id === activeSection;
          return (
            <Link
              key={item.id}
              href={identityHref(item.id)}
              aria-current={active ? "page" : undefined}
              className={cn(
                "relative inline-flex min-h-11 items-center justify-center gap-2 px-5 text-sm transition",
                active
                  ? "text-[var(--foreground)]"
                  : "text-[var(--muted-foreground)] hover:text-[var(--foreground)]",
              )}
            >
              <span>{item.label}</span>
              <span className={cn("absolute bottom-0 left-0 right-0 h-[2px] rounded-full transition", active ? "bg-[var(--primary)]" : "bg-transparent")} />
            </Link>
          );
        })}
      </div>
    </div>
  );
}

function SecondaryTabs({
  activeSection,
  activeTab,
  marketStatusFilter,
  identity,
  systemShares,
  otherProjectShares,
  systemShareHolds,
  otherProjectShareHolds,
}: {
  activeSection: PrimarySection;
  activeTab: SecondaryTab;
  marketStatusFilter: OrderStatusFilter;
  identity: IdentityPage;
  systemShares: SharesLedgerEntry[];
  otherProjectShares: SharesLedgerEntry[];
  systemShareHolds: ShareSettlementHold[];
  otherProjectShareHolds: ShareSettlementHold[];
}) {
  if (activeSection === "identity") {
    return null;
  }

  const tabs = secondaryTabsFor(activeSection);
  const counts = secondaryCountsFor(activeSection, identity, systemShares, otherProjectShares, systemShareHolds, otherProjectShareHolds);

  return (
    <div className="flex overflow-x-auto [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
      <div className="flex min-w-max gap-2">
        {tabs.map((item) => {
          const active = item.id === activeTab;
          return (
            <Link
              key={item.id}
              href={identityHref(activeSection, item.id, activeSection === "market" && marketStatusFilter !== "all" ? { status: marketStatusFilter } : undefined)}
              aria-current={active ? "page" : undefined}
              className={cn(
                "inline-flex min-h-8 items-center gap-2 rounded-[10px] px-3 text-xs transition",
                active
                  ? "bg-[var(--surface-control)] text-[var(--foreground)]"
                  : "bg-transparent text-[var(--muted-foreground)] hover:bg-[var(--surface-2)] hover:text-[var(--foreground)]",
              )}
            >
              <span>{item.label}</span>
              <span className="text-[var(--muted-foreground)]">{counts[item.id]}</span>
            </Link>
          );
        })}
      </div>
    </div>
  );
}

function IdentityContentPane({
  identity,
  activeSection,
  activeTab,
  marketStatusFilter,
  badgeTiles,
  linkedAccounts,
  pendingChallenges,
  systemShares,
  otherProjectShares,
  systemShareHolds,
  otherProjectShareHolds,
  focusedCertifierId,
  activeCertifierId,
  isPending,
  onBeginVerification,
  onSelectDisplaySkin,
  onIdentityReloaded,
}: {
  identity: IdentityPage;
  activeSection: PrimarySection;
  activeTab: SecondaryTab;
  marketStatusFilter: OrderStatusFilter;
  badgeTiles: BadgeTile[];
  linkedAccounts: Map<string, IdentityPage["profile"]["linkedAccounts"][number]>;
  pendingChallenges: Map<string, IdentityPage["challenges"][number]>;
  systemShares: SharesLedgerEntry[];
  otherProjectShares: SharesLedgerEntry[];
  systemShareHolds: ShareSettlementHold[];
  otherProjectShareHolds: ShareSettlementHold[];
  focusedCertifierId: string | null;
  activeCertifierId: string | null;
  isPending: boolean;
  onBeginVerification: (certifier: IdentityCertifier) => void;
  onSelectDisplaySkin: (skin: IdentityDisplaySkin) => void;
  onIdentityReloaded: (identity: IdentityPage) => void;
}) {
  if (activeSection === "market") {
    return <MarketPane identity={identity} activeTab={activeTab as MarketTab} statusFilter={marketStatusFilter} />;
  }

  if (activeSection === "badges") {
    return activeTab === "verification" ? (
      <VerificationPane
        certifiers={identity.certifiers}
        linkedAccounts={linkedAccounts}
        pendingChallenges={pendingChallenges}
        focusedCertifierId={focusedCertifierId}
        activeCertifierId={activeCertifierId}
        isPending={isPending}
        onBeginVerification={onBeginVerification}
        onIdentityReloaded={onIdentityReloaded}
      />
    ) : (
      <BadgeOverviewPane badges={badgeTiles} />
    );
  }

  if (activeSection === "assets") {
    return <AssetsPane activeTab={activeTab as AssetTab} systemShares={systemShares} otherProjectShares={otherProjectShares} systemShareHolds={systemShareHolds} otherProjectShareHolds={otherProjectShareHolds} />;
  }

  return (
    <ProfilePane
      identity={identity}
      isPending={isPending}
      activeCertifierId={activeCertifierId}
      onBeginVerification={onBeginVerification}
      onSelectDisplaySkin={onSelectDisplaySkin}
    />
  );
}

function MarketPane({ identity, activeTab, statusFilter }: { identity: IdentityPage; activeTab: MarketTab; statusFilter: OrderStatusFilter }) {
  const postKind = marketTabPostKind(activeTab);
  const relationOrders = marketTabOrders(identity, activeTab);
  return (
    <div className="space-y-3">
      <MarketStatusFilter activeTab={activeTab} activeFilter={statusFilter} orders={relationOrders} />
      <IdentityOrderActivityBlocks identity={identity} postKind={postKind} statusFilter={statusFilter} empty={marketEmptyText(activeTab, statusFilter)} />
    </div>
  );
}

function orderStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    claimed: "进行中",
    delivered: "已交付",
    accepted_open: "已验收",
    disputed: "争议中",
    final_accepted: "已完成",
    final_closed: "已关闭",
  };
  return status ? labels[status] ?? status : "状态缺失";
}

function orderPostKindLabel(kind?: string) {
  const labels: Record<string, string> = {
    offer: "供给执行",
    request: "需求执行",
    project: "项目执行",
    review: "评审执行",
  };
  return kind ? labels[kind] ?? kind : "执行";
}

function BadgeOverviewPane({ badges }: { badges: BadgeTile[] }) {
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {badges.map((badge) => {
        const statusBadge = badge.status === "earned"
          ? <Badge variant="success">已认证</Badge>
          : badge.status === "pending"
            ? <Badge variant="warning">处理中</Badge>
            : <Badge variant="outline">待认证</Badge>;
        const content = (
          <div
            className={cn(
              "flex min-h-[88px] items-center gap-3 rounded-[8px] border bg-[var(--surface-1)] px-4 py-3 text-left transition",
              badge.status === "earned"
                ? "border-[rgba(72,230,174,0.28)]"
                : badge.status === "pending"
                  ? "border-[rgba(240,180,95,0.28)]"
                  : "border-[var(--border)] hover:bg-[var(--surface-2)]",
            )}
          >
            <span className={cn(
              "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] border bg-[var(--background)]",
              badge.status === "earned"
                ? "border-[rgba(72,230,174,0.34)] text-[rgb(184,255,229)]"
                : "border-[var(--border)] text-[var(--muted-foreground)]",
            )}>
              {badge.icon}
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex min-w-0 flex-wrap items-center gap-2">
                <span className="truncate text-sm text-[var(--foreground)]">{badge.label}</span>
                {statusBadge}
              </span>
              <span className="mt-1 block text-xs leading-5 text-[var(--muted-foreground)]">{badge.description}</span>
            </span>
            {badge.status === "earned" ? <CheckCircle2 className="h-4 w-4 shrink-0 text-[rgb(72,230,174)]" /> : <ArrowRight className="h-4 w-4 shrink-0 text-[var(--muted-foreground)]" />}
          </div>
        );

        return badge.href ? (
          <Link key={badge.id} href={badge.href}>
            {content}
          </Link>
        ) : (
          <div key={badge.id}>{content}</div>
        );
      })}
    </div>
  );
}

function VerificationPane({
  certifiers,
  linkedAccounts,
  pendingChallenges,
  focusedCertifierId,
  activeCertifierId,
  isPending,
  onBeginVerification,
  onIdentityReloaded,
}: {
  certifiers: IdentityCertifier[];
  linkedAccounts: Map<string, IdentityPage["profile"]["linkedAccounts"][number]>;
  pendingChallenges: Map<string, IdentityPage["challenges"][number]>;
  focusedCertifierId: string | null;
  activeCertifierId: string | null;
  isPending: boolean;
  onBeginVerification: (certifier: IdentityCertifier) => void;
  onIdentityReloaded: (identity: IdentityPage) => void;
}) {
  return (
    <div className="grid gap-2">
      {certifiers.map((certifier) => {
        const linked = linkedAccounts.get(certifier.id);
        const pending = pendingChallenges.get(certifier.id);
        const busy = isPending && activeCertifierId === certifier.id;
        return (
          <div
            key={certifier.id}
            className={cn(
              "grid gap-4 rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-center",
              focusedCertifierId === certifier.id ? "border-[var(--primary-border)] bg-[var(--secondary)]" : "",
            )}
          >
            <div className="flex min-w-0 gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] border border-[var(--border)] bg-[var(--background)] text-[var(--muted-foreground)]">
                {providerIcon(certifier.provider)}
              </span>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 text-sm text-[var(--foreground)]">
                  {certifier.name}
                  {linked ? <Badge variant="success">已连接</Badge> : pending ? <Badge variant="warning">处理中</Badge> : <Badge variant="outline">待认证</Badge>}
                </div>
                <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">
                  {linked
                    ? `${linked.displayName || linked.handle || linked.platformUserId} · ${formatDate(linked.verifiedAt)}`
                    : pending
                      ? pending.verificationMethod === "public_proof"
                        ? `挑战创建于 ${formatDate(pending.createdAt)}，提交证明链接后完成。`
                        : `挑战创建于 ${formatDate(pending.createdAt)}，等待 OAuth 回调完成。`
                      : certifier.description}
                </div>
              </div>
            </div>
            <div className="flex items-center justify-start gap-3 md:justify-end">
              <span className="text-xs text-[var(--muted-foreground)]">{verificationMethodLabel(certifier.verificationMethod)}</span>
              <Button
                type="button"
                variant={linked ? "outline" : "primary"}
                loading={busy}
                onClick={() => onBeginVerification(certifier)}
              >
                {linked ? "重新认证" : "开始认证"}
              </Button>
            </div>
            {certifier.verificationMethod === "public_proof" ? (
              <PublicProofVerificationForm
                certifier={certifier}
                pendingChallenge={pending ?? null}
                linked={Boolean(linked)}
                onIdentityReloaded={onIdentityReloaded}
              />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function PublicProofVerificationForm({
  certifier,
  pendingChallenge,
  linked,
  onIdentityReloaded,
}: {
  certifier: IdentityCertifier;
  pendingChallenge: IdentityPage["challenges"][number] | null;
  linked: boolean;
  onIdentityReloaded: (identity: IdentityPage) => void;
}) {
  const placements = publicProofPlacements(certifier);
  const [handle, setHandle] = useState("");
  const [proofPlacement, setProofPlacement] = useState(placements[0] ?? "post");
  const [proofUrl, setProofUrl] = useState("");
  const [challenge, setChallenge] = useState(pendingChallenge);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const activeChallenge = challenge ?? pendingChallenge;
  const tokenText = stringFromRecord(activeChallenge?.instructions, "tokenText");

  async function startPublicProof() {
    setBusy(true);
    setFormError(null);
    try {
      const result = await beginIdentityVerification(certifier.id, { handle, proofPlacement });
      setChallenge(result.challenge);
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : "发起公开证明失败。");
    } finally {
      setBusy(false);
    }
  }

  async function completePublicProof() {
    if (!activeChallenge?.id) {
      setFormError("请先生成认证 token。");
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      await completeIdentityVerification(activeChallenge.id, { proofUrl });
      const nextIdentity = await getIdentityPage();
      setChallenge(null);
      setProofUrl("");
      onIdentityReloaded(nextIdentity);
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : "完成公开证明失败。");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-3 py-3 sm:col-span-2">
      <div className="grid gap-3 md:grid-cols-[1fr_180px_auto]">
        <label className="grid gap-1.5 text-sm font-bold text-[var(--foreground)]">
          平台账号
          <input
            className="mf-control-field h-10 w-full px-3"
            value={handle}
            disabled={busy || Boolean(activeChallenge)}
            onChange={(event) => setHandle(event.target.value)}
            placeholder={`输入 ${providerLabel(certifier.provider)} handle`}
          />
        </label>
        <label className="grid gap-1.5 text-sm font-bold text-[var(--foreground)]">
          证明位置
          <select
            className="mf-control-field h-10 w-full px-3"
            value={proofPlacement}
            disabled={busy || Boolean(activeChallenge)}
            onChange={(event) => setProofPlacement(event.target.value)}
          >
            {placements.map((placement) => (
              <option key={placement} value={placement}>{proofPlacementLabel(placement)}</option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <Button type="button" variant={linked ? "outline" : "primary"} loading={busy && !activeChallenge} disabled={busy || Boolean(activeChallenge)} onClick={startPublicProof}>
            生成 token
          </Button>
        </div>
      </div>
      {activeChallenge ? (
        <div className="mt-3 grid gap-3 rounded-[8px] border border-[rgba(72,108,230,0.28)] bg-[rgba(72,108,230,0.08)] p-3">
          <div className="text-xs font-semibold leading-5 text-[var(--muted-foreground)]">
            把下面 token 放到 {providerLabel(certifier.provider)} 的{proofPlacementLabel(String(activeChallenge.context?.proofPlacement ?? proofPlacement))}，再提交公开链接。
          </div>
          <code className="block overflow-x-auto rounded-[7px] border border-[var(--border)] bg-[var(--background)] px-3 py-2 text-sm font-black text-[var(--foreground)]">
            {tokenText || activeChallenge.challengeToken}
          </code>
          <div className="grid gap-3 md:grid-cols-[1fr_auto]">
            <input
              className="mf-control-field h-10 w-full px-3"
              value={proofUrl}
              disabled={busy}
              onChange={(event) => setProofUrl(event.target.value)}
              placeholder="粘贴公开证明链接"
            />
            <Button type="button" variant="primary" loading={busy} onClick={completePublicProof}>
              完成认证
            </Button>
          </div>
        </div>
      ) : null}
      {formError ? (
        <div className="mt-3 rounded-[8px] border border-[rgba(255,97,97,0.24)] bg-[rgba(255,97,97,0.08)] px-3 py-2 text-sm text-[var(--foreground)]">
          {formError}
        </div>
      ) : null}
    </div>
  );
}

function AssetsPane({
  activeTab,
  systemShares,
  otherProjectShares,
  systemShareHolds,
  otherProjectShareHolds,
}: {
  activeTab: AssetTab;
  systemShares: SharesLedgerEntry[];
  otherProjectShares: SharesLedgerEntry[];
  systemShareHolds: ShareSettlementHold[];
  otherProjectShareHolds: ShareSettlementHold[];
}) {
  const ledgerEntries = activeTab === "system" ? systemShares : otherProjectShares;
  const holdEntries = activeTab === "system" ? systemShareHolds : otherProjectShareHolds;
  const lockedEntries = holdEntries.filter((entry) => settlementHoldBucket(entry) === "locked");
  const frozenEntries = holdEntries.filter((entry) => settlementHoldBucket(entry) === "frozen");
  return (
    <div className="grid gap-4">
      <div className="grid gap-2 sm:grid-cols-3">
        <MetricPill label="已释放" value={String(sumLedgerShares(ledgerEntries))} />
        <MetricPill label="锁定中" value={String(sumHoldShares(lockedEntries))} />
        <MetricPill label="冻结中" value={String(sumHoldShares(frozenEntries))} />
      </div>
      <ShareRows entries={ledgerEntries} empty={activeTab === "system" ? "当前系统项目已释放虚拟股份记录为空。" : "当前其他项目已释放虚拟股份记录为空。"} />
      <ShareHoldRows entries={lockedEntries} title="锁定中的虚拟股份" empty="当前没有锁定中的虚拟股份。" />
      <ShareHoldRows entries={frozenEntries} title="冻结中的虚拟股份" empty="当前没有冻结中的虚拟股份。" />
    </div>
  );
}

function ProfilePane({
  identity,
  isPending,
  activeCertifierId,
  onBeginVerification,
  onSelectDisplaySkin,
}: {
  identity: IdentityPage;
  isPending: boolean;
  activeCertifierId: string | null;
  onBeginVerification: (certifier: IdentityCertifier) => void;
  onSelectDisplaySkin: (skin: IdentityDisplaySkin) => void;
}) {
  return (
    <div className="grid gap-4">
      <DisplaySkinChooser
        identity={identity}
        isPending={isPending}
        activeCertifierId={activeCertifierId}
        onBeginVerification={onBeginVerification}
        onSelect={onSelectDisplaySkin}
      />
      <section className="overflow-hidden rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4">
        <div className="divide-y divide-[var(--border)]">
          <InfoRow label="当前展示" value={`${identity.profile.displaySkin.displayName} @${identity.profile.displaySkin.displayHandle}`} icon={Fingerprint} />
          <InfoRow label="用户名" value={identity.profile.account.displayName} icon={User} />
          <InfoRow label="Handle" value={`@${identity.profile.account.handle}`} icon={KeyRound} />
          <InfoRow label="认证事实" value={`${identity.profile.verifiedFactCount} 条`} icon={BadgeCheck} />
        </div>
      </section>
      <section className="overflow-hidden rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)]">
        <div className="border-b border-[var(--border)] px-4 py-3 text-xs uppercase text-[var(--muted-foreground)]">外部账号</div>
        <div className="divide-y divide-[var(--border)] px-4">
          {identity.profile.linkedAccounts.length === 0 ? (
            <EmptyLine text="当前外部账号连接为空。" />
          ) : (
            identity.profile.linkedAccounts.map((account) => (
              <ListRow
                key={`${account.certifierId}:${account.platformUserId}`}
                icon={account.provider === "github" ? Github : LinkIcon}
                title={account.displayName || account.platformUserId}
                meta={`${account.handle ? `@${account.handle}` : account.platformUserId} · ${providerLabel(account.provider)} · ${formatDate(account.verifiedAt)}`}
                trailing={<Badge variant="success">已认证</Badge>}
              />
            ))
          )}
        </div>
      </section>
    </div>
  );
}

function DisplaySkinChooser({
  identity,
  isPending,
  activeCertifierId,
  onBeginVerification,
  onSelect,
}: {
  identity: IdentityPage;
  isPending: boolean;
  activeCertifierId: string | null;
  onBeginVerification: (certifier: IdentityCertifier) => void;
  onSelect: (skin: IdentityDisplaySkin) => void;
}) {
  const skinOptions = buildDisplaySkinChooserOptions(identity);

  return (
    <div className="py-4">
      <div className="mb-3 text-xs font-black uppercase text-[var(--muted-foreground)]">展示皮肤</div>
      <div className="grid gap-2 md:grid-cols-2">
        {skinOptions.map((option) => {
          if (option.kind === "certifier") {
            const certifier = option.certifier;
            const busy = isPending && activeCertifierId === certifier.id;
            return (
              <div
                key={`certifier:${certifier.id}`}
                className="flex min-h-[92px] items-center gap-3 rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-3 py-3 text-left transition hover:border-[var(--primary-border)] hover:bg-[var(--surface-2)]"
              >
                <span className={cn(
                  "flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-full border border-[var(--border)] text-[var(--foreground)]",
                  certifier.provider === "github" ? "bg-[#24292f]" : certifier.provider === "x" || certifier.provider === "twitter" ? "bg-black" : "bg-[var(--surface-2)]",
                )}>
                  {certifier.provider === "github" ? <Github className="h-4 w-4" /> : <Fingerprint className="h-4 w-4" />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-black text-[var(--foreground)]">{certifier.name}</span>
                  <span className="mt-1 block truncate text-xs font-semibold text-[var(--muted-foreground)]">
                    {providerLabel(certifier.provider)} 待认证皮肤
                  </span>
                  <span className="mt-1 block truncate text-[11px] font-semibold text-[var(--muted-foreground)]">via @{identity.profile.account.handle}</span>
                </span>
                <Button
                  type="button"
                  variant="outline"
                  loading={busy}
                  disabled={isPending}
                  onClick={() => onBeginVerification(certifier)}
                >
                  去认证
                </Button>
              </div>
            );
          }

          const skin = option.skin;
          const requiresVerification = skin.source !== "native" && !skin.verified;
          const certifier = certifierForSkin(identity, skin);
          return (
            <div
              key={`${skin.source}:${skin.certifierId ?? "native"}:${skin.platformUserId ?? "local"}`}
              className={cn(
                "flex min-h-[92px] items-center gap-3 rounded-[8px] border px-3 py-3 text-left transition",
                skin.selected
                  ? "border-[var(--primary-border)] bg-[var(--surface-selected)]"
                  : requiresVerification
                    ? "border-[var(--border)] bg-[var(--surface-1)] hover:border-[var(--primary-border)] hover:bg-[var(--surface-2)]"
                    : "border-[var(--border)] bg-[var(--surface-1)] hover:border-[var(--primary-border)] hover:bg-[var(--surface-2)]",
              )}
            >
              <span className={cn(
                "flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-full border border-[var(--border)] text-[var(--foreground)]",
                skin.themeKey === "x" ? "bg-black" : skin.themeKey === "github" ? "bg-[#24292f]" : "bg-[var(--surface-2)]",
              )}>
                {skin.avatarUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={skin.avatarUrl} alt="" className="h-full w-full object-cover" />
                ) : skin.themeKey === "github" ? (
                  <Github className="h-4 w-4" />
                ) : (
                  <Fingerprint className="h-4 w-4" />
                )}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-black text-[var(--foreground)]">{skin.displayName}</span>
                <span className="mt-1 block truncate text-xs font-semibold text-[var(--muted-foreground)]">
                  @{skin.displayHandle.replace(/^@+/, "")} · {displaySkinStatusLabel(skin)}
                </span>
                <span className="mt-1 block truncate text-[11px] font-semibold text-[var(--muted-foreground)]">via @{identity.profile.account.handle}</span>
              </span>
              {skin.selected ? (
                <span className="shrink-0 rounded-full border border-[var(--border)] px-2.5 py-1 text-[11px] font-black text-[var(--muted-foreground)]">当前</span>
              ) : (
                <Button
                  type="button"
                  variant={requiresVerification ? "outline" : "primary"}
                  disabled={isPending}
                  onClick={() => {
                    if (requiresVerification && certifier) {
                      onBeginVerification(certifier);
                      return;
                    }
                    onSelect(skin);
                  }}
                >
                  {requiresVerification ? "去认证" : "切换"}
                </Button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

type DisplaySkinChooserOption =
  | { kind: "skin"; skin: IdentityDisplaySkin }
  | { kind: "certifier"; certifier: IdentityCertifier };

function buildDisplaySkinChooserOptions(identity: IdentityPage): DisplaySkinChooserOption[] {
  const existingCertifierIds = new Set(identity.profile.displaySkinOptions
    .map((skin) => skin.certifierId)
    .filter((certifierId): certifierId is string => Boolean(certifierId)));
  const missingCertifiers = identity.certifiers
    .filter((certifier) => !existingCertifierIds.has(certifier.id))
    .map((certifier) => ({ kind: "certifier" as const, certifier }));
  return [
    ...identity.profile.displaySkinOptions.map((skin) => ({ kind: "skin" as const, skin })),
    ...missingCertifiers,
  ];
}

function certifierForSkin(identity: IdentityPage, skin: IdentityDisplaySkin) {
  return identity.certifiers.find((certifier) => certifier.id === skin.certifierId);
}

type OrderBucket = {
  publishedOrders: Order[];
  tradedOrders: Order[];
};

type IdentityOrderActivityRow = {
  order: Order;
  roleFallback?: string;
};

function MarketStatusFilter({ activeTab, activeFilter, orders }: { activeTab: MarketTab; activeFilter: OrderStatusFilter; orders: Order[] }) {
  const router = useRouter();
  const counts = Object.fromEntries(marketStatusFilters.map((filter) => [
    filter.id,
    orders.filter((order) => matchesOrderStatusFilter(order, filter.id)).length,
  ])) as Record<OrderStatusFilter, number>;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="text-xs text-[var(--muted-foreground)]">
        {marketStatusFilters.find((filter) => filter.id === activeFilter)?.label ?? "全部状态"} · {counts[activeFilter]} 单
      </div>
      <label className="flex items-center gap-2 text-xs text-[var(--muted-foreground)]">
        <span>状态</span>
        <select
          value={activeFilter}
          className="h-9 rounded-[10px] border border-[var(--border)] bg-[var(--surface-1)] px-3 text-sm text-[var(--foreground)] outline-none transition focus:border-[var(--primary)]"
          onChange={(event) => {
            const value = normalizeOrderStatusFilter(event.target.value);
            // 中文注释：状态筛选独立于市场关系 tab，用户可以在卖、买、项目之间保留同一流程视角。
            router.push(identityHref("market", activeTab, value === "all" ? undefined : { status: value }));
          }}
        >
          {marketStatusFilters.map((filter) => (
            <option key={filter.id} value={filter.id}>
              {filter.label} {counts[filter.id]}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

function IdentityOrderActivityBlocks({ identity, postKind, statusFilter, empty = "当前市场相关执行为空。" }: { identity: IdentityPage; postKind?: string; statusFilter: OrderStatusFilter; empty?: string }) {
  const buckets = buildOrderBuckets(identity);
  // 中文注释：市场页统一承接订单列表入口，发布侧、交易侧和阶段筛选共享同一组订单行。
  const rows: IdentityOrderActivityRow[] = [
    ...buckets.publishedOrders.map((order) => ({ order, roleFallback: "发布方" })),
    ...buckets.tradedOrders.map((order) => ({ order })),
  ]
    .filter(({ order }) => !postKind || order.postKind === postKind)
    .filter(({ order }) => matchesOrderStatusFilter(order, statusFilter))
    .sort((left, right) => new Date(right.order.updatedAt).getTime() - new Date(left.order.updatedAt).getTime());

  if (rows.length === 0) {
    return <EmptyLine text={empty} />;
  }

  return (
    <div className="divide-y divide-[var(--border)] overflow-hidden rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)]">
      {rows.map(({ order, roleFallback }) => (
        <CompactOrderActivityRow key={order.id} order={order} roleFallback={roleFallback} />
      ))}
    </div>
  );
}

function CompactOrderActivityRow({ order, roleFallback }: { order: Order; roleFallback?: string }) {
  const role = roleFallback ?? orderRoleLabel(order.currentAccountRole, order.postKind);
  const riskBadges = buildOrderWorkflowBadges(order);
  return (
    <Link href={orderHref(order)} className="block transition hover:bg-[var(--surface-2)]">
      <div className="grid gap-2 px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="max-w-full truncate text-sm font-normal text-[var(--foreground)]">
              {orderDisplayTitle(order)}
            </span>
            <Badge variant={orderStatusVariant(order.status)}>{orderStatusLabel(order.status)}</Badge>
            <Badge variant="outline">{role}</Badge>
          </div>
          <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-xs font-normal text-[var(--muted-foreground)]">
            <span>{orderPostKindLabel(order.postKind)}</span>
            <span>·</span>
            <span>{formatSettlement(order)}</span>
            {order.progressCount > 0 ? (
              <>
                <span>·</span>
                <span>进度 {order.progressCount} 次</span>
              </>
            ) : null}
          </div>
          {riskBadges.length > 0 ? (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {riskBadges.map((badge) => (
                <Badge key={badge} variant="danger" className="gap-1.5">
                  <TriangleAlert className="h-3.5 w-3.5" />
                  {badge}
                </Badge>
              ))}
            </div>
          ) : null}
        </div>
        <div className="flex items-center justify-between gap-3 text-xs font-normal text-[var(--muted-foreground)] sm:justify-end">
          <span>{formatDate(order.updatedAt)}</span>
        </div>
      </div>
    </Link>
  );
}

function ShareRows({ entries, empty }: { entries: SharesLedgerEntry[]; empty: string }) {
  if (entries.length === 0) return <EmptyLine text={empty} />;
  return (
    <div className="divide-y divide-[var(--border)]">
      {entries.map((entry) => (
        <ListRow
          key={entry.id}
          icon={Wallet}
          title={`${entry.amount} 虚拟股份`}
          meta={`${entry.reason} · ${entry.settlementTypeSnapshot} · curve slot ${entry.curveSlot}`}
          trailing={<span className="text-xs font-bold text-[var(--muted-foreground)]">{formatDate(entry.createdAt)}</span>}
        />
      ))}
    </div>
  );
}

function ShareHoldRows({ entries, title, empty }: { entries: ShareSettlementHold[]; title: string; empty: string }) {
  return (
    <div className="grid gap-2">
      <div className="text-xs font-black uppercase text-[var(--muted-foreground)]">{title}</div>
      {entries.length === 0 ? <EmptyLine text={empty} /> : (
        <div className="divide-y divide-[var(--border)] rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4">
          {entries.map((entry) => (
            <ListRow
              key={entry.id}
              icon={Wallet}
              title={`${entry.amount} 虚拟股份 · ${entry.orderNo}`}
              meta={shareHoldMeta(entry)}
              trailing={<span className="text-xs font-bold text-[var(--muted-foreground)]">{formatDate(entry.updatedAt)}</span>}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function ListRow({
  icon: Icon,
  title,
  meta,
  href,
  trailing,
}: {
  icon: IconComponent;
  title: string;
  meta: string;
  href?: string;
  trailing?: ReactNode;
}) {
  const row = (
    <div className="flex min-h-[62px] items-center gap-3 py-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[9px] border border-[var(--border)] text-[var(--muted-foreground)]">
        <Icon className="h-4 w-4" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-black text-[var(--foreground)]">{title}</span>
        <span className="mt-1 block truncate text-xs font-semibold text-[var(--muted-foreground)]">{meta}</span>
      </span>
      {trailing ? <span className="shrink-0">{trailing}</span> : null}
    </div>
  );

  return href ? (
    <Link href={href} className="block transition hover:bg-[rgba(255,255,255,0.03)]">
      {row}
    </Link>
  ) : row;
}

function InfoRow({ label, value, icon }: { label: string; value: string; icon: IconComponent }) {
  return (
    <ListRow
      icon={icon}
      title={label}
      meta={value}
    />
  );
}

function MetricPill({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[8px] border border-[var(--border)] px-3 py-2">
      <div className="text-[11px] font-bold text-[var(--muted-foreground)]">{label}</div>
      <div className="mt-1 text-base font-black text-[var(--foreground)]">{value}</div>
    </div>
  );
}

function EmptyLine({ text }: { text: string }) {
  return (
    <div className="rounded-[8px] border border-dashed border-[var(--border)] px-4 py-5 text-sm text-[var(--muted-foreground)]">
      {text}
    </div>
  );
}

function PlainState({
  title,
  description,
  icon,
}: {
  title: string;
  description: string;
  icon?: ReactNode;
}) {
  return (
    <section className="border-y border-[var(--border)] px-4 py-8">
      <div className="flex items-center gap-3">
        {icon}
        <div>
          <h1 className="text-xl font-black text-[var(--foreground)]">{title}</h1>
          <p className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">{description}</p>
        </div>
      </div>
    </section>
  );
}

type BadgeTile = {
  id: string;
  label: string;
  description: string;
  status: "earned" | "pending" | "available";
  icon: ReactNode;
  href?: string;
};

function buildBadgeTiles(
  identity: IdentityPage,
  linkedAccounts: Map<string, IdentityPage["profile"]["linkedAccounts"][number]>,
  pendingChallenges: Map<string, IdentityPage["challenges"][number]>,
): BadgeTile[] {
  // 中文注释：徽章总览把统一 Badge 和认证器 Badge 合成一面图标墙，点击待认证项进入认证二级页。
  const earnedBadges = dedupeBadges(identity.profile.badges).map((badge) => ({
    id: `${badge.kind}:${badge.code}`,
    label: badge.label,
    description: badge.kind === "verified" ? "认证类 Badge 已点亮。" : "通过项目协作和信誉积累获得。",
    status: "earned" as const,
    icon: badgeIcon(badge.icon),
  }));

  const certifierBadges = identity.certifiers.map((certifier) => {
    const linked = linkedAccounts.get(certifier.id);
    const pending = pendingChallenges.get(certifier.id);
    return {
      id: `certifier:${certifier.id}`,
      label: `${certifier.name} 认证`,
      description: linked ? "认证已完成。" : pending ? "认证流程处理中。" : "点击进入认证页点亮这个 Badge。",
      status: linked ? "earned" as const : pending ? "pending" as const : "available" as const,
      icon: certifier.provider === "github" ? <Github className="h-4 w-4" /> : <ShieldCheck className="h-4 w-4" />,
      href: linked ? undefined : identityHref("badges", "verification", { certifier: certifier.id }),
    };
  });

  const certifierEarnedCodes = new Set(identity.certifiers.map((certifier) => certifier.id));
  return [
    ...certifierBadges,
    ...earnedBadges.filter((badge) => !Array.from(certifierEarnedCodes).some((certifierId) => badge.id.includes(certifierId))),
  ];
}

function dedupeBadges(badges: IdentityBadge[]) {
  return badges.filter((badge, index, list) => (
    list.findIndex((candidate) => candidate.kind === badge.kind && candidate.code === badge.code) === index
  ));
}

function badgeIcon(icon: IdentityBadge["icon"]) {
  switch (icon) {
    case "github":
      return <Github className="h-4 w-4" />;
    case "flame":
      return <Award className="h-4 w-4" />;
    case "clock":
      return <CircleDashed className="h-4 w-4" />;
    case "sprout":
      return <Star className="h-4 w-4" />;
    default:
      return <ShieldCheck className="h-4 w-4" />;
  }
}

function LinkIcon({ className }: { className?: string }) {
  return <Info className={className} />;
}

function buildOrderBuckets(identity: IdentityPage): OrderBucket {
  const publishedPostIds = new Set([
    ...identity.activity.myOffers.map((offer) => offer.id),
    ...identity.activity.myRequests.map((request) => request.id),
    ...identity.activity.myProjects.map((project) => project.id).filter((id): id is string => !!id),
  ]);
  // 中文注释：订单按发布源归入“我发布”，其余参与订单归入“我交易”，避免身份页再展示重复市场条目。
  const publishedOrders = identity.activity.myOrders.filter((order) => order.postId && publishedPostIds.has(order.postId));
  const tradedOrders = identity.activity.myOrders.filter((order) => !order.postId || !publishedPostIds.has(order.postId));

  return {
    publishedOrders,
    tradedOrders,
  };
}

function orderStageId(order: Order): OrderStageId {
  if (order.status === "disputed") return "dispute";
  if (order.status === "final_accepted" || order.status === "final_closed") return "complete";
  if (isMoneyPaymentPending(order)) return "payment";
  const phase = String(order.displayPhase ?? "").toLowerCase();
  if (phase.includes("payment")) return "payment";
  if (phase.includes("accept") || order.status === "delivered" || order.status === "accepted_open") return "acceptance";
  return "delivery";
}

function matchesOrderStatusFilter(order: Order, filter: OrderStatusFilter) {
  if (filter === "all") return true;
  const stage = orderStageId(order);
  if (filter === "active") return stage === "payment" || stage === "delivery" || stage === "acceptance";
  if (filter === "dispute") return stage === "dispute";
  if (filter === "complete") return stage === "complete";
  return isOrderActionOwnedByCurrentAccount(order, stage);
}

function isOrderActionOwnedByCurrentAccount(order: Order, stage: OrderStageId) {
  const role = order.currentAccountRole;
  if (stage === "payment") return role === "payer";
  if (stage === "delivery") return role === "fulfiller";
  if (stage === "acceptance") return role === "payer";
  if (stage === "dispute") return role === "reviewer" || role === "authority";
  return false;
}

function marketTabPostKind(tab: MarketTab) {
  if (tab === "sell") return "offer";
  if (tab === "buy") return "request";
  if (tab === "projects") return "project";
  return undefined;
}

function marketTabOrders(identity: IdentityPage, tab: MarketTab) {
  const postKind = marketTabPostKind(tab);
  return postKind ? identity.activity.myOrders.filter((order) => order.postKind === postKind) : identity.activity.myOrders;
}

function marketEmptyText(tab: MarketTab, statusFilter: OrderStatusFilter) {
  if (statusFilter !== "all") return "当前状态没有订单。";
  if (tab === "sell") return "当前供给执行为空。";
  if (tab === "buy") return "当前需求执行为空。";
  if (tab === "projects") return "当前项目执行为空。";
  return "当前市场相关执行为空。";
}

function isMoneyPaymentPending(order: Order) {
  if (String(order.settlementType ?? "").toLowerCase() !== "money") return false;
  if (order.status !== "claimed" && order.status !== "delivered") return false;
  return String(order.paymentIntentStatus ?? "missing").toLowerCase() !== "captured";
}

function orderDisplayTitle(order: Order) {
  const name = order.orderName?.trim();
  if (name) return name;
  return order.orderNo;
}

function formatSettlement(order: Order) {
  const amount = typeof order.settlementAmount === "number" ? order.settlementAmount : 0;
  if (order.settlementType === "money") return formatMajorMoney(amount, "USD", "zh-CN");
  if (order.settlementType === "shares") return `${amount} 虚拟股份`;
  return `${amount} ${order.settlementType}`;
}

function sumLedgerShares(entries: SharesLedgerEntry[]) {
  return entries.reduce((total, entry) => total + (entry.amount ?? 0), 0);
}

function sumHoldShares(entries: ShareSettlementHold[]) {
  return entries.reduce((total, entry) => total + (entry.amount ?? 0), 0);
}

function settlementHoldBucket(entry: ShareSettlementHold) {
  if (entry.status === "locked" && entry.orderStatus === "disputed") return "frozen";
  if (entry.status === "locked") return "locked";
  return "other";
}

function shareHoldMeta(entry: ShareSettlementHold) {
  const parts = [entry.lockReason ?? "locked"];
  if (entry.orderStatus) parts.push(orderStatusLabel(entry.orderStatus));
  if (entry.disputeWindowExpiresAt) parts.push(`截止 ${formatDate(entry.disputeWindowExpiresAt)}`);
  return parts.join(" · ");
}

function orderStatusVariant(status?: string): "default" | "success" | "warning" | "danger" | "outline" {
  if (status === "final_accepted" || status === "delivered") return "success";
  if (status === "final_closed") return "outline";
  if (status === "accepted_open") return "warning";
  if (status === "disputed") return "danger";
  if (status === "claimed") return "default";
  return "outline";
}

const orderReviewBadgeLabels: Record<string, string> = {
  open: "等待评审",
  reviewer_assigned: "评审已指派",
  review_submitted: "等待二审决定",
  appeal_open: "二审处理中",
  resolved: "争议已解决",
};

function normalizedOrderSignal(value?: string) {
  const signal = value?.trim().toLowerCase();
  return signal || null;
}

function isOrderTerminal(status?: string) {
  return status === "final_accepted" || status === "final_closed";
}

function buildOrderWorkflowBadges(order: Order) {
  const badges: string[] = [];
  const reviewStatus = normalizedOrderSignal(order.reviewStatus);
  const disputeWindowStatus = normalizedOrderSignal(order.disputeWindowStatus);

  if (isMoneyPaymentPending(order)) {
    badges.push(order.paymentDueAt ? `待付款，截止 ${formatDate(order.paymentDueAt)}` : "待付款");
  }

  if (order.settlementFrozen) {
    badges.push("结算冻结");
  }

  if (order.status === "disputed") {
    badges.push(reviewStatus ? orderReviewBadgeLabels[reviewStatus] ?? "争议处理中" : "争议处理中");
  }

  if (order.status === "accepted_open" && disputeWindowStatus === "open") {
    badges.push("争议窗口开放中");
  }

  if (isOrderTerminal(order.status) && reviewStatus === "resolved") {
    badges.push("争议已解决");
  }

  return badges;
}

function secondaryTabsFor(section: PrimarySection) {
  if (section === "market") return marketTabs;
  if (section === "badges") return badgeTabs;
  return assetTabs;
}

function secondaryCountsFor(
  section: PrimarySection,
  identity: IdentityPage,
  systemShares: SharesLedgerEntry[],
  otherProjectShares: SharesLedgerEntry[],
  systemShareHolds: ShareSettlementHold[],
  otherProjectShareHolds: ShareSettlementHold[],
): Record<string, number> {
  if (section === "market") {
    const orders = identity.activity.myOrders;
    return {
      all: orders.length,
      sell: orders.filter((order) => order.postKind === "offer").length,
      buy: orders.filter((order) => order.postKind === "request").length,
      projects: orders.filter((order) => order.postKind === "project").length,
    };
  }

  if (section === "badges") {
    return {
      overview: identity.profile.badges.length + identity.certifiers.length,
      verification: identity.certifiers.length,
    };
  }

  return {
    system: systemShares.length + systemShareHolds.length,
    other: otherProjectShares.length + otherProjectShareHolds.length,
  };
}

function identityHref(section: PrimarySection, tab?: SecondaryTab, extra?: Record<string, string>) {
  // 中文注释：一级和二级 tab 都生成同一路由的查询参数，刷新和分享链接时保留当前位置。
  const params = new URLSearchParams();
  params.set("section", section);
  const selectedTab = tab ?? defaultSecondaryTab[section];
  if (section !== "identity") {
    params.set("tab", selectedTab);
  }
  Object.entries(extra ?? {}).forEach(([key, value]) => params.set(key, value));
  return `/profile/me?${params.toString()}`;
}

function normalizePrimarySection(value: string | null): PrimarySection {
  return primarySections.some((section) => section.id === value) ? value as PrimarySection : "market";
}

function normalizeSecondaryTab(section: PrimarySection, value: string | null): SecondaryTab {
  const tabs = secondaryTabsFor(section);
  return tabs.some((tab) => tab.id === value) ? value as SecondaryTab : defaultSecondaryTab[section];
}

function normalizeOrderStatusFilter(value: string | null): OrderStatusFilter {
  return marketStatusFilters.some((filter) => filter.id === value) ? value as OrderStatusFilter : "all";
}

function providerLabel(provider: string) {
  if (provider === "github") return "GitHub";
  if (provider === "x") return "X";
  if (provider === "twitter") return "X";
  if (provider === "reddit") return "Reddit";
  if (provider === "youtube") return "YouTube";
  if (provider === "google") return "Google";
  if (provider === "discord") return "Discord";
  return provider;
}

function providerIcon(provider: string) {
  if (provider === "github") return <Github className="h-4 w-4" />;
  if (provider === "youtube") return <Youtube className="h-4 w-4" />;
  return <ShieldCheck className="h-4 w-4" />;
}

function displaySkinStatusLabel(skin: IdentityDisplaySkin) {
  if (skin.source === "native") return "默认皮肤";
  return `${providerLabel(skin.provider)} ${skin.verified ? "已认证皮肤" : "待认证皮肤"}`;
}

function displaySkinMenuLabel(skin: IdentityDisplaySkin) {
  return skin.source === "native" ? "默认皮肤" : `${providerLabel(skin.provider)} 皮肤`;
}

function verificationMethodLabel(method: string) {
  if (method === "oauth") return "OAuth 认证";
  if (method === "public_proof") return "公开证明";
  if (method === "bot_code") return "Bot 验证";
  return method;
}

function publicProofPlacements(certifier: IdentityCertifier) {
  const options = certifier.startInputSchema?.options;
  if (!options || typeof options !== "object" || Array.isArray(options)) return ["post"];
  const placements = (options as Record<string, unknown>).proofPlacement;
  return Array.isArray(placements) ? placements.map(String) : ["post"];
}

function proofPlacementLabel(placement: string) {
  if (placement === "post") return "帖子";
  if (placement === "comment") return "评论";
  if (placement === "bio") return "个人简介";
  if (placement === "channel_about") return "频道简介";
  if (placement === "video_description") return "视频简介";
  return placement;
}

function stringFromRecord(record: Record<string, unknown> | undefined, key: string) {
  const value = record?.[key];
  return typeof value === "string" ? value : "";
}
