"use client";

import dynamic from "next/dynamic";

import { PublishSkeleton } from "@/components/page-skeletons";
import type { PublishType } from "@/components/publish-shared";

const PublishWorkspace = dynamic(
  () => import("@/components/publish-workspace").then((module) => module.PublishWorkspace),
  {
    loading: () => <PublishSkeleton />,
  },
);

export function PublishWorkspaceLoader({ initialType }: { initialType: PublishType }) {
  // 中文注释：发布工作台表单字段多且依赖客户端状态，按路由懒加载减少冷首屏模块体积。
  return <PublishWorkspace initialType={initialType} />;
}
