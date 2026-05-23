"use client";

import { useState } from "react";
import Image from "next/image";
import { X } from "lucide-react";

import type { AuthModalMode } from "@/lib/auth-modal-route";
import { AuthForm } from "@/components/auth-form";
import { cn } from "@/lib/utils";

export function AuthAccessSurface({
  initialMode = "login",
  returnTo = "/market",
  className,
  onDismiss,
  onAuthenticated,
}: {
  initialMode?: AuthModalMode;
  returnTo?: string;
  className?: string;
  onDismiss?: () => void;
  onAuthenticated?: (targetHref: string) => void;
}) {
  const [mode, setMode] = useState<AuthModalMode>(initialMode);

  return (
    <div className={cn("relative isolate mx-auto w-full max-w-[420px] overflow-hidden rounded-[6px] bg-[var(--panel)]", className)}>
      {onDismiss ? (
        <button
          type="button"
          aria-label="关闭登录弹窗"
          onClick={onDismiss}
          className="absolute right-4 top-4 z-10 flex h-8 w-8 items-center justify-center rounded-full text-[var(--foreground)] transition-colors hover:bg-white/6"
        >
          <X className="h-4 w-4" />
        </button>
      ) : null}

      <div className="px-6 py-5">
        <div className="grid justify-items-center gap-3 pb-5 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-[6px] bg-[var(--surface-1)]">
            {/* 中文注释：登录入口用品牌图形建立上下文，表单逻辑仍由 AuthForm 统一处理。 */}
            <Image src="/brand/openmonopoly-mark.png" alt="" aria-hidden="true" width={48} height={48} className="h-12 w-12" />
          </span>
          <span className="block w-[154px] overflow-hidden">
            <Image src="/brand/monopoly-fun-wordmark.svg?v=20260523d" alt="monopolyfun" width={154} height={31} loading="eager" className="max-w-none" style={{ width: 154, height: "auto" }} />
          </span>
        </div>

        <div className="flex border-b border-[rgba(97,97,97,0.72)]">
          <AuthModeButton active={mode === "login"} onClick={() => setMode("login")}>
            登录
          </AuthModeButton>
          <AuthModeButton active={mode === "register"} onClick={() => setMode("register")}>
            注册
          </AuthModeButton>
        </div>

        <div className="mt-5">
          <AuthForm returnTo={returnTo} mode={mode} onAuthenticated={onAuthenticated} />
        </div>
      </div>
    </div>
  );
}

function AuthModeButton({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "relative flex-1 pb-3 text-[14px] font-medium leading-5 transition-colors focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50",
        active
          ? "text-[var(--foreground)]"
          : "text-[var(--muted-foreground)] hover:text-[var(--foreground)]",
      )}
    >
      {children}
      {active ? <span className="absolute inset-x-0 bottom-[-1px] h-[3px] rounded-full bg-[var(--primary)]" /> : null}
    </button>
  );
}
