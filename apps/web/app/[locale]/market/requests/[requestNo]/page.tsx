import {getTranslations} from "next-intl/server";
import {GlobalStatePage, MarketHomeButton} from "@/components/global-state-page";
import {Link} from "@/i18n/navigation";
import {buildSurfaceOwnerIdentity} from "@/components/market-card-primitives";
import {MarketPublisherSection} from "@/components/market-detail-sections";
import {PostOwnerControls} from "@/components/post-owner-controls";
import {ProjectDetailTabs} from "@/components/project-detail-tabs";
import {RequestCurrentStatePanel, type RequestCurrentStateView} from "@/components/request-current-state-panel";
import {RequestItemPicker} from "@/components/request-item-picker";
import {RequestOwnerWorkspacePanel} from "@/components/request-owner-workspace-panel";
import {PostKindBadge, PostStatusBadge} from "@/components/status-badge";
import {PageContainer, PageSection, PageSectionHeader} from "@/components/ui/page-layout";
import {getRequestWorkspace, lookupPublicAccounts, type PostItem, type PostItemSummary} from "@/lib/api";
import {loadAccessiblePageData} from "@/lib/api/page-data";
import {requestHref} from "@/lib/business-routes";

type RequestDetailTranslator = Awaited<ReturnType<typeof getTranslations>>;

function paymentMethodLabel(paymentMethod: string | undefined, t: RequestDetailTranslator) {
    if (paymentMethod === "shares") return t("paymentMethods.shares");
    if (paymentMethod === "okx_direct_pay") return t("paymentMethods.okxDirectPay");
    return t("paymentMethods.pending");
}

function hasItemStatus(items: PostItem[], statuses: string[]) {
    return items.some((item) => statuses.includes(item.status));
}

function activePaymentPendingItem(items: PostItem[]) {
    return items.find((item) => {
        if (!item.activeOrderNo || item.settlementType !== "money" || !item.activeOrderPaymentRequired) return false;
        const status = String(item.activeOrderPaymentStatus ?? "missing_payment_intent").toLowerCase();
        return !["captured", "refunded", "cancelled"].includes(status);
    });
}

const REQUEST_PAYMENT_STATUS_TITLE_KEYS: Record<string, string> = {
    authorized: "paymentStatus.authorized",
    failed: "paymentStatus.failed",
    cancelled: "paymentStatus.cancelled",
    refunded: "paymentStatus.refunded",
    disputed: "paymentStatus.disputed",
};

function requestPaymentStateTitle(item: PostItem, t: RequestDetailTranslator) {
    const status = String(item.activeOrderPaymentStatus ?? "missing").toLowerCase();
    return t(REQUEST_PAYMENT_STATUS_TITLE_KEYS[status] ?? "state.payment.title");
}

function activeOrderItem(items: PostItem[], statuses: string[]) {
    return items.find((item) => item.activeOrderNo && statuses.includes(item.status));
}

function buildRequestState(requestStatus: string, items: PostItem[], ownerHandle: string | null | undefined, t: RequestDetailTranslator): RequestCurrentStateView {
    const paymentItem = activePaymentPendingItem(items);
    if (paymentItem?.activeOrderNo) {
        return {
            title: requestPaymentStateTitle(paymentItem, t),
            orderNo: paymentItem.activeOrderNo,
            paymentOwnerHandle: ownerHandle ?? undefined,
        };
    }
    const acceptanceItem = activeOrderItem(items, ["in_review", "accepted_window_open"]);
    if (acceptanceItem?.activeOrderNo) {
        return {title: t("state.acceptance.title"), orderNo: acceptanceItem.activeOrderNo};
    }
    const workingItem = activeOrderItem(items, ["locked", "in_progress"]);
    if (workingItem?.activeOrderNo) {
        return {title: t("state.working.title"), orderNo: workingItem.activeOrderNo};
    }
    if (hasItemStatus(items, ["open", "released"])) {
        return {title: t("state.waitingClaim.title")};
    }
    if (requestStatus === "closed" || requestStatus === "archived") {
        return {title: t("state.closed.title")};
    }
    return {title: t("state.waitingItems.title")};
}

function requestBudgetLabel(
    value: number | undefined,
    currency: string | undefined,
    itemSummary: PostItemSummary | null | undefined,
    items: Array<{ budgetAmount?: number; priceAmount?: number; currency?: string }>,
    t: RequestDetailTranslator,
) {
    if (value != null) return formatMarketMoney(value, currency);
    const summaryLabel = formatSummaryAmount(itemSummary);
    if (summaryLabel) return summaryLabel;
    const pricedItems = items
        .map((item) => ({amount: item.budgetAmount ?? item.priceAmount, currency: item.currency}))
        .filter((item): item is { amount: number; currency: string | undefined } => item.amount != null);
    if (pricedItems.length === 0) return t("budgetPending");
    const amounts = pricedItems.map((item) => item.amount);
    const minAmount = Math.min(...amounts);
    const maxAmount = Math.max(...amounts);
    const resolvedCurrency = pricedItems.find((item) => item.currency)?.currency ?? currency;
    if (minAmount === maxAmount) return formatMarketMoney(minAmount, resolvedCurrency);
    return `${formatMarketMoney(minAmount, resolvedCurrency)}-${formatMarketMoney(maxAmount, resolvedCurrency)}`;
}

function formatSummaryAmount(summary: PostItemSummary | null | undefined) {
    if (!summary || summary.minAmount == null || summary.maxAmount == null) return null;
    const currency = summary.currency ?? "USD";
    if (summary.minAmount === summary.maxAmount) return formatMarketMoney(summary.minAmount, currency);
    return `${formatMarketMoney(summary.minAmount, currency)}-${formatMarketMoney(summary.maxAmount, currency)}`;
}

function formatMarketMoney(amount: number, currency: string | undefined) {
    const resolvedCurrency = currency ?? "USD";
    const value = Number.isInteger(amount) ? amount.toFixed(0) : amount.toFixed(2);
    return resolvedCurrency === "USD" ? `$${value}` : `${value} ${resolvedCurrency}`;
}

export default async function RequestDetailPage({params}: { params: Promise<{ requestNo: string }> }) {
    const {requestNo} = await params;
    const t = await getTranslations("RequestDetail");
    const stateT = await getTranslations("State.forbidden");
    const actionsT = await getTranslations("State.actions");
    const workspaceResult = await loadAccessiblePageData(getRequestWorkspace(requestNo), `/market/requests/${requestNo}`);

    if (workspaceResult.status === "forbidden") {
        return (
            <GlobalStatePage
                kind="forbidden"
                title={stateT("title")}
                description={stateT("description")}
                primaryAction={<MarketHomeButton label={actionsT("home")}/>}
            />
        );
    }

    const workspace = workspaceResult.data;
    const {request, items} = workspace;
    const accounts = request.actorHandle ? await lookupPublicAccounts([request.actorHandle]).catch(() => []) : [];
    const accountsById = Object.fromEntries(accounts.flatMap((account) => [[account.id, account], [account.handle.replace(/^@+/, "").toLowerCase(), account]]));
    const owner = buildSurfaceOwnerIdentity(request.actorHandle, accountsById);
    const budgetLabel = requestBudgetLabel(request.budgetAmount, request.currency, request.itemSummary, items, t);
    const paymentLabel = paymentMethodLabel(request.paymentMethod, t);
    const currentState = buildRequestState(request.status, items, request.actorHandle, t);
    const openCount = (workspace.itemCounts.open ?? 0) + (workspace.itemCounts.released ?? 0);
    const requestSummary = firstDistinctText(request.title, request.description, request.deliveryStandard);
    const tasksTab = <RequestItemPicker ownerHandle={request.actorHandle} returnTo={requestHref(request)}
                                        items={items}/>;
    const infoTab = (
        <div className="space-y-4">
            <PageSection tone="default" size="lg"
                         className="!rounded-none !border-0 !bg-[var(--background)] !shadow-none">
                <PageSectionHeader heading={t("needSection.heading")} description={t("needSection.description")}/>
                <p className="mt-4 whitespace-pre-line text-sm leading-7 text-[var(--foreground)]">{request.deliveryStandard || t("needSection.empty")}</p>
            </PageSection>

            <MarketPublisherSection
                title={t("publisher.title")}
                name={owner.displayName}
                handle={owner.handle}
                initials={owner.initials}
                hue={owner.hue}
                avatarUrl={owner.avatarUrl}
                summary={owner.summary}
                badges={[t("publisher.badges.publisher"), request.status === "open" ? t("publisher.badges.open") : t("publisher.badges.closed")]}
                profileHref={owner.profileHref}
            />
        </div>
    );

    return (
        <PageContainer width="full" className="space-y-4 pb-12">
            <section className="space-y-5 bg-[var(--background)] px-1 py-3 sm:px-0">
                <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div className="min-w-0 space-y-4">
                        <div className="flex flex-wrap gap-2">
                            <PostKindBadge kind="request"/>
                            <PostStatusBadge status={request.status}/>
                        </div>
                        <div className="space-y-3">
                            <MarketTradeSnapshot
                                amountLabel={t("summary.budget")}
                                amountValue={budgetLabel}
                                meta={[
                                    {label: t("summary.openItems"), value: t("itemCount", {count: openCount})},
                                    {label: t("summary.payment"), value: paymentLabel},
                                ]}
                            />
                            <h1 className="max-w-4xl text-[32px] font-normal leading-tight text-[var(--foreground)]">
                                {request.title}
                            </h1>
                            {requestSummary ? (
                                <p className="max-w-3xl whitespace-pre-line text-sm leading-7 text-[var(--muted-foreground)]">
                                    {requestSummary}
                                </p>
                            ) : null}
                        </div>
                    </div>
                    <MarketOwnerPanel
                        title={t("publisher.title")}
                        name={owner.displayName}
                        handle={owner.handle}
                        initials={owner.initials}
                        hue={owner.hue}
                        avatarUrl={owner.avatarUrl}
                        profileHref={owner.profileHref}
                    />
                </div>

                <RequestCurrentStatePanel state={currentState}/>

                <div className="flex justify-end">
                    <PostOwnerControls post={{...request, kind: "request"}}/>
                </div>
            </section>

            <ProjectDetailTabs
                tabs={[
                    {id: "tasks", label: t("tabs.tasks"), content: tasksTab},
                    {id: "info", label: t("tabs.info"), content: infoTab},
                ]}
            />

            <RequestOwnerWorkspacePanel postId={request.requestNo} postStatus={request.status}
                                        ownerHandle={request.actorHandle} returnTo={requestHref(request)}
                                        initialItems={items}/>
        </PageContainer>
    );
}

function MarketTradeSnapshot({
                                 amountLabel,
                                 amountValue,
                                 meta,
                             }: {
    amountLabel: string;
    amountValue: string;
    meta: Array<{ label: string; value: string }>;
}) {
    return (
        <div className="space-y-2.5">
            <div className="flex flex-wrap gap-2">
                {meta.map((item) => (
                    <span key={item.label}
                          className="inline-flex min-h-8 items-center gap-1.5 rounded-[10px] bg-[var(--surface-control)] px-2.5 text-xs text-[var(--muted-foreground)]">
            {item.label}
                        <span className="font-medium text-[var(--foreground)]">{item.value}</span>
          </span>
                ))}
            </div>
            <div>
                <div className="text-[11px] font-medium text-[var(--muted-foreground)]">{amountLabel}</div>
                <div className="mt-1 break-words text-xl font-black leading-none text-[rgb(255,79,36)]">
                    {amountValue}
                </div>
            </div>
        </div>
    );
}

function MarketOwnerPanel({
                              title,
                              name,
                              handle,
                              initials,
                              hue,
                              avatarUrl,
                              profileHref,
                          }: {
    title: string;
    name: string;
    handle: string;
    initials: string;
    hue: number;
    avatarUrl?: string | null;
    profileHref?: string | null;
}) {
    const content = (
        <>
            <div className="text-[11px] font-medium text-[var(--muted-foreground)]">{title}</div>
            <div className="mt-2 flex items-center gap-3">
        <span
            className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium text-white ring-1 ring-[rgba(255,255,255,0.12)]"
            style={{background: `linear-gradient(135deg, hsl(${hue} 76% 48%), hsl(${(hue + 36) % 360} 72% 36%))`}}
        >
          {avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={avatarUrl} alt="" className="h-full w-full object-cover"/>
          ) : initials}
        </span>
                <span className="min-w-0 flex-1">
          <span className="block truncate text-sm text-[var(--foreground)]">{name}</span>
          <span className="mt-1 block truncate text-xs text-[var(--muted-foreground)]">{handle}</span>
        </span>
            </div>
        </>
    );

    return (
        <aside className="space-y-4 rounded-[12px] bg-[var(--background)] p-4">
            {profileHref ? (
                <Link href={profileHref}
                      className="block transition hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]">
                    {content}
                </Link>
            ) : (
                <div>{content}</div>
            )}
        </aside>
    );
}

function firstDistinctText(...values: Array<string | null | undefined>) {
    const [base, ...candidates] = values;
    const normalizedBase = normalizeText(base);
    return candidates.find((value) => {
        const normalizedValue = normalizeText(value);
        return normalizedValue && normalizedValue !== normalizedBase;
    });
}

function normalizeText(value: string | null | undefined) {
    return value?.replace(/\s+/g, "").trim();
}
