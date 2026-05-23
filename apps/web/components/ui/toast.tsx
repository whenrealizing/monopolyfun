"use client";

import {type ReactNode, useCallback, useMemo} from "react";
import {toast as sonnerToast, Toaster} from "sonner";

import {presentError} from "@/lib/error-messages";

type ToastTone = "info" | "success" | "error";
type ToastItem = {
  tone: ToastTone;
  title: string;
  description?: string;
};
type ToastContextValue = {
  notify: (toast: ToastItem) => void;
  notifyError: (error: unknown, fallbackCode?: string) => void;
};

const recentToastKeys = new Map<string, number>();
const TOAST_DEDUPE_MS = 900;

export function ToastProvider({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <Toaster
        className="mf-toaster"
        position="top-center"
        visibleToasts={4}
        duration={6000}
        toastOptions={{
          classNames: {
            toast: "mf-toast",
            title: "mf-toast-title",
            description: "mf-toast-description",
            closeButton: "mf-toast-close",
            icon: "mf-toast-icon",
          },
        }}
      />
    </>
  );
}

export function useToast(): ToastContextValue {
  const notify = useCallback((toast: ToastItem) => {
    const key = `${toast.tone}:${toast.title}:${toast.description ?? ""}`;
    const now = Date.now();
    const lastShownAt = recentToastKeys.get(key) ?? 0;
    if (now - lastShownAt < TOAST_DEDUPE_MS) return;
    recentToastKeys.set(key, now);

    const options = {
      description: toast.description,
    };

    if (toast.tone === "success") {
      sonnerToast.success(toast.title, options);
      return;
    }
    if (toast.tone === "error") {
      sonnerToast.error(toast.title, options);
      return;
    }
    sonnerToast.info(toast.title, options);
  }, []);

  const notifyError = useCallback((error: unknown, fallbackCode = "common.action.failed") => {
    const presented = presentError(error, fallbackCode);
    notify({
      tone: "error",
      title: presented.message,
    });
  }, [notify]);

  return useMemo(() => ({ notify, notifyError }), [notify, notifyError]);
}
