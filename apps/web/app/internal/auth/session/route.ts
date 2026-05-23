import {cookies} from "next/headers";
import {NextResponse} from "next/server";

import {serverBaseUrl} from "@/lib/api-runtime";

const SESSION_COOKIE_NAME = process.env.SESSION_COOKIE_NAME ?? "MONOPOLYFUN_SESSION";

export async function GET() {
    const sessionCookie = (await cookies()).get(SESSION_COOKIE_NAME);

    if (!sessionCookie?.value) {
        return new NextResponse(null, {status: 204});
    }

    const response = await fetch(`${serverBaseUrl.replace(/\/+$/, "")}/api/v1/auth/me`, {
        headers: {
            Accept: "application/json",
            // 中文注释：只转发服务端会话 cookie，避免把同域 OAuth/CSRF/分析 cookie 泄露给 API base url。
            Cookie: `${SESSION_COOKIE_NAME}=${sessionCookie.value}`,
        },
        cache: "no-store",
    });

    if (response.status === 401 || response.status === 403) {
        // 中文注释：匿名访问属于正常公开态，转换为 204 可以让浏览器控制台保持干净。
        return new NextResponse(null, {status: 204});
    }

    if (!response.ok) {
        // 中文注释：会话恢复失败保留服务端状态，Shell 会按异常路径完成检查状态。
        return NextResponse.json({message: "session.recovery.failed"}, {status: 502});
    }

    return new NextResponse(await response.text(), {
        status: 200,
        headers: {
            "Content-Type": response.headers.get("Content-Type") ?? "application/json",
        },
    });
}
