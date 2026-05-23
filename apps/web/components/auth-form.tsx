"use client";

import {type CSSProperties, useState} from "react";
import {useRouter} from "next/navigation";
import {useTranslations} from "next-intl";
import {Eye, EyeOff, LockKeyhole, User} from "lucide-react";

import {loginAccount, registerAccount} from "@/lib/api";
import {persistAuthSession} from "@/lib/auth-session";
import {presentError, resolveErrorMessage} from "@/lib/error-messages";
import {cn} from "@/lib/utils";
import {Button} from "@/components/ui/button";
import {useToast} from "@/components/ui/toast";

type AuthFieldErrors = {
  handle?: string;
  password?: string;
};

const HANDLE_MIN_LENGTH = 3;
const HANDLE_MAX_LENGTH = 20;
const HANDLE_PATTERN = "^[a-zA-Z0-9_-]+$";
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 120;

export function AuthForm({
  returnTo = "/market",
  mode = "login",
  onAuthenticated,
}: {
  returnTo?: string;
  mode?: "login" | "register";
  onAuthenticated?: (targetHref: string) => void;
}) {
  const router = useRouter();
  const t = useTranslations("Auth");
  const toast = useToast();
  const [pending, setPending] = useState(false);
  const [handle, setHandle] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});
  const [focusedField, setFocusedField] = useState<"handle" | "password" | null>(null);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const normalizedHandle = normalizeHandle(handle);
  const clientFieldErrors = validateAuthInput({ mode, handle: normalizedHandle, password });
  const submitDisabled = pending || Boolean(clientFieldErrors.handle || clientFieldErrors.password);

  async function submit() {
    const nextFieldErrors = validateAuthInput({
      mode,
      handle: normalizedHandle,
      password,
    });
    if (nextFieldErrors.handle || nextFieldErrors.password) {
      setFieldErrors(nextFieldErrors);
      setError(null);
      return;
    }
    setPending(true);
    setError(null);
    setFieldErrors({});

    try {
      const session = mode === "register"
        ? await registerAccount({
            handle: normalizedHandle,
            password,
          })
        : await loginAccount({
            handle: normalizedHandle,
            password,
          });
      persistAuthSession(session);
      toast.notify({ tone: "success", title: t(mode === "register" ? "registerSuccess" : "loginSuccess") });
      if (onAuthenticated) {
        onAuthenticated(returnTo);
      } else {
        router.push(returnTo);
        router.refresh();
      }
    } catch (submissionError) {
      const presented = presentError(submissionError, "ui.auth.failed");
      const nextServerFieldErrors = normalizeFieldErrors(presented.fieldErrors);
      setFieldErrors(nextServerFieldErrors);
      setError(hasFieldErrors(nextServerFieldErrors) ? null : presented.message);
    } finally {
      setPending(false);
    }
  }

  return (
    <form
      className="grid gap-3"
      autoComplete="off"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <label className="grid gap-1.5">
        <span className="text-[12px] leading-4 text-[var(--muted-foreground)]">账号名</span>
        <div className="relative">
          <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <input
            className="mf-control-field w-full pl-10 pr-3"
            style={{ backgroundColor: "rgb(27, 29, 39)", borderColor: focusedField === "handle" ? "var(--ring)" : "rgb(97, 97, 97)", boxShadow: "none" }}
            type="text"
            value={handle}
            onFocus={() => setFocusedField("handle")}
            onBlur={() => setFocusedField(null)}
            onChange={(event) => {
              setHandle(event.target.value);
              setError(null);
              setFieldErrors((current) => ({ ...current, handle: undefined }));
            }}
            name="monopolyfun-handle"
            placeholder="输入账号名"
            autoComplete="off"
            autoCapitalize="none"
            autoCorrect="off"
            data-1p-ignore="true"
            data-lpignore="true"
            spellCheck={false}
            minLength={HANDLE_MIN_LENGTH}
            maxLength={HANDLE_MAX_LENGTH}
            pattern={HANDLE_PATTERN}
            required
          />
        </div>
        <p className="min-h-3 text-[11px] leading-3 text-[var(--danger-foreground)]">{fieldErrors.handle ?? ""}</p>
      </label>

      <label className="grid gap-1.5">
        <span className="text-[12px] leading-4 text-[var(--muted-foreground)]">密码</span>
        <div className="relative">
          <LockKeyhole className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <input
            className="mf-control-field w-full pl-10 pr-11"
            style={{
              backgroundColor: "rgb(27, 29, 39)",
              borderColor: focusedField === "password" ? "var(--ring)" : "rgb(97, 97, 97)",
              boxShadow: "none",
              WebkitTextSecurity: passwordVisible ? "none" : "disc",
            } as CSSProperties & { WebkitTextSecurity?: string }}
            type="text"
            value={password}
            onFocus={() => setFocusedField("password")}
            onBlur={() => setFocusedField(null)}
            onChange={(event) => {
              setPassword(event.target.value);
              setError(null);
              setFieldErrors((current) => ({ ...current, password: undefined }));
            }}
            name={mode === "login" ? "monopolyfun-password" : "monopolyfun-new-password"}
            placeholder={mode === "login" ? "输入密码" : "设置密码"}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            autoCapitalize="none"
            autoCorrect="off"
            data-1p-ignore="true"
            data-lpignore="true"
            spellCheck={false}
            minLength={mode === "register" ? PASSWORD_MIN_LENGTH : 1}
            maxLength={PASSWORD_MAX_LENGTH}
            required
          />
          <button
            type="button"
            className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-[10px] text-[var(--muted-foreground)] transition hover:bg-[rgb(30,31,33)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => setPasswordVisible((visible) => !visible)}
            aria-label={passwordVisible ? "隐藏密码" : "显示密码"}
            title={passwordVisible ? "隐藏密码" : "显示密码"}
          >
            {passwordVisible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
        <p className={cn("min-h-3 text-[11px] leading-3", fieldErrors.password ? "text-[var(--danger-foreground)]" : "text-[var(--muted-foreground)]")}>
          {fieldErrors.password ?? (mode === "register" ? `密码至少 ${PASSWORD_MIN_LENGTH} 个字符。` : "")}
        </p>
      </label>

      {error ? (
        <div className="rounded-[12px] border border-[rgba(213,84,63,0.42)] bg-[var(--danger-bg)] px-3 py-2 text-[12px] leading-4 text-[var(--danger-foreground)]">
          {error}
        </div>
      ) : null}

      <Button className="mt-1 w-full shadow-[var(--shadow-sm)]" type="submit" variant="primary" loading={pending} disabled={submitDisabled}>
        {mode === "register" ? "注册" : "登录"}
      </Button>
    </form>
  );
}

function normalizeHandle(value: string) {
  return value.trim().replace(/^@+/, "");
}

function validateAuthInput(input: {
  mode: "login" | "register";
  handle: string;
  password: string;
}): AuthFieldErrors {
  const errors: AuthFieldErrors = {};

  if (!input.handle.trim()) {
    errors.handle = resolveErrorMessage("auth.handle.required");
  } else if (input.handle.length < HANDLE_MIN_LENGTH || input.handle.length > HANDLE_MAX_LENGTH) {
    errors.handle = resolveErrorMessage("auth.handle.invalid_length");
  } else if (!new RegExp(HANDLE_PATTERN).test(input.handle)) {
    errors.handle = resolveErrorMessage("auth.handle.invalid_pattern");
  }

  if (!input.password) {
    errors.password = resolveErrorMessage("auth.password.required");
  } else if (input.mode === "register" && (input.password.length < PASSWORD_MIN_LENGTH || input.password.length > PASSWORD_MAX_LENGTH)) {
    errors.password = resolveErrorMessage("auth.password.invalid_length");
  }

  return errors;
}

function normalizeFieldErrors(fields: Record<string, string>): AuthFieldErrors {
  return {
    handle: fields.handle,
    password: fields.password,
  };
}

function hasFieldErrors(errors: AuthFieldErrors) {
  return Boolean(errors.handle || errors.password);
}
