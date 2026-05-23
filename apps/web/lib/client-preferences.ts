"use client";

export type ThemeMode = "light" | "dark";

export type ClientSession = {
    accountId: string;
    displayName: string;
    handle: string;
    expiresAt?: string;
};

const THEME_KEY = "monopolyfun-theme";
const SESSION_KEY = "monopolyfun-session";
const THEME_EVENT = "monopolyfun-theme-change";
const SESSION_EVENT = "monopolyfun-session-change";
let cachedSessionRaw: string | null | undefined;
let cachedSessionValue: ClientSession | null = null;

export function readStoredTheme(): ThemeMode {
    if (typeof window === "undefined") return "dark";
    const stored = safeLocalStorageGet(THEME_KEY);
    return stored === "light" ? "light" : "dark";
}

export function applyTheme(mode: ThemeMode) {
    if (typeof document === "undefined") return;
    document.documentElement.dataset.theme = mode;
}

export function saveTheme(mode: ThemeMode) {
    if (typeof window === "undefined") return;
    safeLocalStorageSet(THEME_KEY, mode);
    applyTheme(mode);
    window.dispatchEvent(new CustomEvent(THEME_EVENT, {detail: mode}));
}

export function subscribeTheme(callback: (mode: ThemeMode) => void) {
    if (typeof window === "undefined") return () => {
    };

    const handleTheme = (event: Event) => {
        const customEvent = event as CustomEvent<ThemeMode>;
        callback(customEvent.detail ?? readStoredTheme());
    };

    const handleStorage = (event: StorageEvent) => {
        if (event.key === THEME_KEY) callback(readStoredTheme());
    };

    window.addEventListener(THEME_EVENT, handleTheme);
    window.addEventListener("storage", handleStorage);
    return () => {
        window.removeEventListener(THEME_EVENT, handleTheme);
        window.removeEventListener("storage", handleStorage);
    };
}

export function readStoredSession(): ClientSession | null {
    if (typeof window === "undefined") return null;
    const raw = safeLocalStorageGet(SESSION_KEY);
    if (!raw) {
        cachedSessionRaw = null;
        cachedSessionValue = null;
        return null;
    }

    if (raw === cachedSessionRaw) {
        return cachedSessionValue;
    }

    try {
        const parsed = JSON.parse(raw) as unknown;
        cachedSessionRaw = raw;
        cachedSessionValue = normalizeStoredSession(parsed);
        if (!cachedSessionValue) {
            // 中文注释：过期或损坏的本地 session 直接失效，避免门禁用旧缓存误判登录态。
            safeLocalStorageRemove(SESSION_KEY);
            cachedSessionRaw = null;
        }
        return cachedSessionValue;
    } catch {
        cachedSessionRaw = raw;
        cachedSessionValue = null;
        return null;
    }
}

export function saveSession(session: ClientSession) {
    if (typeof window === "undefined") return;
    const normalizedSession = normalizeStoredSession(session);
    if (!normalizedSession) {
        clearSession();
        return;
    }
    const raw = JSON.stringify(normalizedSession);
    cachedSessionRaw = raw;
    cachedSessionValue = normalizedSession;
    safeLocalStorageSet(SESSION_KEY, raw);
    window.dispatchEvent(new CustomEvent(SESSION_EVENT, {detail: normalizedSession}));
}

export function clearSession() {
    if (typeof window === "undefined") return;
    cachedSessionRaw = null;
    cachedSessionValue = null;
    safeLocalStorageRemove(SESSION_KEY);
    window.dispatchEvent(new CustomEvent(SESSION_EVENT, {detail: null}));
}

export function subscribeSession(callback: (session: ClientSession | null) => void) {
    if (typeof window === "undefined") return () => {
    };

    const handleSession = (event: Event) => {
        const customEvent = event as CustomEvent<ClientSession | null>;
        callback(customEvent.detail ?? null);
    };

    const handleStorage = (event: StorageEvent) => {
        if (event.key === SESSION_KEY) callback(readStoredSession());
    };

    window.addEventListener(SESSION_EVENT, handleSession);
    window.addEventListener("storage", handleStorage);
    return () => {
        window.removeEventListener(SESSION_EVENT, handleSession);
        window.removeEventListener("storage", handleStorage);
    };
}

function normalizeStoredSession(value: unknown): ClientSession | null {
    if (!value || typeof value !== "object") {
        return null;
    }

    const session = value as Partial<ClientSession>;
    if (
        typeof session.accountId !== "string" ||
        typeof session.displayName !== "string" ||
        typeof session.handle !== "string"
    ) {
        return null;
    }

    const accountId = session.accountId.trim();
    const displayName = session.displayName.trim();
    const handle = session.handle.trim();
    if (!accountId || !displayName || !handle) {
        return null;
    }

    if (session.expiresAt) {
        const expiresAtMs = Date.parse(session.expiresAt);
        if (!Number.isFinite(expiresAtMs) || expiresAtMs <= Date.now()) {
            return null;
        }
    }

    return {
        accountId,
        displayName,
        handle,
        expiresAt: session.expiresAt,
    };
}

function safeLocalStorageGet(key: string) {
    try {
        return window.localStorage.getItem(key);
    } catch (error) {
        console.error("localStorage read failed", error);
        return null;
    }
}

function safeLocalStorageSet(key: string, value: string) {
    try {
        window.localStorage.setItem(key, value);
    } catch (error) {
        console.error("localStorage write failed", error);
    }
}

function safeLocalStorageRemove(key: string) {
    try {
        window.localStorage.removeItem(key);
    } catch (error) {
        console.error("localStorage remove failed", error);
    }
}
