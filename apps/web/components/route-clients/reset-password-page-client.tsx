"use client";

import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";

import { ClientOnlyMount } from "@/components/client-only-mount";
import { AuthSkeleton } from "@/components/page-skeletons";
import { ResetPasswordForm } from "@/components/reset-password-form";

export function ResetPasswordPageClient() {
  const t = useTranslations("ResetPassword.page");
  const searchParams = useSearchParams();

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[var(--background)] px-4 py-10">
      <div className="relative w-full max-w-[520px] space-y-4">
        <ClientOnlyMount fallback={<AuthSkeleton />}>
          <div className="overflow-hidden rounded-[6px] bg-[var(--panel)]">
            <div className="px-6 py-5 sm:px-8 sm:py-6">
              <div className="text-2xl font-semibold tracking-tight text-[var(--foreground)]">{t("title")}</div>
              <div className="mt-1 text-sm text-[var(--muted-foreground)]">
                {t("description")}
              </div>
            </div>
            <div className="p-6 pt-0 sm:p-8 sm:pt-0">
              <ResetPasswordForm
                presetHandle={searchParams.get("handle") ?? ""}
                presetToken={searchParams.get("token") ?? ""}
              />
            </div>
          </div>
        </ClientOnlyMount>
      </div>
    </main>
  );
}
