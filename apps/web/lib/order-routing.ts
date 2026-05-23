import type {CommandReceipt, SettlementType} from "@/lib/api";

export function shouldOpenPaymentRequired(receipt: CommandReceipt, actorAccountId: string, settlementType?: SettlementType) {
    const paymentRequired = typeof receipt.payload?.paymentRequired === "boolean"
        ? receipt.payload.paymentRequired
        : settlementType === "money";
    const paymentActorAccountId = typeof receipt.payload?.paymentActorAccountId === "string"
        ? receipt.payload.paymentActorAccountId
        : "";
    // 中文注释：付款提示只给实际付款账号，request 接单人负责交付，避免进入订单页后看到不可点击的签名支付按钮。
    return paymentRequired && (!paymentActorAccountId || paymentActorAccountId === actorAccountId);
}
