"use client";

import {ClientOnlyMount} from "@/components/client-only-mount";
import {WorkbenchSkeleton} from "@/components/page-skeletons";
import {PageContainer} from "@/components/ui/page-layout";
import {WorkbenchPanel} from "@/components/workbench/workbench-panel";

export function WorkbenchPageClient() {
  return (
    <ClientOnlyMount fallback={<WorkbenchSkeleton />}>
      <PageContainer width="full">
        <WorkbenchPanel />
      </PageContainer>
    </ClientOnlyMount>
  );
}
