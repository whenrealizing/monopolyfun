"use client";

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { getCurrentAccount } from "@/lib/api";
import { persistAuthSession } from "@/lib/auth-session";
import { sanitizeAuthReturnTo } from "@/lib/auth-modal-route";

export function OAuthCallbackClient() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    // 中文注释：OAuth 回跳目标只接受站内路径，避免外部 URL 混入登录完成跳转。
    const returnTo = sanitizeAuthReturnTo(searchParams.get("returnTo")) ?? "/market";

    void getCurrentAccount()
      .then((session) => {
        persistAuthSession(session);
        router.replace(returnTo);
        router.refresh();
      })
      .catch(() => router.replace("/login?mode=login"));
  }, [router, searchParams]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-[var(--background)] px-4 text-[var(--foreground)]">
      <div className="rounded-[6px] bg-[var(--panel)] px-6 py-5 text-sm text-[var(--muted-foreground)]">
        正在完成 GitHub OAuth 登录...
      </div>
    </main>
  );
}
