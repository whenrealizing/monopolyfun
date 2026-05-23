import {headers} from "next/headers";
import {redirect} from "next/navigation";

import {
    backofficeAuditContract,
    backofficeDashboardContract,
    backofficeProofAssetContract,
    backofficeRiskContract,
    type PageResult,
    paymentIntentContract,
    type RiskAccount,
} from "@/lib/api";
import {isApiStatus} from "@/lib/api-error";
import {serverJson, serverRequestOptions} from "@/lib/api/server-request";
import * as Api from "@/lib/generated/api/monopolyfun";

async function localizedBackofficePath(path: string) {
    const locale = (await headers()).get("X-NEXT-INTL-LOCALE");
    return locale === "en" ? `/en${path}` : path;
}

async function withBackofficeAuth<T>(operation: () => Promise<T>): Promise<T> {
    try {
        return await operation();
    } catch (error) {
        if (isApiStatus(error, [401])) {
            // 中文注释：后台匿名访问进入登录，保留后台入口作为登录后的回跳目标。
            const loginPath = await localizedBackofficePath("/login");
            const returnTo = await localizedBackofficePath("/backoffice");
            redirect(`${loginPath}?auth=login&returnTo=${encodeURIComponent(returnTo)}`);
        }
        if (isApiStatus(error, [403])) {
            // 中文注释：后台权限不足是可解释状态，统一落到后台权限页，避免各页面散落通用错误。
            redirect(await localizedBackofficePath("/backoffice/forbidden"));
        }
        throw error;
    }
}

export async function getBackofficeDashboardServer() {
    return withBackofficeAuth(async () => backofficeDashboardContract(await Api.getBackofficeDashboard(await serverRequestOptions())));
}

export async function listBackofficeAuditEventsServer(limit = 50) {
    return withBackofficeAuth(async () => (await Api.listBackofficeAuditEvents({limit}, await serverRequestOptions())).map(backofficeAuditContract));
}

export async function listBackofficeRiskEventsServer(limit = 50) {
    return withBackofficeAuth(async () => (await Api.listBackofficeRiskEvents({limit}, await serverRequestOptions())).map(backofficeRiskContract));
}

export async function listRiskAccountsServer(limit = 50) {
    return withBackofficeAuth(async () => {
        const page = await serverJson<PageResult<RiskAccount>>("/api/v1/backoffice/risk/accounts", {limit});
        return page.items.map((account) => ({
            ...account,
            recentEvents: (account.recentEvents ?? []).map(backofficeRiskContract),
        }));
    });
}

export async function getRiskAccountServer(accountId: string) {
    return withBackofficeAuth(async () => {
        const account = await serverJson<RiskAccount>(`/api/v1/backoffice/risk/accounts/${encodeURIComponent(accountId)}`);
        return {
            ...account,
            recentEvents: (account.recentEvents ?? []).map(backofficeRiskContract),
        };
    });
}

export async function listBackofficePaymentIntentsServer(limit = 50) {
    return withBackofficeAuth(async () => (await Api.listBackofficePaymentIntents({limit}, await serverRequestOptions())).map((intent) => paymentIntentContract(intent)!));
}

export async function listBackofficeProofAssetsServer(limit = 50) {
    return withBackofficeAuth(async () => (await Api.listBackofficeProofAssets({limit}, await serverRequestOptions())).map(backofficeProofAssetContract));
}
