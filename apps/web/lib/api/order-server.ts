import {notFound, redirect} from "next/navigation";

import {isApiStatus, isAuthExpired} from "@/lib/api-error";
import {serverJson} from "@/lib/api/server-request";
import type {Account, OrderDetail} from "@/lib/api";

export async function getOrderDetailServer(orderNo: string): Promise<OrderDetail> {
    // 中文注释：订单详情已改成参与方可见，服务端页面必须转发 cookie 才能读取当前用户权限。
    return serverJson<OrderDetail>(`/api/v1/orders/${orderNo}`);
}

export async function listReviewerCandidatesServer(orderNo: string): Promise<Account[]> {
    return serverJson<Account[]>(`/api/v1/orders/${orderNo}/reviewer-candidates`);
}

export async function getOrderDetailPageServer(orderNo: string, returnTo = `/orders/${orderNo}`): Promise<OrderDetail | null> {
    try {
        return await getOrderDetailServer(orderNo);
    } catch (error) {
        if (isApiStatus(error, [401])) {
            // 中文注释：订单读取统一在服务端入口处理登录跳转，页面只处理可展示状态。
            redirect(`/login?auth=login&returnTo=${encodeURIComponent(returnTo)}`);
        }
        if (isApiStatus(error, [404])) {
            notFound();
        }
        if (isApiStatus(error, [403])) {
            // 中文注释：403 是订单读取边界，页面用统一受限态承接。
            return null;
        }
        throw error;
    }
}

export async function listReviewerCandidatesPageServer(orderNo: string): Promise<Account[]> {
    try {
        return await listReviewerCandidatesServer(orderNo);
    } catch (error) {
        if (isAuthExpired(error)) {
            // 中文注释：评审候选只影响争议动作，主体订单内容继续展示。
            return [];
        }
        throw error;
    }
}
