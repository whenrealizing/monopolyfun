"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useState } from "react";

export function AppProviders({ children }: Readonly<{ children: ReactNode }>) {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
      },
    },
  }));
  // 中文注释：全局 QueryClient 先承接 Orval fetch 迁移，后续页面可以逐步替换手写 loading/cache 状态。
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
