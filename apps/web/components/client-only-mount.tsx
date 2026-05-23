"use client";

import { type ReactNode, useSyncExternalStore } from "react";

const subscribeClientMount = () => () => {};
const getMountedSnapshot = () => true;
const getServerSnapshot = () => false;

export function ClientOnlyMount({
  children,
  fallback = null,
}: {
  children: ReactNode;
  fallback?: ReactNode;
}) {
  // 中文注释：用 hydration-aware snapshot 表达客户端挂载状态，避免 effect 内同步 setState 触发额外渲染。
  const mounted = useSyncExternalStore(subscribeClientMount, getMountedSnapshot, getServerSnapshot);

  return mounted ? <>{children}</> : <>{fallback}</>;
}
