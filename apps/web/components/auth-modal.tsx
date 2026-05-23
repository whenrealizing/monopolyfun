"use client";

import { createPortal } from "react-dom";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";

import type { AuthModalMode } from "@/lib/auth-modal-route";
import { AuthAccessSurface } from "@/components/auth-access-surface";

export function AuthModal({
  mode,
  returnTo,
  open,
  onClose,
  onAuthenticated,
}: {
  mode: AuthModalMode;
  returnTo?: string | null;
  open: boolean;
  onClose: () => void;
  onAuthenticated: (targetHref: string) => void;
}) {
  const mounted = useSyncExternalStore(subscribeClientMount, getClientMountSnapshot, getServerMountSnapshot);
  const [closing, setClosing] = useState(false);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const t = useTranslations("Auth");

  const requestClose = useCallback(() => {
    if (closing) {
      return;
    }
    setClosing(true);
    closeTimerRef.current = setTimeout(() => {
      closeTimerRef.current = null;
      onClose();
    }, 140);
  }, [closing, onClose]);

  useEffect(() => {
    if (open) {
      queueMicrotask(() => setClosing(false));
    }

    return () => {
      if (closeTimerRef.current) {
        clearTimeout(closeTimerRef.current);
        closeTimerRef.current = null;
      }
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        requestClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, requestClose]);

  if (!mounted || typeof document === "undefined" || !open) {
    return null;
  }

  return createPortal(
    <div className={closing ? "fixed inset-0 z-[160] animate-overlay-out bg-black/45 backdrop-blur-[2px]" : "fixed inset-0 z-[160] animate-overlay-in bg-black/45 backdrop-blur-[2px]"}>
      <div
        className="flex h-full w-full items-center justify-center p-5 sm:p-6"
        role="dialog"
        aria-modal="true"
        aria-label={t("modalLabel")}
        onClick={requestClose}
      >
        <div
          className={`w-[min(356px,calc(100vw-40px))] ${closing ? "animate-auth-modal-out" : "animate-auth-modal-in"}`}
          onClick={(event) => event.stopPropagation()}
        >
          <AuthAccessSurface
            key={mode}
            initialMode={mode}
            returnTo={returnTo ?? "/"}
            onAuthenticated={onAuthenticated}
            className="max-h-[min(90vh,680px)] overflow-y-auto overscroll-contain"
          />
        </div>
      </div>
    </div>,
    document.body,
  );
}

function subscribeClientMount(callback: () => void) {
  // 中文注释：弹窗挂到 document.body，等浏览器完成首轮挂载后再开放 portal 渲染，避免 hydration 结构差异。
  const timer = window.setTimeout(callback, 0);
  return () => window.clearTimeout(timer);
}

function getClientMountSnapshot() {
  return typeof document !== "undefined";
}

function getServerMountSnapshot() {
  return false;
}
