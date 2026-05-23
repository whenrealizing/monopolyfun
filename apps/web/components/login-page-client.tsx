"use client";

import { useEffect, useSyncExternalStore } from "react";

import { AuthAccessSurface } from "@/components/auth-access-surface";
import { useRouter } from "@/i18n/navigation";
import type { AuthModalMode } from "@/lib/auth-modal-route";
import { getCurrentAccount } from "@/lib/api";
import { persistAuthSession } from "@/lib/auth-session";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";

export function LoginPageClient({
  mode,
  returnTo,
}: {
  mode: AuthModalMode;
  returnTo: string;
}) {
  const router = useRouter();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);

  useEffect(() => {
    if (session?.accountId) {
      return;
    }

    // 中文注释：独立登录页不经过 Shell，进入时也要尝试用 HttpOnly Cookie 恢复前端登录态。
    void getCurrentAccount()
      .then((nextSession) => {
        persistAuthSession(nextSession);
        router.replace(returnTo);
      })
      .catch(() => {});
  }, [returnTo, router, session?.accountId]);

  useEffect(() => {
    if (!session?.accountId) {
      return;
    }

    // 中文注释：登录页也遵守同一登录态优先级，已登录用户直接回到目标页。
    router.replace(returnTo);
  }, [returnTo, router, session?.accountId]);

  if (session?.accountId) {
    return null;
  }

  return <AuthAccessSurface initialMode={mode} returnTo={returnTo} />;
}
