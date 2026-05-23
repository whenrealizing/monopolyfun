import {type ApiErrorPayload, ApiRequestError} from "@/lib/api-error";
import {UiError} from "@/lib/error-messages";

export const serverBaseUrl = process.env.MONOPOLYFUN_API_BASE_URL ?? process.env.API_BASE_URL ?? "http://localhost:8080";
export const clientBaseUrl = process.env.NEXT_PUBLIC_MONOPOLYFUN_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

export function readClientSession() {
    if (typeof window === "undefined") return null;
    try {
        const raw = window.localStorage.getItem("monopolyfun-session");
        if (!raw) return null;
        return JSON.parse(raw) as { accountId?: string };
    } catch (error) {
        console.error("Client session read failed", error);
        return null;
    }
}

export function requireClientSession() {
    const session = readClientSession();
    if (!session?.accountId) {
        throw new UiError("auth.required", undefined, "Authentication required");
    }
    return session;
}

export async function throwApiRequestError(response: Response, fallbackMessage: string): Promise<never> {
    const error = await response.json().catch(() => null) as ApiErrorPayload | null;
    // Spring Security 401/403 can be empty; keep status/path stable for auth recovery.
    throw new ApiRequestError({
        ...(error ?? {}),
        status: error?.status ?? response.status,
        path: error?.path ?? safePathname(response.url),
        message: error?.message ?? fallbackMessage,
    }, fallbackMessage);
}

function isBrowser() {
    return typeof window !== "undefined";
}

function resolveBaseUrl(url: string) {
    if (/^https?:\/\//.test(url)) {
        return requireTrustedAbsoluteApiUrl(url);
    }
    const baseUrl = isBrowser() ? resolveBrowserClientBaseUrl() : serverBaseUrl;
    return `${baseUrl.replace(/\/+$/, "")}${url}`;
}

function requireTrustedAbsoluteApiUrl(url: string) {
    const target = new URL(url);
    const trustedOrigins = trustedApiOrigins();
    if (!trustedOrigins.has(target.origin)) {
        // 中文注释：业务 API fetch 禁止随意打外部 URL，避免 CSRF token 和 cookie 误发到第三方域名。
        throw new UiError("api.origin.untrusted", undefined, "API origin is not trusted");
    }
    return target.toString();
}

function trustedApiOrigins() {
    const origins = new Set<string>();
    if (isBrowser()) {
        origins.add(window.location.origin);
    }
    for (const value of [clientBaseUrl, serverBaseUrl]) {
        try {
            if (value) {
                origins.add(new URL(value).origin);
            }
        } catch {
            // ignore invalid optional API base URL
        }
    }
    return origins;
}

function resolveBrowserClientBaseUrl() {
    if (!clientBaseUrl) return "";
    try {
        const configured = new URL(clientBaseUrl);
        const pageHost = window.location.hostname;
        const configuredHost = configured.hostname;
        // 中文注释：局域网访问时浏览器里的 localhost 指向访问者设备，同源 /api 才能稳定走 Next rewrite。
        if (isLoopbackHost(configuredHost) && !isLoopbackHost(pageHost)) {
            return "";
        }
    } catch {
        return clientBaseUrl;
    }
    return clientBaseUrl;
}

function isLoopbackHost(hostname: string) {
    return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1" || hostname === "[::1]";
}

function appendQuery(url: string, params?: Record<string, unknown>) {
    if (!params) return url;
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") return;
        if (Array.isArray(value)) {
            value.forEach((item) => query.append(key, String(item)));
            return;
        }
        query.set(key, String(value));
    });
    const queryString = query.toString();
    return queryString ? `${url}${url.includes("?") ? "&" : "?"}${queryString}` : url;
}

function shouldAttachCsrf(method?: string) {
    return !["GET", "HEAD", "OPTIONS", "TRACE"].includes((method ?? "GET").toUpperCase());
}

function readCookie(name: string) {
    if (typeof document === "undefined") return null;
    const prefix = `${name}=`;
    return document.cookie
        .split(";")
        .map((item) => item.trim())
        .find((item) => item.startsWith(prefix))
        ?.slice(prefix.length) ?? null;
}

export async function monopolyfunFetch<T>(urlPath: string, options: RequestInit & {
    params?: Record<string, unknown>
} = {}): Promise<T> {
    const url = appendQuery(resolveBaseUrl(urlPath), options.params);
    const trustedApiUrl = isTrustedApiUrl(url);
    const headers = new Headers(options.headers);
    headers.set("Accept", headers.get("Accept") ?? "application/json");
    if (options.body !== undefined && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    if (isBrowser() && trustedApiUrl && shouldAttachCsrf(options.method)) {
        const csrfToken = readCookie("MONOPOLYFUN_CSRF");
        if (csrfToken && !headers.has("X-CSRF-Token")) {
            headers.set("X-CSRF-Token", csrfToken);
        }
    }

    // Generated and hand-written clients share cookie credentials and CSRF behavior here.
    const response = await fetch(url, {
        ...options,
        headers,
        credentials: trustedApiUrl ? "include" : "same-origin",
        cache: "no-store",
    });

    if (!response.ok) {
        await throwApiRequestError(response, `API ${response.status} ${urlPath}`);
    }

    if (response.status === 204) {
        return undefined as T;
    }

    return response.json() as Promise<T>;
}

function isTrustedApiUrl(url: string) {
    try {
        return trustedApiOrigins().has(new URL(url, isBrowser() ? window.location.origin : serverBaseUrl).origin);
    } catch {
        return false;
    }
}

function safePathname(url: string) {
    try {
        return new URL(url).pathname;
    } catch {
        return url;
    }
}
