import {getLocale, getTranslations} from "next-intl/server";
import {notFound, redirect} from "next/navigation";
import type {ReactNode} from "react";
import {
    AlertTriangle,
    Box,
    Check,
    ExternalLink,
    ImageIcon,
    Info,
    MessageCircle,
    ShieldCheck,
    ShieldQuestion
} from "lucide-react";

import {Link} from "@/i18n/navigation";
import {DisputeEvidenceList} from "@/components/dispute-evidence-list";
import {OrderAccessDenied} from "@/components/order-access-denied";
import {OrderActionPanel} from "@/components/order-action-panel";
import {Button} from "@/components/ui/button";
import {EmptyState, PageContainer, PageSection, PageSectionHeader} from "@/components/ui/page-layout";
import {ApiRequestError} from "@/lib/api-error";
import {
    type Account,
    type ActionView,
    formatDate,
    lookupPublicAccounts,
    type Order,
    type OrderEvent,
    type Proof,
    type PublicAccount,
    type ReviewContext,
} from "@/lib/api";
import {getOrderDetailServer, listReviewerCandidatesServer} from "@/lib/api/order-server";
import {profileHref} from "@/lib/business-routes";

type DisputeTranslator = Awaited<ReturnType<typeof getTranslations>>;
type DisplayAccount = Account | PublicAccount;

function localizedLoginHref(locale: string, returnTo: string) {
    const prefix = locale === "en" ? "/en" : "";
    return `${prefix}/login?auth=login&returnTo=${encodeURIComponent(returnTo)}`;
}

async function loadOrderDetail(orderNo: string, locale: string) {
    try {
        return await getOrderDetailServer(orderNo);
    } catch (error) {
        if (error instanceof ApiRequestError && error.status === 401) {
            // 中文注释：争议详情属于参与方和处理人视图，服务端保留原地址方便登录后回看争议链。
            redirect(localizedLoginHref(locale, `/orders/${orderNo}/dispute`));
        }
        if (error instanceof ApiRequestError && error.status === 404) {
            notFound();
        }
        if (error instanceof ApiRequestError && error.status === 403) {
            // 中文注释：争议页读取权限不足时复用订单受限页，保持参与方边界提示一致。
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
            return [];
        }
        throw error;
    }
}

export default async function OrderDisputePage({params}: { params: Promise<{ orderNo: string }> }) {
    const {orderNo} = await params;
    const t = await getTranslations("Orders.dispute");
    const accessT = await getTranslations("Orders.detail.accessDenied");
    const locale = await getLocale();
    const [detail, reviewerCandidates] = await Promise.all([
        loadOrderDetail(orderNo, locale),
        loadReviewerCandidates(orderNo),
    ]);
    if (!detail) {
        return (
            <OrderAccessDenied
                orderNo={orderNo}
                returnHref="/profile/me?section=market&tab=all"
                returnLabel={accessT("backToOrders")}
                heading={accessT("heading")}
                description={accessT("description", {orderNo})}
                note={accessT("note")}
            />
        );
    }
    const {order, proof, reviewContext, eventTimeline} = detail;
    const proofRefs = [...new Set([...proofEvidenceRefs(proof), ...deliveryEvidenceRefs(order)])];
    const handling = disputeHandling(order, reviewContext, detail.availableActions, t);
    const progress = disputeProgress(order, reviewContext, detail.availableActions, eventTimeline, t);
    const disputeRefs = reviewContext?.disputeEvidenceRefs ?? [];
    const accountIds = [
        order.buyerAccountId,
        order.sellerAccountId,
        order.fulfillerAccountId,
        order.acceptorAccountId,
        reviewContext?.disputeOpenedByAccountId,
        reviewContext?.disputeCancelledByAccountId,
        reviewContext?.reviewerAccountId,
        ...eventTimeline.map((event) => event.actorAccountId),
    ].filter(Boolean) as string[];
    const uniqueAccountIds = [...new Set(accountIds)];
    // 中文注释：争议首屏只查询当前时间线涉及的公开账号，避免把完整账号目录并入受保护详情页。
    const accounts = await lookupPublicAccounts(uniqueAccountIds).catch(() => []);
    const openedBy = accountMeta(accounts, reviewContext?.disputeOpenedByAccountId, t, participantFallback(order, reviewContext?.disputeOpenedByAccountId, t));
    const handler = reviewContext?.reviewerAccountId
        ? accountMeta(accounts, reviewContext.reviewerAccountId, t)
        : {label: t("source.handlerPending"), href: null};
    const orderedEvents = [...eventTimeline]
        .filter((event) => isDisputeTimelineEvent(event))
        .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());

    return (
        <PageContainer width="full" className="pb-10">
            <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
                <main className="min-w-0 space-y-4">
                    <section
                        className="relative overflow-hidden rounded-[12px] border border-[rgba(72,108,230,0.24)] bg-[linear-gradient(135deg,rgba(72,108,230,0.16),rgba(72,108,230,0.05)_62%,rgba(255,255,255,0.03))] px-5 py-5">
                        <div
                            className="relative z-10 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                            <div className="flex min-w-0 gap-4">
                                <div
                                    className="flex h-14 w-14 shrink-0 items-center justify-center rounded-[10px] border border-[rgba(72,108,230,0.3)] bg-[rgba(72,108,230,0.16)]">
                                    <ShieldQuestion className="h-7 w-7 text-[var(--primary)]"/>
                                </div>
                                <div className="min-w-0">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h1 className="text-2xl font-semibold leading-8 text-[var(--foreground)]">{handling.title}</h1>
                                        <span
                                            className="rounded-full border border-[rgba(72,108,230,0.35)] bg-[rgba(72,108,230,0.12)] px-2.5 py-1 text-xs font-semibold text-[var(--primary)]">
                      {progress.currentLabel}
                    </span>
                                    </div>
                                    <div
                                        className="mt-2 grid max-w-4xl grid-cols-[auto_minmax(0,1fr)] gap-x-1 text-sm leading-6">
                                        <span className="text-[var(--muted-foreground)]">{t("source.order")}：</span>
                                        <span
                                            className="line-clamp-2 min-w-0 font-medium text-[var(--foreground)]">{order.orderName}</span>
                                    </div>
                                    <div className="mt-3 flex flex-wrap gap-2 text-xs text-[var(--muted-foreground)]">
                                        <MetaPill label={t("source.openedBy")}
                                                  value={<AccountMetaLink meta={openedBy}/>}/>
                                        <MetaPill label={t("source.handler")}
                                                  value={<AccountMetaLink meta={handler}/>}/>
                                        {reviewContext?.disputeOpenedAt ? <MetaPill label={t("current.openedAt")}
                                                                                    value={formatDate(reviewContext.disputeOpenedAt)}/> : null}
                                    </div>
                                </div>
                            </div>
                            <Button asChild size="sm" variant="primary" className="shrink-0">
                                <Link href={`/orders/${order.orderNo}`}>
                                    {t("source.openOrder")}
                                    <ExternalLink className="h-3.5 w-3.5"/>
                                </Link>
                            </Button>
                        </div>
                    </section>

                    <DisputeCard icon={<MessageCircle className="h-5 w-5"/>} title={t("source.reason")}>
                        <div
                            className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3 whitespace-pre-wrap break-words text-sm leading-6 text-[var(--foreground)]">
                            {reviewContext?.disputeReason || t("common.waitingRecord")}
                        </div>
                        {reviewContext?.disputeCancelledByAccountId ? (
                            <div
                                className="mt-4 flex items-start gap-3 rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3">
                                <ShieldCheck className="mt-1 h-4 w-4 shrink-0 text-[var(--muted-foreground)]"/>
                                <div className="min-w-0">
                                    <div
                                        className="text-xs font-semibold text-[var(--muted-foreground)]">{t("source.cancelRecord")}</div>
                                    <div
                                        className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-[var(--foreground)]">
                                        {accountLabel(accounts, reviewContext.disputeCancelledByAccountId, t)} · {reviewContext.disputeCancelReason || t("state.cancelled")}
                                    </div>
                                </div>
                            </div>
                        ) : null}
                    </DisputeCard>

                    <DisputeCard icon={<Box className="h-5 w-5"/>} title={t("evidence.deliveryResult")}>
                        <EvidenceColumn
                            summary={deliverySummary(order, proof, t)}
                            evidenceRefs={proofRefs}
                            empty={t("evidence.emptyDelivery")}
                            t={t}
                        />
                    </DisputeCard>

                    <DisputeCard icon={<ImageIcon className="h-5 w-5"/>} title={t("evidence.disputeEvidence")}>
                        {disputeRefs.length > 0 ? (
                            <DisputeEvidenceList evidenceRefs={disputeRefs} labels={evidenceListLabels(t)}/>
                        ) : (
                            <EmptyState compact title={t("evidence.emptyDispute")}/>
                        )}
                    </DisputeCard>

                    <DisputeCard icon={<Check className="h-5 w-5"/>} title={t("evidence.acceptance")}>
                        {order.acceptanceCriteriaSnapshot.length > 0 ? (
                            <ul className="space-y-1 text-sm leading-6 text-[var(--muted-foreground)]">
                                {order.acceptanceCriteriaSnapshot.map((item, index) => (
                                    <li key={`${index}-${item}`} className="break-words">{item}</li>
                                ))}
                            </ul>
                        ) : (
                            <EmptyState compact title={t("common.waitingRecord")}/>
                        )}
                    </DisputeCard>

                    <DisputeCard icon={<ShieldCheck className="h-5 w-5"/>} title={t("timeline.heading")}>
                        <div className="space-y-2">
                            {orderedEvents.length > 0 ? orderedEvents.map((event) => (
                                <TimelineEvent key={event.id} event={event} accounts={accounts}
                                               disputeReason={reviewContext?.disputeReason} t={t}/>
                            )) : (
                                <EmptyState compact title={t("timeline.empty")}/>
                            )}
                        </div>
                    </DisputeCard>
                </main>

                <aside className="min-w-0 space-y-4 xl:sticky xl:top-4 xl:self-start">
                    <SideCard title={t("progress.heading")}>
                        <DisputeProgress steps={progress.steps}/>
                    </SideCard>
                    <SideCard title={t("management.heading")}
                              icon={<AlertTriangle className="h-5 w-5 text-[rgb(245,98,98)]"/>}>
                        <OrderActionPanel
                            order={order}
                            availableActions={detail.availableActions}
                            reviewerCandidates={reviewerCandidates}
                            paymentIntent={detail.paymentIntent}
                            showActionSummary={false}
                            showOrderActions={false}
                            showDisputeManagementHeading={false}
                        />
                        <div
                            className="mt-4 text-xs leading-5 text-[var(--muted-foreground)]">{handling.action || t("management.note")}</div>
                    </SideCard>
                    <SideCard title={t("notes.heading")} icon={<Info className="h-5 w-5 text-[var(--primary)]"/>}>
                        <p className="text-sm leading-6 text-[var(--muted-foreground)]">{t("notes.description")}</p>
                    </SideCard>
                </aside>
            </div>
        </PageContainer>
    );
}

function MetaPill({label, value}: { label: string; value: ReactNode }) {
    return (
        <span className="rounded-[6px] border border-[var(--border)] bg-[rgba(255,255,255,0.03)] px-2.5 py-1">
      {label}：<span className="text-[var(--foreground)]">{value}</span>
    </span>
    );
}

function AccountMetaLink({meta}: { meta: { label: string; href: string | null } }) {
    if (!meta.href) return <>{meta.label}</>;
    return (
        <Link href={meta.href} className="underline-offset-4 hover:underline">
            {meta.label}
        </Link>
    );
}

function DisputeCard({icon, title, children}: { icon: ReactNode; title: ReactNode; children: ReactNode }) {
    return (
        <PageSection tone="default" size="flush"
                     className="rounded-[10px] border border-[var(--border)] bg-[var(--background)] px-5 py-4 shadow-none">
            <PageSectionHeader
                heading={(
                    <span className="inline-flex items-center gap-2">
            <span className="text-[var(--primary)]">{icon}</span>
                        {title}
          </span>
                )}
            />
            <div className="mt-4">{children}</div>
        </PageSection>
    );
}

function SideCard({title, icon, children}: { title: ReactNode; icon?: ReactNode; children: ReactNode }) {
    return (
        <section className="rounded-[10px] border border-[var(--border)] bg-[var(--background)] px-5 py-4">
            <div className="flex items-center gap-2 text-base font-semibold text-[var(--foreground)]">
                {icon}
                <span>{title}</span>
            </div>
            <div className="mt-4">{children}</div>
        </section>
    );
}

function EvidenceColumn({summary, evidenceRefs, empty, t}: {
    summary: ReactNode;
    evidenceRefs: string[];
    empty: string;
    t: DisputeTranslator;
}) {
    return (
        <section className="min-w-0">
            <div
                className="min-h-12 whitespace-pre-wrap break-words text-sm leading-6 text-[var(--foreground)]">{summary}</div>
            <div className="mt-3">
                {evidenceRefs.length > 0 ? (
                    <DisputeEvidenceList evidenceRefs={evidenceRefs} labels={evidenceListLabels(t)}/>
                ) : (
                    <EmptyState compact title={empty}/>
                )}
            </div>
        </section>
    );
}

type DisputeProgressStep = {
    label: string;
    helper: string;
    status: "done" | "current" | "pending";
};

function DisputeProgress({steps}: { steps: DisputeProgressStep[] }) {
    return (
        <ol className="space-y-0">
            {steps.map((step, index) => {
                const completed = step.status === "done";
                const current = step.status === "current";
                const last = index === steps.length - 1;
                return (
                    <li key={step.label} className="grid grid-cols-[30px_minmax(0,1fr)] gap-3">
                        <div className="flex flex-col items-center">
              <span className={[
                  "flex h-7 w-7 items-center justify-center rounded-full border text-xs font-semibold",
                  current
                      ? "border-[var(--primary)] bg-[var(--primary)] text-white shadow-[0_0_0_3px_rgba(72,108,230,0.18)]"
                      : completed
                          ? "border-[var(--primary)] bg-[rgba(72,108,230,0.18)] text-[var(--primary)]"
                          : "border-[var(--border)] bg-[var(--background)] text-[var(--muted-foreground)]",
              ].join(" ")}>
                {completed ? <Check className="h-3.5 w-3.5"/> : index + 1}
              </span>
                            {!last ? <span
                                className={completed ? "h-10 w-px bg-[rgba(72,108,230,0.5)]" : "h-10 w-px bg-[var(--border)]"}/> : null}
                        </div>
                        <div className={last ? "pb-0" : "pb-4"}>
                            <div
                                className={current || completed ? "text-sm font-semibold text-[var(--foreground)]" : "text-sm font-medium text-[var(--muted-foreground)]"}>
                                {step.label}
                            </div>
                            <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{step.helper}</div>
                        </div>
                    </li>
                );
            })}
        </ol>
    );
}

function TimelineEvent({event, accounts, disputeReason, t}: {
    event: OrderEvent;
    accounts: DisplayAccount[];
    disputeReason?: string;
    t: DisputeTranslator
}) {
    const detail = eventDetailText(event, accounts, disputeReason, t);
    const actor = accountLabel(accounts, event.actorAccountId, t);
    return (
        <div className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] p-4">
            <div className="flex flex-wrap items-center gap-2">
                <span
                    className="text-sm font-black text-[var(--foreground)]">{eventTypeLabel(event.eventType, t)}</span>
                <span
                    className="text-xs font-semibold text-[var(--muted-foreground)]">{actor} · {formatDate(event.createdAt)}</span>
            </div>
            {detail ? <div className="mt-2 text-sm leading-6 text-[var(--foreground)]">{detail}</div> : null}
        </div>
    );
}

function disputeProgress(order: Order, reviewContext: ReviewContext | undefined, actions: ActionView[], events: OrderEvent[], t: DisputeTranslator) {
    const hasAssignment = Boolean(reviewContext?.reviewerAccountId) || events.some((event) => event.eventType === "reviewer_assigned");
    const hasMaterialSupplement = events.some((event) => event.eventType === "review_proof_submitted");
    const cancelled = Boolean(reviewContext?.disputeCancelledAt);
    const finalized = order.status === "final_accepted" || order.status === "final_closed";
    const readyForRuling = actions.some((action) => action.id === "override_accept_original" || action.id === "override_close_original");

    const openedStep: DisputeProgressStep = {
        label: t("progress.opened"),
        helper: reviewContext?.disputeOpenedAt ? formatDate(reviewContext.disputeOpenedAt) : t("progress.done"),
        status: "done",
    };
    if (cancelled) {
        const steps: DisputeProgressStep[] = [
            openedStep,
            {
                label: t("progress.cancelled"),
                helper: reviewContext?.disputeCancelledAt ? formatDate(reviewContext.disputeCancelledAt) : t("progress.done"),
                status: "done"
            },
        ];
        return {steps, currentLabel: steps[steps.length - 1].label};
    }

    const steps: DisputeProgressStep[] = [openedStep];
    if (hasAssignment) {
        steps.push({label: t("progress.assignment"), helper: t("progress.done"), status: "done"});
    } else if (!finalized) {
        steps.push({label: t("progress.assignment"), helper: t("progress.current"), status: "current"});
    }

    if (hasMaterialSupplement) {
        steps.push({label: t("progress.materials"), helper: t("progress.done"), status: "done"});
    } else if (hasAssignment && !readyForRuling && !finalized) {
        steps.push({label: t("progress.materials"), helper: t("progress.current"), status: "current"});
    }

    steps.push({
        label: t("progress.ruling"),
        helper: finalized ? t("progress.done") : readyForRuling ? t("progress.current") : t("progress.pending"),
        status: finalized ? "done" : readyForRuling ? "current" : "pending",
    });
    steps.push({
        label: t("progress.ended"),
        helper: finalized ? t("progress.done") : t("progress.pending"),
        status: finalized ? "done" : "pending",
    });

    const currentLabel = steps.find((step) => step.status === "current")?.label
        ?? [...steps].reverse().find((step) => step.status === "done")?.label
        ?? t("state.active");
    return {steps, currentLabel};
}

function disputeHandling(order: Order, reviewContext: ReviewContext | undefined, actions: ActionView[], t: DisputeTranslator) {
    const hasAction = (id: ActionView["id"]) => actions.some((action) => action.id === id);
    if (reviewContext?.disputeCancelledAt) {
        return {
            title: t("handling.cancelled.title"),
            action: t("handling.cancelled.action"),
        };
    }
    if (order.status === "final_accepted" || order.status === "final_closed") {
        return {
            title: t("handling.final.title"),
            action: t("handling.final.action"),
        };
    }
    if (hasAction("override_accept_original") || hasAction("override_close_original")) {
        return {
            title: t("handling.authority.title"),
            action: t("handling.authority.action"),
        };
    }
    if (hasAction("cancel_dispute")) {
        return {
            title: t("handling.opener.title"),
            action: t("handling.opener.action"),
        };
    }
    if (hasAction("assign_reviewer")) {
        return {
            title: t("handling.assign.title"),
            action: t("handling.assign.action"),
        };
    }
    if (hasAction("open_appeal")) {
        return {
            title: t("handling.appeal.title"),
            action: t("handling.appeal.action"),
        };
    }
    return {
        title: t("handling.waiting.title"),
        action: t("handling.waiting.action"),
    };
}

function eventDetailText(event: OrderEvent, accounts: DisplayAccount[], disputeReason: string | undefined, t: DisputeTranslator) {
    const reason = eventPayloadString(event, "reason");
    if (event.eventType === "order_disputed" && reason === disputeReason) return "";
    if (event.eventType === "order_disputed" && reason) return t("eventDetail.reason", {reason});
    if (event.eventType === "dispute_cancelled" && reason) {
        const restoredStatus = statusLabel(eventPayloadString(event, "restoredStatus"), t);
        return t("eventDetail.cancelled", {reason, status: restoredStatus});
    }
    if (event.eventType === "backoffice_override_applied") {
        const decision = decisionLabel(eventPayloadString(event, "decision"), t);
        return reason ? t("eventDetail.overrideWithReason", {decision, reason}) : t("eventDetail.override", {decision});
    }
    if (event.eventType === "reviewer_assigned") {
        const reviewer = accountLabel(accounts, eventPayloadString(event, "reviewerAccountId"), t);
        return t("eventDetail.reviewer", {reviewer});
    }
    return "";
}

function eventPayloadString(event: OrderEvent, key: string) {
    const value = event.payload?.[key];
    return typeof value === "string" && value.trim() ? value.trim() : "";
}

function isDisputeTimelineEvent(event: OrderEvent) {
    return [
        "order_disputed",
        "dispute_cancelled",
        "reviewer_assigned",
        "review_proof_submitted",
        "backoffice_override_applied",
        "order_closed",
        "order_accepted",
    ].includes(event.eventType);
}

function evidenceListLabels(t: DisputeTranslator) {
    return {
        empty: t("evidence.emptyRefs"),
        download: t("evidence.download"),
        open: t("evidence.open"),
        copy: t("evidence.copy"),
        copied: t("evidence.copied"),
        uploadPrefix: t("evidence.uploadPrefix"),
    };
}

function accountLabel(accounts: DisplayAccount[], accountId: string | null | undefined, t: DisputeTranslator, fallback?: string) {
    if (!accountId) return t("common.waitingRecord");
    const account = accounts.find((item) => item.id === accountId);
    const name = account?.displaySkin?.displayName ?? account?.displayName ?? fallback ?? t("common.unknownAccount");
    const handle = account?.displaySkin?.displayHandle ?? account?.handle;
    return handle ? `${name} · @${handle.replace(/^@+/, "")}` : name;
}

function accountMeta(accounts: DisplayAccount[], accountId: string | null | undefined, t: DisputeTranslator, fallback?: string) {
    if (!accountId) return {label: t("common.waitingRecord"), href: null};
    const account = accounts.find((item) => item.id === accountId);
    const label = accountLabel(accounts, accountId, t, fallback);
    const handle = account?.displaySkin?.displayHandle ?? account?.handle;
    return {
        label,
        href: handle ? profileHref(handle) : null,
    };
}

function participantFallback(order: Order, accountId: string | null | undefined, t: DisputeTranslator) {
    if (!accountId) return undefined;
    if (accountId === order.buyerAccountId || accountId === order.acceptorAccountId) return t("source.buyer");
    if (accountId === order.sellerAccountId || accountId === order.fulfillerAccountId) return t("source.seller");
    return undefined;
}

function statusLabel(status: string | null | undefined, t: DisputeTranslator) {
    const key = String(status ?? "").toLowerCase();
    return t.has(`status.${key}`) ? t(`status.${key}`) : t("common.waitingRecord");
}

function deliverySummary(order: Order, proof: Proof | undefined, t: DisputeTranslator) {
    const summary = readString(order.deliveryReceipt?.summary) || readString(order.deliveryPayload?.summary) || proof?.summary;
    if (!summary) return t("evidence.waitingDelivery");
    const decision = proof?.decision ? t("evidence.decision", {decision: decisionLabel(proof.decision, t)}) : "";
    return `${summary}${decision}`;
}

function proofEvidenceRefs(proof: Proof | undefined) {
    if (!proof) return [];
    const links = (proof.links ?? [])
        .map((link) => typeof link === "object" && link !== null && "href" in link ? String(link.href ?? "") : "")
        .filter(Boolean);
    return [...new Set([...(proof.artifacts ?? []), ...(proof.evidenceRefs ?? []), ...links].filter(Boolean))];
}

function readString(value: unknown) {
    return typeof value === "string" && value.trim() ? value.trim() : "";
}

function readStringArray(value: unknown) {
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && item.trim().length > 0) : [];
}

function deliveryEvidenceRefs(order: Order) {
    const deliveryUrl = readString(order.deliveryPayload?.url);
    return [...new Set([
        ...(deliveryUrl ? [deliveryUrl] : []),
        ...readStringArray(order.deliveryPayload?.artifacts),
        ...readStringArray(order.deliveryReceipt?.artifacts),
    ])];
}

function eventTypeLabel(type: string, t: DisputeTranslator) {
    const key = `event.${type}`;
    return t.has(key) ? t(key) : t("event.unknown");
}

function decisionLabel(decision: string | null | undefined, t: DisputeTranslator) {
    const normalized = String(decision ?? "").toLowerCase();
    if (normalized === "accept_original") return t("result.acceptOriginal");
    if (normalized === "close_original") return t("result.closeOriginal");
    return t("result.pending");
}
