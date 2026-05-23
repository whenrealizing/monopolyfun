"use client";

import {Suspense} from "react";

import {ClientOnlyMount} from "@/components/client-only-mount";
import {IdentityPageClient} from "@/components/identity-page-client";
import {IdentitySkeleton} from "@/components/page-skeletons";

export function ProfilePageClient() {
  return (
    <ClientOnlyMount fallback={<IdentitySkeleton />}>
      <Suspense fallback={<IdentitySkeleton />}>
        <IdentityPageClient />
      </Suspense>
    </ClientOnlyMount>
  );
}
