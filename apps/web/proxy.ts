import createMiddleware from "next-intl/middleware";
import type {NextRequest} from "next/server";
import {NextResponse} from "next/server";

import {routing} from "@/i18n/routing";

const intlMiddleware = createMiddleware(routing);

export default function proxy(request: NextRequest) {
  // 中文注释：API、健康探针和内部同源端点交给 Next 路由层处理，页面请求才进入 locale 解析。
  if (
    request.nextUrl.pathname.startsWith("/api/")
    || request.nextUrl.pathname.startsWith("/actuator/")
    || request.nextUrl.pathname.startsWith("/internal/")
  ) {
    return NextResponse.next();
  }

  return intlMiddleware(request);
}

export const config = {
  matcher: ["/((?!_next|_vercel|.*\\..*).*)"],
};
