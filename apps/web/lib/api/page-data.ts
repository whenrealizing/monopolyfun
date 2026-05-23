import {headers} from "next/headers";
import {notFound, redirect} from "next/navigation";

import {isApiStatus} from "@/lib/api-error";

async function localizedPath(path: string) {
    const locale = (await headers()).get("X-NEXT-INTL-LOCALE");
    return locale === "en" && !path.startsWith("/en/") ? `/en${path}` : path;
}

export async function requirePageData<T>(operation: Promise<T>): Promise<T> {
    try {
        return await operation;
    } catch (error) {
        if (isApiStatus(error, [404])) {
            // 中文注释：详情页主数据只有 404 进入 notFound，其他错误继续暴露给全局错误边界。
            notFound();
        }
        throw error;
    }
}

export type AccessiblePageDataResult<T> =
    | { data: T; status: "ok" }
    | { data: null; status: "forbidden" };

export async function loadAccessiblePageData<T>(operation: Promise<T>, returnTo: string): Promise<AccessiblePageDataResult<T>> {
    try {
        return {data: await operation, status: "ok"};
    } catch (error) {
        if (isApiStatus(error, [401])) {
            // 中文注释：主页面数据需要账号时统一登录回跳，避免页面各自拼接 auth 地址。
            const loginPath = await localizedPath("/login");
            const localizedReturnTo = await localizedPath(returnTo);
            redirect(`${loginPath}?auth=login&returnTo=${encodeURIComponent(localizedReturnTo)}`);
        }
        if (isApiStatus(error, [403])) {
            // 中文注释：主页面权限不足交给页面级受限态展示，保留 URL 让用户明确知道访问边界。
            return {data: null, status: "forbidden"};
        }
        if (isApiStatus(error, [404])) {
            notFound();
        }
        throw error;
    }
}

export async function optionalPageData<T>(
    operation: Promise<T>,
    fallback: T,
    allowedStatuses = [401, 403, 404],
): Promise<T> {
    try {
        return await operation;
    } catch (error) {
        if (isApiStatus(error, allowedStatuses)) {
            // 中文注释：辅助数据允许权限或缺失降级，避免账号列表等非主数据阻断页面。
            return fallback;
        }
        throw error;
    }
}
