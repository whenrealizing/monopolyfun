"use client";

import {useSearchParams} from "next/navigation";

import {ClientOnlyMount} from "@/components/client-only-mount";
import {PublishSkeleton} from "@/components/page-skeletons";
import {readPublishType} from "@/components/publish-shared";
import {PublishWorkspaceLoader} from "@/components/publish-workspace-loader";
import {PageContainer} from "@/components/ui/page-layout";

export function PublishPageClient() {
  const searchParams = useSearchParams();
  const initialType = readPublishType(searchParams.get("type"));

  return (
    <ClientOnlyMount fallback={<PublishSkeleton />}>
      <PageContainer width="full">
        <PublishWorkspaceLoader initialType={initialType} />
      </PageContainer>
    </ClientOnlyMount>
  );
}
