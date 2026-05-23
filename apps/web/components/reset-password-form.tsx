"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { User } from "lucide-react";

import { Button } from "@/components/ui/button";
import { confirmPasswordReset, requestPasswordReset } from "@/lib/api";
import { persistAuthSession } from "@/lib/auth-session";
import { presentError } from "@/lib/error-messages";

type ResetFieldErrors = {
  handle?: string;
  resetToken?: string;
  newPassword?: string;
};
type ResetFormErrorKey =
  | "errors.handleRequired"
  | "errors.handleLength"
  | "errors.resetTokenRequired"
  | "errors.resetTokenLength"
  | "errors.newPasswordRequired"
  | "errors.newPasswordLength";
type ResetFormErrorTranslator = (key: ResetFormErrorKey) => string;

export function ResetPasswordForm({
  presetHandle = "",
  presetToken = "",
}: {
  presetHandle?: string;
  presetToken?: string;
}) {
  const router = useRouter();
  const [handle, setHandle] = useState(presetHandle);
  const [resetToken, setResetToken] = useState(presetToken);
  const [newPassword, setNewPassword] = useState("");
  const [pendingRequest, setPendingRequest] = useState(false);
  const [pendingConfirm, setPendingConfirm] = useState(false);
  const [requestStatus, setRequestStatus] = useState<string | null>(presetToken ? "已带入管理员发放 token" : null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ResetFieldErrors>({});

  async function issueResetToken() {
    const normalizedHandle = normalizeHandle(handle);
    const handleError = validateHandle(normalizedHandle, resetErrorText);
    if (handleError) {
      setFieldErrors((current) => ({ ...current, handle: handleError }));
      setError(null);
      return;
    }

    setError(null);
    setFieldErrors((current) => ({ ...current, handle: undefined }));
    setPendingRequest(true);
    try {
      const result = await requestPasswordReset(normalizedHandle);
      setRequestStatus(`请求时间：${result.requestedAt}`);
      setResetToken("");
    } catch (caught) {
      const presented = presentError(caught, "ui.password_reset.request.failed");
      const nextServerFieldErrors = normalizeFieldErrors(presented.fieldErrors);
      setFieldErrors((current) => ({ ...current, ...nextServerFieldErrors }));
      setError(nextServerFieldErrors.handle ? null : presented.message);
    } finally {
      setPendingRequest(false);
    }
  }

  async function confirmReset() {
    const normalizedResetToken = resetToken.trim();
    const nextFieldErrors = validateResetInput({ resetToken: normalizedResetToken, newPassword, t: resetErrorText });
    if (nextFieldErrors.resetToken || nextFieldErrors.newPassword) {
      setFieldErrors((current) => ({ ...current, ...nextFieldErrors }));
      setError(null);
      return;
    }

    setError(null);
    setFieldErrors((current) => ({ ...current, resetToken: undefined, newPassword: undefined }));
    setPendingConfirm(true);
    try {
      const session = await confirmPasswordReset({ resetToken: normalizedResetToken, newPassword });
      persistAuthSession(session);
      router.push("/market");
      router.refresh();
    } catch (caught) {
      const presented = presentError(caught, "ui.password_reset.confirm.failed");
      const nextServerFieldErrors = normalizeFieldErrors(presented.fieldErrors);
      setFieldErrors((current) => ({ ...current, ...nextServerFieldErrors }));
      setError(hasConfirmFieldErrors(nextServerFieldErrors) ? null : presented.message);
    } finally {
      setPendingConfirm(false);
    }
  }

  return (
    <div className="grid gap-5">
      <form
        className="grid gap-3 rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] p-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void issueResetToken();
        }}
      >
        <div className="text-sm font-black text-[var(--foreground)]">1. 请求重置凭证</div>
        <Field label="账号名" error={fieldErrors.handle}>
          <div className="relative">
            <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
            <input
              className="mf-control-field w-full pl-10 pr-3"
              value={handle}
              onChange={(event) => {
                setHandle(event.target.value);
                setError(null);
                setFieldErrors((current) => ({ ...current, handle: undefined }));
              }}
              placeholder="输入账号名"
              autoComplete="username"
              aria-invalid={Boolean(fieldErrors.handle)}
            />
          </div>
        </Field>
        <Button type="submit" variant="outline" loading={pendingRequest} disabled={pendingRequest}>
          提交重置请求
        </Button>
        {requestStatus ? (
          <div className="rounded-[8px] border border-[rgba(72,230,174,0.28)] bg-[rgba(72,230,174,0.1)] px-3 py-3 text-sm">
            <div className="font-black text-[var(--foreground)]">重置请求已登记</div>
            <div className="mt-2 break-all font-mono text-xs text-[var(--foreground)]">{requestStatus}</div>
          </div>
        ) : null}
      </form>

      <form
        className="grid gap-3 rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] p-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void confirmReset();
        }}
      >
        <div className="text-sm font-black text-[var(--foreground)]">2. 使用管理员发放 token 设置新密码</div>
        <Field label="Reset Token" error={fieldErrors.resetToken}>
          <textarea
            className="mf-control-field min-h-24 w-full px-3 py-3"
            value={resetToken}
            onChange={(event) => {
              setResetToken(event.target.value);
              setError(null);
              setFieldErrors((current) => ({ ...current, resetToken: undefined }));
            }}
            placeholder="粘贴刚刚生成的 token"
            aria-invalid={Boolean(fieldErrors.resetToken)}
          />
        </Field>
        <Field label="New Password" error={fieldErrors.newPassword}>
          <input
            className="mf-control-field w-full px-3"
            type="password"
            value={newPassword}
            onChange={(event) => {
              setNewPassword(event.target.value);
              setError(null);
              setFieldErrors((current) => ({ ...current, newPassword: undefined }));
            }}
            placeholder="至少 8 位"
            autoComplete="new-password"
            aria-invalid={Boolean(fieldErrors.newPassword)}
          />
        </Field>
        <Button type="submit" variant="primary" loading={pendingConfirm} disabled={pendingConfirm}>
          重置并登录
        </Button>
      </form>

      {error ? <div className="rounded-[8px] border border-[rgba(245,98,98,0.28)] bg-[rgba(245,98,98,0.1)] px-3 py-2 text-sm text-[var(--foreground)]">{error}</div> : null}
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-2">
      <span className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">{label}</span>
      {children}
      {error ? <p className="text-sm text-[rgb(255,140,140)]">{error}</p> : null}
    </label>
  );
}

function normalizeHandle(value: string) {
  return value.trim().replace(/^@+/, "");
}

function resetErrorText(key: ResetFormErrorKey) {
  const messages: Record<ResetFormErrorKey, string> = {
    "errors.handleRequired": "请输入账号名。",
    "errors.handleLength": "账号名至少 3 个字符。",
    "errors.resetTokenRequired": "请输入 reset token。",
    "errors.resetTokenLength": "reset token 长度不正确。",
    "errors.newPasswordRequired": "请输入新密码。",
    "errors.newPasswordLength": "新密码至少 8 位。",
  };
  return messages[key];
}

function validateHandle(handle: string, t: ResetFormErrorTranslator) {
  if (!handle) {
    return t("errors.handleRequired");
  }
  if (handle.length < 3) {
    return t("errors.handleLength");
  }
  return undefined;
}

function validateResetInput({
  resetToken,
  newPassword,
  t,
}: {
  resetToken: string;
  newPassword: string;
  t: ResetFormErrorTranslator;
}): ResetFieldErrors {
  const errors: ResetFieldErrors = {};

  if (!resetToken) {
    errors.resetToken = t("errors.resetTokenRequired");
  } else if (resetToken.length < 16) {
    errors.resetToken = t("errors.resetTokenLength");
  }

  if (!newPassword) {
    errors.newPassword = t("errors.newPasswordRequired");
  } else if (newPassword.trim().length < 8) {
    errors.newPassword = t("errors.newPasswordLength");
  }

  return errors;
}

function normalizeFieldErrors(fields: Record<string, string>): ResetFieldErrors {
  return {
    handle: fields.handle,
    resetToken: fields.resetToken ?? fields.token,
    newPassword: fields.newPassword ?? fields.password,
  };
}

function hasConfirmFieldErrors(errors: ResetFieldErrors) {
  return Boolean(errors.resetToken || errors.newPassword);
}
