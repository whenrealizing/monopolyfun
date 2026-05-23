import {Link} from "@/i18n/navigation";
import {getLocale, getTranslations} from "next-intl/server";
import {notFound, redirect} from "next/navigation";
import type {ReactNode} from "react";
import {ExternalLink} from "lucide-react";

import {DisputeEvidenceList} from "@/components/dispute-evidence-list";
import {OrderAccessDenied} from "@/components/order-access-denied";
import {OrderActionPanel} from "@/components/order-action-panel";
import {OrderPaymentCountdown} from "@/components/order-payment-countdown";
import {StatusFlow, type StatusFlowStep} from "@/components/status-flow";
import {Button} from "@/components/ui/button";
import {EmptyState, PageContainer, PageSection, PageSectionHeader} from "@/components/ui/page-layout";
import {ApiRequestError} from "@/lib/api-error";
import {formatMajorMoney, formatMinorMoney} from "@/lib/format-money";
import {
    type Account,
    formatDate,
    lookupPublicAccounts,
    type Order,
    type PaymentIntent,
    type Proof,
    type PublicAccount,
    type SettlementPreview,
    type ShareReleaseRequest,
    type ShareSettlementHold,
} from "@/lib/api";
import {getOrderDetailServer, listReviewerCandidatesServer} from "@/lib/api/order-server";
import {profileHref} from "@/lib/business-routes";
import {buildOrderCurrentState, fulfillerRoleLabel, isMoneyPaymentPending, payerRoleLabel} from "@/lib/order-display";

type OrderDetailTranslator = Awaited<ReturnType<typeof getTranslations>>;
type OrderEvent = Awaited<ReturnType<typeof getOrderDetailServer>>["eventTimeline"][number];

function proofKindLabel(kind: Proof["kind"], t: OrderDetailTranslator) {
    return t(`proof.kind.${kind}`);
}

function orderFlowSteps(order: Order, t: OrderDetailTranslator, events: OrderEvent[]) {
    const isMoneyOrder = order.settlementType.toLowerCase() === "money";
    const paymentStep = isMoneyOrder ? [{
        id: "payment",
        label: t("flow.payment.label"),
        helper: t("flow.payment.helper")
    }] : [];
    if (isTimeoutClosed(order, events)) {
        return [
            {id: "claimed", label: t("flow.claimed.label"), helper: t("flow.claimed.helper")},
            ...paymentStep,
            {id: "timeout_closed", label: t("flow.timeoutClosed.label"), helper: t("flow.timeoutClosed.helper")},
        ];
    }
    if (order.status === "disputed") {
        return [
            {id: "claimed", label: t("flow.claimed.label"), helper: t("flow.claimed.helper")},
            ...paymentStep,
            {id: "delivery", label: t("flow.delivery.label"), helper: t("flow.delivery.helper")},
            {id: "disputed", label: t("flow.disputed.label"), helper: t("flow.disputed.helper")},
        ];
    }
    if (order.status === "final_closed") {
        const disputeStep = hasDisputeEvent(events)
            ? [{id: "disputed", label: t("flow.disputed.label"), helper: t("flow.disputed.helper")}]
            : [];
        return [
            {id: "claimed", label: t("flow.claimed.label"), helper: t("flow.claimed.helper")},
            ...paymentStep,
            ...(disputeStep.length ? [{
                id: "delivery",
                label: t("flow.delivery.label"),
                helper: t("flow.delivery.helper")
            }] : []),
            ...disputeStep,
            {id: "closed", label: t("flow.closed.label"), helper: t("flow.closed.helper")},
        ];
    }
    const baseOrderFlowSteps: StatusFlowStep[] = [
        {id: "claimed", label: t("flow.claimed.label"), helper: t("flow.claimed.helper")},
        {id: "delivery", label: t("flow.delivery.label"), helper: t("flow.delivery.helper")},
        {id: "acceptance", label: t("flow.acceptance.label"), helper: t("flow.acceptance.helper")},
        {id: "accepted", label: t("flow.accepted.label"), helper: t("flow.accepted.helper")},
        {id: "settled", label: t("flow.settled.label"), helper: t("flow.settled.helper")},
    ];
    const moneyOrderFlowSteps: StatusFlowStep[] = [
        {id: "claimed", label: t("flow.claimed.label"), helper: t("flow.claimed.helper")},
        {id: "payment", label: t("flow.payment.label"), helper: t("flow.payment.helper")},
        {id: "delivery", label: t("flow.delivery.label"), helper: t("flow.delivery.helper")},
        {id: "acceptance", label: t("flow.acceptance.label"), helper: t("flow.acceptance.helper")},
        {id: "accepted", label: t("flow.accepted.label"), helper: t("flow.accepted.helper")},
        {id: "settled", label: t("flow.settled.label"), helper: t("flow.settled.helper")},
    ];
    return order.settlementType.toLowerCase() === "money" ? moneyOrderFlowSteps : baseOrderFlowSteps;
}

function hasDisputeEvent(events: OrderEvent[]) {
    return events.some((event) => event.eventType === "order_disputed");
}

function isTimeoutClosed(order: Order, events: OrderEvent[]) {
    return order.status === "final_closed"
        && events.some((event) => event.eventType === "order_closed" && event.payload?.reason === "timeout_release");
}

function decisionLabel(decision: string | null | undefined, t: OrderDetailTranslator) {
    if (decision === "accept_original") return t("decision.acceptOriginal");
    if (decision === "close_original") return t("decision.closeOriginal");
    return "";
}

function eventTypeLabel(type: string, t: OrderDetailTranslator) {
    const key = `event.${type}`;
    return t.has(key) ? t(key) : t("event.unknown");
}

function eventDisplayLabel(event: OrderEvent, t: OrderDetailTranslator) {
    if (event.eventType === "order_closed" && event.payload?.reason === "timeout_release") {
        return t("event.timeout_release");
    }
    if (typeof event.payload?.label === "string" && event.payload.label.trim()) {
        return event.payload.label;
    }
    return eventTypeLabel(event.eventType, t);
}

type DisplayAccount = Account | PublicAccount;

function accountDisplayName(account: DisplayAccount | undefined, fallback: string) {
    return account?.displaySkin.displayName ?? account?.displayName ?? fallback;
}

function accountDisplayHandle(account: DisplayAccount | undefined, fallback: string) {
    const handle = account?.displaySkin.displayHandle ?? account?.handle;
    return handle ? `@${handle.replace(/^@+/, "")}` : fallback;
}

function accountDisplayMeta(account: DisplayAccount | undefined, fallback: string, t: OrderDetailTranslator) {
    if (!account) return fallback;
    const displayHandle = accountDisplayHandle(account, fallback);
    if (account.displaySkin.source === "verified_identity") {
        return t("account.verifiedMeta", {handle: displayHandle, source: account.handle.replace(/^@+/, "")});
    }
    return displayHandle;
}

function accountHref(account: DisplayAccount | undefined) {
    const handle = account?.displaySkin.displayHandle ?? account?.handle;
    return handle ? profileHref(handle.replace(/^@+/, "")) : null;
}

function AccountValue({account, fallback}: { account: DisplayAccount | undefined; fallback: string }) {
    const value = accountDisplayName(account, fallback);
    const href = accountHref(account);
    if (!href) return <>{value}</>;
    return (
        <Link href={href} className="underline-offset-4 hover:underline">
            {value}
        </Link>
    );
}

function orderDetailTitle(order: Order, post: Awaited<ReturnType<typeof getOrderDetailServer>>["post"], item: Awaited<ReturnType<typeof getOrderDetailServer>>["item"]) {
    return item?.title ?? order.orderName ?? post.title;
}

function orderDetailSubtitle(post: Awaited<ReturnType<typeof getOrderDetailServer>>["post"], item: Awaited<ReturnType<typeof getOrderDetailServer>>["item"]) {
    if (item?.summary) return item.summary;
    return post.summary || post.title;
}

function CounterpartyValue({
                               order,
                               payer,
                               fulfiller,
                               payerLabel,
                               fulfillerLabel,
                               fallback,
                           }: {
    order: Order;
    payer: DisplayAccount | undefined;
    fulfiller: DisplayAccount | undefined;
    payerLabel: string;
    fulfillerLabel: string;
    fallback: string;
}) {
    if (order.currentAccountRole === "payer") {
        return (
            <>
                {fulfillerLabel}：<AccountValue account={fulfiller} fallback={fallback}/>
            </>
        );
    }
    if (order.currentAccountRole === "fulfiller") {
        return (
            <>
                {payerLabel}：<AccountValue account={payer} fallback={fallback}/>
            </>
        );
    }
    return (
        <>
            {payerLabel}：<AccountValue account={payer} fallback={fallback}/>
            <span className="mx-1 text-[var(--muted-foreground)]">/</span>
            {fulfillerLabel}：<AccountValue account={fulfiller} fallback={fallback}/>
        </>
    );
}

function shouldShowProofSection(order: Order, proof: Proof | undefined, canSubmitProof: boolean) {
    if (proof) return true;
    if (deliveryEvidenceRefs(order).length > 0 || readString(order.deliveryPayload?.url)) return true;
    if (canSubmitProof) return false;
    return ["delivered", "accepted_open", "disputed", "final_accepted", "final_closed"].includes(order.status);
}

function proofEvidenceRefs(proof: Proof | undefined) {
    if (!proof) return [];
    return [...new Set([...(proof.artifacts ?? []), ...(proof.evidenceRefs ?? [])].filter(Boolean))];
}

function readStringArray(value: unknown) {
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && item.trim().length > 0) : [];
}

function readString(value: unknown) {
    return typeof value === "string" && value.trim().length > 0 ? value.trim() : "";
}

function deliveryEvidenceRefs(order: Order) {
    return [...new Set([
        ...readStringArray(order.deliveryPayload?.artifacts),
        ...readStringArray(order.deliveryReceipt?.artifacts),
    ])];
}

function deliveryEvidenceLinks(order: Order, t: OrderDetailTranslator) {
    const payloadUrl = readString(order.deliveryPayload?.url);
    return payloadUrl ? [{label: t("proof.deliveryLink"), href: payloadUrl}] : [];
}

function deliverySummaryText(order: Order) {
    return readString(order.deliveryReceipt?.summary) || readString(order.deliveryPayload?.summary);
}

function deliverySubmittedAt(order: Order) {
    return readString(order.deliveryReceipt?.submittedAt) || readString(order.deliveryPayload?.submittedAt);
}

function orderFlowIndex(order: Order, paymentIntent: PaymentIntent | undefined, events: OrderEvent[]) {
    const isMoneyOrder = order.settlementType.toLowerCase() === "money";
    if (isTimeoutClosed(order, events)) return isMoneyOrder ? 2 : 1;
    if (order.status === "disputed") return isMoneyOrder ? 3 : 2;
    if (order.status === "final_closed") {
        if (hasDisputeEvent(events)) return isMoneyOrder ? 4 : 3;
        return isMoneyOrder ? 2 : 1;
    }
    if ((order.status === "claimed" || order.status === "delivered") && isMoneyPaymentPending(order, paymentIntent)) return 1;
    if (order.status === "claimed") {
        return isMoneyOrder ? 2 : 1;
    }
    if (order.status === "delivered") return isMoneyOrder ? 3 : 2;
    if (order.status === "accepted_open") return isMoneyOrder ? 4 : 3;
    if (order.status === "final_accepted") return isMoneyOrder ? 5 : 4;
    return 0;
}

function paymentAmountText(order: Order, paymentIntent: PaymentIntent | undefined, t: OrderDetailTranslator, locale: string) {
    // 中文注释：订单详情的结算摘要只表达金额本身，支付状态交给操作面板展示。
    if (paymentIntent && typeof paymentIntent.amountMinor === "number") {
        return formatMinorMoney(paymentIntent.amountMinor, paymentIntent.currency, locale);
    }
    if (order.settlementType === "money") {
        return typeof order.settlementAmount === "number" ? formatMajorMoney(order.settlementAmount, "USD", locale) : t("amount.moneyPending");
    }
    return t("amount.sharesValue", {amount: order.settlementAmount ?? 0});
}

function orderPaymentDueAt(order: Order, paymentIntent: PaymentIntent | undefined) {
    const dueAt = paymentIntent?.metadata?.paymentDueAt ?? order.paymentDueAt;
    return typeof dueAt === "string" && dueAt.trim() ? dueAt.trim() : undefined;
}

function shareLockReasonLabel(reason: string | null | undefined, t: OrderDetailTranslator) {
    const key = String(reason ?? "").toLowerCase();
    return t.has(`settlement.lockReason.${key}`) ? t(`settlement.lockReason.${key}`) : (reason || t("settlement.lockReason.fallback"));
}

function localizedLoginHref(locale: string, returnTo: string) {
    const prefix = locale === "en" ? "/en" : "";
    return `${prefix}/login?auth=login&returnTo=${encodeURIComponent(returnTo)}`;
}

async function loadOrderDetail(orderNo: string, locale: string) {
    try {
        return await getOrderDetailServer(orderNo);
    } catch (error) {
        if (error instanceof ApiRequestError && error.status === 401) {
            // 中文注释：订单详情属于参与方视图，匿名访问直接回到登录并保留公开订单地址。
            redirect(localizedLoginHref(locale, `/orders/${orderNo}`));
        }
        if (error instanceof ApiRequestError && error.status === 404) {
            notFound();
        }
        if (error instanceof ApiRequestError && error.status === 403) {
            // 中文注释：订单读取权限不足属于可解释状态，直接给参与方边界提示，避免落入全局错误页。
            return null;
        }
        throw error;
    }
}

async function loadReviewerCandidates(orderNo: string) {
    try {
        return await listReviewerCandidatesServer(orderNo);
    } catch (error) {
        if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
            // 中文注释：评审候选只影响争议动作，主体订单内容继续按参与方权限展示。
            return [];
        }
        throw error;
    }
}

export default async function OrderPage({params}: { params: Promise<{ orderNo: string }> }) {
    const {orderNo} = await params;
    const t = await getTranslations("Orders.detail");
    const locale = await getLocale() as "zh-CN" | "en";
    const detail = await loadOrderDetail(orderNo, locale);
    if (!detail) {
        return (
            <OrderAccessDenied
                orderNo={orderNo}
                returnHref="/profile/me?section=market&tab=all"
                returnLabel={t("accessDenied.backToOrders")}
                heading={t("accessDenied.heading")}
                description={t("accessDenied.description", {orderNo})}
                note={t("accessDenied.note")}
            />
        );
    }
    const reviewerCandidates = detail.availableActions.some((action) => action.id === "assign_reviewer")
        ? await loadReviewerCandidates(orderNo)
        : [];

    const {
        order,
        post,
        item,
        proof,
        progressTimeline,
        paymentIntent,
        settlementPreview,
        shareSettlementHold,
        shareReleaseRequest,
        reviewContext,
        eventTimeline: events
    } = detail;
    const payerId = order.buyerAccountId ?? order.acceptorAccountId;
    const fulfillerId = order.fulfillerAccountId ?? order.sellerAccountId ?? order.claimedByAccountId;
    const accountIds = [
        payerId,
        fulfillerId,
        reviewContext?.reviewerAccountId,
        reviewContext?.disputeOpenedByAccountId,
        reviewContext?.disputeCancelledByAccountId,
        ...events.map((event) => event.actorAccountId),
    ].filter(Boolean) as string[];
    const uniqueAccountIds = [...new Set(accountIds)];
    const accounts: DisplayAccount[] = await lookupPublicAccounts(uniqueAccountIds)
        .catch(() => []);
    const payer = accounts.find((account) => account.id === payerId);
    const fulfiller = accounts.find((account) => account.id === fulfillerId);
    const assignedReviewer = accounts.find((account) => account.id === reviewContext?.reviewerAccountId);
    const disputeOpener = accounts.find((account) => account.id === reviewContext?.disputeOpenedByAccountId);
    const disputeCanceller = accounts.find((account) => account.id === reviewContext?.disputeCancelledByAccountId);
    const currentState = buildOrderCurrentState(order, paymentIntent, locale);
    const payerLabel = payerRoleLabel(order.postKind, locale);
    const fulfillerLabel = fulfillerRoleLabel(order.postKind, locale);
    const orderedEvents = [...events].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
    const title = orderDetailTitle(order, post, item);
    const subtitle = orderDetailSubtitle(post, item);
    const canSubmitProof = detail.availableActions.some((action) => action.id === "submit_proof" || action.id === "complete_auto_delivery");
    const showProofSection = shouldShowProofSection(order, proof, canSubmitProof);
    const proofRefs = [...new Set([...proofEvidenceRefs(proof), ...deliveryEvidenceRefs(order)])];
    const deliveryLinks = deliveryEvidenceLinks(order, t);
    const proofSummaryText = proof?.summary || deliverySummaryText(order);
    const deliverySubmittedAtText = deliverySubmittedAt(order);
    const isWaitingForAcceptance = order.status === "delivered";
    const isFinal = order.status === "final_closed" || order.status === "final_accepted";
    const compactPaymentPage = !isFinal && isMoneyPaymentPending(order, paymentIntent);
    const paymentDueAt = orderPaymentDueAt(order, paymentIntent);
    const meaningfulEvents = orderedEvents.filter((event) => event.eventType !== "unknown");
    const recentEvents = meaningfulEvents.slice(0, 3);
    const hasDisputeContext = Boolean(reviewContext?.disputeReason || reviewContext?.parentOrderNo || reviewContext?.reviewerAccountId || reviewContext?.backofficeOverrideDecision);
    const actionPanel = (
        <OrderActionPanel
            order={order}
            availableActions={detail.availableActions}
            reviewerCandidates={reviewerCandidates}
            paymentIntent={paymentIntent}
            showDisputeManagement={false}
            showActionSummary={false}
        />
    );

    return (
        <PageContainer width="full" className="pb-10">
            <PageSection tone="subtle" size="flush"
                         className="space-y-5 rounded-[6px] border-0 bg-[var(--background)] shadow-none">
                <div className="min-w-0">
                    <div className="text-sm leading-6 text-[var(--muted-foreground)]">{t("common.orderDetail")}</div>
                    <h1 className="mt-1 line-clamp-2 text-[24px] font-medium leading-8 text-[var(--foreground)]">{title}</h1>
                    {subtitle && subtitle !== title ? (
                        <p className="mt-2 line-clamp-2 max-w-3xl text-sm leading-6 text-[var(--muted-foreground)]">{subtitle}</p>
                    ) : null}
                </div>
                <StatusFlow steps={orderFlowSteps(order, t, events)}
                            currentIndex={orderFlowIndex(order, paymentIntent, events)} showCurrentHelper={!isFinal}/>
                {isFinal ? (
                    <FinalResultSummary order={order} settlementPreview={settlementPreview}
                                        shareReleaseRequest={shareReleaseRequest} t={t}/>
                ) : null}
                {!isFinal ? (
                    <OrderCurrentStateSection
                        title={currentState.title}
                        description={compactPaymentPage ? t("currentState.paymentPending") : currentStateDescription(order, paymentIntent, t)}
                        dueAt={compactPaymentPage ? paymentDueAt : undefined}
                        t={t}
                    />
                ) : null}
                {compactPaymentPage ? (
                    <div className="max-w-3xl bg-[var(--background)] py-1">
                        <OrderSummaryPanel
                            order={order}
                            paymentIntent={paymentIntent}
                            settlementPreview={settlementPreview}
                            shareSettlementHold={shareSettlementHold}
                            shareReleaseRequest={shareReleaseRequest}
                            payer={payer}
                            fulfiller={fulfiller}
                            payerLabel={payerLabel}
                            fulfillerLabel={fulfillerLabel}
                            locale={locale}
                            t={t}
                        />
                    </div>
                ) : null}
                {actionPanel}
                {hasDisputeContext ? (
                    <div className="flex justify-start">
                        <Button asChild variant="outline" size="sm">
                            <Link href={`/orders/${order.orderNo}/dispute`}>
                                <ExternalLink className="h-3.5 w-3.5"/>
                                {t("dispute.viewDetails")}
                            </Link>
                        </Button>
                    </div>
                ) : null}
            </PageSection>

            {showProofSection ? (
                <PageSection tone="default" size="flush"
                             className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
                    <PageSectionHeader
                        heading={isWaitingForAcceptance ? t("proof.deliveryResultHeading") : t("proof.heading")}
                        description={isWaitingForAcceptance ? undefined : t("proof.description")}
                    />
                    {proof || proofRefs.length > 0 || deliveryLinks.length > 0 || proofSummaryText ? (
                        <div className="mt-4 rounded-[6px] border border-[var(--border)] bg-[var(--background)] p-4">
                            {proof || deliverySubmittedAtText ? (
                                <div className="flex flex-wrap items-center gap-2">
                                    {proof ? <span
                                        className="text-xs font-medium text-[var(--muted-foreground)]">{proofKindLabel(proof.kind, t)}</span> : null}
                                    {deliverySubmittedAtText ? <span
                                        className="text-xs text-[var(--muted-foreground)]">{formatDate(deliverySubmittedAtText)}</span> : null}
                                </div>
                            ) : null}
                            {proofSummaryText ? (
                                <p className={proof || deliverySubmittedAtText ? "mt-4 whitespace-pre-line text-sm leading-6 text-[var(--foreground)]" : "whitespace-pre-line text-sm leading-6 text-[var(--foreground)]"}>{proofSummaryText}</p>
                            ) : (
                                <p className={proof || deliverySubmittedAtText ? "mt-4 text-sm leading-6 text-[var(--muted-foreground)]" : "text-sm leading-6 text-[var(--muted-foreground)]"}>
                                    {deliveryLinks.length > 0 ? t("proof.linkOnlyDescription") : t("proof.noSummary")}
                                </p>
                            )}
                            {[...(proof?.links ?? []), ...deliveryLinks].length > 0 ? (
                                <div className="mt-4 flex flex-wrap gap-2">
                                    {[...(proof?.links ?? []), ...deliveryLinks].map((link) => (
                                        <a key={link.href} href={link.href} className="mf-chip" target="_blank"
                                           rel="noreferrer">
                                            <ExternalLink className="h-3.5 w-3.5"/>
                                            {link.label}
                                        </a>
                                    ))}
                                </div>
                            ) : null}
                            {proofRefs.length > 0 ? (
                                <div className="mt-4">
                                    <div
                                        className="mb-1.5 text-xs text-[var(--muted-foreground)]">{t("proof.attachments")}</div>
                                    <DisputeEvidenceList evidenceRefs={proofRefs}/>
                                </div>
                            ) : null}
                            {proof?.decision ? (
                                <div className="mt-4 bg-[rgba(240,180,95,0.1)] p-3">
                                    <div
                                        className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{t("proof.reviewDecision")}</div>
                                    <div
                                        className="mt-1 text-sm font-medium text-[var(--foreground)]">{decisionLabel(proof.decision, t)}</div>
                                </div>
                            ) : null}
                        </div>
                    ) : (
                        <EmptyState compact className="mt-4" title={t("proof.empty")}/>
                    )}
                </PageSection>
            ) : null}

            {compactPaymentPage ? (
                <div className="max-w-4xl">
                    <div className="min-w-0 space-y-8">
                        <PageSection tone="default" size="flush"
                                     className="max-w-3xl border-0 bg-[var(--background)] shadow-none">
                            <PageSectionHeader
                                heading={<span
                                    className="text-sm font-medium text-[var(--foreground)]">{t("delivery.heading")}</span>}
                            />
                            <div className="mt-3 grid gap-4 sm:grid-cols-2">
                                <RequirementBlock label={t("delivery.deliverable")}
                                                  value={item?.deliverableSpec ?? post.deliveryStandard}/>
                                <RequirementBlock label={t("delivery.acceptance")}
                                                  value={item?.acceptanceSpec ?? post.settlementSummary}/>
                            </div>
                        </PageSection>
                    </div>
                </div>
            ) : null}

            <div className="min-w-0 space-y-5">
                {!compactPaymentPage ? (
                    <PageSection tone="default" size="flush"
                                 className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
                        <div className="grid gap-8 lg:grid-cols-2">
                            <section className="space-y-3">
                                <PageSectionHeader heading={t("delivery.heading")}/>
                                <div>
                                    <RequirementBlock label={t("delivery.deliverable")}
                                                      value={item?.deliverableSpec ?? post.deliveryStandard}/>
                                    <RequirementBlock label={t("delivery.acceptance")}
                                                      value={item?.acceptanceSpec ?? post.settlementSummary}/>
                                </div>
                            </section>
                            <section className="space-y-3">
                                <OrderSummaryPanel
                                    order={order}
                                    paymentIntent={paymentIntent}
                                    settlementPreview={settlementPreview}
                                    shareSettlementHold={shareSettlementHold}
                                    shareReleaseRequest={shareReleaseRequest}
                                    payer={payer}
                                    fulfiller={fulfiller}
                                    payerLabel={payerLabel}
                                    fulfillerLabel={fulfillerLabel}
                                    locale={locale}
                                    t={t}
                                />
                            </section>
                        </div>
                        {item && post.title !== title ? (
                            <div className="bg-[var(--background)] py-2">
                                <div
                                    className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{t("delivery.sourcePost")}</div>
                                <div className="mt-1 text-sm font-medium text-[var(--foreground)]">{post.title}</div>
                                {post.summary ?
                                    <p className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">{post.summary}</p> : null}
                            </div>
                        ) : null}
                    </PageSection>
                ) : null}

                {progressTimeline.length > 0 ? (
                    <PageSection tone="default" size="flush"
                                 className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
                        <PageSectionHeader heading={t("progress.heading")}/>
                        <div className="mt-4 space-y-3">
                            {progressTimeline.map((progress) => (
                                <div key={progress.id} className="bg-[var(--background)] py-3">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <span className="mf-chip">{t("progress.update")}</span>
                                        <span
                                            className="text-xs text-[var(--muted-foreground)]">{formatDate(progress.createdAt)}</span>
                                    </div>
                                    <div
                                        className="mt-3 text-sm font-medium text-[var(--foreground)]">{progress.stepTitle}</div>
                                    <p className="mt-2 text-sm leading-6 text-[var(--muted-foreground)]">{progress.summary}</p>
                                </div>
                            ))}
                        </div>
                    </PageSection>
                ) : null}

                {!compactPaymentPage && recentEvents.length > 0 ? (
                    <PageSection tone="default" size="flush" className="border-t border-[rgba(255,255,255,0.08)] pt-5">
                        <PageSectionHeader heading={t("events.recent")}/>
                        <div className="mt-4 space-y-3">
                            {recentEvents.map((event) => {
                                const actor = accounts.find((account) => account.id === event.actorAccountId);
                                const actorMeta = actor ? accountDisplayMeta(actor, t("account.unknown"), t) : "";
                                return (
                                    <div key={event.id} className="bg-[var(--background)] py-2">
                                        <div
                                            className="text-sm font-medium text-[var(--foreground)]">{eventDisplayLabel(event, t)}</div>
                                        <div className="mt-1 text-xs text-[var(--muted-foreground)]">
                                            {[actorMeta, formatDate(event.createdAt)].filter(Boolean).join(" · ")}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                        {meaningfulEvents.length > recentEvents.length ? (
                            <div className="mt-3 text-xs text-[var(--muted-foreground)]">
                                {t("events.hasMore", {
                                    hidden: meaningfulEvents.length - recentEvents.length,
                                    count: meaningfulEvents.length
                                })}
                            </div>
                        ) : null}
                    </PageSection>
                ) : null}
            </div>

            {hasDisputeContext ? (
                <PageSection tone="muted" size="flush"
                             className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
                    <PageSectionHeader
                        heading={order.status === "final_accepted" || order.status === "final_closed" ? t("dispute.resultHeading") : t("dispute.heading")}
                    />
                    <div className="mt-4 grid gap-3 md:grid-cols-2">
                        {reviewContext?.disputeOpenedByAccountId ? (
                            <DisputeFact
                                label={t("dispute.openedBy")}
                                value={`${accountDisplayName(disputeOpener, t("account.unknown"))}${reviewContext.disputeOpenedAt ? ` · ${formatDate(reviewContext.disputeOpenedAt)}` : ""}`}
                            />
                        ) : null}
                        {reviewContext?.disputeReason ?
                            <DisputeFact label={t("dispute.reason")} value={reviewContext.disputeReason}/> : null}
                        {order.status === "disputed" ? (
                            <DisputeFact
                                label={t("dispute.handler")}
                                value={reviewContext?.reviewerAccountId
                                    ? `${accountDisplayName(assignedReviewer, t("account.unknown"))}${reviewContext.reviewDueAt ? t("dispute.dueAt", {date: formatDate(reviewContext.reviewDueAt)}) : ""}`
                                    : t("dispute.handlerPending")}
                            />
                        ) : null}
                        {reviewContext?.disputeCancelledByAccountId ? (
                            <DisputeFact
                                label={t("dispute.cancelled")}
                                value={`${accountDisplayName(disputeCanceller, t("account.unknown"))}${reviewContext.disputeCancelReason ? ` · ${reviewContext.disputeCancelReason}` : ""}`}
                            />
                        ) : null}
                        {order.status !== "disputed" && reviewContext?.reviewerAccountId ? (
                            <DisputeFact
                                label={t("dispute.reviewer")}
                                value={`${accountDisplayName(assignedReviewer, t("account.unknown"))}${reviewContext.reviewDueAt ? t("dispute.dueAt", {date: formatDate(reviewContext.reviewDueAt)}) : ""}`}
                            />
                        ) : null}
                        {reviewContext?.backofficeOverrideDecision ? (
                            <DisputeFact
                                label={t("dispute.backofficeHandling")}
                                value={`${decisionLabel(reviewContext.backofficeOverrideDecision, t)}${reviewContext.backofficeOverrideReason ? ` · ${reviewContext.backofficeOverrideReason}` : ""}`}
                            />
                        ) : null}
                        {reviewContext?.parentOrderNo ? <LinkedFact label={t("dispute.originalOrder")}
                                                                    href={`/orders/${reviewContext.parentOrderNo}`}
                                                                    value={t("dispute.openOriginalOrder")}/> : null}
                        {reviewContext?.reviewOrderNo ?
                            <LinkedFact label={t("dispute.reviewOrder")} href={`/orders/${reviewContext.reviewOrderNo}`}
                                        value={t("dispute.openReviewOrder")}/> : null}
                    </div>
                </PageSection>
            ) : null}

        </PageContainer>
    );
}

function FinalResultSummary({
                                order,
                                settlementPreview,
                                shareReleaseRequest,
                                t,
                            }: {
    order: Order;
    settlementPreview: SettlementPreview;
    shareReleaseRequest?: ShareReleaseRequest;
    t: OrderDetailTranslator;
}) {
    const isClosed = order.status === "final_closed";
    const isShares = settlementPreview.settlementType.toLowerCase() === "shares";
    const message = isClosed
        ? t("finalSummary.closed")
        : isShares && shareReleaseRequest
            ? t("finalSummary.sharesPending")
            : isShares
                ? t("finalSummary.sharesReady")
                : t("finalSummary.moneyDone");

    return (
        <section className="rounded-[12px] bg-[var(--background)] py-1">
            <div className="text-sm font-medium leading-6 text-[var(--foreground)]">{message}</div>
        </section>
    );
}

function OrderCurrentStateSection({
                                      title,
                                      description,
                                      dueAt,
                                      t,
                                  }: {
    title: string;
    description?: string;
    dueAt?: string;
    t: OrderDetailTranslator;
}) {
    return (
        <section>
            <div
                className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{t("common.currentState")}</div>
            <div className="mt-1 text-lg font-medium leading-6 text-[var(--foreground)]">{title}</div>
            {description ?
                <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{description}</div> : null}
            <OrderPaymentCountdown dueAt={dueAt}/>
        </section>
    );
}

function OrderSummaryPanel({
                               order,
                               paymentIntent,
                               settlementPreview,
                               shareSettlementHold,
                               shareReleaseRequest,
                               payer,
                               fulfiller,
                               payerLabel,
                               fulfillerLabel,
                               locale,
                               t,
                           }: {
    order: Order;
    paymentIntent?: PaymentIntent;
    settlementPreview: SettlementPreview;
    shareSettlementHold?: ShareSettlementHold;
    shareReleaseRequest?: ShareReleaseRequest;
    payer: DisplayAccount | undefined;
    fulfiller: DisplayAccount | undefined;
    payerLabel: string;
    fulfillerLabel: string;
    locale: string;
    t: OrderDetailTranslator;
}) {
    const isShares = settlementPreview.settlementType.toLowerCase() === "shares";
    const amountLabel = isShares ? t("amount.sharesValue", {amount: settlementPreview.amount ?? order.settlementAmount ?? 0}) : paymentAmountText(order, paymentIntent, t, locale);
    const windowLabel = order.disputeWindowExpiresAt ? formatDate(order.disputeWindowExpiresAt) : t("settlement.windowPending");
    const counterpartyLabel = order.currentAccountRole === "payer" ? fulfillerLabel : order.currentAccountRole === "fulfiller" ? payerLabel : t("settlement.counterparty");
    const counterpartyValue = order.currentAccountRole === "payer"
        ? <AccountValue account={fulfiller} fallback={t("account.pending")}/>
        : order.currentAccountRole === "fulfiller"
            ? <AccountValue account={payer} fallback={t("account.pending")}/>
            : <CounterpartyValue order={order} payer={payer} fulfiller={fulfiller} payerLabel={payerLabel}
                                 fulfillerLabel={fulfillerLabel} fallback={t("account.pending")}/>;
    const hasCounterparty = order.currentAccountRole === "payer" ? Boolean(fulfiller) : order.currentAccountRole === "fulfiller" ? Boolean(payer) : Boolean(payer || fulfiller);
    const nextOwner = isShares
        ? shareReleaseRequest
            ? t("settlement.waitingApproval")
            : order.status === "accepted_open" ? t("settlement.waitingDisputeWindow") : t("settlement.waitingAcceptance")
        : t("settlement.payer", {payer: accountDisplayName(payer, t("account.pending"))});

    return (
        <div>
            <div className="text-base font-medium text-[var(--foreground)]">{t("summary.heading")}</div>
            <div className="mt-3">
                <SummaryRow label={isShares ? t("settlement.sharesAmount") : t("settlement.payableAmount")}
                            value={amountLabel}/>
                {hasCounterparty ? <SummaryRow label={counterpartyLabel} value={counterpartyValue}/> : null}
                {isShares ? <SummaryRow label={t("settlement.disputeWindowEnds")} value={windowLabel}/> : null}
            </div>
            {shareSettlementHold || shareReleaseRequest ? (
                <div className="mt-3 grid gap-2 text-xs leading-5 text-[var(--muted-foreground)] sm:grid-cols-2">
                    <div>{t("settlement.nextOwner", {owner: nextOwner})}</div>
                    {shareSettlementHold ?
                        <div>{t("settlement.lockReasonLine", {reason: shareLockReasonLabel(shareSettlementHold.lockReason, t)})}</div> : null}
                    {shareReleaseRequest ? <div>{t("settlement.approvalPending")}</div> : null}
                </div>
            ) : null}
        </div>
    );
}

function disputeWindowDescription(order: Order, t: OrderDetailTranslator) {
    return order.disputeWindowExpiresAt
        ? t("currentState.disputeWindowEnds", {date: formatDate(order.disputeWindowExpiresAt)})
        : t("currentState.disputeWindowUnknown");
}

function currentStateDescription(order: Order, paymentIntent: PaymentIntent | undefined, t: OrderDetailTranslator) {
    if (order.status === "accepted_open") {
        return disputeWindowDescription(order, t);
    }
    const paymentDueAt = orderPaymentDueAt(order, paymentIntent);
    if (isMoneyPaymentPending(order, paymentIntent)) {
        if (typeof paymentDueAt === "string" && paymentDueAt.trim()) {
            return t("currentState.paymentDueAt", {date: formatDate(paymentDueAt)});
        }
        return t("currentState.paymentPending");
    }
    if (order.status === "claimed" && typeof order.nextProgressDueAt === "string" && order.nextProgressDueAt.trim()) {
        return t("currentState.deliveryDueAt", {date: formatDate(order.nextProgressDueAt)});
    }
    return undefined;
}

function RequirementBlock({label, value}: { label: string; value: string }) {
    return (
        <div className="py-2 first:pt-0 last:pb-0">
            <div className="text-xs text-[var(--muted-foreground)]">{label}</div>
            <div className="mt-1 break-words text-sm leading-6 text-[var(--foreground)]">{value}</div>
        </div>
    );
}

function SummaryRow({label, value}: { label: string; value: ReactNode }) {
    return (
        <div className="border-b border-[rgba(255,255,255,0.06)] py-3 first:pt-0 last:border-b-0 last:pb-0">
            <div className="text-xs text-[var(--muted-foreground)]">{label}</div>
            <div className="break-words text-sm font-medium leading-6 text-[var(--foreground)]">{value}</div>
        </div>
    );
}

function DisputeFact({label, value}: { label: string; value: string }) {
    return (
        <div className="min-w-0 border-b border-[var(--border)] pb-3 last:border-b-0">
            <div className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{label}</div>
            <div className="mt-1 break-words text-sm leading-6 text-[var(--foreground)]">{value}</div>
        </div>
    );
}

function LinkedFact({label, href, value}: { label: string; href: string; value: string }) {
    return (
        <Link href={href} className="bg-[var(--background)] p-4 transition hover:bg-[var(--surface-1)]">
            <div className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{label}</div>
            <div className="mt-1 break-words text-sm font-medium text-[var(--accent-blue)]">{value}</div>
        </Link>
    );
}
