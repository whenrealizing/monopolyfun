import {cookies} from "next/headers";

import {serverBaseUrl, throwApiRequestError} from "@/lib/api-runtime";

export async function serverRequestOptions(): Promise<RequestInit> {
    const cookieHeader = (await cookies())
        .getAll()
        .map((cookie) => `${cookie.name}=${cookie.value}`)
        .join("; ");
    return {
        headers: {
            Accept: "application/json",
            // 中文注释：服务端渲染统一透传 HttpOnly session，后端权限判断保持当前账号上下文。
            ...(cookieHeader ? {Cookie: cookieHeader} : {}),
        },
    };
}

function appendQuery(url: string, params?: Record<string, unknown>) {
    if (!params) return url;
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            query.set(key, String(value));
        }
    });
    const queryString = query.toString();
    return queryString ? `${url}?${queryString}` : url;
}

export async function serverJson<T>(urlPath: string, params?: Record<string, unknown>): Promise<T> {
    const response = await fetch(appendQuery(`${serverBaseUrl.replace(/\/+$/, "")}${urlPath}`, params), {
        ...(await serverRequestOptions()),
        cache: "no-store",
    });
    if (!response.ok) {
        await throwApiRequestError(response, `API ${response.status} ${urlPath}`);
    }
    return response.json() as Promise<T>;
}
