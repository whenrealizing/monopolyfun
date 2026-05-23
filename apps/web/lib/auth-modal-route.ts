export const AUTH_MODAL_QUERY_KEY = "auth";
export const AUTH_RETURN_TO_QUERY_KEY = "returnTo";

export type AuthModalMode = "login" | "register";

const AUTH_MODAL_MODES = new Set<AuthModalMode>(["login", "register"]);

function isSafeInternalPath(value: string | null | undefined) {
    return Boolean(value && value.startsWith("/") && !value.startsWith("//"));
}

export function parseAuthModalMode(value: string | null | undefined): AuthModalMode | null {
    if (!value) {
        return null;
    }

    return AUTH_MODAL_MODES.has(value as AuthModalMode) ? (value as AuthModalMode) : null;
}

export function sanitizeAuthReturnTo(value: string | null | undefined) {
    if (!isSafeInternalPath(value)) {
        return null;
    }

    const normalizedValue = value as string;

    if (normalizedValue === "/login" || normalizedValue.startsWith("/login?")) {
        return "/";
    }

    return normalizedValue;
}

export function buildPathWithSearch(
    pathname: string,
    searchParams?: URLSearchParams | Pick<URLSearchParams, "toString"> | null,
) {
    const search = searchParams?.toString() ?? "";
    return search ? `${pathname}?${search}` : pathname;
}

export function stripAuthModalSearchParams(
    searchParams?: URLSearchParams | Pick<URLSearchParams, "toString"> | null,
) {
    const params = new URLSearchParams(searchParams?.toString() ?? "");
    params.delete(AUTH_MODAL_QUERY_KEY);
    params.delete(AUTH_RETURN_TO_QUERY_KEY);
    return params;
}

export function buildAuthModalHref({
                                       pathname,
                                       searchParams,
                                       mode = "login",
                                       returnTo,
                                   }: {
    pathname: string;
    searchParams?: URLSearchParams | Pick<URLSearchParams, "toString"> | null;
    mode?: AuthModalMode;
    returnTo?: string | null | undefined;
}) {
    const params = stripAuthModalSearchParams(searchParams);
    params.set(AUTH_MODAL_QUERY_KEY, mode);

    const normalizedReturnTo = sanitizeAuthReturnTo(returnTo);
    if (normalizedReturnTo) {
        params.set(AUTH_RETURN_TO_QUERY_KEY, normalizedReturnTo);
    } else {
        params.delete(AUTH_RETURN_TO_QUERY_KEY);
    }

    return buildPathWithSearch(pathname, params);
}
