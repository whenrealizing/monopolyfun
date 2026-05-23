import {getDisplayPhaseLabel, getStatusLabel, type Order, type PaymentIntent,} from "@/lib/api";

export type OrderActorRole = "payer" | "fulfiller" | "reviewer" | "authority";
export type OrderDisplayLocale = "zh-CN" | "en";

export type OrderCurrentState = {
    title: string;
};

export function isPayerRole(role?: string) {
    return role === "payer";
}

export function isFulfillerRole(role?: string) {
    return role === "fulfiller";
}

export function payerRoleLabel(postKind?: string, locale: OrderDisplayLocale = "zh-CN") {
    if (locale === "en") {
        if (postKind === "request") return "Requester";
        if (postKind === "offer") return "Buyer";
        if (postKind === "project") return "Reviewer";
        return "Payer";
    }
    if (postKind === "request") return "发布人";
    if (postKind === "offer") return "买家";
    if (postKind === "project") return "验收方";
    return "付款方";
}

export function fulfillerRoleLabel(postKind?: string, locale: OrderDisplayLocale = "zh-CN") {
    if (locale === "en") {
        if (postKind === "request") return "Worker";
        if (postKind === "offer") return "Seller";
        if (postKind === "project") return "Contributor";
        return "Fulfiller";
    }
    if (postKind === "request") return "接单人";
    if (postKind === "offer") return "卖家";
    if (postKind === "project") return "执行方";
    return "交付方";
}

export function orderRoleLabel(role?: string, postKind?: string, locale: OrderDisplayLocale = "zh-CN") {
    const labels: Record<string, string> = {
        payer: payerRoleLabel(postKind, locale),
        fulfiller: fulfillerRoleLabel(postKind, locale),
        reviewer: locale === "en" ? "Reviewer" : "评审方",
        authority: locale === "en" ? "Authority" : "处理方",
    };
    return role ? labels[role] ?? role : locale === "en" ? "Participant" : "参与方";
}

export function isMoneyPaymentPending(order: Order, paymentIntent?: PaymentIntent) {
    if (order.settlementType.toLowerCase() !== "money") return false;
    const status = paymentIntent?.status?.toLowerCase();
    return status !== "captured";
}

export function roleAwarePaymentPendingLabel(order: Order, paymentIntent?: PaymentIntent, locale: OrderDisplayLocale = "zh-CN") {
    if (!isMoneyPaymentPending(order, paymentIntent)) return null;
    if (order.status !== "claimed" && order.status !== "delivered") return null;
    const role = order.currentAccountRole;
    // 中文注释：现金订单在付款完成前优先显示付款责任，避免把 claimed 阶段误读成交付阶段。
    if (isPayerRole(role)) return locale === "en" ? "Waiting for your payment" : "等待你付款";
    const payer = payerRoleLabel(order.postKind, locale);
    return locale === "en" ? `Waiting for ${payer} payment` : `等待${payer}付款`;
}

export function orderPanelPhaseLabel(order: Order, paymentIntent?: PaymentIntent, localStatus: Order["status"] = order.status, locale: OrderDisplayLocale = "zh-CN") {
    if (localStatus !== order.status) {
        return localStatus === "final_closed" ? locale === "en" ? "Closed" : "已关闭" : getStatusLabel(localStatus, locale);
    }
    if (order.deliveryMode === "instant_fulfillment") {
        if (localStatus === "final_accepted") return locale === "en" ? "Direct delivery received" : "直接发货已到账";
        if (order.deliveryReceipt) return locale === "en" ? "Direct delivery received" : "直接发货已到账";
        if (paymentIntent?.status?.toLowerCase() === "captured") return locale === "en" ? "Direct delivery processing" : "直接发货处理中";
        if (order.status === "claimed") return locale === "en" ? "Ships automatically after payment" : "等待付款后自动发货";
    }
    return roleAwarePaymentPendingLabel(order, paymentIntent, locale) ?? getDisplayPhaseLabel(order.displayPhase, locale);
}

export function buildOrderCurrentState(order: Order, paymentIntent?: PaymentIntent, locale: OrderDisplayLocale = "zh-CN"): OrderCurrentState {
    const role = order.currentAccountRole;
    const payerLabel = payerRoleLabel(order.postKind, locale);
    const fulfillerLabel = fulfillerRoleLabel(order.postKind, locale);

    if ((order.status === "claimed" || order.status === "delivered") && isMoneyPaymentPending(order, paymentIntent)) {
        if (isPayerRole(role)) {
            return {
                title: locale === "en" ? "Waiting for your payment" : "等待你付款",
            };
        }
        if (isFulfillerRole(role)) {
            return {
                title: locale === "en" ? `Waiting for ${payerLabel} payment` : `等待${payerLabel}付款`,
            };
        }
        return {
            title: locale === "en" ? `Waiting for ${payerLabel} payment` : `等待${payerLabel}付款`,
        };
    }

    if (order.status === "claimed") {
        if (order.deliveryMode === "instant_fulfillment") {
            const paid = paymentIntent?.status?.toLowerCase() === "captured";
            return {
                title: paid
                    ? locale === "en" ? "Direct delivery processing" : "直接发货处理中"
                    : locale === "en" ? "Ships automatically after payment" : "等待付款后自动发货",
            };
        }
        const deliveryWord = locale === "en" ? "delivery" : "交付结果";
        if (isFulfillerRole(role)) {
            return {
                title: locale === "en" ? `Waiting for your ${deliveryWord}` : `等待你提交${deliveryWord}`,
            };
        }
        return {
            title: locale === "en" ? `Waiting for ${fulfillerLabel} delivery` : `等待${fulfillerLabel}交付`,
        };
    }

    if (order.status === "delivered") {
        if (isPayerRole(role)) {
            return {
                title: locale === "en" ? "Waiting for your review" : "等待你验收交付结果",
            };
        }
        return {
            title: locale === "en" ? `Waiting for ${payerLabel} review` : `等待${payerLabel}确认结果`,
        };
    }

    if (order.status === "disputed") {
        return {
            title: locale === "en" ? "Dispute in review" : "争议处理中",
        };
    }

    if (order.status === "accepted_open") {
        return {
            title: locale === "en" ? "Waiting for dispute window to end" : "等待争议窗口结束",
        };
    }

    return {
        title: order.deliveryMode === "instant_delivery" && order.deliveryReceipt
            ? locale === "en" ? "Direct delivery received" : "直接发货已到账"
            : locale === "en" ? "Order ended" : "订单已结束",
    };
}
