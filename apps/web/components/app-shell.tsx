"use client";

import {useLocale, useTranslations} from "next-intl";
import {useSearchParams} from "next/navigation";
import {
    Boxes,
    ChevronLeft,
    Github,
    Handshake,
    Home,
    ListChecks,
    LogOut,
    PanelLeftClose,
    PanelLeftOpen,
    Plus,
    Rocket,
    Search,
    ShieldCheck,
    UserRound,
} from "lucide-react";
import type {ReactNode} from "react";
import {useCallback, useEffect, useRef, useState, useSyncExternalStore, useTransition} from "react";
import {createPortal} from "react-dom";

import {AuthModal} from "@/components/auth-modal";
import {AuthRequiredLink} from "@/components/auth-required-gate";
import {Button, buttonVariants} from "@/components/ui/button";
import {ToastProvider} from "@/components/ui/toast";
import {Link, usePathname, useRouter} from "@/i18n/navigation";
import {type AppLocale, LOCALE_COOKIE_NAME, LOCALES} from "@/i18n/locale-config";
import {
    ApiRequestError,
    getCurrentAccount,
    hasCurrentAccountBackofficeAccess,
    logoutAccount,
    recoverCurrentAccount
} from "@/lib/api";
import {isApiStatus} from "@/lib/api-error";
import {
    buildAuthModalHref,
    buildPathWithSearch,
    parseAuthModalMode,
    sanitizeAuthReturnTo,
    stripAuthModalSearchParams
} from "@/lib/auth-modal-route";
import {isAuthRequiredPath} from "@/lib/auth-required-routes";
import {
    applyTheme,
    clearSession,
    readStoredSession,
    readStoredTheme,
    saveSession,
    subscribeSession
} from "@/lib/client-preferences";
import {setErrorMessageLocale} from "@/lib/error-messages";
import {cn} from "@/lib/utils";

const navItems = [
  { href: "/", labelKey: "nav.home", icon: Home, authRequired: false },
  { href: "/workbench", labelKey: "nav.workbench", icon: ListChecks, authRequired: true },
  { href: "/profile/me", labelKey: "nav.profile", icon: UserRound, authRequired: true },
];

const mobileNavItems = [
  { href: "/", labelKey: "nav.home", icon: Home, authRequired: false },
  { href: "/workbench", labelKey: "nav.workbench", icon: ListChecks, authRequired: true },
  { href: "/profile/me", labelKey: "nav.profile", icon: UserRound, authRequired: true },
];
const backofficeNavItem = { href: "/backoffice", labelKey: "nav.backoffice", icon: ShieldCheck, authRequired: true };

const publishItems = [
  { href: "/publish?type=trade", labelKey: "publish.trade", icon: Handshake },
  { href: "/publish?type=project", labelKey: "publish.project", icon: Rocket },
];

const utilityLinks = [
  { href: "/about", labelKey: "footer.about" },
  { href: "/terms", labelKey: "footer.terms" },
  { href: "/privacy", labelKey: "footer.privacy" },
];

const routeProgressStartEvent = "monopolyfun:route-progress-start";
const shellRouteStackKey = "monopolyfun.shell.routeStack";
const shellRouteStackLimit = 20;
const localeOptions = [
  { value: "zh-CN", label: "中" },
  { value: "en", label: "En" },
] satisfies Array<{ value: AppLocale; label: string }>;
type ShellTranslator = ReturnType<typeof useTranslations>;

function isActive(pathname: string, href: string) {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

function buildHandleLabel(handle: string) {
  return `@${handle.replace(/^@+/, "")}`;
}

function buildAvatarLabel(displayName: string, handle: string) {
  const source = displayName.trim() || handle.trim();
  return source.slice(0, 2).toUpperCase();
}

function startRouteProgress() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(routeProgressStartEvent));
}

function RouteProgressBar({ routeKey }: { routeKey: string }) {
  const [state, setState] = useState<"idle" | "loading" | "finishing">("idle");
  // 中文注释：Portal 需要等浏览器端接管后再访问 document，useSyncExternalStore 可避开 effect 内同步 setState。
  const mounted = useSyncExternalStore(
    useCallback((onStoreChange) => {
      const timer = window.setTimeout(onStoreChange, 0);
      return () => window.clearTimeout(timer);
    }, []),
    () => true,
    () => false,
  );
  const routeKeyRef = useRef(routeKey);
  const finishTimerRef = useRef<number | null>(null);
  const staleTimerRef = useRef<number | null>(null);

  const clearTimers = useCallback(() => {
    if (finishTimerRef.current) window.clearTimeout(finishTimerRef.current);
    if (staleTimerRef.current) window.clearTimeout(staleTimerRef.current);
    finishTimerRef.current = null;
    staleTimerRef.current = null;
  }, []);

  const start = useCallback(() => {
    clearTimers();
    setState("loading");
    staleTimerRef.current = window.setTimeout(() => {
      setState("finishing");
      finishTimerRef.current = window.setTimeout(() => setState("idle"), 220);
    }, 8000);
  }, [clearTimers]);

  useEffect(() => {
    function handleClick(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = (event.target as Element | null)?.closest?.("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) return;
      if (anchor.target && anchor.target !== "_self") return;
      if (anchor.hasAttribute("download")) return;

      const nextUrl = new URL(anchor.href, window.location.href);
      if (nextUrl.origin !== window.location.origin) return;

      const currentRoute = `${window.location.pathname}${window.location.search}`;
      const nextRoute = `${nextUrl.pathname}${nextUrl.search}`;
      if (nextRoute === currentRoute) return;
      start();
    }

    window.addEventListener(routeProgressStartEvent, start);
    window.addEventListener("click", handleClick, true);
    window.addEventListener("popstate", start);
    return () => {
      window.removeEventListener(routeProgressStartEvent, start);
      window.removeEventListener("click", handleClick, true);
      window.removeEventListener("popstate", start);
      clearTimers();
    };
  }, [clearTimers, start]);

  useEffect(() => {
    if (routeKeyRef.current === routeKey) return;
    routeKeyRef.current = routeKey;
    if (state === "idle") return;
    clearTimers();
    // 中文注释：路由完成信号来自外部导航状态，延迟到 timer 回调中收尾可避免 hydration 后的级联渲染。
    finishTimerRef.current = window.setTimeout(() => {
      setState("finishing");
      finishTimerRef.current = window.setTimeout(() => setState("idle"), 220);
    }, 0);
  }, [clearTimers, routeKey, state]);

  if (!mounted) return null;

  return createPortal((
    <div className="pointer-events-none fixed left-0 top-0 z-[80] h-[3px] w-screen" aria-hidden="true">
      <div
        className="h-full origin-left bg-[var(--primary)] transition-[opacity,transform] duration-200 ease-out"
        style={{
          opacity: state === "idle" ? 0 : 1,
          transform: state === "idle" ? "scaleX(0)" : state === "loading" ? "scaleX(0.72)" : "scaleX(1)",
          transitionDuration: state === "loading" ? "900ms" : "200ms",
          boxShadow: "0 0 8px color-mix(in srgb, var(--primary) 58%, transparent)",
        }}
      />
    </div>
  ), document.body);
}

function SessionIdentityCard({
  displayName,
  handle,
  className,
  compact = false,
}: {
  displayName: string;
  handle: string;
  className?: string;
  compact?: boolean;
}) {
  const handleLabel = buildHandleLabel(handle);
  const avatarLabel = buildAvatarLabel(displayName, handleLabel);

  return (
    <div className={cn("flex min-w-0 items-center gap-2", className)}>
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] border border-[var(--primary-border)] bg-[var(--primary-soft)] text-sm font-semibold text-[var(--foreground)]">
        {avatarLabel}
      </span>
      {!compact ? (
        <span className="hidden min-w-0 sm:block">
          <span className="block truncate text-sm font-semibold text-[var(--foreground)]">{displayName}</span>
          <span className="block truncate text-xs text-[var(--muted-foreground)]">{handleLabel}</span>
        </span>
      ) : null}
    </div>
  );
}

function BrandLockup({ collapsed = false, mobile = false }: { collapsed?: boolean; mobile?: boolean }) {
  // 中文注释：收紧品牌锁定区的间距和视觉高度，让金币与字标在侧栏里形成更稳定的整体。
  return (
    <span className={cn("flex min-w-0 items-center overflow-hidden transition-[gap] duration-200 ease-out", collapsed ? "gap-0" : mobile ? "gap-1" : "gap-0.5")}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/openmonopoly-mark.png" alt="" aria-hidden="true" className="h-8 w-8 shrink-0" />
      <span className={cn(
        "block overflow-hidden transition-[max-width,opacity] duration-200 ease-out",
        collapsed ? "max-w-0 opacity-0" : mobile ? "max-w-[148px] opacity-100 max-[380px]:max-w-0 max-[380px]:opacity-0" : "max-w-[148px] opacity-100",
      )}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/brand/monopoly-fun-wordmark.svg?v=20260523d" alt="monopolyfun" className={cn("h-auto object-contain", mobile ? "w-[140px] max-[380px]:hidden" : "w-[148px]")} />
      </span>
    </span>
  );
}

function PublishMenu({
  collapsed = false,
  menuAlign = "left",
  floating = false,
  t,
}: {
  collapsed?: boolean;
  menuAlign?: "left" | "right" | "center";
  floating?: boolean;
  t: ShellTranslator;
}) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<number | null>(null);

  const requestClose = useCallback(() => {
    if (!open || closing) return;
    setClosing(true);
    closeTimerRef.current = window.setTimeout(() => {
      setOpen(false);
      setClosing(false);
      closeTimerRef.current = null;
    }, 140);
  }, [closing, open]);

  useEffect(() => () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
  }, []);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        requestClose();
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") requestClose();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, requestClose]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        className={cn(
          buttonVariants({ variant: "primary", size: "sm" }),
          "h-10 overflow-hidden rounded-[12px] transition-[width,padding,gap] duration-200 ease-out",
          collapsed ? "w-10 min-w-10 max-w-10 justify-center gap-0 px-0" : "w-full justify-center gap-2 px-4",
          floating ? "h-10 w-10 min-w-10 max-w-10 rounded-full px-0 shadow-[0_8px_24px_rgba(0,0,0,0.34)]" : null,
        )}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={collapsed ? t("create") : undefined}
        title={collapsed ? t("create") : undefined}
        onClick={() => {
          if (open) {
            requestClose();
            return;
          }
          setOpen(true);
        }}
      >
        <Plus className="h-5 w-5 shrink-0" />
        <span className={cn("truncate transition-[max-width,opacity] duration-200 ease-out", collapsed ? "max-w-0 opacity-0" : "max-w-[72px] opacity-100")}>{t("create")}</span>
      </button>
      {open ? (
        <div
          className={cn(
            "absolute z-30 w-[168px]",
            closing ? "animate-popover-out" : "animate-popover-in",
            floating
              ? "bottom-[calc(100%+10px)] left-1/2 -ml-[84px]"
              : collapsed
              ? menuAlign === "right" ? "right-0 top-[calc(100%+8px)]" : "left-[calc(100%+8px)] top-0"
              : menuAlign === "center" ? "left-1/2 top-[calc(100%+8px)] -translate-x-1/2"
              : menuAlign === "right" ? "right-0 top-[calc(100%+8px)]" : "left-0 top-[calc(100%+8px)]",
          )}
        >
          <div className="rounded-[12px] border border-[var(--border)] bg-[rgb(24,25,27)] p-1.5 shadow-[var(--shadow-md)]">
            {publishItems.map((item) => {
              const Icon = item.icon;
              return (
                <AuthRequiredLink key={item.href} href={item.href} className="flex h-10 items-center gap-3 rounded-[12px] px-3 text-[14px] leading-5 text-[var(--foreground)] transition-colors hover:bg-[rgb(33,34,37)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]" onClick={requestClose}>
                  <Icon className="h-5 w-5 shrink-0 text-[var(--muted-foreground)]" />
                  <span className="min-w-0 truncate">{t(item.labelKey)}</span>
                </AuthRequiredLink>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function BackButton({ visible, onBack, label }: { visible: boolean; onBack: () => void; label: string }) {
  if (!visible) return null;

  return (
    <button
      type="button"
      onClick={onBack}
      aria-label={label}
      title={label}
      className="hidden h-10 w-10 shrink-0 items-center justify-center rounded-[12px] bg-[rgb(33,34,37)] text-[var(--foreground)] shadow-[var(--shadow-sm)] transition-colors hover:bg-[rgb(30,31,33)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)] md:inline-flex"
    >
      <ChevronLeft className="h-6 w-6" aria-hidden="true" />
    </button>
  );
}

function writeLocaleCookie(locale: AppLocale) {
  document.cookie = `${LOCALE_COOKIE_NAME}=${locale}; path=/; max-age=31536000; samesite=lax`;
}

function LocaleSwitcher() {
  const locale = useLocale() as AppLocale;
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const t = useTranslations("Shell");
  const [isPending, startTransition] = useTransition();

  function handleLocaleChange(nextLocale: AppLocale) {
    if (nextLocale === locale || !LOCALES.includes(nextLocale)) return;
    const query = searchParams.toString();
    const nextHref = `${query ? `${pathname}?${query}` : pathname}${window.location.hash}`;
    writeLocaleCookie(nextLocale);
    startRouteProgress();
    startTransition(() => {
      router.replace(nextHref, { locale: nextLocale, scroll: false });
    });
  }

  return (
    <div className="inline-flex h-10 items-center gap-1 rounded-[12px] bg-[var(--surface-control)] p-1 ring-1 ring-[var(--border)]" aria-label={t("language")} title={t("language")}>
      {localeOptions.map((item) => (
        <button
          key={item.value}
          type="button"
          disabled={isPending}
          onClick={() => handleLocaleChange(item.value)}
          className={cn(
            "flex h-8 min-w-8 items-center justify-center rounded-[8px] px-2 text-[12px] font-semibold leading-4 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]",
            locale === item.value
              ? "bg-[var(--primary)] text-[var(--primary-foreground)]"
              : "text-[var(--muted-foreground)] hover:bg-[var(--surface-control-hover)] hover:text-[var(--foreground)]",
          )}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

function readShellRouteStack() {
  try {
    const raw = window.sessionStorage.getItem(shellRouteStackKey);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string") : [];
  } catch {
    return [];
  }
}

function writeShellRouteStack(stack: string[]) {
  try {
    window.sessionStorage.setItem(shellRouteStackKey, JSON.stringify(stack.slice(-shellRouteStackLimit)));
  } catch {
    // sessionStorage can be unavailable in restricted browser contexts.
  }
}

function useShellCanGoBack(routeKey: string) {
  const previousRouteRef = useRef<string | null>(null);
  const [canGoBack, setCanGoBack] = useState(false);

  useEffect(() => {
    const stack = readShellRouteStack();
    const previousRoute = previousRouteRef.current;
    const nextStack = previousRoute && previousRoute !== routeKey ? [...stack.filter((item) => item !== previousRoute), previousRoute] : stack.filter((item) => item !== routeKey);

    writeShellRouteStack(nextStack);
    previousRouteRef.current = routeKey;
    setCanGoBack(nextStack.length > 0);
  }, [routeKey]);

  function recordShellBack() {
    const stack = readShellRouteStack();
    stack.pop();
    writeShellRouteStack(stack);
    setCanGoBack(stack.length > 0);
  }

  return { canGoBack, recordShellBack };
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const locale = useLocale() as AppLocale;
  const t = useTranslations("Shell");
  const routeKey = buildPathWithSearch(pathname, searchParams);
  const { canGoBack, recordShellBack } = useShellCanGoBack(routeKey);
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sessionChecked, setSessionChecked] = useState(false);
  const [backofficeAccess, setBackofficeAccess] = useState<{ accountId: string; allowed: boolean } | null>(null);
  const authMode = parseAuthModalMode(searchParams.get("auth"));
  const routeRequiresAuth = isAuthRequiredPath(pathname);
  const fallbackReturnTo = buildPathWithSearch(pathname, stripAuthModalSearchParams(searchParams));
  const authReturnTo = sanitizeAuthReturnTo(searchParams.get("returnTo")) ?? fallbackReturnTo;
  const authModalHref = buildAuthModalHref({ pathname, searchParams, mode: "login", returnTo: fallbackReturnTo });
  const closeModalHref = routeRequiresAuth && !session ? "/" : buildPathWithSearch(pathname, stripAuthModalSearchParams(searchParams));
  const shouldHideAuthRequiredChildren = routeRequiresAuth && !session;

  useEffect(() => {
    applyTheme(readStoredTheme());
  }, []);

  useEffect(() => {
    setErrorMessageLocale(locale);
  }, [locale]);

  useEffect(() => {
    if (sessionChecked) return;
    void recoverCurrentAccount()
      .then((nextSession) => {
        if (nextSession) {
          saveRecoveredSession(nextSession);
        } else {
          clearSession();
        }
        setSessionChecked(true);
      })
      .catch((error) => {
        if (isAuthExpired(error)) clearSession();
        setSessionChecked(true);
      });
  }, [sessionChecked]);

  useEffect(() => {
    if (!routeRequiresAuth || session || authMode || !sessionChecked) return;
    router.replace(authModalHref);
  }, [authMode, authModalHref, routeRequiresAuth, router, session, sessionChecked]);

  useEffect(() => {
    let active = true;
    if (!session?.accountId || !sessionChecked) return () => {
      active = false;
    };

    void hasCurrentAccountBackofficeAccess()
      .then((allowed) => {
        if (active) setBackofficeAccess({ accountId: session.accountId, allowed });
      })
      .catch((error) => {
        if (isApiStatus(error, [401])) clearSession();
        if (active) setBackofficeAccess({ accountId: session.accountId, allowed: false });
      });

    return () => {
      active = false;
    };
  }, [session?.accountId, sessionChecked]);

  if (pathname === "/login") {
    return (
      <ToastProvider>
        <RouteProgressBar routeKey={routeKey} />
        <div className="min-h-screen bg-[var(--background)] text-[var(--foreground)]">{children}</div>
      </ToastProvider>
    );
  }

  const canViewBackoffice = Boolean(session?.accountId && backofficeAccess?.accountId === session.accountId && backofficeAccess.allowed);
  const visibleNavItems = canViewBackoffice ? [...navItems, backofficeNavItem] : navItems;
  const currentQuery = searchParams.get("q") ?? "";
  const searchAction = locale === "en" ? "/en" : "/";

  function handleGoBack() {
    recordShellBack();
    startRouteProgress();
    router.back();
  }

  return (
    <ToastProvider>
      <RouteProgressBar routeKey={routeKey} />
      <div
        className="min-h-[100svh] bg-[var(--background)] text-[var(--foreground)] transition-[grid-template-columns] duration-200 ease-out md:grid md:h-screen md:overflow-hidden"
        style={{ gridTemplateColumns: sidebarCollapsed ? "80px minmax(0,1fr)" : "232px minmax(0,1fr)" }}
      >
        <aside className={cn(
          "hidden h-screen flex-col border-r border-[var(--border)] bg-[var(--surface-sidebar)] py-6 transition-[padding] duration-200 ease-out md:flex",
          sidebarCollapsed ? "px-3" : "px-5",
        )}>
          <div className={cn(
            "mb-6 flex overflow-hidden transition-[height,gap] duration-200 ease-out",
            sidebarCollapsed ? "h-[72px] flex-col items-center justify-start gap-3" : "h-10 flex-row items-center justify-between gap-2",
          )}>
            <Link href="/" className={cn("flex min-w-0 items-center rounded-[12px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]", sidebarCollapsed ? "justify-center" : null)} aria-label={t("brand")}>
              <BrandLockup collapsed={sidebarCollapsed} />
            </Link>
            <button
              type="button"
              aria-label={sidebarCollapsed ? t("expandSidebar") : t("collapseSidebar")}
              title={sidebarCollapsed ? t("expandSidebar") : t("collapseSidebar")}
              onClick={() => setSidebarCollapsed((value) => !value)}
              className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-[8px] bg-transparent text-[var(--muted-foreground)] transition-colors hover:bg-[var(--surface-control)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
            >
              {sidebarCollapsed ? <PanelLeftOpen className="h-[18px] w-[18px]" /> : <PanelLeftClose className="h-[18px] w-[18px]" />}
            </button>
          </div>

          <nav className="space-y-2 overflow-y-auto overflow-x-visible" aria-label={t("primaryNavigation")}>
            {visibleNavItems.map((item) => (
              <NavItemLink key={item.href} item={item} active={isActive(pathname, item.href)} collapsed={sidebarCollapsed} t={t} />
            ))}
          </nav>

          <div className={cn("mt-6", sidebarCollapsed ? "mx-auto w-10" : null)}>
            <PublishMenu collapsed={sidebarCollapsed} t={t} />
          </div>
          <div className="flex-1" />
        </aside>

        <div className="flex min-h-[100svh] flex-col md:h-screen md:min-h-0">
          <header className="sticky top-0 z-20 h-[69px] border-b border-[var(--border)] bg-[rgba(17,17,19,0.94)] backdrop-blur-md">
            <div className="flex h-full w-full items-center justify-between gap-3 px-4 sm:px-6">
              <div className="flex min-w-0 items-center gap-3">
                <BackButton visible={canGoBack} onBack={handleGoBack} label={t("back")} />
                <Link href="/" className="flex h-10 min-w-0 shrink-0 items-center justify-center rounded-[12px] md:hidden" aria-label={t("brand")}>
                  <BrandLockup mobile />
                </Link>
                <div className="relative hidden w-[min(520px,42vw)] md:block">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
                  <form action={searchAction} noValidate suppressHydrationWarning>
                    <input
                      name="q"
                      defaultValue={currentQuery}
                      className="mf-control-field h-10 w-full rounded-[12px] pl-10 pr-3"
                      placeholder={t("searchPlaceholder")}
                      suppressHydrationWarning
                    />
                  </form>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <a
                  href="https://github.com/whenrealizing/monopolyfun"
                  target="_blank"
                  rel="noreferrer"
                  aria-label={t("github")}
                  title={t("github")}
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] text-[var(--muted-foreground)] transition hover:bg-[var(--surface-hover)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
                >
                  <Github className="h-5 w-5" aria-hidden="true" />
                </a>
                <LocaleSwitcher />
                {session ? (
                  <UserMenu displayName={session.displayName} handle={session.handle} t={t} />
                ) : (
                  <Button asChild size="sm" variant="primary" className="h-10">
                    <Link href={authModalHref}>{t("login")}</Link>
                  </Button>
                )}
              </div>
            </div>
          </header>

          <main className="flex flex-1 flex-col px-4 pb-[calc(5rem+env(safe-area-inset-bottom))] pt-3 md:min-h-0 md:overflow-y-auto md:px-6 md:pb-4">
            <div className="flex min-h-full flex-1 flex-col">
              <div className="w-full flex-1">
                {shouldHideAuthRequiredChildren ? null : children}
              </div>
              <FooterLinks t={t} />
            </div>
          </main>
        </div>

        <nav className="fixed inset-x-0 bottom-0 z-40 grid h-[calc(64px+env(safe-area-inset-bottom))] grid-cols-5 grid-rows-[64px] border-t border-[var(--border)] bg-[rgba(17,17,19,0.96)] px-2 pb-[env(safe-area-inset-bottom)] backdrop-blur-md md:hidden">
          {mobileNavItems.slice(0, 2).map((item) => {
            const Icon = item.icon;
            const active = isActive(pathname, item.href);
            return (
              <NavLink key={item.href} href={item.href} authRequired={item.authRequired} className="flex min-w-0 items-center justify-center" ariaLabel={t(item.labelKey)}>
                <span className={cn("flex h-8 w-10 items-center justify-center rounded-[12px]", active ? "border border-[var(--primary-border)] bg-[var(--primary-soft)] text-[var(--primary)]" : "text-[var(--muted-foreground)]")}>
                  <Icon className="h-5 w-5" />
                </span>
              </NavLink>
            );
          })}
          <div className="relative flex items-center justify-center">
            <div className="absolute bottom-3">
              <PublishMenu collapsed floating menuAlign="center" t={t} />
            </div>
          </div>
          {mobileNavItems.slice(2).map((item) => {
            const Icon = item.icon;
            const active = isActive(pathname, item.href);
            return (
              <NavLink key={item.href} href={item.href} authRequired={item.authRequired} className="flex min-w-0 items-center justify-center" ariaLabel={t(item.labelKey)}>
                <span className={cn("flex h-8 w-10 items-center justify-center rounded-[12px]", active ? "border border-[var(--primary-border)] bg-[var(--primary-soft)] text-[var(--primary)]" : "text-[var(--muted-foreground)]")}>
                  <Icon className="h-5 w-5" />
                </span>
              </NavLink>
            );
          })}
        </nav>
      </div>
      <AuthModal
        mode={authMode ?? "login"}
        returnTo={authReturnTo}
        open={Boolean(authMode)}
        onClose={() => {
          startRouteProgress();
          router.replace(closeModalHref);
        }}
        onAuthenticated={(targetHref) => {
          startRouteProgress();
          router.push(targetHref);
          router.refresh();
        }}
      />
    </ToastProvider>
  );
}

function FooterLinks({ t }: { t: ShellTranslator }) {
  return (
    <footer className="mx-auto mt-4 flex w-full max-w-[1180px] flex-col items-center gap-2 px-1 py-2 text-[12px] leading-4 text-[var(--muted-foreground)] sm:mt-6 sm:flex-row sm:justify-between sm:py-4">
      <span>© monopolyfun 2026</span>
      <div className="flex flex-wrap items-center justify-center gap-x-4 gap-y-2">
        {utilityLinks.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className="rounded-[12px] transition-colors hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
          >
            {t(item.labelKey)}
          </Link>
        ))}
      </div>
    </footer>
  );
}

function UserMenu({
  displayName,
  handle,
  t,
}: {
  displayName: string;
  handle: string;
  t: ShellTranslator;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const [isPending, startTransition] = useTransition();
  const rootRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<number | null>(null);

  const requestClose = useCallback(() => {
    if (!open || closing) return;
    setClosing(true);
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    closeTimerRef.current = window.setTimeout(() => {
      setOpen(false);
      setClosing(false);
    }, 120);
  }, [closing, open]);

  useEffect(() => {
    if (!open) return undefined;

    function handlePointerDown(event: PointerEvent) {
      const target = event.target;
      if (target instanceof Node && rootRef.current?.contains(target)) return;
      requestClose();
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") requestClose();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, closing, requestClose]);

  useEffect(() => () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
  }, []);

  function handleLogout() {
    startTransition(async () => {
      try {
        await logoutAccount();
      } catch {
        // 本地清理必须继续执行，避免后端临时不可用时用户卡在登录态。
      }
      clearSession();
      requestClose();
      router.refresh();
    });
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        className="rounded-[12px] transition-colors hover:bg-[var(--surface-control-hover)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t("accountMenu")}
        title={t("accountMenu")}
        onClick={() => {
          if (open) {
            requestClose();
            return;
          }
          setOpen(true);
        }}
      >
        <SessionIdentityCard compact displayName={displayName} handle={handle} />
      </button>
      {open ? (
        <div className={cn("absolute right-0 top-[calc(100%+8px)] z-30 w-[190px]", closing ? "animate-popover-out" : "animate-popover-in")}>
          <div className="rounded-[12px] border border-[var(--border)] bg-[rgb(24,25,27)] p-1.5 shadow-[var(--shadow-md)]">
            <Link href="/profile/me" className="flex h-10 items-center gap-3 rounded-[12px] px-3 text-[14px] leading-5 text-[var(--foreground)] transition-colors hover:bg-[rgb(33,34,37)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]" onClick={requestClose}>
              <UserRound className="h-5 w-5 shrink-0 text-[var(--muted-foreground)]" />
              <span className="min-w-0 truncate">{t("nav.profile")}</span>
            </Link>
            <button
              type="button"
              disabled={isPending}
              className="flex h-10 w-full items-center gap-3 rounded-[12px] px-3 text-left text-[14px] leading-5 text-[var(--muted-foreground)] transition-colors hover:bg-[rgb(33,34,37)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)] disabled:opacity-60"
              onClick={handleLogout}
            >
              <LogOut className="h-5 w-5 shrink-0" />
              <span className="min-w-0 truncate">{t("logout")}</span>
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function NavItemLink({
  item,
  active,
  collapsed = false,
  t,
}: {
  item: (typeof navItems)[number] | typeof backofficeNavItem;
  active: boolean;
  collapsed?: boolean;
  t: ShellTranslator;
}) {
  const Icon = item.icon;
  return (
    <NavLink
      href={item.href}
      authRequired={item.authRequired}
      className={cn(
        "flex h-10 items-center rounded-[12px] text-sm transition-[background-color,color,padding,gap] duration-200 ease-out",
        collapsed ? "mx-auto w-10 justify-center gap-0 px-0" : "w-full justify-start gap-3 px-3",
        active
          ? "bg-[var(--surface-selected)] text-[var(--foreground)]"
          : "text-[var(--muted-foreground)] hover:bg-[var(--surface-hover)] hover:text-[var(--foreground)]",
      )}
    >
      <Icon className={cn("h-5 w-5 stroke-[2.4]", active ? "text-[var(--primary)]" : "text-current")} />
      <span className={cn("min-w-0 overflow-hidden transition-[max-width,opacity] duration-200 ease-out", collapsed ? "max-w-0 opacity-0" : "max-w-[150px] opacity-100")}>
        <span className="block truncate">{t(item.labelKey)}</span>
      </span>
    </NavLink>
  );
}

function NavLink({
  href,
  authRequired,
  className,
  ariaLabel,
  children,
}: {
  href: string;
  authRequired: boolean;
  className: string;
  ariaLabel?: string;
  children: ReactNode;
}) {
  if (authRequired) {
    return (
      <AuthRequiredLink href={href} className={className} aria-label={ariaLabel} title={ariaLabel}>
        {children}
      </AuthRequiredLink>
    );
  }
  return (
    <Link href={href} className={className} aria-label={ariaLabel} title={ariaLabel}>
      {children}
    </Link>
  );
}

function saveRecoveredSession(session: Awaited<ReturnType<typeof getCurrentAccount>>) {
  saveSession({
    accountId: session.account.id,
    displayName: session.account.displayName,
    handle: session.account.handle,
    expiresAt: session.expiresAt,
  });
}

function isAuthExpired(error: unknown) {
  return error instanceof ApiRequestError && (error.status === 401 || error.status === 403);
}
