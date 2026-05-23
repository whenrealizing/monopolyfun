import {getTranslations} from "next-intl/server";

import {GlobalStatePage, MarketHomeButton} from "@/components/global-state-page";
import {Link} from "@/i18n/navigation";
import {buildSurfaceOwnerIdentity} from "@/components/market-card-primitives";
import {MarketDetailSelectionBar} from "@/components/market-detail-selection-bar";
import {PostOwnerControls} from "@/components/post-owner-controls";
import {PostItemWorkspacePanel} from "@/components/post-item-workspace-panel";
import {PostKindBadge, PostStatusBadge} from "@/components/status-badge";
import {PageContainer} from "@/components/ui/page-layout";
import {getOfferWorkspace, lookupPublicAccounts, type PaymentMethodCode, type PostItemSummary} from "@/lib/api";
import {loadAccessiblePageData} from "@/lib/api/page-data";
import {offerHref} from "@/lib/business-routes";

type OfferDetailTranslator = Awaited<ReturnType<typeof getTranslations>>;

function readPriceLabel(priceAmount: number | undefined, currency: string | undefined, t: OfferDetailTranslator) {
    return priceAmount != null ? formatMarketMoney(priceAmount, currency) : t("pricePending");
}

function readOfferPriceLabel(
    priceAmount: number | undefined,
    currency: string | undefined,
    itemSummary: PostItemSummary | null | undefined,
    items: Array<{ priceAmount?: number; currency?: string }>,
    t: OfferDetailTranslator,
) {
    if (priceAmount != null) return formatMarketMoney(priceAmount, currency);
    const summaryLabel = formatSummaryAmount(itemSummary);
    if (summaryLabel) return summaryLabel;
    const pricedItems = items.filter((item) => item.priceAmount != null);
    if (pricedItems.length === 0) return t("pricePending");
    const amounts = pricedItems.map((item) => item.priceAmount ?? 0);
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

function paymentMethodLabel(paymentMethod: PaymentMethodCode | undefined, t: OfferDetailTranslator) {
    if (paymentMethod === "shares") return t("paymentMethods.shares");
    if (paymentMethod === "okx_direct_pay") return t("paymentMethods.okxDirectPay");
    return t("paymentMethods.pending");
}

function itemPaymentMethodLabel(paymentMethod: PaymentMethodCode | undefined, fallback: string, t: OfferDetailTranslator) {
    return paymentMethod ? paymentMethodLabel(paymentMethod, t) : fallback;
}

function itemStatusLabel(status: string, t: OfferDetailTranslator) {
    const labels: Record<string, string> = {
        open: t("itemStatus.open"),
        released: t("itemStatus.open"),
        claimed: t("itemStatus.claimed"),
        locked: t("itemStatus.locked"),
        in_progress: t("itemStatus.inProgress"),
        in_review: t("itemStatus.inReview"),
        accepted_window_open: t("itemStatus.acceptedWindowOpen"),
        completed: t("itemStatus.completed"),
        closed: t("itemStatus.closed"),
        archived: t("itemStatus.archived"),
    };
    return labels[status] ?? t("itemStatus.unavailable");
}

function itemStatusTone(item: {
    status: string;
    activeOrderPaymentRequired?: boolean;
    activeOrderPaymentStatus?: string
}) {
    if (item.status === "open" || item.status === "released") return "success" as const;
    const paymentStatus = String(item.activeOrderPaymentStatus ?? "").toLowerCase();
    if (item.activeOrderPaymentRequired && paymentStatus && paymentStatus !== "captured") return "warning" as const;
    if (item.status === "claimed" || item.status === "locked" || item.status === "in_progress" || item.status === "in_review") return "warning" as const;
    return "default" as const;
}

const OFFER_PAYMENT_STATUS_LABEL_KEYS: Record<string, string> = {
    authorized: "paymentStatus.authorized",
    captured: "paymentStatus.captured",
    failed: "paymentStatus.failed",
    cancelled: "paymentStatus.cancelled",
    refunded: "paymentStatus.refunded",
    disputed: "paymentStatus.disputed",
};

function paymentStatusLabel(status: string | undefined, t: OfferDetailTranslator) {
    const key = String(status ?? "missing").toLowerCase();
    return t(OFFER_PAYMENT_STATUS_LABEL_KEYS[key] ?? "paymentStatus.pending");
}

function itemInventoryLabel(item: {
    seatCount?: number | null;
    activeOrdersCount?: number | null
}, t: OfferDetailTranslator) {
    if (item.seatCount == null) return "";
    const available = Math.max(0, item.seatCount - (item.activeOrdersCount ?? 0));
    return t("itemCount", {count: available});
}

export default async function OfferDetailPage({params}: { params: Promise<{ offerNo: string }> }) {
    const {offerNo} = await params;
    const t = await getTranslations("OfferDetail");
    const stateT = await getTranslations("State.forbidden");
    const actionsT = await getTranslations("State.actions");
    const workspaceResult = await loadAccessiblePageData(getOfferWorkspace(offerNo), `/market/offers/${offerNo}`);

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
    const {offer, items} = workspace;
    const accounts = offer.actorHandle ? await lookupPublicAccounts([offer.actorHandle]).catch(() => []) : [];
    const accountsById = Object.fromEntries(accounts.flatMap((account) => [[account.id, account], [account.handle.replace(/^@+/, "").toLowerCase(), account]]));
    const owner = buildSurfaceOwnerIdentity(offer.actorHandle, accountsById);
    const priceLabel = readOfferPriceLabel(offer.priceAmount, offer.currency, offer.itemSummary, items, t);
    const paymentLabel = paymentMethodLabel(offer.paymentMethod, t);
    const postTradable = offer.status === "open" && offer.visibility !== "participant_only";
    const openItemCount = (workspace.itemCounts.open ?? 0) + (workspace.itemCounts.released ?? 0);
    const offerSummary = firstDistinctText(offer.title, offer.description, offer.deliveryStandard);
    const itemsTab = (
        <MarketDetailSelectionBar
            kind="offer"
            ownerHandle={offer.actorHandle}
            returnTo={offerHref(offer)}
            initialItems={[
                ...items.map((item) => ({
                    id: item.id,
                    title: item.title,
                    priceLabel: readPriceLabel(item.priceAmount, item.currency, t),
                    subtitle: item.summary,
                    detail: item.summary,
                    statusLabel: itemStatusLabel(item.status, t),
                    statusTone: itemStatusTone(item),
                    settlementType: item.settlementType,
                    disabled: !postTradable || !(item.status === "open" || item.status === "released"),
                    activeOrderNo: item.activeOrderNo,
                    claimedByAccountId: item.claimedByAccountId,
                    activeOrderPaymentRequired: item.activeOrderPaymentRequired,
                    activeOrderPaymentStatus: item.activeOrderPaymentStatus,
                    paymentStatusLabel: item.activeOrderPaymentRequired ? paymentStatusLabel(item.activeOrderPaymentStatus, t) : undefined,
                    buyerNotePlaceholder: item.buyerNotePlaceholder,
                    deliverableSpec: item.deliverableSpec,
                    acceptanceSpec: item.acceptanceSpec,
                    acceptanceCriteria: item.acceptanceCriteria,
                    paymentLabel: itemPaymentMethodLabel(item.paymentMethod, paymentLabel, t),
                    recipientLabel: offer.paymentRecipient ?? t("publisherWallet"),
                    deliveryMode: item.deliveryMode,
                    deliveryProvider: item.deliveryProvider,
                    deliverySlaLabel: item.deliverySlaLabel,
                    deliveryFailurePolicy: item.deliveryFailurePolicy,
                    priceAmount: item.priceAmount,
                    budgetAmount: item.budgetAmount,
                    inventoryLabel: itemInventoryLabel(item, t),
                })),
            ]}
        />
    );
    return (
        <PageContainer width="full" className="space-y-4 pb-12">
            <section className="space-y-5 bg-[var(--background)] px-1 py-3 sm:px-0">
                <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div className="min-w-0 space-y-4">
                        <div className="flex flex-wrap gap-2">
                            <PostKindBadge kind="offer"/>
                            <PostStatusBadge status={offer.status}/>
                            {offer.visibility === "participant_only" ?
                                <span className="mf-chip">{t("sold")}</span> : null}
                        </div>
                        <div className="space-y-3">
                            <h1 className="max-w-4xl text-[32px] font-normal leading-tight text-[var(--foreground)]">
                                {offer.title}
                            </h1>
                            {offerSummary ? (
                                <p className="max-w-3xl whitespace-pre-line text-sm leading-7 text-[var(--muted-foreground)]">
                                    {offerSummary}
                                </p>
                            ) : null}
                            <MarketTradeSnapshot
                                amountValue={priceLabel}
                                meta={[
                                    {label: t("summary.openItems"), value: t("itemCount", {count: openItemCount})},
                                    {label: t("summary.payment"), value: paymentLabel},
                                ]}
                            />
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

                <div className="flex justify-end">
                    <PostOwnerControls post={{...offer, kind: "offer"}}/>
                </div>
            </section>

            {postTradable ? null : (
                <div className="bg-[rgba(245,98,98,0.08)] px-4 py-3 text-sm leading-6 text-[var(--foreground)]">
                    {t("closedNotice")}
                </div>
            )}

            {itemsTab}

            <PostItemWorkspacePanel postKind="offer" postId={offer.offerNo}
                                    postStatus={postTradable ? offer.status : "closed"} ownerHandle={offer.actorHandle}
                                    returnTo={offerHref(offer)} initialItems={items} ownerOnly/>
        </PageContainer>
    );
}

function MarketTradeSnapshot({
                                 amountValue,
                                 meta,
                             }: {
    amountValue: string;
    meta: Array<{ label: string; value: string }>;
}) {
    return (
        <div className="space-y-2.5">
            <div className="break-words text-xl font-black leading-none text-[rgb(255,79,36)]">
                {amountValue}
            </div>
            <div className="flex flex-wrap gap-2">
                {meta.map((item) => (
                    <span key={item.label}
                          className="inline-flex min-h-8 items-center gap-1.5 rounded-[10px] bg-[var(--surface-control)] px-2.5 text-xs text-[var(--muted-foreground)]">
            {item.label}
                        <span className="font-medium text-[var(--foreground)]">{item.value}</span>
          </span>
                ))}
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
