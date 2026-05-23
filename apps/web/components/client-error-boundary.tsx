"use client";

import type { ErrorInfo, ReactNode } from "react";
import { Component } from "react";
import { usePathname } from "next/navigation";

type ClientErrorBoundaryProps = {
  children: ReactNode;
  resetKey: string;
};

type ClientErrorBoundaryState = {
  error: Error | null;
};

class ClientErrorBoundaryRoot extends Component<ClientErrorBoundaryProps, ClientErrorBoundaryState> {
  state: ClientErrorBoundaryState = { error: null };

  private handleWindowError = (event: ErrorEvent) => {
    console.error("Unhandled window error", event.error ?? event.message);
  };

  private handleUnhandledRejection = (event: PromiseRejectionEvent) => {
    console.error("Unhandled promise rejection", event.reason);
  };

  static getDerivedStateFromError(error: Error): ClientErrorBoundaryState {
    return { error };
  }

  componentDidMount() {
    window.addEventListener("error", this.handleWindowError);
    window.addEventListener("unhandledrejection", this.handleUnhandledRejection);
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("React render error", error, errorInfo);
  }

  componentDidUpdate(previousProps: ClientErrorBoundaryProps) {
    if (this.state.error && previousProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  componentWillUnmount() {
    window.removeEventListener("error", this.handleWindowError);
    window.removeEventListener("unhandledrejection", this.handleUnhandledRejection);
  }

  render() {
    if (this.state.error) {
      return (
        <main className="min-h-screen bg-[var(--background)] px-4 py-10 text-[var(--foreground)]">
          <section className="mx-auto max-w-xl rounded-[12px] border border-[var(--border)] bg-[var(--surface-1)] p-6">
            <h1 className="text-xl font-semibold">页面出现错误，请刷新重试</h1>
            <p className="mt-3 text-sm leading-6 text-[var(--muted-foreground)]">
              当前页面渲染失败，错误信息已经写入浏览器控制台。
            </p>
            <button
              type="button"
              className="mt-5 inline-flex h-10 items-center justify-center rounded-[12px] border border-[var(--primary)] bg-[var(--primary)] px-4 text-sm font-semibold text-[var(--primary-foreground)]"
              onClick={() => window.location.reload()}
            >
              刷新页面
            </button>
          </section>
        </main>
      );
    }

    return this.props.children;
  }
}

export function ClientErrorBoundary({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return <ClientErrorBoundaryRoot resetKey={pathname}>{children}</ClientErrorBoundaryRoot>;
}
