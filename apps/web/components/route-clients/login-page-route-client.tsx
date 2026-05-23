"use client";

import { useSearchParams } from "next/navigation";

import { ClientOnlyMount } from "@/components/client-only-mount";
import { LoginPageClient } from "@/components/login-page-client";
import { AuthSkeleton } from "@/components/page-skeletons";
import { parseAuthModalMode, sanitizeAuthReturnTo } from "@/lib/auth-modal-route";

export function LoginPageRouteClient() {
  const searchParams = useSearchParams();
  const routeMode = searchParams.get("auth") ?? searchParams.get("mode");
  const returnTo = sanitizeAuthReturnTo(searchParams.get("returnTo")) ?? "/";
  const mode = parseAuthModalMode(routeMode) ?? "login";

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[var(--background)] px-4 py-10">
      <div className="relative w-full max-w-[460px]">
        <ClientOnlyMount fallback={<AuthSkeleton />}>
          <LoginPageClient mode={mode} returnTo={returnTo} />
        </ClientOnlyMount>
      </div>
    </main>
  );
}
