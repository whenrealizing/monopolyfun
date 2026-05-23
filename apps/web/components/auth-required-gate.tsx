"use client";

import type { ComponentProps, MouseEvent } from "react";
import { forwardRef, useCallback, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "next/navigation";

import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { buildAuthModalHref, buildPathWithSearch, stripAuthModalSearchParams } from "@/lib/auth-modal-route";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";

export function useAuthGate() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const currentHref = useMemo(
    () => buildPathWithSearch(pathname, stripAuthModalSearchParams(searchParams)),
    [pathname, searchParams],
  );
  const buildLoginHref = useCallback(
    (returnTo = currentHref) => buildAuthModalHref({
      pathname,
      searchParams,
      mode: "login",
      returnTo,
    }),
    [currentHref, pathname, searchParams],
  );
  const openLogin = useCallback((returnTo = currentHref) => {
    // 中文注释：所有登录门禁都改为打开同一个 URL 驱动弹窗，登录成功后回到触发动作的目标页。
    router.push(buildLoginHref(returnTo));
  }, [buildLoginHref, currentHref, router]);
  const requireSession = useCallback((returnTo = currentHref) => {
    if (session?.accountId) {
      return session;
    }

    openLogin(returnTo);
    return null;
  }, [currentHref, openLogin, session]);

  return {
    session,
    currentHref,
    buildLoginHref,
    openLogin,
    requireSession,
  };
}

type AuthRequiredLinkProps = Omit<ComponentProps<typeof Link>, "href"> & {
  href: string;
  returnTo?: string;
};

export const AuthRequiredLink = forwardRef<HTMLAnchorElement, AuthRequiredLinkProps>(function AuthRequiredLink({
  href,
  returnTo,
  onClick,
  ...props
}, ref) {
  const { session, openLogin } = useAuthGate();

  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (event.defaultPrevented || session?.accountId) {
      return;
    }

    event.preventDefault();
    openLogin(returnTo ?? href);
  }

  return <Link ref={ref} href={href} onClick={handleClick} {...props} />;
});
