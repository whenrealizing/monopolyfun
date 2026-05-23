"use client";

import {ShieldCheck} from "lucide-react";
import {useTranslations} from "next-intl";
import {usePathname, useRouter} from "next/navigation";
import {type ReactNode, useCallback, useEffect, useMemo, useState, useTransition} from "react";

import {
    amountText,
    BackofficeWorkspace,
    bytesText,
    EmptyLine,
    Evidence,
    Panel,
    shortRef,
    SimpleRow,
    textValue,
} from "@/components/backoffice/backoffice-ui";
import {ClientOnlyMount} from "@/components/client-only-mount";
import {GlobalStatePage, MarketHomeButton, RetryButton} from "@/components/global-state-page";
import {BackofficeSkeleton} from "@/components/page-skeletons";
import {EmptyState, PageContainer, PageIntro, PageSection} from "@/components/ui/page-layout";
import {
    type BackofficeAuditEvent,
    type BackofficeDashboard,
    type BackofficeProofAsset,
    type BackofficeRiskEvent,
    formatDate,
    getBackofficeDashboard,
    getRiskAccount,
    listBackofficeAuditEvents,
    listBackofficePaymentIntents,
    listBackofficeProofAssets,
    listBackofficeRiskEvents,
    listRiskAccounts,
    type PaymentIntent,
    type RiskAccount,
} from "@/lib/api";
import {isApiStatus} from "@/lib/api-error";

type BackofficePageKind = "home" | "audit" | "payments" | "assets" | "risk" | "risk-account" | "security" | "forbidden";

type AsyncState<T> =
  | { status: "loading"; data?: undefined; error?: undefined }
  | { status: "ready"; data: T; error?: undefined }
  | { status: "error"; data?: undefined; error: unknown };

export function BackofficePageClient({ kind, accountId }: { kind: BackofficePageKind; accountId?: string }) {
  return (
    <ClientOnlyMount fallback={<BackofficeSkeleton />}>
      <BackofficePageContent kind={kind} accountId={accountId} />
    </ClientOnlyMount>
  );
}

function BackofficePageContent({ kind, accountId }: { kind: BackofficePageKind; accountId?: string }) {
  if (kind === "forbidden") return <ForbiddenPage />;
  if (kind === "security") return <SecurityPage />;
  if (kind === "audit") return <AuditPage />;
  if (kind === "payments") return <PaymentsPage />;
  if (kind === "assets") return <AssetsPage />;
  if (kind === "risk") return <RiskPage />;
  if (kind === "risk-account" && accountId) return <RiskAccountPage accountId={accountId} />;
  return <HomePage />;
}

function useBackofficeData<T>(load: () => Promise<T>, dependencyKey = ""): AsyncState<T> {
  const router = useRouter();
  const pathname = usePathname();
  const [state, setState] = useState<AsyncState<T>>({ status: "loading" });
  const [, startTransition] = useTransition();

  useEffect(() => {
    let active = true;
    // 中文注释：路由切换重新拉取后台数据，loading 状态放入 transition 以免 effect 内同步级联渲染。
    startTransition(() => setState({ status: "loading" }));
    load()
      .then((data) => {
        if (active) setState({ status: "ready", data });
      })
      .catch((error) => {
        if (!active) return;
        if (isApiStatus(error, [401])) {
          const loginPath = pathname.startsWith("/en/") ? "/en/login" : "/login";
          router.push(`${loginPath}?auth=login&returnTo=${encodeURIComponent(pathname)}`);
          return;
        }
        if (isApiStatus(error, [403])) {
          router.push(pathname.startsWith("/en/") ? "/en/backoffice/forbidden" : "/backoffice/forbidden");
          return;
        }
        setState({ status: "error", error });
      });
    return () => {
      active = false;
    };
  }, [dependencyKey, load, pathname, router, startTransition]);

  return state;
}

function LoadingOrError<T>({ state, children }: { state: AsyncState<T>; children: (data: T) => ReactNode }) {
  const t = useTranslations("State.error");
  if (state.status === "loading") return <BackofficeSkeleton />;
  if (state.status === "error") {
    return (
      <GlobalStatePage
        kind="error"
        title={t("title")}
        description={state.error instanceof Error ? state.error.message : t("description")}
        primaryAction={<RetryButton onClick={() => window.location.reload()} />}
      />
    );
  }
  return <>{children(state.data)}</>;
}

function HomePage() {
  const t = useTranslations("Backoffice");
  const state = useBackofficeData(getBackofficeDashboard);
  const valueLabels = t.raw("values") as Record<string, string>;

  return (
    <LoadingOrError state={state}>
      {(dashboard: BackofficeDashboard) => {
        const queue = [
          ...dashboard.recentRiskEvents.slice(0, 2).map((event) => ({
            key: `risk:${event.id ?? `${event.kind}:${event.subjectId}:${event.createdAt}`}`,
            title: event.reason || t("home.riskEvent"),
            meta: t("home.riskMeta", { actor: event.actorRef, date: formatDate(event.createdAt) }),
            status: event.severity,
            href: "/backoffice/risk",
          })),
          ...dashboard.recentPaymentIntents.slice(0, 2).map((intent) => ({
            key: `payment:${intent.id ?? intent.paymentNo}`,
            title: `${intent.paymentNo} · ${amountText(intent.amountMinor, intent.currency)}`,
            meta: t("home.paymentMeta", { status: textValue(intent.status, valueLabels, t("emptyValue")), date: formatDate(intent.updatedAt ?? intent.createdAt) }),
            status: intent.status,
            href: "/backoffice/payments",
          })),
          ...dashboard.recentProofAssets.slice(0, 1).map((asset) => ({
            key: `asset:${asset.id ?? `${asset.orderId}:${asset.objectKey}:${asset.createdAt}`}`,
            title: asset.filename || t("home.proofAsset"),
            meta: t("home.assetMeta", { order: shortRef(asset.orderId), date: formatDate(asset.updatedAt ?? asset.createdAt) }),
            status: asset.status,
            href: "/backoffice/assets",
          })),
        ].slice(0, 5);

        return (
          <BackofficeWorkspace active="home" title={t("home.title")}>
            <Panel title={t("home.queue")}>
              {queue.length > 0 ? queue.map((item) => <SimpleRow key={item.key} title={item.title} meta={item.meta} status={item.status} href={item.href} />) : <EmptyLine>{t("home.emptyQueue")}</EmptyLine>}
            </Panel>
          </BackofficeWorkspace>
        );
      }}
    </LoadingOrError>
  );
}

function AuditPage() {
  const t = useTranslations("Backoffice.audit");
  const commonT = useTranslations("Backoffice");
  const loadAuditEvents = useCallback(() => listBackofficeAuditEvents(20), []);
  const state = useBackofficeData(loadAuditEvents);
  const valueLabels = commonT.raw("values") as Record<string, string>;
  const auditLabels = useMemo(() => ({
    auth_login: t("labels.authLogin"),
    auth_logout: t("labels.authLogout"),
    auth_register: t("labels.authRegister"),
    upload_complete: t("labels.uploadComplete"),
    upload_presign: t("labels.uploadPresign"),
  }), [t]);

  return (
    <LoadingOrError state={state}>
      {(events: BackofficeAuditEvent[]) => (
        <BackofficeWorkspace active="audit" title={t("title")} description={t("description")}>
          <Panel title={t("events")}>
            {events.length > 0 ? events.map((event) => (
              <div key={event.id}>
                <SimpleRow title={auditLabels[event.type as keyof typeof auditLabels] ?? textValue(event.type, valueLabels, commonT("emptyValue"))} meta={t("eventMeta", { actor: shortRef(event.actorAccountId), date: formatDate(event.createdAt) })} status={event.outcome} />
                <Evidence value={event.payload} />
              </div>
            )) : <EmptyLine>{t("empty")}</EmptyLine>}
          </Panel>
        </BackofficeWorkspace>
      )}
    </LoadingOrError>
  );
}

function PaymentsPage() {
  const t = useTranslations("Backoffice.payments");
  const loadPaymentIntents = useCallback(() => listBackofficePaymentIntents(20), []);
  const state = useBackofficeData(loadPaymentIntents);
  return (
    <LoadingOrError state={state}>
      {(intents: PaymentIntent[]) => (
        <BackofficeWorkspace active="payments" title={t("title")} description={t("description")}>
          <Panel title={t("paymentIntents")}>
            {intents.length > 0 ? intents.map((intent) => (
              <div key={intent.id}>
                <SimpleRow title={`${intent.paymentNo} · ${amountText(intent.amountMinor, intent.currency)}`} meta={t("paymentMeta", { order: shortRef(intent.orderId), providerRef: shortRef(intent.providerPaymentRef), date: formatDate(intent.updatedAt ?? intent.createdAt) })} status={intent.status} />
                <Evidence value={intent.metadata} />
              </div>
            )) : <EmptyLine>{t("empty")}</EmptyLine>}
          </Panel>
        </BackofficeWorkspace>
      )}
    </LoadingOrError>
  );
}

function AssetsPage() {
  const t = useTranslations("Backoffice.assets");
  const commonT = useTranslations("Backoffice");
  const loadProofAssets = useCallback(() => listBackofficeProofAssets(20), []);
  const state = useBackofficeData(loadProofAssets);
  const valueLabels = commonT.raw("values") as Record<string, string>;
  return (
    <LoadingOrError state={state}>
      {(assets: BackofficeProofAsset[]) => (
        <BackofficeWorkspace active="assets" title={t("title")} description={t("description")}>
          <Panel title={t("proofAssets")}>
            {assets.length > 0 ? assets.map((asset) => (
              <div key={asset.id}>
                <SimpleRow title={asset.filename} meta={t("assetMeta", { order: shortRef(asset.orderId), provider: textValue(asset.storageProvider, valueLabels, commonT("emptyValue")), size: bytesText(asset.contentLengthBytes), date: formatDate(asset.updatedAt ?? asset.createdAt) })} status={asset.status} />
                <Evidence value={asset.metadata} />
              </div>
            )) : <EmptyLine>{t("empty")}</EmptyLine>}
          </Panel>
        </BackofficeWorkspace>
      )}
    </LoadingOrError>
  );
}

function RiskPage() {
  const t = useTranslations("Backoffice.risk");
  const loadRiskData = useCallback(() => Promise.all([listRiskAccounts(20), listBackofficeRiskEvents(20)]), []);
  const state = useBackofficeData(loadRiskData);
  return (
    <LoadingOrError state={state}>
      {([accounts, events]: [RiskAccount[], BackofficeRiskEvent[]]) => (
        <BackofficeWorkspace active="risk" title={t("title")} description={t("description")}>
          <Panel title={t("accounts")}>
            {accounts.length > 0 ? accounts.map((account) => (
              <SimpleRow key={account.accountId} title={`${account.displayName} · @${account.handle}`} meta={t("accountMeta", { frozenUntil: account.frozenUntil ? formatDate(account.frozenUntil) : t("none"), reason: account.riskReason ?? t("noReason") })} status={account.riskLevel} href={`/backoffice/risk/${encodeURIComponent(account.accountId)}`} />
            )) : <EmptyLine>{t("emptyAccounts")}</EmptyLine>}
          </Panel>
          <Panel title={t("events")}>
            {events.length > 0 ? events.map((event) => (
              <div key={event.id}>
                <SimpleRow title={event.reason} meta={t("eventMeta", { actor: event.actorRef, subject: shortRef(event.subjectId), date: formatDate(event.createdAt) })} status={event.severity} />
                <Evidence value={event.payload} />
              </div>
            )) : <EmptyLine>{t("emptyEvents")}</EmptyLine>}
          </Panel>
        </BackofficeWorkspace>
      )}
    </LoadingOrError>
  );
}

function RiskAccountPage({ accountId }: { accountId: string }) {
  const t = useTranslations("Backoffice.riskDetail");
  const loadRiskAccount = useCallback(() => getRiskAccount(accountId), [accountId]);
  const state = useBackofficeData(loadRiskAccount, accountId);
  return (
    <LoadingOrError state={state}>
      {(account: RiskAccount) => (
        <BackofficeWorkspace active="risk" title={t("title")} description={t("description")}>
          <Panel title={t("profile")}>
            <SimpleRow title={account.displayName} meta={`@${account.handle} · ${account.accountId}`} status={account.status} />
            <SimpleRow title={t("riskState")} meta={t("riskMeta", { frozenUntil: account.frozenUntil ? formatDate(account.frozenUntil) : t("none"), updatedAt: account.riskUpdatedAt ? formatDate(account.riskUpdatedAt) : t("none"), reason: account.riskReason ?? t("noReason") })} status={account.riskLevel} />
          </Panel>
          <Panel title={t("events")}>
            {account.recentEvents.length > 0 ? account.recentEvents.map((event) => (
              <div key={event.id}>
                <SimpleRow title={event.reason} meta={t("eventMeta", { actor: event.actorRef, subject: shortRef(event.subjectId), date: formatDate(event.createdAt) })} status={event.severity} />
                <Evidence value={event.payload} />
              </div>
            )) : <EmptyLine>{t("emptyEvents")}</EmptyLine>}
          </Panel>
        </BackofficeWorkspace>
      )}
    </LoadingOrError>
  );
}

function SecurityPage() {
  const t = useTranslations("Backoffice.security");
  return (
    <PageContainer>
      <PageSection tone="subtle" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
        <PageIntro heading={t("heading")} description={t("description")} />
      </PageSection>
      <PageSection tone="default" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
        <EmptyState className="rounded-[6px] border-0 bg-[var(--background)]" icon={<ShieldCheck className="h-8 w-8" />} title={t("title")} description={t("body")} />
      </PageSection>
    </PageContainer>
  );
}

function ForbiddenPage() {
  const t = useTranslations("State.forbidden");
  const actionsT = useTranslations("State.actions");
  return (
    <GlobalStatePage
      kind="forbidden"
      title={t("title")}
      description={t("description")}
      note={t("backofficeNote")}
      primaryAction={<MarketHomeButton label={actionsT("home")} />}
    />
  );
}
