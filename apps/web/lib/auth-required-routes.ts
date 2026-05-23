const AUTH_REQUIRED_ROUTE_PREFIXES = [
    "/workbench",
    "/profile/me",
    "/publish",
    "/backoffice",
] as const;

export function isAuthRequiredPath(pathname: string | null | undefined) {
    if (!pathname) return false;

    // 中文注释：需要账号上下文的页面统一在 Shell 层拦截，避免各页面重复维护登录提示。
    return AUTH_REQUIRED_ROUTE_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}
