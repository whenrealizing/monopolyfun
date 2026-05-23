"use client";

import { useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";

import { PostItemWorkspacePanel } from "@/components/post-item-workspace-panel";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";
import type { PostItem } from "@/lib/api";

export function RequestOwnerWorkspacePanel({
  postId,
  postStatus,
  ownerHandle,
  returnTo,
  initialItems,
}: {
  postId: string;
  postStatus: string;
  ownerHandle?: string | null;
  returnTo: string;
  initialItems: PostItem[];
}) {
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const t = useTranslations("RequestDetail.ownerWorkspace");

  // 中文注释：公开 Request 只暴露 owner handle，发布维护区据此判断当前会话是否为发布人。
  if (!session?.handle || !ownerHandle || normalizeHandle(session.handle) !== normalizeHandle(ownerHandle)) {
    return null;
  }

  return (
    <section className="bg-[var(--background)] p-4">
      <div className="mb-4">
        <h2 className="text-lg font-black text-[var(--foreground)]">{t("title")}</h2>
        <p className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{t("description")}</p>
      </div>
      <PostItemWorkspacePanel postKind="request" postId={postId} postStatus={postStatus} ownerHandle={ownerHandle} returnTo={returnTo} initialItems={initialItems} />
    </section>
  );
}

function normalizeHandle(value: string) {
  return value.replace(/^@+/, "").trim().toLowerCase();
}
